package de.emil.shizubox.cleaner

import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.t

/** Which tool found / removes something (shown in the trash and the log). */
enum class CleanTool(val labelRes: Int) {
    Corpses(R.string.cl_corpses),
    System(R.string.cl_system),
    AppCache(R.string.cl_appcache),
    Duplicates(R.string.cl_dupes),
    Analysis(R.string.cl_analysis),
    Auto(R.string.cl_auto),
    ;
    val label get() = t(labelRes)
}

/**
 * The last line of defence: every path is checked here right before it is moved to the trash,
 * whatever tool found it. Fixed rules (cannot be switched off):
 * - only inside the internal shared storage, never the storage root or a standard folder itself
 * - never the trash, never anything of Shizuku
 * - `Android/data` and `Android/obb` of installed apps (any user/profile, also apps uninstalled
 *   with "keep data") are locked – except their `cache` folder for the app cleaner
 * - inside `Android/` only app folders, nothing else
 * plus the user's exclusions (paths and apps).
 */
object Guard {
    const val SELF = "de.emil.shizubox"
    const val AICORE = "com.google.android.aicore"
    private val SHIZUKU = setOf("moe.shizuku.privileged.api", "moe.shizuku.manager")
    val PKG = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")

    val STANDARD = setOf(
        "Android", "DCIM", "Download", "Pictures", "Movies", "Music", "Documents", "Alarms", "Audiobooks",
        "Notifications", "Podcasts", "Recordings", "Ringtones", "DCIM/Camera", "Pictures/Screenshots",
        "Android/data", "Android/obb", "Android/media",
    )
    val TRASH_DIRS = listOf("${Fs.ROOT}/.ShizuBoxTrash", "${Fs.DATA}/.ShizuBoxTrash", "${Fs.OBB}/.ShizuBoxTrash")

    @Volatile private var installed: Set<String> = emptySet()
    @Volatile private var installedAt = 0L

    /**
     * Packages installed for any user, including "uninstalled but data kept" (`-u`).
     * Android 17: `pm list packages -u --user all` → 462 packages in 0.06 s.
     */
    suspend fun installedPackages(force: Boolean = false): Set<String> {
        if (!force && installed.isNotEmpty() && System.currentTimeMillis() - installedAt < 60_000) return installed
        val r = ShizukuManager.exec("pm list packages -u --user all", 10_000, 60_000)
        val set = r.out.lineSequence().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()
        // An empty or failed list must never make every app folder look orphaned.
        if (r.code != 0 || set.size < 20) error("package list unavailable")
        installed = set
        installedAt = System.currentTimeMillis()
        return set
    }

    fun isInstalled(pkg: String) = pkg in installed || pkg in SHIZUKU || pkg == SELF

    /** null = may be moved to the trash; otherwise a short reason. */
    fun blockReason(path: String, tool: CleanTool, cfg: CleanerConfig = CleanerSettings.current): String? {
        if (!path.startsWith(Fs.ROOT + "/") || path.contains("/../") || path.endsWith("/..") || path.contains("//")) return t(R.string.cl_lock_outside)
        val rel = Fs.rel(path).trimEnd('/')
        if (rel.isEmpty() || rel in STANDARD) return t(R.string.cl_lock_standard)
        if (TRASH_DIRS.any { path == it || path.startsWith("$it/") }) return t(R.string.cl_lock_trash)
        cfg.excludedPaths.firstOrNull { ex -> val e = ex.trim('/'); e.isNotEmpty() && (rel == e || rel.startsWith("$e/")) }
            ?.let { return t(R.string.cl_lock_excluded) }
        if (rel.startsWith("Android/")) {
            val seg = rel.split('/')
            if (seg.size < 3 || seg[1] !in setOf("data", "obb", "media")) return t(R.string.cl_lock_system)
            val pkg = seg[2]
            if (pkg in SHIZUKU) return t(R.string.cl_lock_shizuku)
            if (pkg in cfg.excludedApps) return t(R.string.cl_lock_excluded)
            if (!PKG.matches(pkg)) return t(R.string.cl_lock_system)
            if (isInstalled(pkg)) {
                val inCache = seg[1] == "data" && seg.size >= 4 && seg[3] == "cache"
                return when {
                    pkg == AICORE && !cfg.includeAiCore -> t(R.string.cl_lock_aicore)
                    // The app cleaner empties caches; ShizuBox' own cache counts as its leftovers.
                    inCache && (tool == CleanTool.AppCache || tool == CleanTool.Auto || (pkg == SELF && tool == CleanTool.System)) -> null
                    // Android/media of installed apps is shared media (e.g. WhatsApp pictures), not app data.
                    seg[1] == "media" && seg.size >= 4 && pkg != SELF -> null
                    else -> t(R.string.cl_lock_installed)
                }
            }
            if (pkg == SELF) return t(R.string.cl_lock_installed)
        }
        return null
    }
}
