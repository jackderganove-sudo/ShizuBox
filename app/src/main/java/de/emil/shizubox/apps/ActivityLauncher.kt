package de.emil.shizubox.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** All activities of one app, for the cross-app launcher. */
data class AppActivities(
    val pkg: String,
    val label: String,
    val versionName: String?,
    val isSystem: Boolean,
    val lastUpdate: Long,
    val activities: List<ActivityEntry>,
) {
    val exportedCount get() = activities.count { it.exported }
}

/**
 * Starting activities, shared by the app details and the Activity Launcher.
 * Tested on Android 17: an exported activity that needs a permission we lack (e.g. the hidden
 * `com.android.phone/.settings.RadioInfo`) starts through Shizuku with `am start -n`;
 * non-exported ones are refused for everyone but the app itself ("not exported from uid").
 */
object ActivityLauncher {
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    /** "pkg/activity" of starred activities. */
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _apps = MutableStateFlow<List<AppActivities>?>(null)
    val apps: StateFlow<List<AppActivities>?> = _apps.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = appContext.createDeviceProtectedStorageContext().getSharedPreferences("activity_launcher", Context.MODE_PRIVATE)
        _favorites.value = prefs.getStringSet("favorites", emptySet()).orEmpty()
    }

    fun key(pkg: String, activity: String) = "$pkg/$activity"

    fun toggleFavorite(pkg: String, activity: String) {
        val k = key(pkg, activity)
        val set = if (k in _favorites.value) _favorites.value - k else _favorites.value + k
        _favorites.value = set
        prefs.edit().putStringSet("favorites", set).apply()
    }

    /** Reloads favorites after a backup restore. */
    fun reload() {
        _favorites.value = prefs.getStringSet("favorites", emptySet()).orEmpty()
    }

    fun entry(pm: PackageManager, ai: ActivityInfo) = ActivityEntry(
        name = ai.name,
        exported = ai.exported && ai.enabled,
        label = ai.nonLocalizedLabel?.toString() ?: runCatching { if (ai.labelRes != 0) ai.loadLabel(pm).toString() else null }.getOrNull(),
    )

    /**
     * Loads the activities of all apps. Packages whose last update did not change come from a
     * small JSON cache, so after the first run the list is there almost instantly.
     */
    fun load(force: Boolean = false) {
        if (_loading.value || (_apps.value != null && !force)) return
        _loading.value = true
        scope.launch {
            try {
                val pm = appContext.packageManager
                val cache = readCache()
                @Suppress("DEPRECATION")
                val packages = pm.getInstalledPackages(PackageManager.MATCH_DISABLED_COMPONENTS)
                val result = packages.mapNotNull { p ->
                    val ai = p.applicationInfo ?: return@mapNotNull null
                    val cached = cache[p.packageName]?.takeIf { !force && it.lastUpdate == p.lastUpdateTime }
                    val activities = cached?.activities ?: runCatching {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(p.packageName, PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS)
                            .activities.orEmpty().map { entry(pm, it) }.sortedWith(compareBy({ !it.exported }, { it.name }))
                    }.getOrDefault(emptyList())
                    if (activities.isEmpty()) return@mapNotNull null
                    AppActivities(
                        pkg = p.packageName,
                        label = cached?.label ?: ai.loadLabel(pm).toString(),
                        versionName = p.versionName,
                        isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        lastUpdate = p.lastUpdateTime,
                        activities = activities,
                    )
                }.sortedBy { it.label.lowercase() }
                _apps.value = result
                writeCache(result)
            } finally {
                _loading.value = false
            }
        }
    }

    /** Starts the activity; returns null on success or a readable error. */
    suspend fun launch(context: Context, pkg: String, activity: String): String? {
        val intent = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkg, activity)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val direct = runCatching { context.startActivity(intent) }
        if (direct.isSuccess) return null
        val e = direct.exceptionOrNull()
        if (e is android.content.ActivityNotFoundException) return t(R.string.al_not_found)
        if (!ShizukuManager.isReady) return t(R.string.al_needs_shizuku)
        return withContext(Dispatchers.IO) {
            val r = runCatching { ShizukuManager.exec("am start -n ${pkg}/${activity}", timeoutMs = 8000) }.getOrNull()
                ?: return@withContext t(R.string.shizuku_not_conn)
            val text = r.out + "\n" + r.err
            when {
                "not exported" in text -> t(R.string.al_not_exported)
                "does not exist" in text || "unable to resolve" in text.lowercase() -> t(R.string.al_not_found)
                "Error" in text || "Exception" in text -> "⚠ " + text.lines().firstOrNull { "Error" in it || "Exception" in it }.orEmpty().trim()
                else -> null
            }
        }
    }

    /** Pins a home screen shortcut that opens the activity. */
    fun pinShortcut(context: Context, pkg: String, activity: String, label: String) {
        val sm = context.getSystemService(ShortcutManager::class.java)
        if (!sm.isRequestPinShortcutSupported) return
        val icon = Apps.icon(pkg)?.let { Icon.createWithBitmap(it) } ?: Icon.createWithResource(context, R.mipmap.ic_launcher)
        val intent = Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkg, activity)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val info = ShortcutInfo.Builder(context, "act:$pkg/$activity")
            .setShortLabel(label)
            .setLongLabel("$label · ${activity.substringAfterLast('.')}")
            .setIcon(icon)
            .setIntent(intent)
            .build()
        sm.requestPinShortcut(info, null)
    }

    // ------------------------------------------------------------- cache

    private val cacheFile get() = File(appContext.cacheDir, "activities.json")

    private fun readCache(): Map<String, AppActivities> = runCatching {
        val arr = JSONArray(cacheFile.readText())
        (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            val acts = o.getJSONArray("a")
            o.getString("p") to AppActivities(
                pkg = o.getString("p"),
                label = o.getString("l"),
                versionName = null,
                isSystem = o.optBoolean("s"),
                lastUpdate = o.getLong("u"),
                activities = (0 until acts.length()).map { j ->
                    val a = acts.getJSONObject(j)
                    ActivityEntry(a.getString("n"), a.getBoolean("e"), a.optString("t").takeIf { it.isNotEmpty() })
                },
            )
        }
    }.getOrDefault(emptyMap())

    private fun writeCache(list: List<AppActivities>) = runCatching {
        val arr = JSONArray()
        list.forEach { app ->
            arr.put(
                JSONObject().put("p", app.pkg).put("l", app.label).put("s", app.isSystem).put("u", app.lastUpdate)
                    .put("a", JSONArray(app.activities.map { JSONObject().put("n", it.name).put("e", it.exported).put("t", it.label ?: "") })),
            )
        }
        cacheFile.writeText(arr.toString())
    }
}
