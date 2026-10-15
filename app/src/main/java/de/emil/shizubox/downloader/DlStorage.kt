package de.emil.shizubox.downloader

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

data class SavedFile(val uri: Uri, val name: String, val mime: String, val size: Long)

/**
 * Where finished downloads go. yt-dlp works in a private temp folder; the result is then
 * copied to Download/ShizuBox (MediaStore, no storage permission needed) or to the folder the
 * user picked (Storage Access Framework).
 */
object DlStorage {
    const val DEFAULT_FOLDER = "Download/ShizuBox"

    fun tempRoot(context: Context) = File(context.cacheDir, "ytdl")
    fun tempDir(context: Context, id: String) = File(tempRoot(context), id)

    fun tempSize(context: Context): Long = tempRoot(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearTemp(context: Context, keep: Set<String> = emptySet()) {
        tempRoot(context).listFiles()?.forEach { if (it.name !in keep) it.deleteRecursively() }
    }

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

    fun save(context: Context, file: File, settings: DlSettings): SavedFile {
        val mime = mimeOf(file.name)
        val resolver = context.contentResolver
        val tree = settings.folderUri?.let(Uri::parse)
        val uri = if (tree != null) {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            DocumentsContract.createDocument(resolver, parent, mime, file.name) ?: error("cannot create file")
        } else {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, DEFAULT_FOLDER)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("cannot create file")
        }
        resolver.openOutputStream(uri, "w")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        if (tree == null) resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return SavedFile(uri, file.name, mime, file.length())
    }

    fun delete(context: Context, uri: Uri): Boolean = runCatching {
        if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.deleteDocument(context.contentResolver, uri)
        else context.contentResolver.delete(uri, null, null) > 0
    }.getOrDefault(false)

    fun exists(context: Context, uri: Uri): Boolean =
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)

    fun openIntent(task: DownloadTask): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(task.fileUri), task.mimeType ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    fun shareIntent(task: DownloadTask): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType(task.mimeType ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse(task.fileUri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        task.title,
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the download folder in a file manager. */
    fun folderIntent(settings: DlSettings): Intent = settings.folderUri?.let {
        Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(it), DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    } ?: Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Copies a content URI into a temp file (for converting a finished download). */
    fun toTemp(context: Context, uri: Uri, name: String): File {
        val f = File(tempRoot(context), "convert/$name").apply { parentFile?.mkdirs() }
        context.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
        return f
    }
}
