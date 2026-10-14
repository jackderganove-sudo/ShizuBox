/*
 * App updates modelled on Obtainium / ObtainX (GPL-3.0). Own Kotlin implementation.
 */
package de.emil.shizubox.updates

import de.emil.shizubox.R
import de.emil.shizubox.t
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import de.emil.shizubox.installer.ApkParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateState { NotInstalled, Update, UpToDate, Unknown }

data class UpdateSettings(
    /** Hours between background checks, 0 = off. */
    val intervalHours: Int = 24,
    val autoInstall: Boolean = false,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val checkOnStart: Boolean = true,
    val prereleasesByDefault: Boolean = false,
    val githubToken: String = "",
    /** Background installs only after a clean VirusTotal result (needs the API key). */
    val virusTotalGate: Boolean = true,
)

object Updates {
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var file: File
    private val saveLock = Mutex()

    private val _apps = MutableStateFlow<List<TrackedApp>>(emptyList())
    val apps: StateFlow<List<TrackedApp>> = _apps.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _settings = MutableStateFlow(UpdateSettings())
    val settings: StateFlow<UpdateSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val dp = appContext.createDeviceProtectedStorageContext()
        prefs = dp.getSharedPreferences("updates", Context.MODE_PRIVATE)
        file = File(dp.filesDir, "tracked_apps.json")
        _apps.value = runCatching { parseList(file.readText()) }.getOrDefault(emptyList())
        _settings.value = UpdateSettings(
            intervalHours = prefs.getInt("interval", 24),
            autoInstall = prefs.getBoolean("auto_install", false),
            wifiOnly = prefs.getBoolean("wifi_only", true),
            chargingOnly = prefs.getBoolean("charging_only", false),
            checkOnStart = prefs.getBoolean("check_on_start", true),
            prereleasesByDefault = prefs.getBoolean("prereleases", false),
            githubToken = prefs.getString("github_token", "").orEmpty(),
            virusTotalGate = prefs.getBoolean("vt_gate", true),
        )
        Source.GitHub.token = _settings.value.githubToken
    }

    fun setSettings(s: UpdateSettings) {
        _settings.value = s
        Source.GitHub.token = s.githubToken
        prefs.edit()
            .putInt("interval", s.intervalHours)
            .putBoolean("auto_install", s.autoInstall)
            .putBoolean("wifi_only", s.wifiOnly)
            .putBoolean("charging_only", s.chargingOnly)
            .putBoolean("check_on_start", s.checkOnStart)
            .putBoolean("prereleases", s.prereleasesByDefault)
            .putString("github_token", s.githubToken)
            .putBoolean("vt_gate", s.virusTotalGate)
            .apply()
        UpdateWorker.schedule(appContext)
    }

    // ------------------------------------------------------------- state

    fun installedVersionName(pkg: String): String? = if (pkg.isBlank()) null else runCatching {
        appContext.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
    }.getOrNull()

    private fun installedSince(pkg: String): Long = runCatching {
        appContext.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
    }.getOrDefault(0)

    fun state(app: TrackedApp): UpdateState {
        val real = installedVersionName(app.id) ?: return UpdateState.NotInstalled
        val latest = app.latestVersion ?: return UpdateState.Unknown
        if (app.installedVersion == latest) return UpdateState.UpToDate
        // Like Obtainium's standard version detection: compare the numeric cores.
        Versions.compare(latest, real)?.let { return if (it > 0) UpdateState.Update else UpdateState.UpToDate }
        // Versions not comparable (tag like "nightly"): fall back to what ShizuBox installed.
        return if (app.installedVersion == null) UpdateState.Unknown else UpdateState.Update
    }

    fun updateCount() = _apps.value.count { state(it) == UpdateState.Update }

    // ------------------------------------------------------------- editing

    suspend fun add(rawUrl: String): TrackedApp {
        val url = Source.normalize(rawUrl)
        val source = Source.forUrl(url) ?: throw SourceException(t(R.string.e_unsupported))
        _apps.value.firstOrNull { it.url.equals(url, ignoreCase = true) }?.let { throw SourceException(t(R.string.e_tracked, it.name)) }
        val draft = TrackedApp(id = "", url = url, name = "", author = "", includePrereleases = _settings.value.prereleasesByDefault)
        return addResolved(url, source.latest(draft))
    }

    /** Adds an app whose latest release was already fetched (bulk import). */
    suspend fun addResolved(rawUrl: String, release: Release): TrackedApp {
        val url = Source.normalize(rawUrl)
        val source = Source.forUrl(url) ?: throw SourceException(t(R.string.e_unsupported))
        _apps.value.firstOrNull { it.url.equals(url, ignoreCase = true) }?.let { throw SourceException(t(R.string.e_tracked, it.name)) }
        val draft = TrackedApp(id = "", url = url, name = "", author = "", includePrereleases = _settings.value.prereleasesByDefault)
        val id = release.appId ?: guessPackage(release.name)
        if (release.apks.isEmpty() && !source.trackOnly) throw SourceException(t(R.string.e_no_apk_rel))
        val real = installedVersionName(id)
        val app = draft.copy(
            id = id,
            // F-Droid's API has no app names: prefer the installed app's label.
            name = if (source is Source.FDroidLike) label(id) ?: release.name else release.name,
            author = release.author,
            latestVersion = release.version,
            apks = release.apks,
            releaseDate = release.date,
            changeLog = release.changeLog,
            releaseUrl = release.releaseUrl,
            lastCheck = System.currentTimeMillis(),
            // Already installed in exactly this version? Then it counts as installed.
            installedVersion = if (real != null && Versions.compare(release.version, real) == 0) release.version else null,
        )
        _apps.update { it + app }
        save()
        return app
    }

    fun update(app: TrackedApp) {
        _apps.update { list -> list.map { if (it.key == app.key) app else it } }
        saveAsync()
    }

    fun remove(app: TrackedApp) {
        _apps.update { list -> list.filterNot { it.key == app.key } }
        saveAsync()
    }

    /** Installed app whose name matches a repo name (GitHub etc. don't tell the package). */
    private fun guessPackage(name: String): String {
        val n = name.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (n.length < 3) return ""
        val pm = appContext.packageManager
        val apps = pm.getInstalledApplications(0)
        return apps.firstOrNull { it.loadLabel(pm).toString().lowercase().replace(Regex("[^a-z0-9]"), "") == n }?.packageName
            ?: apps.firstOrNull { it.packageName.substringAfterLast('.').lowercase() == n }?.packageName
            ?: ""
    }

    private fun label(pkg: String): String? = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    }.getOrNull()

    // ------------------------------------------------------------- checking

    suspend fun check(app: TrackedApp): TrackedApp {
        val source = Source.forUrl(app.url)
        val checked = if (source == null) {
            app.copy(error = t(R.string.e_source_unsupported), lastCheck = System.currentTimeMillis())
        } else {
            try {
                val r = source.latest(app)
                val keepPreferred = app.chosenApk?.let { c -> r.apks.indexOfFirst { it.name == c.name } } ?: -1
                app.copy(
                    id = app.id.ifBlank { r.appId ?: "" },
                    latestVersion = r.version,
                    apks = r.apks,
                    preferredApk = keepPreferred,
                    releaseDate = r.date,
                    changeLog = r.changeLog,
                    releaseUrl = r.releaseUrl,
                    error = null,
                    lastCheck = System.currentTimeMillis(),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                app.copy(error = e.message ?: e.toString(), lastCheck = System.currentTimeMillis())
            }
        }
        val confirmed = confirmPendingInstall(checked)
        update(confirmed)
        return confirmed
    }

    /** Starts [checkAll] independent of any screen (leaving the screen must not cancel it). */
    fun checkAllInBackground() {
        ioScope.launch { checkAll() }
    }

    /** Checks every app one after another (keeps within GitHub's rate limit). */
    suspend fun checkAll(): Int {
        if (_checking.value) return updateCount()
        _checking.value = true
        try {
            for (app in _apps.value) check(app)
        } finally {
            _checking.value = false
        }
        return updateCount()
    }

    /** An install started by ShizuBox counts once the package was updated after it began. */
    private fun confirmPendingInstall(app: TrackedApp): TrackedApp {
        val pending = app.pendingVersion ?: return app
        return if (app.id.isNotBlank() && installedSince(app.id) >= app.pendingSince) {
            app.copy(installedVersion = pending, pendingVersion = null, pendingSince = 0)
        } else app
    }

    fun refreshInstalledStates() {
        _apps.update { list -> list.map(::confirmPendingInstall) }
        saveAsync()
    }

    /** Mark the current release as installed (for apps whose versions can't be compared). */
    fun markInstalled(app: TrackedApp) = update(app.copy(installedVersion = app.latestVersion, pendingVersion = null))

    // ------------------------------------------------------------- download

    /**
     * Downloads the chosen APK to the cache and returns a content Uri for the installer.
     * Also learns the package name from the file if it was unknown.
     */
    suspend fun download(app: TrackedApp, onProgress: (Float) -> Unit): Pair<TrackedApp, Uri> = withContext(Dispatchers.IO) {
        val asset = app.chosenApk ?: throw SourceException(t(R.string.e_no_apk))
        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        // Never delete files of other downloads (the installer may still be reading them),
        // only leftovers older than a day.
        val dayAgo = System.currentTimeMillis() - 24 * 3600_000L
        dir.listFiles()?.filter { it.lastModified() < dayAgo }?.forEach { it.delete() }
        val target = File(dir, "${System.currentTimeMillis()}_" + asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val conn = openFollowingRedirects(asset.url)
        if (conn.responseCode !in 200..299) throw SourceException(t(R.string.e_dl_http, conn.responseCode))
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", target)
        var updated = app
        if (app.id.isBlank()) {
            runCatching { ApkParser.parse(appContext, uri).packageName }.getOrNull()
                ?.takeIf { it != "?" }?.let { updated = updated.copy(id = it) }
        }
        updated = updated.copy(pendingVersion = app.latestVersion, pendingSince = System.currentTimeMillis())
        update(updated)
        updated to uri
    }

    /** Follows redirects across hosts (GitHub -> objects.githubusercontent.com, APKPure CDN …). */
    private fun openFollowingRedirects(start: String): HttpURLConnection {
        var url = start
        repeat(8) {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "ShizuBox")
                connectTimeout = 20_000
                readTimeout = 60_000
            }
            if (c.responseCode !in 300..399) return c
            val next = c.getHeaderField("Location") ?: return c
            url = URL(URL(url), next).toString()
            c.disconnect()
        }
        throw SourceException(t(R.string.e_redirects))
    }

    /**
     * Downloads and installs an update silently through Shizuku, with the same safety gates as the
     * background updater. Returns null on success, otherwise the reason it was not installed.
     */
    suspend fun installSilently(app: TrackedApp, onProgress: (Float) -> Unit = {}): String? {
        if (Source.forUrl(app.url)?.trackOnly == true) return t(R.string.h_apkm)
        if (!de.emil.shizubox.shizuku.ShizukuManager.isReady) return t(R.string.shizuku_not_conn)
        val (current, uri) = download(app, onProgress)
        val info = ApkParser.parse(appContext, uri)
        if (info.signatureMismatch == true) return t(R.string.h_sig)
        val vt = de.emil.shizubox.installer.VirusTotal
        if (_settings.value.virusTotalGate && vt.apiKey.isNotBlank()) {
            val sha = appContext.contentResolver.openInputStream(uri)!!.use { vt.sha256(it) }
            val r = vt.lookup(sha)
            if (r.status != de.emil.shizubox.installer.VtStatus.CLEAN) {
                return "VirusTotal: " + when (r.status) {
                    de.emil.shizubox.installer.VtStatus.MALICIOUS -> t(R.string.h_malicious, r.malicious)
                    de.emil.shizubox.installer.VtStatus.SUSPICIOUS -> t(R.string.h_suspicious, r.suspicious)
                    de.emil.shizubox.installer.VtStatus.NOT_FOUND, de.emil.shizubox.installer.VtStatus.NOT_ANALYZED -> t(R.string.h_unknown)
                    else -> t(R.string.h_uncheckable)
                }
            }
        }
        val uris = info.splitEntries.filter { it.selected }.map { it.uri }
        val result = de.emil.shizubox.installer.ShizukuApkInstaller.install(appContext, current.name, uris, de.emil.shizubox.installer.InstallOptions()) {}
        return when (result) {
            de.emil.shizubox.installer.InstallResult.Success -> {
                markInstalled(_apps.value.first { it.key == current.key })
                null
            }
            is de.emil.shizubox.installer.InstallResult.Failure -> "${result.title}: ${result.hint}"
        }
    }

    private val _updatingAll = MutableStateFlow<Pair<String?, Map<String, String>>?>(null)

    /** (app currently installing, failures by app name) while "update all" runs, else null. */
    val updatingAll: StateFlow<Pair<String?, Map<String, String>>?> = _updatingAll.asStateFlow()

    /** Runs "update all" as foreground work: survives leaving the app, shows progress. */
    fun updateAllInBackground() {
        if (_updatingAll.value?.first != null) return
        UpdateWorker.updateAllNow(appContext)
    }

    /** Called by the worker; installs every pending update one after another. */
    suspend fun runUpdateAll(onStep: (index: Int, total: Int, name: String, progress: Float) -> Unit): Pair<List<String>, Map<String, String>> {
        val todo = _apps.value.filter { state(it) == UpdateState.Update && Source.forUrl(it.url)?.trackOnly != true }
        val installed = mutableListOf<String>()
        val failed = linkedMapOf<String, String>()
        try {
            todo.forEachIndexed { i, app ->
                _updatingAll.value = app.key to failed.toMap()
                onStep(i, todo.size, app.name, 0f)
                val reason = try {
                    installSilently(app) { p -> onStep(i, todo.size, app.name, p) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.message ?: e.toString()
                }
                if (reason == null) installed += app.name else failed[app.name] = reason
            }
        } finally {
            _updatingAll.value = null to failed.toMap()
        }
        return installed to failed
    }

    fun clearUpdateAllResult() {
        _updatingAll.value = null
    }

    // ------------------------------------------------------------- import / export

    /** Obtainium / ObtainX export: {"apps":[…],"settings":{…}} or a plain list. */
    fun import(json: String): Pair<Int, Int> {
        val imported = parseList(json)
        val known = _apps.value.map { it.url.lowercase() }.toSet()
        val fresh = imported.filter { it.url.isNotBlank() && it.url.lowercase() !in known }
        _apps.update { it + fresh }
        saveAsync()
        return fresh.size to imported.count { Source.forUrl(it.url) == null }
    }

    fun export(): String = JSONObject()
        .put("apps", JSONArray(_apps.value.map { it.toJson() }))
        .put("settings", JSONObject())
        .toString(2)

    /** Sources that can look up an installed app by its package name, in search order. */
    val PACKAGE_SOURCES = linkedMapOf(
        "F-Droid" to { pkg: String -> "https://f-droid.org/packages/$pkg" },
        "IzzyOnDroid" to { pkg: String -> "https://apt.izzysoft.de/fdroid/index/apk/$pkg" },
        "APKPure" to { pkg: String -> "https://apkpure.com/app/$pkg" },
    )

    /** Adds installed apps by package name from the first selected source that has them. */
    suspend fun importInstalled(packages: List<String>, sources: Set<String>, onProgress: (Int, String) -> Unit): Int {
        var added = 0
        packages.forEachIndexed { i, pkg ->
            onProgress(i, pkg)
            if (_apps.value.any { it.id == pkg }) return@forEachIndexed
            for (url in PACKAGE_SOURCES.filterKeys { it in sources }.values.map { it(pkg) }) {
                if (runCatching { add(url) }.isSuccess) {
                    added++
                    break
                }
            }
        }
        return added
    }

    private fun parseList(json: String): List<TrackedApp> {
        val trimmed = json.trim()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("apps") ?: JSONArray()
        return (0 until arr.length()).map { TrackedApp.fromJson(arr.getJSONObject(it)) }
    }

    private suspend fun save() = saveLock.withLock {
        withContext(Dispatchers.IO) { file.writeText(JSONArray(_apps.value.map { it.toJson() }).toString()) }
    }

    private val ioScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    private fun saveAsync() {
        ioScope.launch { save() }
    }
}
