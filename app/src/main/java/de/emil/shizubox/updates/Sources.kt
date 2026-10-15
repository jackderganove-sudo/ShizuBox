/*
 * Release sources modelled on Obtainium / ObtainX (GPL-3.0). Own Kotlin implementation.
 */
package de.emil.shizubox.updates

import de.emil.shizubox.R
import de.emil.shizubox.t
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/** What a source reports as its newest usable release. */
data class Release(
    val version: String,
    val apks: List<ApkAsset>,
    val name: String,
    val author: String,
    /** Package name if the source knows it (F-Droid, APKPure, …). */
    val appId: String? = null,
    val date: Long = 0,
    val changeLog: String? = null,
    val releaseUrl: String? = null,
)

class SourceException(message: String) : Exception(message)

sealed class Source(val label: String) {
    /** Only reports versions; the APK has to be downloaded by hand (APKMirror blocks bots). */
    open val trackOnly: Boolean = false

    abstract fun matches(url: Uri): Boolean
    abstract suspend fun latest(app: TrackedApp): Release

    companion object {
        // Lazy: the subclasses are nested objects of this class; building the list during the
        // class's own static init would capture them as null (circular initialization).
        val ALL: List<Source> by lazy { listOf(GitHub, Codeberg, GitLab, FDroid, Izzy, ApkMirror, ApkPure) }

        fun forUrl(url: String): Source? {
            val uri = Uri.parse(url.trim())
            return ALL.firstOrNull { it.matches(uri) }
        }

        /** Normalizes a pasted link (adds https, drops tracking parts). */
        fun normalize(url: String): String {
            var u = url.trim()
            if (!u.startsWith("http")) u = "https://$u"
            return u.substringBefore('#').substringBefore('?').trimEnd('/')
        }
    }

    // ---------------------------------------------------------------- helpers

    protected suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "ShizuBox")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        when (val code = c.responseCode) {
            in 200..299 -> c.inputStream.bufferedReader().readText()
            404 -> throw SourceException(t(R.string.e_404))
            403, 429 -> throw SourceException(
                if (url.contains("api.github.com")) t(R.string.e_gh_limit) else t(R.string.e_denied, code),
            )
            else -> throw SourceException("HTTP $code")
        }
    }

    protected fun pathParts(uri: Uri) = uri.pathSegments.filter { it.isNotBlank() }

    /** Release lists from GitHub/Gitea/GitLab: first non-draft (pre)release that has APKs after filtering. */
    protected fun pickRelease(
        releases: List<Triple<JSONObject, Boolean, List<ApkAsset>>>,
        app: TrackedApp,
        version: (JSONObject) -> String,
        name: String,
        author: String,
        date: (JSONObject) -> Long,
        body: (JSONObject) -> String?,
        page: (JSONObject) -> String?,
    ): Release {
        if (releases.isEmpty()) throw SourceException(t(R.string.e_no_releases))
        for ((json, prerelease, assets) in releases) {
            if (prerelease && !app.includePrereleases) continue
            val apks = ApkFilters.filterApks(assets, app)
            if (apks.isEmpty()) continue
            return Release(version(json), apks, name, author, date = date(json), changeLog = body(json), releaseUrl = page(json))
        }
        throw SourceException(t(R.string.e_no_apk_release) + if (!app.includePrereleases) t(R.string.e_pre_excluded) else "")
    }

    protected fun iso(s: String?): Long = runCatching {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).parse(s!!.replace(Regex("\\.\\d+"), ""))!!.time
    }.getOrDefault(0)

    // ---------------------------------------------------------------- sources

    object GitHub : Source("GitHub") {
        var token: String = ""

        override fun matches(url: Uri) = url.host?.removePrefix("www.") == "github.com" && pathParts(url).size >= 2

        override suspend fun latest(app: TrackedApp): Release {
            val (owner, repo) = pathParts(Uri.parse(app.url)).let { it[0] to it[1] }
            val headers = buildMap {
                put("Accept", "application/vnd.github+json")
                if (token.isNotBlank()) put("Authorization", "Bearer $token")
            }
            val arr = JSONArray(get("https://api.github.com/repos/$owner/$repo/releases?per_page=20", headers))
            val releases = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { !it.optBoolean("draft") }.map { r ->
                val assets = r.optJSONArray("assets") ?: JSONArray()
                Triple(r, r.optBoolean("prerelease"), (0 until assets.length()).map { i ->
                    assets.getJSONObject(i).let { ApkAsset(it.getString("name"), it.getString("browser_download_url")) }
                })
            }
            return pickRelease(
                releases, app, { it.getString("tag_name") }, repo, owner,
                { iso(it.optString("published_at")) }, { it.optString("body").ifBlank { null } }, { it.optString("html_url") },
            )
        }
    }

    /** Codeberg and other Forgejo/Gitea instances (same API). */
    object Codeberg : Source("Codeberg") {
        private val HOSTS = setOf("codeberg.org", "gitea.com", "git.disroot.org", "next.forgejo.org")

        override fun matches(url: Uri) = url.host in HOSTS && pathParts(url).size >= 2

        override suspend fun latest(app: TrackedApp): Release {
            val uri = Uri.parse(app.url)
            val (owner, repo) = pathParts(uri).let { it[0] to it[1] }
            val arr = JSONArray(get("https://${uri.host}/api/v1/repos/$owner/$repo/releases?limit=20"))
            val releases = (0 until arr.length()).map { arr.getJSONObject(it) }.filter { !it.optBoolean("draft") }.map { r ->
                val assets = r.optJSONArray("assets") ?: JSONArray()
                Triple(r, r.optBoolean("prerelease"), (0 until assets.length()).map { i ->
                    assets.getJSONObject(i).let { ApkAsset(it.getString("name"), it.getString("browser_download_url")) }
                })
            }
            return pickRelease(
                releases, app, { it.getString("tag_name") }, repo, owner,
                { iso(it.optString("published_at")) }, { it.optString("body").ifBlank { null } }, { it.optString("html_url") },
            )
        }
    }

    object GitLab : Source("GitLab") {
        override fun matches(url: Uri) = url.host == "gitlab.com" && pathParts(url).size >= 2

        override suspend fun latest(app: TrackedApp): Release {
            val parts = pathParts(Uri.parse(app.url)).takeWhile { it != "-" }
            val project = Uri.encode(parts.joinToString("/"))
            val arr = JSONArray(get("https://gitlab.com/api/v4/projects/$project/releases?per_page=20"))
            val releases = (0 until arr.length()).map { arr.getJSONObject(it) }.map { r ->
                val links = r.optJSONObject("assets")?.optJSONArray("links") ?: JSONArray()
                Triple(r, r.optBoolean("upcoming_release"), (0 until links.length()).map { i ->
                    links.getJSONObject(i).let { l ->
                        val url = l.optString("direct_asset_url").ifBlank { l.getString("url") }
                        ApkAsset(l.optString("name").ifBlank { url.substringAfterLast('/') }, url)
                    }
                })
            }
            return pickRelease(
                releases, app, { it.getString("tag_name") }, parts.last(), parts.first(),
                { iso(it.optString("released_at")) }, { it.optString("description").ifBlank { null } }, { it.optJSONObject("_links")?.optString("self") },
            )
        }
    }

    /** F-Droid style repos: version list from the JSON API, APKs named <pkg>_<versionCode>.apk. */
    abstract class FDroidLike(label: String, private val api: String, private val repo: String) : Source(label) {
        abstract fun packageOf(url: Uri): String?

        override suspend fun latest(app: TrackedApp): Release {
            val pkg = packageOf(Uri.parse(app.url)) ?: throw SourceException(t(R.string.e_no_pkg))
            val o = JSONObject(get("$api/$pkg"))
            val versions = o.getJSONArray("packages")
            if (versions.length() == 0) throw SourceException(t(R.string.e_no_versions))
            val suggested = o.optString("suggestedVersionCode")
            val pick = (0 until versions.length()).map { versions.getJSONObject(it) }.let { list ->
                if (app.includePrereleases) list.first() else list.firstOrNull { it.optString("versionCode") == suggested } ?: list.first()
            }
            val code = pick.getString("versionCode")
            return Release(
                version = pick.getString("versionName"),
                apks = listOf(ApkAsset("${pkg}_$code.apk", "$repo/${pkg}_$code.apk")),
                name = app.name.ifBlank { pkg },
                author = label,
                appId = pkg,
                releaseUrl = app.url,
            )
        }
    }

    object FDroid : FDroidLike("F-Droid", "https://f-droid.org/api/v1/packages", "https://f-droid.org/repo") {
        override fun matches(url: Uri) = url.host?.removePrefix("www.") == "f-droid.org" && packageOf(url) != null
        override fun packageOf(url: Uri) = pathParts(url).let { p -> p.getOrNull(p.indexOf("packages") + 1)?.takeIf { "packages" in p } }
    }

    object Izzy : FDroidLike("IzzyOnDroid", "https://apt.izzysoft.de/fdroid/api/v1/packages", "https://apt.izzysoft.de/fdroid/repo") {
        override fun matches(url: Uri) = url.host?.endsWith("izzysoft.de") == true && packageOf(url) != null
        override fun packageOf(url: Uri): String? {
            val p = pathParts(url)
            // apt.izzysoft.de/fdroid/index/apk/<pkg>  or  .../packages/<pkg>
            return p.lastOrNull()?.takeIf { it.contains('.') && ("apk" in p || "packages" in p) }
        }
    }

    /**
     * APKMirror: every upload is checked against the developer's signature, which makes it the
     * most trustworthy store mirror. Track-only via its RSS feed, like ObtainX: APKMirror does not
     * allow automated downloads, so the user downloads the .apk/.apkm on the release page.
     */
    object ApkMirror : Source("APKMirror") {
        override val trackOnly = true

        override fun matches(url: Uri) = url.host?.removePrefix("www.") == "apkmirror.com" &&
            pathParts(url).let { it.size >= 3 && it[0] == "apk" }

        override suspend fun latest(app: TrackedApp): Release {
            val p = pathParts(Uri.parse(app.url))
            val base = "https://www.apkmirror.com/apk/${p[1]}/${p[2]}"
            val xml = get("$base/feed/")
            val items = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.groupValues[1] }.mapNotNull { item ->
                val title = Regex("<title>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</title>", RegexOption.DOT_MATCHES_ALL).find(item)?.groupValues?.get(1)?.trim()
                    ?: return@mapNotNull null
                val link = Regex("<link>(.*?)</link>").find(item)?.groupValues?.get(1)?.trim()
                val date = Regex("<pubDate>(.*?)</pubDate>").find(item)?.groupValues?.get(1)?.let { d ->
                    runCatching { java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH).parse(d)!!.time }.getOrDefault(0)
                } ?: 0
                Triple(title, link, date)
            }.toList()
            if (items.isEmpty()) throw SourceException(t(R.string.e_no_versions_apkm))
            val usable = items.filter { app.includePrereleases || !Regex("\\b(beta|alpha)\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.first) }
                .ifEmpty { items }
            // The feed is ordered by upload time, not version: take the highest version.
            val best = usable.maxWithOrNull { a, b -> Versions.compare(versionOf(a.first), versionOf(b.first)) ?: 0 }!!
            val label = best.first.substringBeforeLast(" by ")
            return Release(
                version = versionOf(best.first),
                apks = emptyList(),
                name = label.removeSuffix(versionOf(best.first)).trim(),
                author = best.first.substringAfterLast(" by ", "APKMirror"),
                date = best.third,
                releaseUrl = best.second ?: base,
            )
        }

        /** "Google Keep - Notes and Lists 5.26.381.02 by Google LLC" -> "5.26.381.02". */
        private fun versionOf(title: String): String {
            val label = title.substringBeforeLast(" by ").trim()
            return label.split(' ').lastOrNull { t -> t.any(Char::isDigit) } ?: label
        }
    }

    /** APKPure via its app API (same request ObtainX makes). */
    object ApkPure : Source("APKPure") {
        override fun matches(url: Uri) = url.host?.let { it.endsWith("apkpure.com") || it.endsWith("apkpure.net") } == true &&
            pathParts(url).size >= 2

        override suspend fun latest(app: TrackedApp): Release {
            val pkg = pathParts(Uri.parse(app.url)).last { it.contains('.') }
            val body = withNetHint {
                get(
                    "https://tapi.pureapk.com/v3/get_app_his_version?package_name=$pkg&hl=en",
                    mapOf(
                        "Ual-Access-Businessid" to "projecta",
                        "Ual-Access-ProjectA" to """{"device_info":{"os_ver":"${Build.VERSION.SDK_INT}"}}""",
                    ),
                )
            }
            val list = JSONObject(body).optJSONArray("version_list") ?: throw SourceException(t(R.string.e_no_versions))
            val variants = (0 until list.length()).map { list.getJSONObject(it) }
            if (variants.isEmpty()) throw SourceException(t(R.string.e_no_versions))
            val abis = Build.SUPPORTED_ABIS.toSet()
            // Newest version first; take the variants of that version that run on this device.
            for (versionName in variants.map { it.optString("version_name") }.distinct()) {
                val forVersion = variants.filter { it.optString("version_name") == versionName }.filter { v ->
                    val native = (0 until (v.optJSONArray("native_code")?.length() ?: 0)).map { v.getJSONArray("native_code").getString(it) }
                    native.isEmpty() || "universal" in native || native.any { it in abis }
                }
                val apks = forVersion.mapNotNull { v ->
                    val asset = v.optJSONObject("asset") ?: return@mapNotNull null
                    val type = asset.optString("type").lowercase().ifBlank { "apk" }
                    ApkAsset("$pkg-${v.optString("version_code")}.$type", asset.optString("url").ifBlank { return@mapNotNull null })
                }.distinctBy { it.name }
                if (apks.isEmpty()) continue
                val v = forVersion.first()
                return Release(
                    version = versionName,
                    apks = apks,
                    name = v.optString("title").ifBlank { pkg },
                    author = v.optString("developer").ifBlank { "APKPure" },
                    appId = pkg,
                    date = iso(v.optString("update_date")),
                    changeLog = v.optString("whatsnew").ifBlank { null },
                    releaseUrl = app.url,
                )
            }
            throw SourceException(t(R.string.e_no_version_device))
        }
    }
}

object ApkFilters {
    private val APK_EXTENSIONS = listOf(".apk", ".apks", ".xapk", ".apkm")
    private val ABI_ALIASES = mapOf(
        "arm64-v8a" to listOf("arm64-v8a", "arm64_v8a", "aarch64", "arm64"),
        "armeabi-v7a" to listOf("armeabi-v7a", "armeabi_v7a", "armv7", "armeabi"),
        "x86_64" to listOf("x86_64", "x64"),
        "x86" to listOf("x86"),
    )

    fun isApk(name: String) = APK_EXTENSIONS.any { name.lowercase().endsWith(it) }

    /** User regex filter, then (like Obtainium) narrow down to the best matching CPU architecture. */
    fun filterApks(assets: List<ApkAsset>, app: TrackedApp): List<ApkAsset> {
        var list = assets.filter { isApk(it.name) }
        if (app.apkFilterRegex.isNotBlank()) {
            val re = runCatching { Regex(app.apkFilterRegex) }.getOrNull()
            if (re != null) list = list.filter { re.containsMatchIn(it.name) != app.invertApkFilter }
        }
        if (list.size > 1) {
            for (abi in Build.SUPPORTED_ABIS) {
                val variants = ABI_ALIASES[abi] ?: listOf(abi)
                val re = Regex(".*(${variants.joinToString("|") { Regex.escape(it) }}).*", RegexOption.IGNORE_CASE)
                val narrowed = list.filter { re.matches(it.name) }
                if (narrowed.isNotEmpty() && narrowed.size < list.size) {
                    list = narrowed
                    break
                }
            }
        }
        return list
    }
}

/** Hosts that VPNs and DNS filters like to block (APKPure API and CDN). */
fun isApkPureHost(url: String) = Regex("""(^|\.)(pureapk\.com|apkpure\.(com|net)|winudf\.com)(/|$|:)""").containsMatchIn(url.substringAfter("://"))

/**
 * Runs an APKPure request with one retry. Network errors (DNS, TLS, refused, timeout) and
 * 403/5xx become a readable hint that names the likely cause: an active VPN or a private DNS
 * filter such as NextDNS.
 */
suspend fun <T> withNetHint(block: suspend () -> T): T {
    var last: Throwable? = null
    repeat(2) { attempt ->
        try {
            return block()
        } catch (e: java.io.IOException) {
            last = e
        } catch (e: SourceException) {
            // 404 and "no versions" are real answers, only blocks are worth a retry.
            if (e.message?.contains("403") != true && e.message?.startsWith("HTTP 5") != true) throw e
            last = e
        }
        if (attempt == 0) kotlinx.coroutines.delay(1500)
    }
    throw SourceException(apkPureHint(last))
}

fun apkPureHint(e: Throwable?): String {
    val ctx = de.emil.shizubox.Str.context
    val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
    val vpn = cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true }
    val dns = cm.getLinkProperties(cm.activeNetwork)?.privateDnsServerName
    val why = when (e) {
        is java.net.UnknownHostException -> "DNS"
        is javax.net.ssl.SSLException -> "TLS"
        is java.net.SocketTimeoutException -> "Timeout"
        null -> "?"
        else -> e.message?.take(60) ?: e.javaClass.simpleName
    }
    return when {
        vpn -> t(R.string.e_apkpure_vpn, why)
        dns != null -> t(R.string.e_apkpure_dns, why, dns)
        else -> t(R.string.e_apkpure_net, why)
    }
}
