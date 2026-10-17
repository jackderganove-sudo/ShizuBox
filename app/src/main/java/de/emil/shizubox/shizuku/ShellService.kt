package de.emil.shizubox.shizuku

import android.os.ParcelFileDescriptor
import de.emil.shizubox.IShellService
import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Runs inside a separate process started by Shizuku, with the Shizuku user's identity
 * (uid 2000 "shell" when Shizuku was started via ADB). Everything here has ADB rights.
 */
class ShellService : IShellService.Stub() {
    private companion object {
        /** Characters; UTF-16 in the parcel, so 256 K chars ≈ 512 KB – well below the 1 MB binder limit. */
        const val MAX_OUT = 256 * 1024
    }

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(cmd: String): String {
        val process = ProcessBuilder("sh", "-c", cmd).start()
        process.outputStream.close()
        var err = ""
        val errReader = thread { err = process.errorStream.bufferedReader().readText() }
        val out = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        errReader.join()
        return "$code\u0000$out\u0000$err"
    }

    /**
     * A command that never returns (e.g. `pm clear --cache-only` on Android 17) used to block the
     * binder thread – and with it a thread in ShizuBox – forever; every further call stacked up
     * another blocked thread. Now the whole process tree is killed after [limitMs].
     */
    override fun execLimited(cmd: String, limitMs: Long): String {
        val process = ProcessBuilder("sh", "-c", cmd).start()
        process.outputStream.close()
        var out = ""
        var err = ""
        val outReader = thread { out = runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("") }
        val errReader = thread { err = runCatching { process.errorStream.bufferedReader().readText() }.getOrDefault("") }
        val done = process.waitFor(limitMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        if (!done) {
            pidOf(process)?.let(::killTree)
            process.destroyForcibly()
        }
        outReader.join(2000)
        errReader.join(2000)
        // A binder reply over ~1 MB fails as a whole (TransactionTooLargeException) and looks like
        // a lost Shizuku connection to the caller – cut long output instead and say so.
        val o = if (out.length > MAX_OUT) out.take(MAX_OUT) else out
        val e = (if (err.length > 64_000) err.take(64_000) else err) + if (out.length > MAX_OUT) "\n[ShizuBox] output cut at ${MAX_OUT / 1024} KB" else ""
        if (!done) return "124\u0000$o\u0000${e}\n[ShizuBox] killed after ${limitMs / 1000} s"
        return "${process.exitValue()}\u0000$o\u0000$e"
    }

    private fun pidOf(p: Process): Int? = runCatching {
        p.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getInt(p)
    }.getOrNull()

    /** Kills [root] and all its descendants (children first), found through /proc. */
    private fun killTree(root: Int) {
        val parents = File("/proc").listFiles().orEmpty().mapNotNull { d ->
            val pid = d.name.toIntOrNull() ?: return@mapNotNull null
            // /proc/<pid>/stat: "pid (comm) state ppid …" – comm may contain spaces, so split after ')'.
            val ppid = runCatching { File(d, "stat").readText().substringAfterLast(')').trim().split(' ')[1].toInt() }.getOrNull()
            ppid?.let { pid to it }
        }
        val tree = mutableListOf(root)
        var i = 0
        while (i < tree.size) {
            val p = tree[i++]
            parents.filter { it.second == p }.forEach { tree += it.first }
        }
        tree.asReversed().forEach { android.os.Process.killProcess(it) }
        // killProcess only works for own-UID processes, which these all are (shell); also send SIGKILL by name.
        tree.asReversed().forEach { runCatching { android.os.Process.sendSignal(it, 9) } }
    }

    // ------------------------------------------------------------------ clipboard

    private var clipListener: android.os.Binder? = null
    private var clipCallback: de.emil.shizubox.IClipCallback? = null

    /** IClipboard via reflection (hidden API; fine in this shell process). */
    private fun clipboard(): Any {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "clipboard") as android.os.IBinder
        return Class.forName("android.content.IClipboard\$Stub").getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)!!
    }

    /** Fills a hidden method's parameters: package "com.android.shell", no attribution tag, user 0, default device. */
    private fun args(m: java.lang.reflect.Method, vararg leading: Any?): Array<Any?> {
        var strings = 0
        var ints = 0
        return m.parameterTypes.mapIndexed { i, type ->
            when {
                i < leading.size -> leading[i]
                type == String::class.java -> if (strings++ == 0) "com.android.shell" else null
                type == Int::class.javaPrimitiveType -> if (ints++ == 0) 0 else 0
                else -> null
            }
        }.toTypedArray()
    }

    private fun readClip() {
        val cb = clipCallback ?: return
        runCatching {
            val c = clipboard()
            val get = c.javaClass.methods.first { it.name == "getPrimaryClip" }
            val clip = get.invoke(c, *args(get)) as? android.content.ClipData ?: return
            val source = runCatching {
                val m = c.javaClass.methods.first { it.name == "getPrimaryClipSource" }
                m.invoke(c, *args(m)) as? String
            }.getOrNull()
            val sensitive = clip.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
            val item = if (clip.itemCount > 0) clip.getItemAt(0) else null
            val text = item?.text?.toString() ?: item?.uri?.toString() ?: item?.intent?.toUri(0) ?: return
            cb.onClip(if (sensitive) "" else text, source.orEmpty(), sensitive, System.currentTimeMillis())
        }
    }

    override fun watchClipboard(callback: de.emil.shizubox.IClipCallback) {
        unwatchClipboard()
        clipCallback = callback
        val listener = object : android.os.Binder() {
            override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
                if (code == FIRST_CALL_TRANSACTION) {
                    // dispatchPrimaryClipChanged(): read it on another thread, the binder thread must return.
                    thread { readClip() }
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }
        val proxy = Class.forName("android.content.IOnPrimaryClipChangedListener\$Stub").getMethod("asInterface", android.os.IBinder::class.java).invoke(null, listener)
        val c = clipboard()
        val add = c.javaClass.methods.first { it.name == "addPrimaryClipChangedListener" }
        add.invoke(c, *args(add, proxy))
        clipListener = listener
        // If ShizuBox dies, stop listening.
        runCatching { callback.asBinder().linkToDeath({ unwatchClipboard() }, 0) }
    }

    override fun unwatchClipboard() {
        val l = clipListener ?: return
        clipListener = null
        clipCallback = null
        runCatching {
            val proxy = Class.forName("android.content.IOnPrimaryClipChangedListener\$Stub").getMethod("asInterface", android.os.IBinder::class.java).invoke(null, l)
            val c = clipboard()
            val rm = c.javaClass.methods.first { it.name == "removePrimaryClipChangedListener" }
            rm.invoke(c, *args(rm, proxy))
        }
    }

    // ------------------------------------------------------------------ volume per app

    private val appVolumes = java.util.concurrent.ConcurrentHashMap<Int, Float>()
    private var playbackListener: android.os.Binder? = null

    private fun audio(): Any {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "audio") as android.os.IBinder
        return Class.forName("android.media.IAudioService\$Stub").getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun configs(): List<Any> = runCatching {
        val a = audio()
        a.javaClass.getMethod("getActivePlaybackConfigurations").invoke(a) as List<Any>
    }.getOrDefault(emptyList())

    private fun uidOf(c: Any) = c.javaClass.getMethod("getClientUid").invoke(c) as Int

    override fun listPlayers(): String = configs().joinToString("\n") { c ->
        val piid = c.javaClass.getMethod("getPlayerInterfaceId").invoke(c)
        val state = c.javaClass.getMethod("getPlayerState").invoke(c)
        val usage = (c.javaClass.getMethod("getAudioAttributes").invoke(c) as android.media.AudioAttributes).usage
        "${uidOf(c)}|$piid|$state|$usage"
    }

    /** Sets the player volume of every player of [only] (or of all configured apps). */
    private fun apply(only: Int? = null) {
        for (c in configs()) {
            val uid = uidOf(c)
            if (only != null && uid != only) continue
            val v = appVolumes[uid] ?: if (uid == only) 1f else continue
            runCatching {
                val proxy = c.javaClass.getMethod("getPlayerProxy").invoke(c) ?: return@runCatching
                proxy.javaClass.getMethod("setVolume", Float::class.javaPrimitiveType).invoke(proxy, v)
            }
        }
    }

    override fun setAppVolume(uid: Int, volume: Float) {
        if (volume >= 0.999f) appVolumes.remove(uid) else appVolumes[uid] = volume.coerceIn(0f, 1f)
        apply(uid)
        if (appVolumes.isNotEmpty() && playbackListener == null) {
            // dispatchPlaybackConfigChange(): a player started/changed – it begins at volume 1 again.
            val listener = object : android.os.Binder() {
                override fun onTransact(code: Int, data: android.os.Parcel, reply: android.os.Parcel?, flags: Int): Boolean {
                    if (code == FIRST_CALL_TRANSACTION) {
                        thread { apply() }
                        return true
                    }
                    return super.onTransact(code, data, reply, flags)
                }
            }
            runCatching {
                val dispatcher = Class.forName("android.media.IPlaybackConfigDispatcher\$Stub").getMethod("asInterface", android.os.IBinder::class.java).invoke(null, listener)
                val a = audio()
                a.javaClass.methods.first { it.name == "registerPlaybackCallback" }.invoke(a, dispatcher)
                playbackListener = listener
            }
        }
    }

    // ------------------------------------------------------------------ files (cleaner)

    override fun execStream(cmd: String, limitMs: Long): ParcelFileDescriptor {
        val (read, write) = ParcelFileDescriptor.createPipe()
        val process = ProcessBuilder("sh", "-c", "($cmd) 2>/dev/null").start()
        process.outputStream.close()
        thread {
            // Reader gone (EPIPE) or finished: either way the command must not keep running.
            runCatching { ParcelFileDescriptor.AutoCloseOutputStream(write).use { process.inputStream.copyTo(it, 64 * 1024) } }
            if (process.isAlive) {
                pidOf(process)?.let(::killTree)
                process.destroyForcibly()
            }
        }
        thread {
            if (!process.waitFor(limitMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                pidOf(process)?.let(::killTree)
                process.destroyForcibly()
            }
        }
        return read
    }

    override fun rename(from: String, to: String): String = try {
        File(to).parentFile?.mkdirs()
        android.system.Os.rename(from, to)
        ""
    } catch (e: Exception) {
        e.message ?: e.javaClass.simpleName
    }

    override fun writeFile(fd: ParcelFileDescriptor, path: String): Long = try {
        File(path).parentFile?.mkdirs()
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
            File(path).outputStream().use { input.copyTo(it) }
        }
    } catch (e: Exception) {
        -1
    }
}
