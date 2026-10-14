package de.emil.shizubox.shizuku

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

class ShizukuUnavailableException : Exception("Shizuku ist nicht verbunden")

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

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(appContext.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            binding = false
            if (binder != null && binder.pingBinder()) {
                service = IShellService.Stub.asInterface(binder)
            }
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            binding = false
            refresh()
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            binding = false
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

    private fun bindService() {
        if (binding) return
        binding = true
        try {
            Shizuku.bindUserService(serviceArgs, connection)
        } catch (e: Exception) {
            Log.e(TAG, "bindUserService failed", e)
            binding = false
        }
    }

    /** Waits up to [timeoutMs] for the shell service and returns it, or null. */
    private suspend fun awaitService(timeoutMs: Long): IShellService? {
        service?.let { return it }
        return withTimeoutOrNull(timeoutMs) {
            _status.first { it == ShizukuStatus.Ready }
            service
        }
    }

    val isReady get() = _status.value == ShizukuStatus.Ready

    /** Runs [cmd] with ADB rights. Throws [ShizukuUnavailableException] if Shizuku is not connected. */
    suspend fun exec(cmd: String, timeoutMs: Long = 3000): ShellResult = withContext(Dispatchers.IO) {
        val svc = awaitService(timeoutMs) ?: throw ShizukuUnavailableException()
        val raw = try {
            svc.exec(cmd)
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
