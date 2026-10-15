package de.emil.shizubox.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import de.emil.shizubox.shizuku.ShizukuManager
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
        intent.getStringExtra("shot")?.let { name ->
            // Offscreen screenshot: --es shot home|settings|system|device|processes|terminal|apps|app|updates|custom|school [--ei h 2400] [--es pkg x]
            val pending = goAsync()
            val ready = ShizukuManager.isReady
            val widget = if (name == "widget") kotlinx.coroutines.runBlocking { de.emil.shizubox.tiles.QuickWidgets.build(context) } else null
            val pkg = intent.getStringExtra("pkg") ?: context.packageName
            de.emil.shizubox.debug.ScreenRenderer.render(
                context, name, intent.getIntExtra("h", 2400), intent.getLongExtra("delay", 3500L), onDone = { pending.finish() },
            ) {
                val noop = {}
                when (name) {
                    "home" -> de.emil.shizubox.ui.home.HomeScreen(
                        shizukuStatus = ShizukuManager.status.value, serverMode = ShizukuManager.serverMode,
                        school = de.emil.shizubox.school.SchoolMode.state.value, busy = false, updateCount = Updates.updateCount(),
                        onShizukuAction = noop, onActivateSchool = noop, onOpenSection = {}, onOpenSettings = noop,
                    )
                    "settings" -> de.emil.shizubox.ui.settings.SettingsScreen(noop, noop, noop)
                    "system" -> de.emil.shizubox.ui.system.SystemScreen(shizukuReady = ready, onOpenPage = {}, onOpenUniversalInstaller = {}, onBack = noop)
                    "device" -> de.emil.shizubox.ui.system.DeviceInfoScreen(noop)
                    "processes" -> de.emil.shizubox.ui.system.ProcessesScreen(ready, noop)
                    "terminal" -> de.emil.shizubox.ui.system.TerminalScreen(ready, noop)
                    "widget" -> androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(top = 200.dp, start = 16.dp, end = 16.dp)) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx -> widget!!.apply(ctx, android.widget.FrameLayout(ctx)) },
                            modifier = androidx.compose.ui.Modifier.fillMaxWidth().height(96.dp),
                        )
                    }
                    "screentime" -> de.emil.shizubox.ui.system.ScreenTimeScreen(ready, noop)
                    "nhistory" -> de.emil.shizubox.ui.system.NotificationHistoryScreen(ready, noop, intent.getStringExtra("pkg"), intent.getStringExtra("conv"))
                    "apps" -> de.emil.shizubox.ui.apps.AppsScreen(ready, noop)
                    "app" -> de.emil.shizubox.ui.apps.AppDetailScreen(pkg, ready, noop)
                    "updates" -> de.emil.shizubox.ui.updates.UpdatesScreen(ready, noop)
                    "custom" -> de.emil.shizubox.ui.customization.CustomizationScreen(ready, noop)
                    else -> de.emil.shizubox.ui.school.SchoolScreen(
                        de.emil.shizubox.school.SchoolMode.state.value, ready, false, noop, {}, {}, noop,
                    )
                }
            }
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
                        "schedule" -> {
                            val sch = de.emil.shizubox.school.SchoolSchedule
                            fun f(t: Long?) = t?.let { java.util.Date(it).toString() } ?: "none"
                            val base = de.emil.shizubox.school.Schedule(enabled = true, days = 0b0011111, minuteOfDay = 7 * 60 + 50)
                            "real=${sch.state.value} weekdays7:50=${f(sch.nextStart(base))} sundayOnly=${f(sch.nextStart(base.copy(days = 0b1000000)))} " +
                                "skipFirst=${f(sch.nextStart(base.copy(skipUntil = (sch.nextStart(base) ?: 0) + 1)))} disabled=${f(sch.nextStart(base.copy(enabled = false)))}"
                        }
                        "ytdl" -> {
                            val t0 = System.currentTimeMillis()
                            Log.i(TAG, "ytdl: init …")
                            de.emil.shizubox.downloader.Ytdl.ensureInit()
                            Log.i(TAG, "ytdl: init done after ${System.currentTimeMillis() - t0} ms, version=${de.emil.shizubox.downloader.Ytdl.version()}")
                            val info = de.emil.shizubox.downloader.Ytdl.info(intent.getStringExtra("url") ?: "https://www.youtube.com/watch?v=jNQXAC9IVRw")
                            "info after ${System.currentTimeMillis() - t0} ms: ${info.title} heights=${info.heights} entries=${info.entries.size}"
                        }
                        "updnotify" -> {
                            UpdateNotifications.available(context)
                            "installable=${Updates.installableUpdates().map { "${it.name} ${it.installedVersion}->${it.latestVersion}" }}"
                        }
                        "updnotify_fake" -> {
                            // Visual check only: fake keys, so "Install" just opens the updater.
                            val n = intent.getIntExtra("n", 1)
                            val real = Updates.apps.value.take(n).map { it.copy(url = "fake:" + it.url, latestVersion = (it.latestVersion ?: "1") + ".1") }
                            UpdateNotifications.available(context, real)
                            "shown ${real.map { it.name }}"
                        }
                        "apkpure" -> buildString {
                            // VPN/DNS diagnosis: resolve, query the API, read the first KB of the download.
                            for (h in listOf("tapi.pureapk.com", "data.winudf.com", "d.apkpure.com")) {
                                append("dns $h=").append(runCatching { java.net.InetAddress.getAllByName(h).joinToString { it.hostAddress ?: "?" } }.getOrElse { "ERR ${it.javaClass.simpleName}: ${it.message}" }).append('\n')
                            }
                            val api = "https://tapi.pureapk.com/v3/get_app_his_version?package_name=com.x8bit.bitwarden&hl=en"
                            var dl: String? = null
                            runCatching {
                                val c = java.net.URL(api).openConnection() as java.net.HttpURLConnection
                                c.setRequestProperty("Ual-Access-Businessid", "projecta")
                                c.setRequestProperty("Ual-Access-ProjectA", "{\"device_info\":{\"os_ver\":\"37\"}}")
                                c.connectTimeout = 15000; c.readTimeout = 15000
                                val code = c.responseCode
                                val body = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
                                append("api http=$code len=${body.length} head=${body.take(160).replace('\n', ' ')}\n")
                                dl = runCatching { org.json.JSONObject(body).getJSONArray("version_list").getJSONObject(0).getJSONObject("asset").getString("url") }.getOrNull()
                            }.onFailure { append("api ERR ${it.javaClass.name}: ${it.message}\n") }
                            dl?.let { u ->
                                runCatching {
                                    val c = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                                    c.setRequestProperty("Range", "bytes=0-1023")
                                    c.connectTimeout = 15000; c.readTimeout = 15000
                                    val code = c.responseCode
                                    val body = (if (code < 400) c.inputStream else c.errorStream)?.readBytes() ?: ByteArray(0)
                                    append("dl http=$code url=${c.url.host} bytes=${body.size} type=${c.contentType} head=${String(body.take(120).toByteArray()).replace('\n', ' ')}\n")
                                }.onFailure { append("dl ERR ${it.javaClass.name}: ${it.message}\n") }
                            }
                            // Is a VPN active?
                            val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
                            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                            append("vpn=${caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)} privateDns=${cm.getLinkProperties(cm.activeNetwork)?.privateDnsServerName}")
                        }
                        "nh" -> {
                            // --es mode on|off|show ; "show" only lists test entries posted by the shell.
                            val h = de.emil.shizubox.notifications.NotifHistory
                            when (intent.getStringExtra("mode")) {
                                "on" -> { h.update { it.copy(enabled = true) }; "access=${h.setAccess(context, true)}" }
                                "sim" -> {
                                    // Deterministic chat as the shell package: 3 messages, then the middle one
                                    // disappears (deleted), then the app cancels the notification (withdrawn).
                                    val t0 = System.currentTimeMillis() - 60_000
                                    fun sbn(vararg m: Pair<String, Long>): android.service.notification.StatusBarNotification {
                                        val me = androidx.core.app.Person.Builder().setName("Ich").build()
                                        val style = androidx.core.app.NotificationCompat.MessagingStyle(me).setConversationTitle("Simchat (3 Nachrichten)").setGroupConversation(true)
                                        m.forEach { (txt, t) -> style.addMessage(txt, t, androidx.core.app.Person.Builder().setName(if (txt.startsWith("B")) "Bea" else "Alex").build()) }
                                        val n = androidx.core.app.NotificationCompat.Builder(context, "x").setSmallIcon(android.R.drawable.ic_dialog_info).setStyle(style).build()
                                        @Suppress("DEPRECATION")
                                        return android.service.notification.StatusBarNotification("com.android.shell", "com.android.shell", 4242, "sim", 2000, 0, 0, n, android.os.Process.myUserHandle(), System.currentTimeMillis())
                                    }
                                    h.posted(sbn("A: Hallo" to t0, "A: Wie gehts?" to t0 + 1000, "B: Gut" to t0 + 2000))
                                    kotlinx.coroutines.delay(500)
                                    h.posted(sbn("A: Hallo" to t0, "B: Gut" to t0 + 2000, "A: Neu" to t0 + 3000))
                                    kotlinx.coroutines.delay(500)
                                    h.removed(sbn("A: Hallo" to t0), android.service.notification.NotificationListenerService.REASON_APP_CANCEL)
                                    kotlinx.coroutines.delay(500)
                                    h.entries(pkg = "com.android.shell").joinToString("\n") { "${it.conv} | ${it.sender} | ${it.text} | ${it.state}" }
                                }
                                "clean" -> de.emil.shizubox.notifications.HistoryListener.instance?.let { l ->
                                    l.activeNotifications.filter { it.packageName == "com.android.shell" }.onEach { l.cancelNotification(it.key) }.size.toString()
                                } ?: "no listener"
                                "off" -> { h.update { it.copy(enabled = false) }; h.deleteAll(); "revoked=${h.setAccess(context, false)} " }
                                else -> "access=${h.hasAccess(context)} " + h.entries(pkg = "com.android.shell").joinToString("\n") { "${it.conv} | ${it.sender} | ${it.text} | ${it.state} | msg=${it.message} img=${it.image != null}" }
                            }
                        }
                        "netblock" -> {
                            // --es pkg <p> --es mode block|allow|restore
                            val nb = de.emil.shizubox.apps.NetBlock
                            val p = intent.getStringExtra("pkg") ?: "com.google.android.calculator"
                            when (intent.getStringExtra("mode")) {
                                "block" -> nb.set(listOf(p), true)
                                "allow" -> nb.set(listOf(p), false)
                                "restore" -> nb.restore()
                                else -> Unit
                            }
                            val sh = ShizukuManager.exec("cmd connectivity get-chain3-enabled; cmd connectivity get-package-networking-enabled $p").out.replace('\n', ' ')
                            "blocked=${nb.blocked.value} canBlock($p)=${nb.canBlock(p)} canBlock(settings)=${nb.canBlock("com.android.settings")} shell=$sh"
                        }
                        "boostfx" -> buildString {
                            // Can we attach effects to the global output mix (session 0) on this Android version?
                            runCatching {
                                val e = android.media.audiofx.LoudnessEnhancer(0)
                                e.setTargetGain(500); e.enabled = true
                                append("loudness ok control=${e.hasControl()} enabled=${e.enabled} gain=${e.targetGain}; ")
                                e.release()
                            }.onFailure { append("loudness ERR ${it.javaClass.simpleName}: ${it.message}; ") }
                            runCatching {
                                val d = android.media.audiofx.DynamicsProcessing(0)
                                append("dynamics ok control=${d.hasControl()} channels=${d.channelCount}; ")
                                d.release()
                            }.onFailure { append("dynamics ERR ${it.javaClass.simpleName}: ${it.message}; ") }
                            append("dose=${de.emil.shizubox.system.VolumeBoost.soundDose()}")
                        }
                        "boost" -> {
                            val pct = intent.getIntExtra("n", -1)
                            de.emil.shizubox.system.VolumeBoost.update { if (pct < 0) it.copy(enabled = false) else it.copy(enabled = true, speaker = pct, headphones = pct) }
                            de.emil.shizubox.system.BoostService.sync(context)
                            kotlinx.coroutines.delay(800)
                            "settings=${de.emil.shizubox.system.VolumeBoost.settings.value} hp=${de.emil.shizubox.system.VolumeBoost.headphonesConnected.value} fx=${de.emil.shizubox.system.VolumeBoost.status()}"
                        }
                        "queue" -> de.emil.shizubox.downloader.DownloadQueue.tasks.value.groupingBy { it.state }.eachCount().toString()
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
