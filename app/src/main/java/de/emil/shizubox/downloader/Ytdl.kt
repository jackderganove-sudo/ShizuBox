package de.emil.shizubox.downloader

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** One entry of a playlist/channel or a single video, as yt-dlp reports it. */
data class MediaEntry(
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSec: Long,
    val thumbnail: String?,
)

/** Result of looking up a URL: a single video (with its heights) or a list of entries. */
data class MediaInfo(
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSec: Long,
    val heights: List<Int>,
    val entries: List<MediaEntry>,
    val url: String,
) {
    val isPlaylist get() = entries.isNotEmpty()
}

/**
 * yt-dlp and FFmpeg through youtubedl-android. Initialising unpacks Python and FFmpeg once
 * (a few seconds), so it happens lazily on first use and never on the main thread.
 */
object Ytdl {
    private val initLock = Mutex()
    @Volatile private var ready = false
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun ensureInit() {
        if (ready) return
        initLock.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().init(appContext)
                FFmpeg.getInstance().init(appContext)
            }
            ready = true
        }
    }

    private fun cookiesFile() = File(appContext.filesDir, "cookies.txt")

    private fun YoutubeDLRequest.common(): YoutubeDLRequest = apply {
        addOption("--no-warnings")
        cookiesFile().takeIf { it.exists() }?.let { addOption("--cookies", it.absolutePath) }
    }

    /** Looks up a URL without downloading (flat for playlists, so it stays fast). */
    suspend fun info(url: String): MediaInfo = withContext(Dispatchers.IO) {
        ensureInit()
        val req = YoutubeDLRequest(url).common()
            .addOption("--dump-single-json")
            .addOption("--flat-playlist")
        val out = YoutubeDL.getInstance().execute(req).out
        val o = JSONObject(out.substring(out.indexOf('{')))
        val entries = o.optJSONArray("entries")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val e = arr.optJSONObject(i) ?: return@mapNotNull null
                val u = e.optString("url").ifEmpty { e.optString("webpage_url") }.ifEmpty { return@mapNotNull null }
                MediaEntry(
                    url = if (u.startsWith("http")) u else "https://www.youtube.com/watch?v=$u",
                    title = e.optString("title", u),
                    uploader = e.optString("uploader").ifEmpty { e.optString("channel") }.ifEmpty { null },
                    durationSec = e.optDouble("duration", 0.0).toLong(),
                    thumbnail = bestThumb(e),
                )
            }
        }.orEmpty()
        val heights = o.optJSONArray("formats")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optInt("height", 0)?.takeIf { h -> h > 0 } }
        }.orEmpty().distinct().sortedDescending()
        MediaInfo(
            title = o.optString("title", url),
            uploader = o.optString("uploader").ifEmpty { o.optString("channel") }.ifEmpty { null },
            thumbnail = o.optString("thumbnail").ifEmpty { null } ?: bestThumb(o),
            durationSec = o.optDouble("duration", 0.0).toLong(),
            heights = heights,
            entries = entries,
            url = o.optString("webpage_url").ifEmpty { url },
        )
    }

    private fun bestThumb(o: JSONObject): String? {
        val arr = o.optJSONArray("thumbnails") ?: return null
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .maxByOrNull { it.optInt("width", 0) }?.optString("url")?.ifEmpty { null }
    }

    /** yt-dlp arguments for the chosen format. */
    fun formatArgs(f: FormatChoice): List<Pair<String, String?>> = buildList {
        if (f.kind == MediaKind.AUDIO) {
            add("-f" to "ba/b")
            add("-x" to null)
            add("--audio-format" to f.audioFormat)
            add("--audio-quality" to "0")
        } else {
            val h = if (f.height > 0) "[height<=${f.height}]" else ""
            val ext = if (f.container == "mp4") "[ext=mp4]" else ""
            val audioExt = if (f.container == "mp4") "[ext=m4a]" else ""
            add("-f" to "bv*$h$ext+ba$audioExt/bv*$h+ba/b$h/b")
            add("--merge-output-format" to f.container)
        }
        if (f.embedMetadata) add("--embed-metadata" to null)
        // webm/opus cannot carry cover art reliably; yt-dlp would fail the whole download.
        val coverOk = if (f.kind == MediaKind.AUDIO) f.audioFormat != "opus" else f.container != "webm"
        if (f.embedThumbnail && coverOk) {
            add("--embed-thumbnail" to null)
            add("--convert-thumbnails" to "jpg")
        }
    }

    /** Downloads into [dir]; returns the produced file. */
    suspend fun download(
        task: DownloadTask,
        dir: File,
        onProgress: (Float, String?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        ensureInit()
        dir.mkdirs()
        val req = YoutubeDLRequest(task.url).common()
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("-o", File(dir, "%(title).150B [%(id)s].%(ext)s").absolutePath)
        formatArgs(task.format).forEach { (k, v) -> if (v == null) req.addOption(k) else req.addOption(k, v) }
        YoutubeDL.getInstance().execute(req, task.id) { progress, eta, line ->
            val status = line.takeIf { "%" in it }?.substringAfter("[download]")?.trim()
            onProgress((progress / 100f).coerceIn(0f, 1f), status ?: if (eta > 0) "ETA ${eta}s" else null)
        }
        dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && !it.name.endsWith(".jpg") && !it.name.endsWith(".webp") }
            ?.maxByOrNull { it.length() }
            ?: error("no output file")
    }

    fun cancel(id: String) = runCatching { YoutubeDL.getInstance().destroyProcessById(id) }.getOrDefault(false)

    /** Updates yt-dlp to the latest stable release; returns the new version or null if already current. */
    suspend fun update(): String? = withContext(Dispatchers.IO) {
        ensureInit()
        val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
        if (status == YoutubeDL.UpdateStatus.DONE) version() else null
    }

    /** Version of the yt-dlp in use; the bundled one only reports it via `--version`. */
    fun version(): String? = runCatching {
        YoutubeDL.getInstance().versionName(appContext)
            ?: YoutubeDL.getInstance().execute(YoutubeDLRequest(emptyList<String>()).addOption("--version")).out.trim().ifEmpty { null }
    }.getOrNull()?.removePrefix("yt-dlp")?.trim()

    /**
     * Converts a local video to audio with the bundled FFmpeg. It runs from the native library
     * dir like youtubedl-android does, with the extracted shared libraries on LD_LIBRARY_PATH.
     */
    suspend fun convertToAudio(input: File, output: File, format: String): Unit = withContext(Dispatchers.IO) {
        ensureInit()
        val base = File(appContext.noBackupFilesDir, "youtubedl-android/packages")
        val ffmpeg = File(appContext.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val codec = when (format) {
            "m4a" -> listOf("-c:a", "aac", "-b:a", "192k")
            "opus" -> listOf("-c:a", "libopus", "-b:a", "160k")
            else -> listOf("-c:a", "libmp3lame", "-q:a", "2")
        }
        val cmd = listOf(ffmpeg.absolutePath, "-y", "-i", input.absolutePath, "-vn") + codec + output.absolutePath
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = listOf(File(base, "python/usr/lib"), File(base, "ffmpeg/usr/lib"))
            .joinToString(":") { it.absolutePath }
        val p = pb.start()
        val log = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0 || !output.exists()) error(log.lines().lastOrNull { it.isNotBlank() } ?: "ffmpeg failed")
    }

    fun hasCookies() = cookiesFile().exists()

    fun setCookies(text: String?) {
        if (text == null) cookiesFile().delete() else cookiesFile().writeText(text)
    }
}
