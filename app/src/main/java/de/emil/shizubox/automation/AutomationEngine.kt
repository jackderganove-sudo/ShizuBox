package de.emil.shizubox.automation

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.school.SchoolMode
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import de.emil.shizubox.t
import de.emil.shizubox.tp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** What the device looks like right now, as far as triggers care. */
data class DeviceState(
    val foreground: String? = null,
    val headphones: Boolean = false,
    val bluetooth: Set<String> = emptySet(),
    val charging: Boolean = false,
    val battery: Int = 100,
    /** null = no Wi-Fi; "" = connected, name unknown (Shizuku not ready). */
    val ssid: String? = null,
)

/**
 * Evaluates the rules. Battery-friendly: battery, audio devices and Wi-Fi come as callbacks;
 * only "app opened/closed" needs to look at usage events, every 1.5 s and only while the
 * screen is on and such a rule exists. Times use exact alarms.
 * While school mode is active, everything pauses (no rule starts or ends).
 */
object AutomationEngine {
    private const val TAG = "ShizuBoxAuto"
    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(DeviceState())
    val state: StateFlow<DeviceState> = _state.asStateFlow()

    /** Rules whose state trigger is currently true (and were applied). */
    private val _active = MutableStateFlow<Set<String>>(emptySet())
    val active: StateFlow<Set<String>> = _active.asStateFlow()

    private val headphoneTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
    )
    private val btTypes = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST,
    )

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("automation_state", Context.MODE_PRIVATE)
        _active.value = prefs.getStringSet("active", emptySet()).orEmpty()
        // Rules changed: (re)start or stop the service, reschedule times, evaluate.
        scope.launch {
            Rules.rules.collect { list ->
                sync(appContext)
                scheduleNextTime(list)
                evaluate()
            }
        }
        // School mode ended → catch up (rules that ended meanwhile are reverted now).
        scope.launch {
            SchoolMode.state.map { it.active }.distinctUntilChanged().collect { if (!it) evaluate() }
        }
        // Almost every action needs Shizuku: wait for it (e.g. after a reboot), then catch up.
        scope.launch {
            ShizukuManager.status.collect { if (it == ShizukuStatus.Ready) evaluate() }
        }
    }

    private fun needsService(rules: List<Rule>) = rules.any { it.enabled && it.trigger.kind != TriggerKind.Time }

    fun sync(context: Context) {
        val intent = Intent(context, AutomationService::class.java)
        if (needsService(Rules.rules.value)) runCatching { context.startForegroundService(intent) }
        else context.stopService(intent)
    }

    private fun paused() = SchoolMode.state.value.active

    /** Set once the service has read the real device state (before that, everything is a default). */
    @Volatile internal var sensorsReady = false

    // ------------------------------------------------------------------ state updates (from the service)

    fun update(block: (DeviceState) -> DeviceState) {
        val before = _state.value
        val after = block(before)
        if (after == before) return
        _state.value = after
        scope.launch {
            // "App closed" is an event: the app left the foreground just now.
            if (before.foreground != null && before.foreground != after.foreground) {
                Rules.rules.value.filter { it.enabled && it.trigger.kind == TriggerKind.AppClose && it.trigger.pkg == before.foreground }
                    .forEach { fire(it) }
            }
            evaluate()
        }
    }

    private fun holds(r: Rule, s: DeviceState): Boolean {
        val tr = r.trigger
        return when (tr.kind) {
            TriggerKind.AppOpen -> s.foreground == tr.pkg
            TriggerKind.Headphones -> s.headphones == tr.connected
            TriggerKind.Bluetooth -> (if (tr.name.isBlank()) s.bluetooth.isNotEmpty() else s.bluetooth.any { it.equals(tr.name, true) }) == tr.connected
            TriggerKind.Charger -> s.charging == tr.connected
            TriggerKind.Battery -> s.battery < tr.percent
            TriggerKind.Wifi -> (if (tr.name.isBlank()) s.ssid != null else s.ssid.equals(tr.name, true)) == tr.connected
            TriggerKind.AppClose, TriggerKind.Time -> false
        }
    }

    /** Starts rules whose condition became true, ends (reverts) those that are no longer true. */
    suspend fun evaluate() = lock.withLock {
        if (paused() || !ShizukuManager.isReady) return@withLock
        val s = _state.value
        val rules = Rules.rules.value
        val active = _active.value.toMutableSet()
        // Removed or disabled rules end right away; the others only once the real state is known.
        for (id in active.toList()) {
            val r = rules.firstOrNull { it.id == id }
            if (r == null || !r.enabled || (sensorsReady && !holds(r, s))) {
                revert(id)
                active -= id
            }
        }
        for (r in rules) {
            if (!sensorsReady) break
            if (!r.enabled || !r.trigger.kind.isState || r.id in active || !holds(r, s)) continue
            if (r.trigger.kind == TriggerKind.Wifi && r.trigger.name.isNotBlank() && s.ssid == "") continue // name not known yet
            apply(r)
            active += r.id
        }
        _active.value = active
        prefs.edit().putStringSet("active", active).apply()
    }

    /** One-shot rules (time, app closed). */
    private suspend fun fire(r: Rule) = lock.withLock {
        if (paused() || !loopGuard(r)) return@withLock
        Log.i(TAG, "fire ${r.name}")
        r.actions.forEach { a -> runCatching { Actions.run(appContext, a) }.onFailure { Log.w(TAG, "action ${a.kind}", it) } }
    }

    /** Start times per rule within the last minute, for the loop guard. */
    private val recentStarts = mutableMapOf<String, ArrayDeque<Long>>()

    /**
     * A rule whose action flips its own trigger (e.g. "Wi-Fi connected → shell turns Wi-Fi off")
     * would run forever. More than 6 starts in a minute: the rule is switched off and logged.
     */
    private fun loopGuard(r: Rule): Boolean {
        val now = System.currentTimeMillis()
        val q = recentStarts.getOrPut(r.id) { ArrayDeque() }
        while (q.isNotEmpty() && now - q.first() > 60_000) q.removeFirst()
        q.addLast(now)
        if (q.size <= 6) return true
        recentStarts.remove(r.id)
        Rules.setEnabled(r.id, false)
        de.emil.shizubox.debug.ErrorLog.add(
            de.emil.shizubox.debug.ErrorKind.Hang, "Automation stopped: ${r.name.ifBlank { r.trigger.kind.name }}",
            "The rule started more than 6 times within a minute – probably its action switches its own trigger. It was turned off.",
        )
        return false
    }

    private suspend fun apply(r: Rule) {
        if (!loopGuard(r)) return
        Log.i(TAG, "start ${r.name}")
        val undo = mutableListOf<Action>()
        for (a in r.actions) {
            runCatching { Actions.run(appContext, a) }
                .onSuccess { inv -> if (r.revert && a.kind.revertible && inv != null) undo += inv }
                .onFailure { Log.w(TAG, "action ${a.kind}", it) }
        }
        prefs.edit().putString("undo_${r.id}", JSONArray(undo.map(Rules::encodeAction)).toString()).apply()
    }

    private suspend fun revert(id: String) {
        val json = prefs.getString("undo_$id", null) ?: return
        prefs.edit().remove("undo_$id").apply()
        val undo = runCatching { JSONArray(json).let { arr -> (0 until arr.length()).map { Rules.decodeAction(arr.getJSONObject(it)) } } }.getOrDefault(emptyList())
        Log.i(TAG, "end $id, undo ${undo.size}")
        // Reverse order, like unwinding.
        undo.asReversed().forEach { a -> runCatching { Actions.run(appContext, a) } }
    }

    /** Runs the actions of [r] once right now (editor "Try"). */
    suspend fun tryNow(r: Rule) {
        r.actions.forEach { a -> runCatching { Actions.run(appContext, a) } }
    }

    // ------------------------------------------------------------------ time triggers

    private fun nextTime(r: Rule, from: Calendar): Long? {
        if (r.trigger.days and 0b1111111 == 0) return null
        for (d in 0..7) {
            val c = (from.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, d)
                set(Calendar.HOUR_OF_DAY, r.trigger.minute / 60); set(Calendar.MINUTE, r.trigger.minute % 60)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (c.timeInMillis <= from.timeInMillis) continue
            val bit = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
            if (r.trigger.days shr bit and 1 == 1) return c.timeInMillis
        }
        return null
    }

    fun scheduleNextTime(rules: List<Rule> = Rules.rules.value) {
        val am = appContext.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(appContext, 77, Intent(appContext, AutomationAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val now = Calendar.getInstance()
        val next = rules.filter { it.enabled && it.trigger.kind == TriggerKind.Time }.mapNotNull { nextTime(it, now) }.minOrNull()
        if (next == null) am.cancel(pi) else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    }

    /**
     * Alarm went off: run every time rule whose occurrence is due, each occurrence only once
     * ("time_<id>" = the last one that ran, see [TimeTrigger]), then schedule the next one.
     */
    suspend fun onAlarm() {
        val now = Calendar.getInstance()
        for (r in Rules.rules.value) {
            if (!r.enabled || r.trigger.kind != TriggerKind.Time) continue
            val occ = TimeTrigger.due(r.trigger.days, r.trigger.minute, now, prefs.getLong("time_${r.id}", 0)) ?: continue
            // Marked before running: a crash in the actions must not repeat them on the next alarm.
            prefs.edit().putLong("time_${r.id}", occ).commit()
            fire(r)
        }
        scheduleNextTime()
    }

    internal fun usesApps() = Rules.rules.value.any { it.enabled && (it.trigger.kind == TriggerKind.AppOpen || it.trigger.kind == TriggerKind.AppClose) }

    // ------------------------------------------------------------------ sensors (run inside the service)

    internal class Sensors(private val context: Context, private val scope: CoroutineScope) {
        private val audio = context.getSystemService(AudioManager::class.java)
        private val cm = context.getSystemService(ConnectivityManager::class.java)
        private val power = context.getSystemService(PowerManager::class.java)
        private var poller: Job? = null

        private val battery = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                update { it.copy(charging = plugged, battery = if (level >= 0) level * 100 / scale else it.battery) }
            }
        }
        private val screen = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = pollApps()
        }
        private val devices = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = readAudio()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = readAudio()
        }
        private val wifi = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = readWifi()
            override fun onLost(network: Network) = readWifi()
        }

        fun start() {
            context.registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            context.registerReceiver(screen, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
            audio.registerAudioDeviceCallback(devices, null)
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifi)
            readAudio()
            readWifi()
            pollApps()
            scope.launch {
                // The first Wi-Fi read is delayed; after that the state is real.
                delay(2500)
                sensorsReady = true
                AutomationEngine.evaluate()
            }
            // Shizuku came up later (boot): the Wi-Fi name can be read now.
            scope.launch { ShizukuManager.status.collect { if (it == ShizukuStatus.Ready) readWifi() } }
            scope.launch { Rules.rules.collect { pollApps() } }
        }

        fun stop() {
            sensorsReady = false
            runCatching { context.unregisterReceiver(battery) }
            runCatching { context.unregisterReceiver(screen) }
            audio.unregisterAudioDeviceCallback(devices)
            runCatching { cm.unregisterNetworkCallback(wifi) }
            poller?.cancel()
        }

        private fun readAudio() {
            val outs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            update { s ->
                s.copy(
                    headphones = outs.any { it.type in headphoneTypes },
                    bluetooth = outs.filter { it.type in btTypes }.map { it.productName.toString() }.toSet(),
                )
            }
        }

        /** Wi-Fi name via `cmd wifi status` ("Wifi is connected to "Name"", tested) – no location permission needed. */
        private fun readWifi() {
            scope.launch {
                delay(1500) // let the connection settle
                val caps = cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }
                val onWifi = caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
                val name = if (!onWifi) null else if (!ShizukuManager.isReady) "" else runCatching {
                    Regex("connected to \"(.*)\"").find(ShizukuManager.exec("cmd wifi status").out)?.groupValues?.get(1)
                }.getOrNull() ?: ""
                update { it.copy(ssid = name) }
            }
        }

        /** Foreground app from usage events, only while needed (screen on + app rule). */
        private fun pollApps() {
            val want = power.isInteractive && usesApps()
            if (!want) {
                poller?.cancel(); poller = null
                return
            }
            if (poller?.isActive == true) return
            poller = scope.launch {
                val usm = context.getSystemService(UsageStatsManager::class.java)
                var last = System.currentTimeMillis() - 10_000
                val e = UsageEvents.Event()
                while (isActive) {
                    val now = System.currentTimeMillis()
                    var fg: String? = null
                    runCatching {
                        val events = usm.queryEvents(last, now)
                        while (events.hasNextEvent()) {
                            events.getNextEvent(e)
                            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) fg = e.packageName
                        }
                    }
                    last = now
                    fg?.let { p -> update { it.copy(foreground = p) } }
                    delay(1500)
                }
            }
        }
    }
}

/** Runs while at least one non-time rule is on; the notification says how many. */
class AutomationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sensors: AutomationEngine.Sensors? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (sensors == null) {
            sensors = AutomationEngine.Sensors(this, scope).also { it.start() }
            scope.launch {
                Rules.rules.collect { getSystemService(NotificationManager::class.java).notify(ID, notification()) }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sensors?.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, t(R.string.sec_auto), NotificationManager.IMPORTANCE_MIN))
        val n = Rules.rules.value.count { it.enabled }
        val open = PendingIntent.getActivity(
            this, 93, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "Automations"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tile_auto)
            .setContentTitle(tp(R.plurals.au_running, n, n))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val ID = 11
        private const val CHANNEL = "automations"
    }
}

class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                AutomationEngine.onAlarm()
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Clock or time zone changed: alarms are absolute times, so the next time rule and the automatic
 * cleaning are planned again (Daniel's review, 0.11.1). The school schedule has its own receiver.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        runCatching { AutomationEngine.scheduleNextTime() }
        runCatching { de.emil.shizubox.cleaner.CleanerSchedule.schedule(context) }
    }
}
