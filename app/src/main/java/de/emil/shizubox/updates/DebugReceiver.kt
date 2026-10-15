package de.emil.shizubox.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Diagnostics from the PC (adb only, protected by the DUMP permission):
 * adb shell am broadcast -n de.emil.shizubox/.updates.DebugReceiver --es url <link>
 * Logs what the source returns under the tag "ShizuBoxUpdates" without adding the app.
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.hasExtra("strings")) {
            // Language check without unlocking: --ez strings true
            val app = de.emil.shizubox.Str.context
            Log.i(TAG, "locale=${app.resources.configuration.locales} settings=${de.emil.shizubox.t(de.emil.shizubox.R.string.settings)} " +
                "plural=${de.emil.shizubox.tp(de.emil.shizubox.R.plurals.w_pending, 3, 3)}")
            return
        }
        intent.getStringExtra("test")?.let { what ->
            // Backend checks without the UI: --es test processes|tweaks|cpu|device
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = when (what) {
                        "processes" -> de.emil.shizubox.system.Processes.snapshot().let { s ->
                            "summary=${s.summary} n=${s.processes.size} top=${s.processes.take(5).map { "${it.name}/${it.group}/${it.pssKb}" }}"
                        }
                        "perms" -> de.emil.shizubox.apps.PermissionOverview.load(context).entries.joinToString { (g, l) ->
                            "${g.name}=${l.count { !it.isSystem }}/${l.size}"
                        }
                        "backup" -> {
                            fun strip(j: String) = org.json.JSONObject(j).apply { remove("created") }.toString()
                            val a = de.emil.shizubox.settings.Backup.export(context, includeSecrets = false)
                            val r = de.emil.shizubox.settings.Backup.import(context, a)
                            val b = de.emil.shizubox.settings.Backup.export(context, includeSecrets = false)
                            "roundtrip equal=${strip(a) == strip(b)} result=$r size=${a.length} keys=${org.json.JSONObject(a).getJSONObject("prefs").keys().asSequence().toList()} vtKeyKept=${de.emil.shizubox.installer.VirusTotal.apiKey.isNotBlank()}"
                        }
                        "tweaks" -> de.emil.shizubox.system.Tweaks.read().toString()
                        "cpu" -> de.emil.shizubox.system.DeviceInfo.cpu().toString()
                        "device" -> "${de.emil.shizubox.system.DeviceInfo.battery(context)} ${de.emil.shizubox.system.DeviceInfo.memory(context)} ${de.emil.shizubox.system.DeviceInfo.storage()} soc=${de.emil.shizubox.system.DeviceInfo.soc()}"
                        else -> if (what.startsWith("app:")) {
                            de.emil.shizubox.apps.Apps.details(what.removePrefix("app:")).let { d ->
                                "tech=${d.tech.copy(activities = emptyList())} activities=${d.tech.activities.size}/${d.tech.activities.count { it.exported }} " +
                                    "devPerms=${d.permissions.filter { it.development }}"
                            }
                        } else "unknown test"
                    }
                    Log.i(TAG, "test $what: $result")
                } catch (e: Throwable) {
                    Log.e(TAG, "test $what failed", e)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        intent.getStringExtra("pkgs")?.let { pkgs ->
            // Bulk import dry run: adb shell am broadcast -n …/.updates.DebugReceiver --es pkgs a,b,c
            BulkImport.reset()
            BulkImport.start(pkgs.split(','), Updates.PACKAGE_SOURCES.keys.toList())
            CoroutineScope(Dispatchers.IO).launch {
                while (!BulkImport.state.value.finished) kotlinx.coroutines.delay(500)
                val st = BulkImport.state.value
                Log.i(TAG, "bulk hits=${st.hits.map { "${it.pkg}@${it.source}:${it.release.version}" }} notFound=${st.notFound}")
                BulkImport.reset()
            }
            return
        }
        val url = intent.getStringExtra("url") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val normalized = Source.normalize(url)
                val source = Source.forUrl(normalized)
                Log.i(TAG, "url=$normalized source=${source?.label}")
                val r = source?.latest(TrackedApp(id = "", url = normalized, name = "", author = ""))
                Log.i(TAG, "ok version=${r?.version} id=${r?.appId} name=${r?.name} apks=${r?.apks?.map { it.name }}")
            } catch (e: Throwable) {
                Log.e(TAG, "failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ShizuBoxUpdates"
    }
}
