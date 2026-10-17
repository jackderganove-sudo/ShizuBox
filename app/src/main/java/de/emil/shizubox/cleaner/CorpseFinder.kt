package de.emil.shizubox.cleaner

import de.emil.shizubox.R
import de.emil.shizubox.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class CorpseZone(val rel: String) { Data("Android/data"), Obb("Android/obb"), Media("Android/media"), Storage("") }

/**
 * Leftovers of uninstalled apps. Careful by design – when in doubt, a folder is not shown:
 * - `Android/data|obb|media/<name>`: only if <name> is a valid package name and that package is
 *   installed for no user/profile, not even "uninstalled, data kept" (`pm list packages -u --user all`;
 *   an incomplete list aborts the scan instead of showing everything)
 * - top-level folders of the storage: only those named like a package (two dots or more) or known
 *   folders of specific apps (small hand-checked list) whose apps are all gone
 * - Shizuku, ShizuBox, hidden folders and anything the [Guard] locks or the user excluded: never
 */
object CorpseFinder {
    private val _result = MutableStateFlow<Map<CorpseZone, List<Finding>>?>(null)
    val result: StateFlow<Map<CorpseZone, List<Finding>>?> = _result.asStateFlow()

    fun clear(paths: Collection<String>) {
        val set = paths.toSet()
        _result.value = _result.value?.mapValues { (_, l) -> l.filterNot { it.path in set } }
    }

    suspend fun scan(report: (String) -> Unit = {}): Map<CorpseZone, List<Finding>> = withContext(Dispatchers.IO) {
        report(t(R.string.cf_progress_pkgs))
        val installed = Guard.installedPackages(force = true)
        val names = de.emil.shizubox.updates.Updates.apps.value.filter { it.id.isNotEmpty() }.associate { it.id to it.name }
        report(t(R.string.cf_progress_dirs))
        val found = ArrayList<Pair<CorpseZone, Finding>>()
        val appZones = listOf(CorpseZone.Data to Fs.DATA, CorpseZone.Obb to Fs.OBB, CorpseZone.Media to Fs.MEDIA)
        Fs.find(appZones.map { it.second }, "-mindepth 1 -maxdepth 1 -type d") { seq ->
            for (e in seq) {
                val pkg = e.name
                if (pkg.startsWith(".") || !Guard.PKG.matches(pkg) || pkg in installed || Guard.isInstalled(pkg)) continue
                val zone = appZones.first { e.parent == it.second }.first
                found += zone to Finding(e.path, 0, e.mtime, true, note = names[pkg]?.let { "$it · $pkg" } ?: pkg)
            }
        }
        Fs.find(listOf(Fs.ROOT + "/"), "-mindepth 1 -maxdepth 1 -type d") { seq ->
            for (e in seq) {
                val n = e.name
                if (n.startsWith(".") || n == "Android") continue
                val known = Guard.APP_FOLDERS[n]
                val note = when {
                    known != null && known.second.none { it in installed } -> t(R.string.cf_known, known.first)
                    known == null && n.count { it == '.' } >= 2 && Guard.PKG.matches(n) && n !in installed && !Guard.isInstalled(n) -> names[n]?.let { "$it · $n" } ?: n
                    else -> null
                } ?: continue
                found += CorpseZone.Storage to Finding(e.path, 0, e.mtime, true, note = note)
            }
        }
        report(t(R.string.cf_progress_sizes))
        val sizes = if (found.isEmpty()) emptyMap() else Fs.sizes(found.map { it.second.path })
        val res = CorpseZone.entries.associateWith { z ->
            found.filter { it.first == z }.map { it.second.copy(size = sizes[it.second.path] ?: 0L) }
                .filter { Guard.blockReason(it.path, CleanTool.Corpses) == null }
                .sortedByDescending { it.size }
        }
        _result.value = res
        res
    }

    suspend fun clean(findings: List<Finding>, onProgress: (Int, Int) -> Unit = { _, _ -> }): MoveResult {
        // Re-checked right before moving: an app may have been installed since the scan.
        Guard.installedPackages(force = true)
        val r = Trash.move(findings.map { Candidate(it.path, it.size, true) }, CleanTool.Corpses, onProgress)
        clear(findings.map { it.path })
        return r
    }
}
