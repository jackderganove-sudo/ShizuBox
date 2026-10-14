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

    override fun writeFile(fd: ParcelFileDescriptor, path: String): Long = try {
        File(path).parentFile?.mkdirs()
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
            File(path).outputStream().use { input.copyTo(it) }
        }
    } catch (e: Exception) {
        -1
    }
}
