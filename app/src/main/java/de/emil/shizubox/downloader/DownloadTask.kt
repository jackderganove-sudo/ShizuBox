package de.emil.shizubox.downloader

import org.json.JSONObject

enum class TaskState { QUEUED, RUNNING, PAUSED, FAILED, DONE }

enum class MediaKind { VIDEO, AUDIO }

/** What to download: video up to a height, or audio in a format. */
data class FormatChoice(
    val kind: MediaKind = MediaKind.VIDEO,
    /** Max video height, 0 = best available. */
    val height: Int = 1080,
    /** Video container: mp4, mkv or webm. */
    val container: String = "mp4",
    /** Audio format: mp3, m4a or opus. */
    val audioFormat: String = "mp3",
    val embedThumbnail: Boolean = true,
    val embedMetadata: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind.name).put("height", height).put("container", container)
        .put("audio", audioFormat).put("thumb", embedThumbnail).put("meta", embedMetadata)

    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) FormatChoice() else FormatChoice(
            kind = runCatching { MediaKind.valueOf(o.optString("kind")) }.getOrDefault(MediaKind.VIDEO),
            height = o.optInt("height", 1080),
            container = o.optString("container", "mp4"),
            audioFormat = o.optString("audio", "mp3"),
            embedThumbnail = o.optBoolean("thumb", true),
            embedMetadata = o.optBoolean("meta", true),
        )
    }
}

/** One entry of the queue; finished ones form the downloads list. */
data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val uploader: String? = null,
    val thumbnail: String? = null,
    val durationSec: Long = 0,
    val format: FormatChoice = FormatChoice(),
    val state: TaskState = TaskState.QUEUED,
    val progress: Float = 0f,
    /** Speed/ETA line from yt-dlp while running. */
    val status: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0,
    /** Final file: content:// URI (MediaStore or folder chosen by the user). */
    val fileUri: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
) {
    val isAudio get() = format.kind == MediaKind.AUDIO

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("url", url).put("title", title).put("uploader", uploader ?: JSONObject.NULL)
        .put("thumbnail", thumbnail ?: JSONObject.NULL).put("duration", durationSec).put("format", format.toJson())
        // A download that was running when the app died continues as "queued".
        .put("state", (if (state == TaskState.RUNNING) TaskState.QUEUED else state).name)
        .put("progress", progress.toDouble()).put("error", error ?: JSONObject.NULL)
        .put("created", createdAt).put("finished", finishedAt)
        .put("fileUri", fileUri ?: JSONObject.NULL).put("fileName", fileName ?: JSONObject.NULL)
        .put("mime", mimeType ?: JSONObject.NULL).put("size", sizeBytes)

    companion object {
        fun fromJson(o: JSONObject): DownloadTask {
            fun str(k: String) = o.optString(k).takeIf { it.isNotEmpty() && it != "null" }
            return DownloadTask(
                id = o.getString("id"),
                url = o.getString("url"),
                title = o.optString("title"),
                uploader = str("uploader"),
                thumbnail = str("thumbnail"),
                durationSec = o.optLong("duration"),
                format = FormatChoice.fromJson(o.optJSONObject("format")),
                state = runCatching { TaskState.valueOf(o.optString("state")) }.getOrDefault(TaskState.QUEUED),
                progress = o.optDouble("progress", 0.0).toFloat(),
                error = str("error"),
                createdAt = o.optLong("created"),
                finishedAt = o.optLong("finished"),
                fileUri = str("fileUri"),
                fileName = str("fileName"),
                mimeType = str("mime"),
                sizeBytes = o.optLong("size"),
            )
        }
    }
}
