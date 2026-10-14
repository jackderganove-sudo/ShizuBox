package de.emil.shizubox.system

import de.emil.shizubox.shizuku.ShizukuManager

data class ProcessEntry(
    val name: String,
    val pid: Int,
    val pssKb: Long,
    /** OOM group from meminfo, e.g. "Visible" or "Cached". */
    val group: String,
    val userId: Int,
) {
    /** Package for app processes ("com.foo:remote" → "com.foo"); null for native ones. */
    val pkg: String? get() = name.substringBefore(':').takeIf { '.' in it && !it.startsWith('/') }
}

data class MemSummary(val totalKb: Long, val freeKb: Long, val usedKb: Long)

data class ProcessSnapshot(val processes: List<ProcessEntry>, val summary: MemSummary?)

/**
 * Parses `dumpsys meminfo` (tested on Android 17): the "Total PSS by OOM adjustment" section
 * lists groups with their processes indented below, e.g. `  1,411,087K: Visible` followed by
 * `        201,976K: com.snapchat.android (pid 18863) (user 10)`.
 */
object Processes {
    private val entry = Regex("""^\s*([\d,]+)K: (.+?) \(pid (\d+)[^)]*\)(?: \(user (\d+)\))?""")
    private val group = Regex("""^\s*([\d,]+)K: ([^()]+)$""")

    suspend fun snapshot(): ProcessSnapshot {
        val lines = ShizukuManager.exec("dumpsys meminfo", timeoutMs = 20_000).out.lines()
        val start = lines.indexOfFirst { it.startsWith("Total PSS by OOM adjustment") }
        val list = mutableListOf<ProcessEntry>()
        if (start >= 0) {
            var current = ""
            for (line in lines.drop(start + 1)) {
                if (line.isBlank()) break
                val e = entry.find(line)
                if (e != null) {
                    list += ProcessEntry(
                        name = e.groupValues[2],
                        pid = e.groupValues[3].toInt(),
                        pssKb = e.groupValues[1].replace(",", "").toLong(),
                        group = current,
                        userId = e.groupValues[4].toIntOrNull() ?: 0,
                    )
                } else {
                    group.find(line)?.let { current = it.groupValues[2].trim() }
                }
            }
        }
        fun kb(prefix: String) = lines.firstOrNull { it.trimStart().startsWith(prefix) }
            ?.substringAfter(": ")?.substringBefore("K")?.replace(",", "")?.trim()?.toLongOrNull()
        val total = kb("Total RAM")
        val free = kb("Free RAM")
        val used = kb("Used RAM")
        return ProcessSnapshot(
            processes = list.sortedByDescending { it.pssKb },
            summary = if (total != null && free != null && used != null) MemSummary(total, free, used) else null,
        )
    }

    suspend fun forceStop(pkg: String, userId: Int) = ShizukuManager.exec("am force-stop --user $userId $pkg")

    /** Kills all cached background processes (nothing visible). */
    suspend fun killBackground() = ShizukuManager.exec("am kill-all")
}
