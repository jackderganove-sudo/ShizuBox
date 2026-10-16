/*
 * Ported from Universal Installer (https://github.com/pass-with-high-score/universal-installer),
 * Copyright (C) pass-with-high-score and contributors, licensed under GPL-3.0.
 * Adapted for ShizuBox: HttpURLConnection instead of Ktor, German texts, no notifications.
 */
package de.emil.shizubox.installer

import de.emil.shizubox.R
import de.emil.shizubox.t
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class VtStatus {
    CLEAN, MALICIOUS, SUSPICIOUS, NOT_FOUND,
    /** Known to VirusTotal (e.g. just uploaded elsewhere) but no scanner has reported yet. */
    NOT_ANALYZED, NO_API_KEY, INVALID_API_KEY, RATE_LIMITED, ERROR, TOO_LARGE,
    SCANNING, UPLOADING, QUEUED, ANALYZING,
}

data class VtEngineResult(val engineName: String, val category: String, val result: String?)

data class VtResult(
    val status: VtStatus,
    val malicious: Int = 0,
    val suspicious: Int = 0,
    val harmless: Int = 0,
    val undetected: Int = 0,
    val errorMessage: String = "",
    val uploadProgress: Int = 0,
    val sha256: String = "",
    val engineResults: List<VtEngineResult> = emptyList(),
) {
    val total get() = malicious + suspicious + harmless + undetected
    val busy get() = status in setOf(VtStatus.SCANNING, VtStatus.UPLOADING, VtStatus.QUEUED, VtStatus.ANALYZING)
}

/**
 * VirusTotal v3 client. The free API key allows 4 requests per minute and 500 per day.
 * Flow: SHA-256 lookup first (nothing leaves the phone); only on explicit request is an unknown
 * file uploaded and analysed.
 */
object VirusTotal {
    private const val BASE_URL = "https://www.virustotal.com/api/v3"
    private const val SIZE_LIMIT_DIRECT = 32L * 1024 * 1024
    const val SIZE_LIMIT_LARGE = 650L * 1024 * 1024

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("virustotal", Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = prefs.getString("api_key", "").orEmpty()
        set(value) = prefs.edit().putString("api_key", value.trim()).apply()

    suspend fun sha256(input: InputStream): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Hash lookup only. */
    suspend fun lookup(sha256: String): VtResult = withContext(Dispatchers.IO) {
        val key = apiKey
        if (key.isBlank()) return@withContext VtResult(VtStatus.NO_API_KEY, sha256 = sha256)
        runCatching {
            val conn = open("$BASE_URL/files/$sha256", key)
            when (conn.responseCode) {
                200 -> parseFile(conn.inputStream.bufferedReader().readText())
                404 -> VtResult(VtStatus.NOT_FOUND)
                else -> httpFailure(conn)
            }.copy(sha256 = sha256)
        }.getOrElse { VtResult(VtStatus.ERROR, errorMessage = networkMessage(it), sha256 = sha256) }
    }

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val _scans = kotlinx.coroutines.flow.MutableStateFlow<Map<String, VtResult>>(emptyMap())
    /** Uploads by SHA-256 – they keep running when the screen is left or the card scrolls away. */
    val scans: kotlinx.coroutines.flow.StateFlow<Map<String, VtResult>> = _scans

    /** Starts (once per file) an upload + analysis in the background; progress lands in [scans]. */
    fun startUpload(sha256: String, file: suspend () -> File?) {
        if (_scans.value[sha256]?.busy == true) return
        fun set(r: VtResult) { _scans.value = _scans.value + (sha256 to r.copy(sha256 = sha256)) }
        set(VtResult(VtStatus.UPLOADING))
        scope.launch {
            val f = runCatching { file() }.getOrNull()
            try {
                set(if (f == null) VtResult(VtStatus.ERROR, errorMessage = t(R.string.file_unreadable)) else uploadAndScan(f, sha256) { set(it) })
            } finally {
                // Copies made only for the upload (Universal Installer); never an installed app's APK.
                if (f != null && f.name.startsWith("vt_upload_")) f.delete()
            }
        }
    }

    /** Uploads [file] and waits for the analysis (up to ~10 minutes). */
    suspend fun uploadAndScan(file: File, sha256: String, onProgress: (VtResult) -> Unit): VtResult = withContext(Dispatchers.IO) {
        val key = apiKey
        if (key.isBlank()) return@withContext VtResult(VtStatus.NO_API_KEY)
        if (file.length() > SIZE_LIMIT_LARGE) return@withContext VtResult(VtStatus.TOO_LARGE)
        val analysisId = try {
            val target = if (file.length() <= SIZE_LIMIT_DIRECT) "$BASE_URL/files" else {
                val c = open("$BASE_URL/files/upload_url", key)
                if (c.responseCode !in 200..299) return@withContext httpFailure(c)
                JSONObject(c.inputStream.bufferedReader().readText()).getString("data")
            }
            upload(target, key, file) { pct -> onProgress(VtResult(VtStatus.UPLOADING, uploadProgress = pct, sha256 = sha256)) }
                ?: return@withContext VtResult(VtStatus.ERROR, errorMessage = t(R.string.vt_upload_failed), sha256 = sha256)
        } catch (e: VtHttpException) {
            return@withContext e.result.copy(sha256 = sha256)
        } catch (e: Exception) {
            return@withContext VtResult(VtStatus.ERROR, errorMessage = networkMessage(e), sha256 = sha256)
        }
        onProgress(VtResult(VtStatus.QUEUED, sha256 = sha256))
        poll(key, analysisId, sha256, onProgress)
    }

    private fun upload(url: String, key: String, file: File, onProgress: (Int) -> Unit): String? {
        val boundary = "----ShizuBox${System.currentTimeMillis()}"
        val head = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n"
        val tail = "\r\n--$boundary--\r\n"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 20 * 60_000
            setRequestProperty("x-apikey", key)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setFixedLengthStreamingMode(head.length + file.length() + tail.length)
        }
        conn.outputStream.use { out ->
            out.write(head.toByteArray())
            val total = file.length()
            var sent = 0L
            var lastPct = -1
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    sent += n
                    val pct = (sent * 100 / total).toInt()
                    if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                }
            }
            out.write(tail.toByteArray())
        }
        if (conn.responseCode !in 200..299) throw VtHttpException(httpFailure(conn))
        return JSONObject(conn.inputStream.bufferedReader().readText()).getJSONObject("data").getString("id")
    }

    /** Staggered polling to stay within the free tier's 4 requests/minute. */
    private suspend fun poll(key: String, id: String, sha256: String, onProgress: (VtResult) -> Unit): VtResult {
        // The free tier queues uploads for a while when VirusTotal is busy: 3 minutes were often
        // too short. Every third round also ask for the file report, which can be ready earlier.
        val deadline = System.currentTimeMillis() + 10 * 60_000
        var attempt = 0
        while (System.currentTimeMillis() < deadline) {
            delay(when (attempt++) { 0 -> 3_000L; 1, 2 -> 5_000L; 3, 4 -> 10_000L; else -> 15_000L })
            if (attempt % 3 == 0) {
                val report = lookup(sha256)
                if (report.status == VtStatus.CLEAN || report.status == VtStatus.SUSPICIOUS || report.status == VtStatus.MALICIOUS) return report
            }
            val result = runCatching {
                val c = open("$BASE_URL/analyses/$id", key)
                if (c.responseCode !in 200..299) return@runCatching httpFailure(c)
                val attrs = JSONObject(c.inputStream.bufferedReader().readText()).getJSONObject("data").getJSONObject("attributes")
                when (attrs.optString("status")) {
                    "queued" -> VtResult(VtStatus.QUEUED)
                    "in-progress" -> VtResult(VtStatus.ANALYZING)
                    "completed" -> toResult(attrs.getJSONObject("stats"), engines(attrs.optJSONObject("results")))
                    else -> VtResult(VtStatus.ERROR, errorMessage = t(R.string.vt_unknown_status))
                }
            }.getOrNull()?.copy(sha256 = sha256) ?: continue
            if (result.status == VtStatus.QUEUED || result.status == VtStatus.ANALYZING) {
                onProgress(result)
                continue
            }
            return result
        }
        return lookup(sha256).takeIf { it.status == VtStatus.CLEAN || it.status == VtStatus.SUSPICIOUS || it.status == VtStatus.MALICIOUS }
            ?: VtResult(VtStatus.ERROR, errorMessage = t(R.string.vt_too_long), sha256 = sha256)
    }

    private fun open(url: String, key: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 30_000
        setRequestProperty("x-apikey", key)
        setRequestProperty("Accept", "application/json")
    }

    private fun parseFile(body: String): VtResult {
        val attrs = JSONObject(body).getJSONObject("data").getJSONObject("attributes")
        val result = toResult(attrs.optJSONObject("last_analysis_stats") ?: JSONObject(), engines(attrs.optJSONObject("last_analysis_results")))
        // "Clean, 0 of 0" would be misleading: the analysis simply has not run yet.
        return if (result.total == 0) VtResult(VtStatus.NOT_ANALYZED) else result
    }

    private fun engines(obj: JSONObject?): List<VtEngineResult> {
        if (obj == null) return emptyList()
        val order = mapOf("malicious" to 0, "suspicious" to 1)
        return obj.keys().asSequence().mapNotNull { k ->
            val e = obj.optJSONObject(k) ?: return@mapNotNull null
            VtEngineResult(k, e.optString("category", "undetected"), e.optString("result").takeIf { it.isNotBlank() && it != "null" })
        }.sortedWith(compareBy<VtEngineResult> { order[it.category] ?: 2 }.thenBy { it.engineName.lowercase() }).toList()
    }

    private fun toResult(stats: JSONObject, engines: List<VtEngineResult>): VtResult {
        val malicious = stats.optInt("malicious")
        val suspicious = stats.optInt("suspicious")
        return VtResult(
            status = when { malicious > 0 -> VtStatus.MALICIOUS; suspicious > 0 -> VtStatus.SUSPICIOUS; else -> VtStatus.CLEAN },
            malicious = malicious,
            suspicious = suspicious,
            harmless = stats.optInt("harmless"),
            undetected = stats.optInt("undetected"),
            engineResults = engines,
        )
    }

    private class VtHttpException(val result: VtResult) : Exception()

    private fun httpFailure(conn: HttpURLConnection): VtResult = when (conn.responseCode) {
        401, 403 -> VtResult(VtStatus.INVALID_API_KEY)
        429 -> VtResult(VtStatus.RATE_LIMITED)
        else -> VtResult(VtStatus.ERROR, errorMessage = "HTTP ${conn.responseCode}")
    }

    private fun networkMessage(e: Throwable) = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
            t(R.string.no_internet)
        else -> e.message ?: e.toString()
    }
}
