package de.emil.shizubox.hilight

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import de.emil.shizubox.cleaner.CleanerJobs
import de.emil.shizubox.downloader.DownloadQueue
import de.emil.shizubox.downloader.TaskState
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.updates.Updates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Notifications → LEDs, called by ShizuBox' notification listener ([de.emil.shizubox.notifications.HistoryListener]).
 * - progress notifications (EXTRA_PROGRESS/_MAX, _INDETERMINATE) of the chosen apps/channels
 *   (default: system updates, Play Store) and ShizuBox' own app updates → [HiLightProgress]
 * - other notifications: colour per sender (contact rules, from MessagingStyle) before colour per app
 */
object HiLightNotifications {
    private val seen = HashMap<String, Long>()
    private const val OWN_UPDATE_CHANNEL = "app_update_progress"

    fun posted(context: Context, sbn: StatusBarNotification) {
        if (!HiLight.supported) return
        val n = sbn.notification
        val cfg = HiLightSettings.current
        val max = n.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val own = sbn.packageName == context.packageName
        if (max > 0 || indeterminate) {
            // Own downloads and cleaner scans are followed directly (OwnSources); here only app updates.
            if (own && n.channelId != OWN_UPDATE_CHANNEL) return
            val src = "${sbn.packageName}/${n.channelId}"
            if (!own && sbn.packageName !in cfg.progressSources && src !in cfg.progressSources) return
            val fraction = if (indeterminate || max <= 0) null else n.extras.getInt(Notification.EXTRA_PROGRESS, 0).toDouble() / max
            HiLightProgress.update(sbn.key, own, fraction)
            return
        }
        if (own || n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE) != 0) return
        // An update of the same notification that should not alert again (e.g. a typing indicator).
        synchronized(seen) {
            val prev = seen[sbn.key]
            seen[sbn.key] = sbn.postTime
            if (prev != null && n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) return
            if (seen.size > 500) seen.clear()
        }
        val sender = runCatching {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages?.lastOrNull()?.person?.name?.toString()
        }.getOrNull() ?: n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val contact = sender?.let { s -> cfg.contactRules.firstOrNull { it.name.isNotBlank() && s.contains(it.name, ignoreCase = true) } }
        val app = cfg.appRules.firstOrNull { it.on && it.pkg == sbn.packageName }
        val (color, pattern, faceDown) = when {
            // The sender's colour wins – unless the app itself was switched off.
            contact != null && cfg.appRules.none { it.pkg == sbn.packageName && !it.on } -> Triple(contact.color, contact.pattern, contact.faceDown)
            app != null -> Triple(app.color, app.pattern, app.faceDown)
            else -> return
        }
        HiLight.launch {
            HiLight.show(LightRequest("n:${sbn.key}", Layer.Notification, Pattern.of(pattern), intArrayOf(color), cfg.alertSeconds * 1000L, faceDown = faceDown))
        }
    }

    fun removed(sbn: StatusBarNotification) {
        if (!HiLight.supported) return
        if (HiLightProgress.active(sbn.key)) HiLightProgress.finish(sbn.key, sbn.packageName == de.emil.shizubox.Str.context.packageName, null)
    }
}

/** ShizuBox' own work: downloader, cleaner scans, update downloads – followed through their state. */
object OwnSources {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init() {
        if (!HiLight.supported) return
        scope.launch {
            val states = HashMap<String, TaskState>()
            DownloadQueue.tasks.collect { tasks ->
                for (t in tasks) {
                    val before = states[t.id]
                    states[t.id] = t.state
                    when (t.state) {
                        TaskState.RUNNING -> HiLightProgress.update("dl:${t.id}", true, t.progress.toDouble().takeIf { it > 0 })
                        TaskState.DONE -> if (before == TaskState.RUNNING) HiLightProgress.finish("dl:${t.id}", true, true)
                        TaskState.FAILED -> if (before == TaskState.RUNNING) HiLightProgress.finish("dl:${t.id}", true, false)
                        else -> Unit
                    }
                }
            }
        }
        scope.launch {
            var before = emptySet<de.emil.shizubox.cleaner.CleanTool>()
            CleanerJobs.running.collect { running ->
                running.values.forEach { HiLightProgress.update("cl:${it.tool}", true, it.progress?.toDouble()) }
                (before - running.keys).forEach { tool -> HiLightProgress.finish("cl:$tool", true, tool !in CleanerJobs.errors.value) }
                before = running.keys
            }
        }
        scope.launch {
            var before = emptySet<String>()
            Updates.downloads.collect { d ->
                d.forEach { (k, p) -> HiLightProgress.update("up:$k", true, p.toDouble().takeIf { it > 0 }) }
                (before - d.keys).forEach { k -> HiLightProgress.finish("up:$k", true, Updates.apps.value.firstOrNull { it.key == k }?.error == null) }
                before = d.keys
            }
        }
    }
}

/**
 * Camera/microphone hint with HiLight Studio's privacy timing (PrivacyScheduler, MIT): from the
 * moment a camera/microphone use starts, 10 s light, 10 s dark, repeated – but only within the
 * first minute of that use; after that it stays dark until the use ends. Camera before microphone.
 */
object HiLightPrivacy {
    private const val LIGHT_MS = 10_000L
    private const val COOLDOWN_MS = 10_000L
    private const val EPISODE_MAX_MS = 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watching = false
    val isWatching get() = watching
    private val episodes = HashMap<String, Long>() // "camera"/"mic" → start
    private var ticker: Job? = null

    private val callback = object : de.emil.shizubox.IHiLightCallback.Stub() {
        override fun onPrivacy(json: String) {
            val active = runCatching { JSONArray(json).let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("op") }.toSet() } }.getOrDefault(emptySet())
            onActive(active)
        }
    }

    /** Starts/stops watching according to the settings (and Shizuku). */
    fun sync() {
        if (!HiLight.supported) return
        val want = HiLightSettings.current.let { it.enabled && it.privacy } && ShizukuManager.isReady
        scope.launch {
            if (want && !watching) watching = ShizukuManager.watchPrivacy(callback)
            else if (!want && watching) { ShizukuManager.unwatchPrivacy(); watching = false; onActive(emptySet()) }
        }
    }

    /** The shell service went away (Shizuku stopped): its watcher is gone too. */
    fun disconnected() {
        watching = false
        onActive(emptySet())
    }

    @Synchronized
    private fun onActive(active: Set<String>) {
        val cfg = HiLightSettings.current
        val wanted = active.filter { (it == "camera" && cfg.privacyCamera) || (it == "mic" && cfg.privacyMic) }.toSet()
        val now = SystemClock.elapsedRealtime()
        episodes.keys.retainAll(wanted)
        wanted.forEach { episodes.putIfAbsent(it, now) }
        ticker?.cancel()
        if (episodes.isEmpty()) { HiLight.launch { HiLight.off("privacy") }; return }
        ticker = scope.launch {
            while (true) {
                val (op, start) = synchronized(this@HiLightPrivacy) { (episodes.entries.firstOrNull { it.key == "camera" } ?: episodes.entries.firstOrNull())?.toPair() } ?: break
                val elapsed = SystemClock.elapsedRealtime() - start
                if (elapsed >= EPISODE_MAX_MS) { HiLight.off("privacy"); break }
                val pos = elapsed % (LIGHT_MS + COOLDOWN_MS)
                if (pos < LIGHT_MS) {
                    HiLight.show(LightRequest("privacy", Layer.Privacy, if (op == "camera") Pattern.Solid else Pattern.Breathe,
                        intArrayOf(HiLightSettings.current.privacyColor), LIGHT_MS - pos, speedMs = 2000))
                    delay(LIGHT_MS - pos)
                } else {
                    HiLight.off("privacy")
                    delay(LIGHT_MS + COOLDOWN_MS - pos)
                }
            }
        }
    }
}

/** Charging: battery level as a bar when the charger is plugged in; green once at 100 %. */
object HiLightCharging {
    private const val PREF = "hilight_charge"

    fun plugged(context: Context) {
        val cfg = HiLightSettings.current
        if (!HiLight.supported || !cfg.charging) return
        val level = context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("full_shown", false).apply()
        HiLight.launch {
            HiLight.show(LightRequest("charge", Layer.Charging, Pattern.Progress, intArrayOf(if (level >= 100) HiLight.GREEN else 0xFFFFB300.toInt()), 5000, level / 100.0))
        }
    }

    /** From the 15-minute battery worker: once per charge, 3 s green when full. */
    fun check(context: Context) {
        val cfg = HiLightSettings.current
        if (!HiLight.supported || !cfg.charging) return
        val bm = context.getSystemService(BatteryManager::class.java)
        val prefs = context.createDeviceProtectedStorageContext().getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!bm.isCharging || bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) < 100 || prefs.getBoolean("full_shown", false)) return
        prefs.edit().putBoolean("full_shown", true).apply()
        HiLight.launch { HiLight.show(LightRequest("charge", Layer.Charging, Pattern.Solid, intArrayOf(HiLight.GREEN), 3000)) }
    }
}

class HiLightPowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_POWER_CONNECTED) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    // Shizuku may need a moment when the process was just started for this broadcast.
                    repeat(10) { if (!ShizukuManager.isReady) delay(300) }
                    HiLightCharging.plugged(context)
                    delay(1500)
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
