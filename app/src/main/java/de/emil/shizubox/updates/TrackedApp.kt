/*
 * App tracking modelled on Obtainium / ObtainX (GPL-3.0, github.com/ImranR98/Obtainium,
 * github.com/bikram-agarwal/ObtainX). Own Kotlin implementation; the export format is compatible.
 */
package de.emil.shizubox.updates

import org.json.JSONArray
import org.json.JSONObject

data class ApkAsset(val name: String, val url: String)

/** One app followed from its source (GitHub release page, F-Droid, APKPure, …). */
data class TrackedApp(
    /** Package name; empty until known (e.g. a GitHub repo before the first install). */
    val id: String,
    val url: String,
    val name: String,
    val author: String,
    /** Release version (tag) that is installed, as tracked by ShizuBox. */
    val installedVersion: String? = null,
    val latestVersion: String? = null,
    val apks: List<ApkAsset> = emptyList(),
    val preferredApk: Int = -1,
    val includePrereleases: Boolean = false,
    val apkFilterRegex: String = "",
    val invertApkFilter: Boolean = false,
    val releaseDate: Long = 0,
    val changeLog: String? = null,
    val releaseUrl: String? = null,
    val lastCheck: Long = 0,
    val error: String? = null,
    val pinned: Boolean = false,
    /** Set when an install was started: (version, start time). Confirmed on the next refresh. */
    val pendingVersion: String? = null,
    val pendingSince: Long = 0,
    /** Release the user chose to skip; it is not offered as an update. */
    val skippedVersion: String? = null,
    /** Obtainium settings we don't use, kept so an export round-trips without losing them. */
    val extraSettings: JSONObject = JSONObject(),
) {
    val key get() = url

    val chosenApk: ApkAsset?
        get() = apks.getOrNull(if (preferredApk in apks.indices) preferredApk else apks.lastIndex)

    fun toJson(): JSONObject {
        val settings = JSONObject(extraSettings.toString())
            .put("includePrereleases", includePrereleases)
            .put("apkFilterRegEx", apkFilterRegex)
            .put("invertAPKFilter", invertApkFilter)
        return JSONObject()
            .put("id", id)
            .put("url", url)
            .put("author", author)
            .put("name", name)
            .put("installedVersion", installedVersion ?: JSONObject.NULL)
            .put("latestVersion", latestVersion ?: JSONObject.NULL)
            // Obtainium stores these nested as JSON strings.
            .put("apkUrls", JSONArray(apks.map { JSONArray().put(it.name).put(it.url) }).toString())
            .put("preferredApkIndex", if (preferredApk in apks.indices) preferredApk else apks.lastIndex.coerceAtLeast(0))
            .put("additionalSettings", settings.toString())
            .put("lastUpdateCheck", lastCheck * 1000) // microseconds
            .put("pinned", pinned)
            .put("categories", JSONArray())
            .put("releaseDate", if (releaseDate > 0) releaseDate * 1000 else JSONObject.NULL)
            .put("changeLog", changeLog ?: JSONObject.NULL)
            // ShizuBox-only fields (ignored by Obtainium).
            .put("sbReleaseUrl", releaseUrl ?: JSONObject.NULL)
            .put("sbError", error ?: JSONObject.NULL)
            .put("sbPendingVersion", pendingVersion ?: JSONObject.NULL)
            .put("sbPendingSince", pendingSince)
            .put("sbSkippedVersion", skippedVersion ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(o: JSONObject): TrackedApp {
            fun str(k: String) = o.optString(k).takeIf { it.isNotBlank() && it != "null" }
            val settings = when (val raw = o.opt("additionalSettings")) {
                is JSONObject -> raw
                is String -> runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
                else -> JSONObject()
            }
            val apkArray = when (val raw = o.opt("apkUrls")) {
                is JSONArray -> raw
                is String -> runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
                else -> JSONArray()
            }
            val apks = (0 until apkArray.length()).mapNotNull { i ->
                when (val e = apkArray.get(i)) {
                    is JSONArray -> ApkAsset(e.optString(0), e.optString(1))
                    is JSONObject -> e.keys().asSequence().firstOrNull()?.let { ApkAsset(it, e.getString(it)) }
                    else -> null
                }
            }
            return TrackedApp(
                id = str("id").orEmpty(),
                url = str("url").orEmpty(),
                name = str("name") ?: str("id").orEmpty(),
                author = str("author").orEmpty(),
                installedVersion = str("installedVersion"),
                latestVersion = str("latestVersion"),
                apks = apks,
                preferredApk = o.optInt("preferredApkIndex", -1),
                includePrereleases = settings.optBoolean("includePrereleases", false),
                apkFilterRegex = settings.optString("apkFilterRegEx", ""),
                invertApkFilter = settings.optBoolean("invertAPKFilter", false),
                releaseDate = o.optLong("releaseDate", 0) / 1000,
                changeLog = str("changeLog"),
                releaseUrl = str("sbReleaseUrl"),
                lastCheck = o.optLong("lastUpdateCheck", 0) / 1000,
                error = str("sbError"),
                pinned = o.optBoolean("pinned", false),
                pendingVersion = str("sbPendingVersion"),
                pendingSince = o.optLong("sbPendingSince", 0),
                skippedVersion = str("sbSkippedVersion"),
                extraSettings = settings,
            )
        }
    }
}

/** Version comparison in the spirit of Obtainium's "standard version detection". */
object Versions {
    private val NUMBERS = Regex("""\d+(?:\.\d+)+|\d+""")

    /** Numeric core of a version string, e.g. "v1.6.17-beta" -> [1, 6, 17]. */
    fun numbers(v: String?): List<Long>? =
        v?.let { NUMBERS.find(it)?.value?.split('.')?.mapNotNull { p -> p.toLongOrNull() } }?.takeIf { it.isNotEmpty() }

    /** >0 if a is newer than b, 0 if equal, <0 if older; null if not comparable. */
    fun compare(a: String?, b: String?): Int? {
        val x = numbers(a) ?: return null
        val y = numbers(b) ?: return null
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }
}
