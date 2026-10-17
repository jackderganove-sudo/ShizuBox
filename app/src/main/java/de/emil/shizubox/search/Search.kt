package de.emil.shizubox.search

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import de.emil.shizubox.apps.Apps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

sealed interface SearchResult {
    val score: Int
    data class Feature(val entry: SearchEntry, override val score: Int) : SearchResult
    data class App(val pkg: String, val label: String, override val score: Int) : SearchResult
}

/**
 * Local search over [SearchIndex] (titles and keywords in all 5 languages) and installed apps.
 * Tolerant of typos: every query word must match a word of the entry – exactly, as a prefix,
 * inside (from 3 letters) or with 1 typo (from 4 letters) / 2 typos (from 7 letters).
 * The words are prepared once in the background; a search then takes well under a millisecond.
 */
object Search {
    private val LANGS = listOf("en", "de", "tr", "es", "fr")
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    @Volatile private var words: Map<String, List<String>> = emptyMap()
    /** Title words in all languages – a hit there ranks higher than one in the keywords. */
    @Volatile private var titleWords: Map<String, List<String>> = emptyMap()
    /** pkg, label, prepared words. */
    @Volatile private var apps: List<Triple<String, String, List<String>>> = emptyList()
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    /** Ids of recently opened entries (or "app:<pkg>"), newest first. */
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("search", Context.MODE_PRIVATE)
        _recent.value = prefs.getString("recent", "").orEmpty().split('\n').filter { it.isNotBlank() }
    }

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(Locale.ROOT).replace("ß", "ss"), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Builds the word lists (all languages) and the app list – call off the main thread. */
    suspend fun prepare() = withContext(Dispatchers.Default) {
        if (words.isEmpty()) {
            val res = LANGS.map { lang ->
                appContext.createConfigurationContext(Configuration(appContext.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }).resources
            }
            titleWords = SearchIndex.entries.associate { e ->
                e.id to norm(res.joinToString(" ") { r -> runCatching { r.getString(e.titleRes) }.getOrDefault("") }).split(' ').filter { it.isNotEmpty() }.distinct()
            }
            words = SearchIndex.entries.associate { e ->
                val keys = res.joinToString(" ") { r -> runCatching { r.getString(e.keysRes).replace(',', ' ') }.getOrDefault("") }
                e.id to (titleWords[e.id].orEmpty() + norm(keys).split(' ').filter { it.isNotEmpty() }).distinct()
            }
        }
        if (apps.isEmpty()) apps = runCatching {
            Apps.list().filter { it.installed }.map { Triple(it.pkg, it.label, (norm(it.label).split(' ') + it.pkg.lowercase(Locale.ROOT).split('.')).filter { w -> w.isNotEmpty() }) }
        }.getOrDefault(emptyList())
    }

    fun search(query: String): List<SearchResult> {
        val q = norm(query).split(' ').filter { it.isNotEmpty() }
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<SearchResult>()
        for (e in SearchIndex.entries) {
            if (!e.available()) continue
            val score = score(q, words[e.id].orEmpty())
            if (score > 0) out += SearchResult.Feature(e, score + if (score(q, titleWords[e.id].orEmpty()) > 0) 30 else 0)
        }
        for ((pkg, label, ws) in apps) {
            val score = score(q, ws)
            if (score >= 60 * q.size) out += SearchResult.App(pkg, label, score - 10)
        }
        return out.sortedByDescending { it.score }.take(30)
    }

    private fun score(query: List<String>, ws: List<String>): Int {
        var total = 0
        for (qw in query) {
            val best = ws.maxOfOrNull { w -> wordScore(qw, w) } ?: 0
            if (best == 0) return 0
            total += best
        }
        return total
    }

    private fun wordScore(q: String, w: String): Int = when {
        w == q -> 100
        w.startsWith(q) -> 80
        // Inside a word only from 4 letters ("led" would match "preinstalled").
        q.length >= 4 && w.contains(q) -> 55
        q.length >= 4 && typos(q, w.take(q.length + 1)) <= (if (q.length >= 7) 2 else 1) -> 40
        else -> 0
    }

    /** Damerau-Levenshtein distance (with swapped neighbours), capped early. */
    private fun typos(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        // Compare against the word cut to the query's length or one longer – whichever is closer.
        return minOf(d[a.length][b.length], d[a.length][minOf(b.length, a.length)])
    }

    fun remember(id: String) {
        val list = (listOf(id) + _recent.value.filter { it != id }).take(8)
        _recent.value = list
        prefs.edit().putString("recent", list.joinToString("\n")).apply()
    }

    fun clearRecent() {
        _recent.value = emptyList()
        prefs.edit().remove("recent").apply()
    }
}
