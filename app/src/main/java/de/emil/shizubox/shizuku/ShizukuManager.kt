package de.emil.shizubox.shizuku

import de.emil.shizubox.R
import de.emil.shizubox.t
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import de.emil.shizubox.BuildConfig
import de.emil.shizubox.IShellService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

data class ShellResult(val code: Int, val out: String, val err: String) {
    val ok get() = code == 0
}

class ShizukuUnavailableException : Exception(t(R.string.shizuku_not_connected))

enum class ShizukuStatus {
    /** Shizuku app is not installed. */
    NotInstalled,
    /** Installed but the Shizuku server is not running (e.g. after a reboot). */
    NotRunning,
    /** Running, but ShizuBox has not been granted access yet. */
    NoPermission,
    /** The user denied access with "don't ask again"; must be granted in the Shizuku app. */
    PermissionDenied,
    /** Permission granted, shell service is being started. */
    Connecting,
    Ready,
}

object ShizukuManager {
    /** Bump when IShellService changes. */
    private const val SERVICE_REV = 7
    /** Hard limit for a shell command when the caller gives none. */
    const val DEFAULT_LIMIT = 60_000L

    private const val TAG = "ShizukuManager"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val SHIZUKU_PERMISSION_ACTION = "moe.shizuku.privileged.api.intent.action.REQUEST_PERMISSION"
    private const val PERMISSION_REQUEST_CODE = 4711

    private lateinit var appContext: Context

    private val _status = MutableStateFlow(ShizukuStatus.NotRunning)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    /** "ADB" or "Root", depending on how the Shizuku server was started. */
    var serverMode: String? = null
        private set

    @Volatile
    private var service: IShellService? = null
    private var binding = false

    /**
     * Bumped whenever the service died: a new tag makes Shizuku treat the next bind as a new user
     * service. Rebinding under the old tag (and the old connection) started a process whose binder
     * was never delivered – ShizuBox stayed disconnected until it was restarted (stability round 2).
     */
    private var generation = 0

    private fun makeArgs() =
        Shizuku.UserServiceArgs(ComponentName(appContext.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .tag("shell-$generation")
            .debuggable(BuildConfig.DEBUG)
            // + service revision: a changed AIDL needs a fresh service process even within one app version.
            .version(BuildConfig.VERSION_CODE * 100 + SERVICE_REV)

    private var serviceArgs: Shizuku.UserServiceArgs? = null
    private var connection: ServiceConnection? = null

    private fun newConnection(): ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "service connected (generation $generation)")
            if (connection !== this) return
            binding = false
            if (binder != null && binder.pingBinder()) {
                service = IShellService.Stub.asInterface(binder)
                // Shizuku does not always report a dead user service: watch the binder ourselves.
                runCatching { binder.linkToDeath({ serviceDied(binder) }, 0) }
            }
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            if (connection !== this) return
            service?.asBinder()?.let { serviceDied(it) } ?: run { binding = false; refresh() }
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        // Shizuku itself stopped: forget the binding, a restarted Shizuku gets a fresh one.
        Shizuku.addBinderDeadListener {
            dropBinding()
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    fun refresh() {
        val running = Shizuku.pingBinder()
        _status.value = when {
            !running && shizukuPackage == null -> ShizukuStatus.NotInstalled
            !running || Shizuku.isPreV11() -> ShizukuStatus.NotRunning
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                if (Shizuku.shouldShowRequestPermissionRationale()) ShizukuStatus.PermissionDenied
                else ShizukuStatus.NoPermission
            service != null -> ShizukuStatus.Ready
            else -> {
                bindService()
                ShizukuStatus.Connecting
            }
        }
        if (running) serverMode = if (Shizuku.getUid() == 0) "Root" else "ADB"
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    /**
     * Package of the installed Shizuku manager app. Forks use their own package name
     * but still handle Shizuku's permission action, so look it up by that.
     */
    val shizukuPackage: String?
        get() {
            val pm = appContext.packageManager
            val byAction = pm.queryIntentActivities(Intent(SHIZUKU_PERMISSION_ACTION), 0)
                .firstOrNull()?.activityInfo?.packageName
            if (byAction != null) return byAction
            return try {
                pm.getPackageInfo(SHIZUKU_PACKAGE, 0)
                SHIZUKU_PACKAGE
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }

    fun shizukuLaunchIntent(): Intent? =
        shizukuPackage?.let { appContext.packageManager.getLaunchIntentForPackage(it) }

    private fun serviceDied(binder: IBinder) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (service?.asBinder() != binder) return@post
            Log.w(TAG, "shell service died – reconnecting")
            dropBinding()
            refresh()
        }
    }

    /** Forgets the current binding; the next bind uses a new connection and a new tag. */
    private fun dropBinding() {
        val args = serviceArgs
        val conn = connection
        service = null
        binding = false
        serviceArgs = null
        connection = null
        generation++
        if (args != null && conn != null) runCatching { Shizuku.unbindUserService(args, conn, true) }
    }

    private var connectingSince = 0L

    private fun bindService() {
        if (binding) return
        binding = true
        try {
            val args = serviceArgs ?: makeArgs().also { serviceArgs = it }
            val conn = connection ?: newConnection().also { connection = it }
            Shizuku.bindUserService(args, conn)
        } catch (e: Exception) {
            Log.e(TAG, "bindUserService failed", e)
            binding = false
        }
    }

    /** Waits up to [timeoutMs] for the shell service and returns it, or null. */
    private suspend fun awaitService(timeoutMs: Long): IShellService? {
        service?.let { return it }
        val got = withTimeoutOrNull(timeoutMs) {
            _status.first { it == ShizukuStatus.Ready }
            service
        }
        if (got == null) rebindIfStuck()
        return got
    }

    /** Still "connecting" after 10 s although Shizuku runs: drop the binding and start over. */
    private fun rebindIfStuck() {
        if (_status.value != ShizukuStatus.Connecting) { connectingSince = 0; return }
        val now = android.os.SystemClock.elapsedRealtime()
        if (connectingSince == 0L) { connectingSince = now; return }
        if (now - connectingSince < 10_000) return
        connectingSince = 0
        Log.w(TAG, "shell service stuck connecting – rebinding")
        android.os.Handler(android.os.Looper.getMainLooper()).post { dropBinding(); refresh() }
    }

    val isReady get() = _status.value == ShizukuStatus.Ready

    /** Clipboard changes are reported to [callback] until [unwatchClipboard] (or ShizuBox dies). */
    suspend fun watchClipboard(callback: de.emil.shizubox.IClipCallback): Boolean = withContext(Dispatchers.IO) {
        val svc = awaitService(3000) ?: return@withContext false
        runCatching { svc.watchClipboard(callback) }.onFailure { Log.e(TAG, "watchClipboard failed", it) }.isSuccess
    }

    /** Players right now: "uid|piid|state|usage" per line (state 2 = playing). */
    suspend fun listPlayers(): String = withContext(Dispatchers.IO) {
        runCatching { awaitService(3000)?.listPlayers() }.getOrNull().orEmpty()
    }

    suspend fun setAppVolume(uid: Int, volume: Float): Boolean = withContext(Dispatchers.IO) {
        runCatching { awaitService(3000)?.setAppVolume(uid, volume) }.isSuccess
    }

    suspend fun unwatchClipboard() = withContext(Dispatchers.IO) {
        runCatching { awaitService(1000)?.unwatchClipboard() }
    }

    /**
     * Output of [cmd] as records split at [sep] (NUL for `find -printf '…\0'`, so any file name
     * works), without the binder size limit. Leaving [block] or cancelling the caller closes the
     * pipe, and the service then kills the command. Throws [ShizukuUnavailableException].
     */
    suspend fun <T> execRecords(cmd: String, limitMs: Long = DEFAULT_LIMIT, sep: Char = '\u0000', block: (Sequence<String>) -> T): T = withContext(Dispatchers.IO) {
        val svc = awaitService(3000) ?: throw ShizukuUnavailableException()
        val pfd = try {
            svc.execStream(cmd, limitMs)
        } catch (e: Exception) {
            Log.e(TAG, "execStream failed: $cmd", e)
            throw ShizukuUnavailableException()
        }
        val ctx = coroutineContext
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader(Charsets.UTF_8).use { r ->
            val records = sequence {
                val sb = StringBuilder()
                val buf = CharArray(16 * 1024)
                while (ctx.isActive) {
                    val n = r.read(buf)
                    if (n < 0) break
                    for (i in 0 until n) {
                        if (buf[i] == sep) { yield(sb.toString()); sb.setLength(0) } else sb.append(buf[i])
                    }
                }
                if (sb.isNotEmpty() && ctx.isActive) yield(sb.toString())
            }
            block(records)
        }.also { ctx.ensureActive() }
    }

    /** HiLight command in the shell service (JSON, see hilight/). */
    suspend fun hilight(json: String, timeoutMs: Long = 3000): String = withContext(Dispatchers.IO) {
        val svc = awaitService(timeoutMs) ?: throw ShizukuUnavailableException()
        try { svc.hilight(json) } catch (e: Exception) { throw ShizukuUnavailableException() }
    }

    suspend fun watchPrivacy(callback: de.emil.shizubox.IHiLightCallback): Boolean = withContext(Dispatchers.IO) {
        val svc = awaitService(3000) ?: return@withContext false
        runCatching { svc.watchPrivacy(callback) }.onFailure { Log.e(TAG, "watchPrivacy failed", it) }.isSuccess
    }

    suspend fun unwatchPrivacy() = withContext(Dispatchers.IO) { runCatching { awaitService(1000)?.unwatchPrivacy() } }

    /** Pure rename as the Shizuku user (never a copy). Returns null on success, else the error. */
    suspend fun rename(from: String, to: String): String? = withContext(Dispatchers.IO) {
        val svc = awaitService(3000) ?: throw ShizukuUnavailableException()
        val r = try {
            svc.rename(from, to)
        } catch (e: Exception) {
            throw ShizukuUnavailableException()
        }
        r.ifEmpty { null }
    }

    /** Copies [fd] to [path] as the Shizuku user. Returns bytes written or -1. */
    suspend fun writeFile(fd: android.os.ParcelFileDescriptor, path: String): Long = withContext(Dispatchers.IO) {
        val svc = awaitService(3000) ?: throw ShizukuUnavailableException()
        try {
            svc.writeFile(fd, path)
        } catch (e: Exception) {
            Log.e(TAG, "writeFile failed: $path", e)
            -1L
        }
    }

    /**
     * Runs [cmd] with ADB rights. Throws [ShizukuUnavailableException] if Shizuku is not connected.
     * [timeoutMs] is how long to wait for Shizuku; [limitMs] is a hard limit for the command itself –
     * after that it is killed with its children and the result has code 124.
     */
    suspend fun exec(cmd: String, timeoutMs: Long = 3000, limitMs: Long = maxOf(timeoutMs, DEFAULT_LIMIT)): ShellResult = withContext(Dispatchers.IO) {
        val svc = awaitService(timeoutMs) ?: throw ShizukuUnavailableException()
        val raw = try {
            svc.execLimited(cmd, limitMs)
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: $cmd", e)
            throw ShizukuUnavailableException()
        }
        val parts = raw.split('\u0000', limit = 3)
        ShellResult(
            code = parts[0].toIntOrNull() ?: -1,
            out = parts.getOrElse(1) { "" }.trimEnd(),
            err = parts.getOrElse(2) { "" }.trimEnd(),
        ).also { Log.d(TAG, "$ $cmd -> ${it.code}") }
    }
}
