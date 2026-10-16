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

    override fun writeFile(fd: ParcelFileDescriptor, path: String): Long = try {
        File(path).parentFile?.mkdirs()
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
            File(path).outputStream().use { input.copyTo(it) }
        }
    } catch (e: Exception) {
        -1
    }
}
