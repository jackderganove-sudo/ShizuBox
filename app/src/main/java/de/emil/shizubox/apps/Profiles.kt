package de.emil.shizubox.apps

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A named group of apps that get frozen / unfrozen together (e.g. "Social Media"). */
data class FreezeProfile(val id: String, val name: String, val packages: Set<String>)

object Profiles {
    private lateinit var prefs: SharedPreferences
    private val _profiles = MutableStateFlow<List<FreezeProfile>>(emptyList())
    val profiles: StateFlow<List<FreezeProfile>> = _profiles.asStateFlow()

    fun init(context: Context) {
        prefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences("freeze_profiles", Context.MODE_PRIVATE)
        _profiles.value = load()
    }

    fun create(name: String) = save(_profiles.value + FreezeProfile(UUID.randomUUID().toString(), name.trim(), emptySet()))

    fun delete(id: String) = save(_profiles.value.filterNot { it.id == id })

    fun toggle(id: String, pkg: String) = save(
        _profiles.value.map {
            if (it.id != id) it else it.copy(packages = if (pkg in it.packages) it.packages - pkg else it.packages + pkg)
        },
    )

    private fun load(): List<FreezeProfile> {
        val arr = JSONArray(prefs.getString("profiles", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val pk = o.getJSONArray("packages")
            FreezeProfile(o.getString("id"), o.getString("name"), (0 until pk.length()).map { pk.getString(it) }.toSet())
        }
    }

    private fun save(list: List<FreezeProfile>) {
        _profiles.value = list
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("packages", JSONArray(p.packages.sorted())))
        }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }
}
