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
 * plus the user's exclusions (paths and apps – an excluded app also protects its known folders in the
 * main storage, e.g. /WhatsApp, and a top-level folder named like it, 0.11.1).
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

    /**
     * Known top-level folders of specific apps (hand-checked): folder → app name and its packages.
     * Used by the corpse finder and for the user's app exclusions.
     */
    val APP_FOLDERS = mapOf(
        "WhatsApp" to ("WhatsApp" to listOf("com.whatsapp", "com.whatsapp.w4b")),
        "Telegram" to ("Telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web")),
        "tencent" to ("WeChat / QQ" to listOf("com.tencent.mm", "com.tencent.mobileqq")),
        "MT2" to ("MT Manager" to listOf("bin.mt.plus", "bin.mt.plus.canary")),
        "Snapseed" to ("Snapseed" to listOf("com.niksoftware.snapseed")),
        "Viber" to ("Viber" to listOf("com.viber.voip")),
        "TitaniumBackup" to ("Titanium Backup" to listOf("com.keramidas.TitaniumBackup")),
        "osmand" to ("OsmAnd" to listOf("net.osmand", "net.osmand.plus")),
        "SHAREit" to ("SHAREit" to listOf("com.lenovo.anyshare.gps")),
        "UCDownloads" to ("UC Browser" to listOf("com.UCMobile.intl")),
        // Aliucord can also keep the package name of the Discord app it patches.
        "Aliucord" to ("Aliucord" to listOf("com.aliucord", "com.aliucord.manager", "com.discord")),
        "VidMate" to ("VidMate" to listOf("com.nemo.vidmate")),
        "Tachiyomi" to ("Tachiyomi" to listOf("eu.kanade.tachiyomi")),
        "SwiftBackup" to ("Swift Backup" to listOf("org.swiftapps.swiftbackup")),
    )

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
        if (excludedByApp(rel, cfg.excludedApps)) return t(R.string.cl_lock_excluded)
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

/**
 * Is the first folder of [rel] one of an excluded app outside `Android/`? Known app folders
 * ([Guard.APP_FOLDERS], e.g. "WhatsApp") and folders named like the package itself.
 * (`Android/data|obb|media/<pkg>` is checked in [Guard.blockReason].)
 */
internal fun excludedByApp(rel: String, excludedApps: Set<String>): Boolean {
    if (excludedApps.isEmpty()) return false
    val top = rel.substringBefore('/')
    if (top == "Android") return false
    if (top in excludedApps) return true
    val known = Guard.APP_FOLDERS.entries.firstOrNull { it.key.equals(top, ignoreCase = true) }?.value ?: return false
    return known.second.any { it in excludedApps }
}
