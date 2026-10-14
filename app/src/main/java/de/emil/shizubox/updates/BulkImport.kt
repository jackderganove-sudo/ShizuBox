/*
 * Bulk import of installed apps, flow modelled on ObtainX (GPL-3.0): search source by source,
 * then let the user review the hits before adding them.
 */
package de.emil.shizubox.updates

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportHit(val pkg: String, val source: String, val url: String, val release: Release)

data class SourceProgress(val source: String, val done: Int = 0, val total: Int = 0, val finished: Boolean = false)

data class BulkImportState(
    val running: Boolean = false,
    val current: String? = null,
    val progress: List<SourceProgress> = emptyList(),
    val hits: List<ImportHit> = emptyList(),
    val notFound: List<String> = emptyList(),
    val cancelled: Int = 0,
    val finished: Boolean = false,
)

object BulkImport {
    private val _state = MutableStateFlow(BulkImportState())
    val state: StateFlow<BulkImportState> = _state.asStateFlow()

    @Volatile
    private var cancel = false

    /** Own scope: the search must survive the screen change from selection to progress. */
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    fun start(packages: List<String>, sources: List<String>) {
        scope.launch { run(packages, sources) }
    }

    fun addHitsInBackground(hits: List<ImportHit>, onDone: (Int) -> Unit) {
        scope.launch {
            val n = addHits(hits)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(n) }
        }
    }

    fun cancel() {
        cancel = true
    }

    fun reset() {
        _state.value = BulkImportState()
    }

    /** Each source is searched for all packages not found by an earlier source (like ObtainX). */
    private suspend fun run(packages: List<String>, sources: List<String>) {
        cancel = false
        _state.value = BulkImportState(running = true, progress = sources.map { SourceProgress(it) })
        var remaining = packages
        for (source in sources) {
            val urlFor = Updates.PACKAGE_SOURCES[source] ?: continue
            val todo = remaining
            setProgress(source) { it.copy(total = todo.size) }
            val stillMissing = mutableListOf<String>()
            todo.forEachIndexed { i, pkg ->
                if (cancel) {
                    stillMissing += todo.drop(i)
                    return@forEachIndexed
                }
                _state.update { it.copy(current = "$source: $pkg") }
                val url = urlFor(pkg)
                val src = Source.forUrl(url)
                val release = try {
                    src?.latest(TrackedApp(id = pkg, url = url, name = "", author = ""))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (release != null && (release.apks.isNotEmpty() || src?.trackOnly == true)) {
                    _state.update { it.copy(hits = it.hits + ImportHit(pkg, source, url, release)) }
                } else {
                    stillMissing += pkg
                }
                setProgress(source) { it.copy(done = i + 1) }
            }
            setProgress(source) { it.copy(finished = true) }
            remaining = stillMissing
            if (cancel) break
        }
        _state.update {
            it.copy(
                running = false,
                current = null,
                notFound = if (cancel) emptyList() else remaining,
                cancelled = if (cancel) remaining.size else 0,
                finished = true,
            )
        }
    }

    private fun setProgress(source: String, block: (SourceProgress) -> SourceProgress) {
        _state.update { s -> s.copy(progress = s.progress.map { if (it.source == source) block(it) else it }) }
    }

    /** Adds the chosen hits without asking the sources again. */
    suspend fun addHits(hits: List<ImportHit>): Int {
        var n = 0
        for (h in hits) if (runCatching { Updates.addResolved(h.url, h.release) }.isSuccess) n++
        return n
    }
}
