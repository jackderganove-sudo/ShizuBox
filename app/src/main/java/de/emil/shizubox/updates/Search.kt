package de.emil.shizubox.updates

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SearchResult(val name: String, val description: String, val url: String, val source: String, val stars: Int = 0)

/** Text search on sources that offer it (GitHub, Codeberg, GitLab). */
object SourceSearch {
    val SOURCES = listOf("GitHub", "Codeberg", "GitLab")

    suspend fun search(query: String, sources: Set<String>): List<SearchResult> = coroutineScope {
        val q = Uri.encode(query.trim())
        val jobs = buildList {
            if ("GitHub" in sources) add(async { github(q) })
            if ("Codeberg" in sources) add(async { codeberg(q) })
            if ("GitLab" in sources) add(async { gitlab(q) })
        }
        jobs.awaitAll().flatten().sortedByDescending { it.stars }
    }

    private suspend fun github(q: String) = runCatching {
        val headers = if (Source.GitHub.token.isNotBlank()) mapOf("Authorization" to "Bearer ${Source.GitHub.token}") else emptyMap()
        val items = JSONObject(get("https://api.github.com/search/repositories?q=$q+android&per_page=15", headers)).getJSONArray("items")
        (0 until items.length()).map { items.getJSONObject(it) }.map {
            SearchResult(it.getString("full_name"), it.optString("description").takeIf { d -> d != "null" }.orEmpty(), it.getString("html_url"), "GitHub", it.optInt("stargazers_count"))
        }
    }.getOrDefault(emptyList())

    private suspend fun codeberg(q: String) = runCatching {
        val data = JSONObject(get("https://codeberg.org/api/v1/repos/search?q=$q&limit=15")).getJSONArray("data")
        (0 until data.length()).map { data.getJSONObject(it) }.map {
            SearchResult(it.getString("full_name"), it.optString("description"), it.getString("html_url"), "Codeberg", it.optInt("stars_count"))
        }
    }.getOrDefault(emptyList())

    private suspend fun gitlab(q: String) = runCatching {
        val arr = JSONArray(get("https://gitlab.com/api/v4/projects?search=$q&per_page=15&order_by=star_count"))
        (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            SearchResult(it.getString("path_with_namespace"), it.optString("description").takeIf { d -> d != "null" }.orEmpty(), it.getString("web_url"), "GitLab", it.optInt("star_count"))
        }
    }.getOrDefault(emptyList())

    private suspend fun get(url: String, headers: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "ShizuBox")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        c.inputStream.bufferedReader().readText()
    }
}
