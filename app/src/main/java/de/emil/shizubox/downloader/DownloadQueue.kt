package de.emil.shizubox.downloader

import android.content.Context
import android.net.Uri
import de.emil.shizubox.R
import de.emil.shizubox.t
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import java.util.UUID

/**
 * All downloads: waiting, running, failed and finished. Runs up to [DlSettings.parallel] at
 * once inside [DownloadService], which keeps the process alive when the app is closed.
 */
object DownloadQueue {
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val jobs = mutableMapOf<String, Job>()
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val file get() = File(appContext.filesDir, "downloads.json")
    private const val MIN_FREE = 500L * 1024 * 1024

    /** False before the first unlock: then the list file can't be read and must not be overwritten. */
    @Volatile private var loaded = false

    fun init(context: Context) {
        appContext = context.applicationContext
        // Before the first unlock the list would read as empty and the next save would wipe it.
        de.emil.shizubox.util.Unlock.whenUnlocked(appContext) {
            _tasks.value = runCatching {
                val a = JSONArray(file.readText())
                (0 until a.length()).map { DownloadTask.fromJson(a.getJSONObject(it)) }
            }.getOrDefault(emptyList()) + _tasks.value
            loaded = true
            // A progress notification can survive a killed process (e.g. app update); drop it if idle.
            if (!active) runCatching { appContext.getSystemService(android.app.NotificationManager::class.java).cancel(7) }
        }
    }

    private fun save() {
        if (!loaded) return
        runCatching { file.writeText(JSONArray(_tasks.value.map { it.toJson() }).toString()) }
    }

    /** Pauses everything (e.g. the service's daily time budget ran out). */
    fun pauseAll() {
        _tasks.value.filter { it.state == TaskState.RUNNING || it.state == TaskState.QUEUED }.forEach { pause(it.id) }
    }

    private fun change(id: String, block: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) block(it) else it } }
    }

    val active get() = _tasks.value.any { it.state == TaskState.RUNNING || it.state == TaskState.QUEUED }

    fun enqueue(url: String, title: String, format: FormatChoice, uploader: String? = null, thumbnail: String? = null, durationSec: Long = 0) {
        val task = DownloadTask(UUID.randomUUID().toString(), url, title.ifBlank { url }, uploader, thumbnail, durationSec, format)
        _tasks.update { it + task }
        save()
        DownloadService.start(appContext)
    }

    fun enqueueAll(entries: List<MediaEntry>, format: FormatChoice) {
        val new = entries.map { DownloadTask(UUID.randomUUID().toString(), it.url, it.title, it.uploader, it.thumbnail, it.durationSec, format) }
        _tasks.update { it + new }
        save()
        DownloadService.start(appContext)
    }

    /** Starts waiting tasks up to the parallel limit; called by the service whenever something changes. */
    fun pump() {
        scope.launch {
            lock.withLock {
                val limit = DownloaderSettings.current.parallel.coerceIn(1, 4)
                val running = _tasks.value.count { it.state == TaskState.RUNNING }
                _tasks.value.filter { it.state == TaskState.QUEUED }.take((limit - running).coerceAtLeast(0)).forEach { start(it) }
            }
        }
    }

    private fun start(task: DownloadTask) {
        // Videos can be several GB: don't start with less than 500 MB free.
        if (android.os.StatFs(appContext.cacheDir.path).availableBytes < MIN_FREE) {
            change(task.id) { it.copy(state = TaskState.FAILED, error = t(R.string.e_no_space), status = null) }
            save()
            return
        }
        change(task.id) { it.copy(state = TaskState.RUNNING, error = null, status = null) }
        jobs[task.id] = scope.launch {
            val dir = DlStorage.tempDir(appContext, task.id)
            try {
                var lastEmit = 0L
                val out = Ytdl.download(task, dir) { p, status ->
                    val now = System.currentTimeMillis()
                    if (now - lastEmit > 300 || p >= 1f) {
                        lastEmit = now
                        change(task.id) { it.copy(progress = p, status = status) }
                    }
                }
                change(task.id) { it.copy(status = t(R.string.dl_saving)) }
                val saved = DlStorage.save(appContext, out, DownloaderSettings.current)
                dir.deleteRecursively()
                change(task.id) {
                    it.copy(
                        state = TaskState.DONE, progress = 1f, status = null, finishedAt = System.currentTimeMillis(),
                        fileUri = saved.uri.toString(), fileName = saved.name, mimeType = saved.mime, sizeBytes = saved.size,
                    )
                }
                DownloadService.notifyDone(appContext, _tasks.value.first { it.id == task.id })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // A pause/cancel kills the process; that is not an error.
                val cur = _tasks.value.firstOrNull { it.id == task.id }
                if (cur != null && cur.state == TaskState.RUNNING) {
                    change(task.id) { it.copy(state = TaskState.FAILED, status = null, error = readable(e)) }
                }
            } finally {
                jobs.remove(task.id)
                save()
                pump()
            }
        }
    }

    private fun readable(e: Throwable): String {
        val msg = e.message.orEmpty()
        return msg.lines().lastOrNull { it.startsWith("ERROR") }?.removePrefix("ERROR:")?.trim()
            ?: msg.lines().lastOrNull { it.isNotBlank() }?.take(300)
            ?: e.javaClass.simpleName
    }

    /** Stops the process but keeps the partial file; [resume] continues where it stopped. */
    fun pause(id: String) {
        change(id) { it.copy(state = TaskState.PAUSED, status = null) }
        Ytdl.cancel(id)
        save()
    }

    fun resume(id: String) {
        change(id) { it.copy(state = TaskState.QUEUED, error = null) }
        save()
        DownloadService.start(appContext)
    }

    fun retry(id: String) = resume(id)

    /** Cancels and forgets a task (its temp files too). */
    fun remove(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (task.state == TaskState.RUNNING) {
            change(id) { it.copy(state = TaskState.PAUSED) }
            Ytdl.cancel(id)
        }
        _tasks.update { list -> list.filterNot { it.id == id } }
        scope.launch { kotlinx.coroutines.delay(500); DlStorage.tempDir(appContext, id).deleteRecursively() }
        save()
    }

    /** Deletes a finished download from storage and from the list. */
    fun deleteFile(id: String) {
        _tasks.value.firstOrNull { it.id == id }?.fileUri?.let { DlStorage.delete(appContext, Uri.parse(it)) }
        remove(id)
    }

    fun retryAllFailed() {
        _tasks.update { list -> list.map { if (it.state == TaskState.FAILED) it.copy(state = TaskState.QUEUED, error = null) else it } }
        save()
        DownloadService.start(appContext)
    }

    fun clearFailed() {
        _tasks.update { list -> list.filterNot { it.state == TaskState.FAILED } }
        save()
    }

    /** Adds a converted file (offline video → audio) to the downloads list. */
    fun addFinished(title: String, saved: SavedFile, format: FormatChoice) {
        val task = DownloadTask(
            UUID.randomUUID().toString(), "", title, format = format, state = TaskState.DONE, progress = 1f,
            finishedAt = System.currentTimeMillis(), fileUri = saved.uri.toString(), fileName = saved.name,
            mimeType = saved.mime, sizeBytes = saved.size,
        )
        _tasks.update { it + task }
        save()
    }
}
