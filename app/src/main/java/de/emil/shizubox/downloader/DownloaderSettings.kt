package de.emil.shizubox.downloader

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A locally followed channel (no account needed). */
data class Subscription(val url: String, val name: String, val avatar: String?)

data class DlSettings(
    /** Tree URI of a folder picked by the user; null = Download/ShizuBox. */
    val folderUri: String? = null,
    val folderName: String? = null,
    val defaultFormat: FormatChoice = FormatChoice(),
    val parallel: Int = 2,
    val subscriptions: List<Subscription> = emptyList(),
)

/** Downloader settings, part of the normal ShizuBox backup ("downloader" preferences). */
object DownloaderSettings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(DlSettings())
    val state: StateFlow<DlSettings> = _state.asStateFlow()
    val current get() = _state.value

    fun init(context: Context) {
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("downloader", Context.MODE_PRIVATE)
        load()
    }

    fun load() {
        _state.value = DlSettings(
            folderUri = prefs.getString("folder_uri", null),
            folderName = prefs.getString("folder_name", null),
            defaultFormat = FormatChoice.fromJson(prefs.getString("format", null)?.let { runCatching { JSONObject(it) }.getOrNull() }),
            parallel = prefs.getInt("parallel", 2),
            subscriptions = runCatching {
                val a = JSONArray(prefs.getString("subscriptions", "[]"))
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    Subscription(o.getString("url"), o.optString("name"), o.optString("avatar").ifEmpty { null })
                }
            }.getOrDefault(emptyList()),
        )
    }

    fun update(block: (DlSettings) -> DlSettings) {
        val s = block(_state.value)
        _state.value = s
        prefs.edit()
            .putString("folder_uri", s.folderUri)
            .putString("folder_name", s.folderName)
            .putString("format", s.defaultFormat.toJson().toString())
            .putInt("parallel", s.parallel)
            .putString(
                "subscriptions",
                JSONArray(s.subscriptions.map { JSONObject().put("url", it.url).put("name", it.name).put("avatar", it.avatar ?: "") }).toString(),
            )
            .apply()
    }

    fun isSubscribed(url: String) = current.subscriptions.any { it.url == url }

    fun toggleSubscription(sub: Subscription) = update { s ->
        s.copy(subscriptions = if (s.subscriptions.any { it.url == sub.url }) s.subscriptions.filterNot { it.url == sub.url } else s.subscriptions + sub)
    }
}
