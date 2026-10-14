package de.emil.shizubox.system

import de.emil.shizubox.shizuku.ShizukuManager

data class LogLine(val level: Char, val text: String)

/** Reads the system log through Shizuku (the shell user has READ_LOGS). */
object Logcat {
    val LEVELS = listOf('V', 'D', 'I', 'W', 'E')

    /**
     * @param level minimum priority
     * @param pkg if set, only lines from this app's process(es)
     */
    suspend fun read(level: Char, pkg: String?, lines: Int = 800): List<LogLine> {
        val pidFilter = if (pkg.isNullOrBlank()) "" else {
            val pids = ShizukuManager.exec("pidof ${pkg.trim()}").out.trim()
            if (pids.isEmpty()) return listOf(LogLine('W', "Prozess ${pkg.trim()} läuft gerade nicht."))
            // logcat accepts a single --pid; take the main process.
            "--pid=${pids.split(' ').first()}"
        }
        val r = ShizukuManager.exec("logcat -d -v time -t $lines $pidFilter '*:$level'", timeoutMs = 5000)
        return r.out.lines()
            .filter { it.isNotBlank() && !it.startsWith("---------") }
            .map { line ->
                // "10-14 20:04:13.198 W/tag(  566): message"
                val lvl = line.getOrNull(19)?.takeIf { it in "VDIWEF" } ?: 'I'
                LogLine(lvl, line)
            }
    }

    suspend fun clear() = ShizukuManager.exec("logcat -c")
}
