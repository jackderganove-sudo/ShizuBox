package de.emil.shizubox.hilight

import android.app.NotificationManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import de.emil.shizubox.debug.ErrorKind
import de.emil.shizubox.debug.ErrorLog
import de.emil.shizubox.school.SchoolMode
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.math.sqrt

/** Who wants the LEDs; a higher [prio] wins while it lasts. */
enum class Layer(val prio: Int, val kind: String) {
    Charging(0, "progress"), ForeignProgress(1, "progress"), Privacy(2, "alert"), Notification(3, "alert"),
    OwnProgress(4, "progress"), Manual(5, "ambient"), Preview(6, "preview"),
}

data class LightRequest(
    val id: String,
    val layer: Layer,
    val pattern: Pattern,
    val colors: IntArray,
    val durationMs: Long,
    val progress: Double = 0.0,
    val speedMs: Long = 1500,
    /** This rule wants the phone face down (in addition to the global switch). */
    val faceDown: Boolean = false,
)

/**
 * App side of HiLight: decides whether something may light up and hands it to the renderer in the
 * shell service ([HiLightEngine]). Never while school mode runs (checked, school mode is not
 * touched), not when switched off, not during quiet times (Do Not Disturb, night, battery saver,
 * low battery), and with "face down only" only when the phone lies on its display.
 */
object HiLight {
    const val STUDIO_PACKAGE = "com.hilight.studio"
    private val MODELS = setOf("Pixel 11 Pro", "Pixel 11 Pro XL", "Pixel 11 Pro Fold")
    const val GREEN = 0xFF00C853.toInt()
    const val RED = 0xFFFF1744.toInt()
    const val OWN = 0xFF26C6DA.toInt()
    const val FOREIGN = 0xFF448AFF.toInt()

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var current: LightRequest? = null
    private var currentUntil = 0L
    private val _last = MutableStateFlow<String?>(null)
    /** Last decision, for the screen ("shown", "busy", reason why not). */
    val last: StateFlow<String?> = _last.asStateFlow()

    /** Pixel 11 Pro / Pro XL / Pro Fold with Android 17 – everywhere else HiLight is hidden. */
    val supported get() = Build.MODEL in MODELS && Build.VERSION.SDK_INT >= 37

    fun studioInstalled(context: Context) = runCatching { context.packageManager.getPackageInfo(STUDIO_PACKAGE, 0) }.isSuccess

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!supported) return
        // Stale LEDs after a crash or a killed process: clean once whenever Shizuku (re)connects –
        // also after a reboot, as soon as Shizuku runs.
        scope.launch {
            ShizukuManager.status.map { it == ShizukuStatus.Ready }.distinctUntilChanged().collect { ready ->
                if (ready) {
                    runCatching { send(JSONObject().put("op", "reset")) }
                    HiLightPrivacy.sync()
                } else {
                    HiLightPrivacy.disconnected()
                }
            }
        }
        // School mode: dark, without exception.
        scope.launch {
            SchoolMode.state.map { it.active }.distinctUntilChanged().collect { active -> if (active) off() }
        }
        scope.launch {
            HiLightSettings.state.map { it.enabled to it.privacy }.distinctUntilChanged().collect { (on, _) -> if (!on) off(); HiLightPrivacy.sync() }
        }
        // Charger plugged in: a runtime receiver – Android delivers POWER_CONNECTED to manifest
        // receivers no longer (implicit broadcast), so this works while ShizuBox' process lives;
        // the 15-minute battery worker covers "full".
        appContext.registerReceiver(HiLightPowerReceiver(), android.content.IntentFilter(android.content.Intent.ACTION_POWER_CONNECTED))
    }

    private suspend fun send(cmd: JSONObject): String = ShizukuManager.hilight(cmd.toString(), 4000)

    /** Why nothing may light up right now, or null. A [preview] skips the quiet times. */
    fun quietReason(cfg: HiLightConfig = HiLightSettings.current, preview: Boolean = false): String? {
        if (!supported) return "unsupported"
        if (SchoolMode.state.value.active) return "school"
        if (!cfg.enabled && !preview) return "off"
        if (!ShizukuManager.isReady) return "shizuku"
        // A preview is started by hand on purpose: quiet times do not apply (school mode and the
        // renderer's safety limits still do).
        if (preview) return null
        if (cfg.quietDnd && appContext.getSystemService(NotificationManager::class.java).currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) return "dnd"
        if (cfg.quietNight) {
            val c = Calendar.getInstance()
            val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
            val inNight = if (cfg.nightFrom <= cfg.nightTo) m in cfg.nightFrom until cfg.nightTo else m >= cfg.nightFrom || m < cfg.nightTo
            if (inNight) return "night"
        }
        if (cfg.quietPowerSave && appContext.getSystemService(PowerManager::class.java).isPowerSaveMode) return "powersave"
        if (cfg.quietLowBattery) {
            val bm = appContext.getSystemService(BatteryManager::class.java)
            if (!bm.isCharging && bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) in 0 until cfg.lowBatteryPercent) return "battery"
        }
        return null
    }

    /** Shows [r] if nothing forbids it. Returns true when the renderer accepted it. */
    suspend fun show(r: LightRequest): Boolean = lock.withLock {
        val cfg = HiLightSettings.current
        val preview = r.layer == Layer.Preview
        quietReason(cfg, preview)?.let { _last.value = it; return false }
        val now = SystemClock.elapsedRealtime()
        val cur = current
        if (cur != null && now < currentUntil && cur.layer.prio > r.layer.prio && cur.id != r.id) { _last.value = "lower"; return false }
        // A finite manual preview may skip only the face-down check (as in HiLight Studio).
        if ((cfg.faceDownOnly || r.faceDown) && !preview && !FaceDown.isFaceDown(appContext)) { _last.value = "facedown"; return false }
        val limit = if (r.layer == Layer.Notification) 60_000L else 300_000L
        val res = runCatching {
            send(JSONObject().put("op", "play").put("id", r.id).put("kind", r.layer.kind).put("pattern", r.pattern.key)
                .put("colors", JSONArray(r.colors.map { it.toLong() and 0xFFFFFFFFL })).put("durationMs", r.durationMs.coerceAtMost(limit))
                .put("progress", r.progress).put("speedMs", r.speedMs).put("brightness", cfg.brightness.toDouble()))
        }.getOrElse { "error: $it" }
        _last.value = res
        when {
            res == "ok" -> {
                current = r
                currentUntil = now + r.durationMs
                // The preview skipped the face-down check, so lifting must not end it either.
                if ((cfg.faceDownOnly || r.faceDown) && !preview) watchLift(r)
                return true
            }
            res == "busy" -> return false
            else -> { ErrorLog.add(ErrorKind.HiLight, "HiLight: ${res.take(120)}", res); return false }
        }
    }

    /** Lifting the phone ends a face-down effect at once. */
    private fun watchLift(r: LightRequest) {
        scope.launch {
            val lifted = FaceDown.waitForLift(appContext, r.durationMs)
            if (lifted) off(r.id)
        }
    }

    /** Stops the effect [id] (or whatever runs). */
    suspend fun off(id: String? = null) {
        lock.withLock {
            if (id == null || current?.id == id) { current = null; currentUntil = 0 }
        }
        if (ShizukuManager.isReady) runCatching { send(JSONObject().put("op", "off").apply { if (id != null) put("id", id) }) }
    }

    /** Updates the bar of a running progress effect without restarting it. */
    suspend fun progress(id: String, value: Double) {
        if (current?.id == id && ShizukuManager.isReady) runCatching { send(JSONObject().put("op", "progress").put("id", id).put("progress", value)) }
    }

    /** "Reset LEDs": everything black, session released. */
    suspend fun reset(): String {
        lock.withLock { current = null; currentUntil = 0 }
        return runCatching { send(JSONObject().put("op", "reset")) }.getOrElse { "error: $it" }
    }

    suspend fun status(): JSONObject? = runCatching { JSONObject(send(JSONObject().put("op", "status"))) }.getOrNull()

    fun launch(block: suspend () -> Unit) = scope.launch { runCatching { block() } }

    /** The configured colours of the wallpaper (Material You), as suggestions. */
    fun wallpaperColors(context: Context): List<Int> = listOf(
        android.R.color.system_accent1_400, android.R.color.system_accent2_400, android.R.color.system_accent3_400,
        android.R.color.system_accent1_200, android.R.color.system_accent3_200,
    ).mapNotNull { runCatching { context.getColor(it) }.getOrNull() }.distinct()
}

/**
 * Face down = gravity points out of the display. Read only when needed (no permanent sensor
 * listener, no extra service): a short sample of up to 1 s that needs ~400 ms of stable readings;
 * no sensor or no stable reading counts as "not face down" (fail closed, as in HiLight Studio).
 */
object FaceDown {
    private fun sensor(sm: SensorManager) = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun available(context: Context) = sensor(context.getSystemService(SensorManager::class.java)) != null

    private fun down(e: SensorEvent): Boolean? {
        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
        val g = sqrt(x * x + y * y + z * z)
        if (g < 7f || g > 12.5f) return null // moving
        return z / g < -0.85f
    }

    suspend fun isFaceDown(context: Context): Boolean {
        val sm = context.getSystemService(SensorManager::class.java)
        val s = sensor(sm) ?: return false
        return withTimeoutOrNull(1200) {
            suspendCancellableCoroutine { cont ->
                var since = 0L
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        val d = down(e)
                        val now = SystemClock.elapsedRealtime()
                        if (d != true) { since = 0; if (d == false) finish(false); return }
                        if (since == 0L) since = now
                        if (now - since >= 400) finish(true)
                    }
                    fun finish(v: Boolean) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(v)
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
                }
                sm.registerListener(l, s, SensorManager.SENSOR_DELAY_GAME)
                cont.invokeOnCancellation { sm.unregisterListener(l) }
            }
        } ?: false
    }

    /** True if the phone is lifted within [ms] (listens only that long). */
    suspend fun waitForLift(context: Context, ms: Long): Boolean {
        val sm = context.getSystemService(SensorManager::class.java)
        val s = sensor(sm) ?: return true
        return withTimeoutOrNull(ms) {
            suspendCancellableCoroutine { cont ->
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        if (down(e) == false) {
                            sm.unregisterListener(this)
                            if (cont.isActive) cont.resume(true)
                        }
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
                }
                sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(l) }
            }
        } ?: false
    }
}

/**
 * Progress on the LEDs, shown briefly and in intervals – never permanently: when something starts,
 * at every further 10 %, and otherwise every 90 s, each time for 4 s. At the end 3 s green
 * (success) or red (error). ShizuBox' own work outranks other apps' progress.
 */
object HiLightProgress {
    private class Track(var step: Int = -1, var shownAt: Long = 0L, var last: Double? = null)
    private val tracks = HashMap<String, Track>()
    private const val SHOW_MS = 4000L
    private const val EVERY_MS = 90_000L

    @Synchronized
    private fun due(key: String, fraction: Double?): Boolean {
        val now = SystemClock.elapsedRealtime()
        val t = tracks.getOrPut(key) { Track() }
        t.last = fraction
        val step = fraction?.let { (it * 10).toInt() } ?: -1
        val show = t.shownAt == 0L || (fraction != null && step > t.step) || now - t.shownAt >= EVERY_MS
        if (show) { t.shownAt = now; t.step = step }
        return show
    }

    /** [fraction] null = unknown (running light). */
    fun update(key: String, own: Boolean, fraction: Double?) {
        if (!HiLight.supported) return
        val cfg = HiLightSettings.current
        if (own && !cfg.ownProgress) return
        if (!due(key, fraction)) return
        HiLight.launch {
            HiLight.show(
                LightRequest(
                    "p:$key", if (own) Layer.OwnProgress else Layer.ForeignProgress,
                    if (fraction == null) Pattern.Scan else Pattern.Progress, intArrayOf(if (own) HiLight.OWN else HiLight.FOREIGN),
                    SHOW_MS, fraction ?: 0.0, speedMs = 1600,
                ),
            )
        }
    }

    /** [success] null = unknown (no colour at the end). */
    fun finish(key: String, own: Boolean, success: Boolean?) {
        val t = synchronized(this) { tracks.remove(key) } ?: return
        if (!HiLight.supported || (own && !HiLightSettings.current.ownProgress)) return
        val ok = success ?: if ((t.last ?: 0.0) >= 0.99) true else null
        HiLight.launch {
            HiLight.off("p:$key")
            if (ok != null) HiLight.show(
                LightRequest("done:$key", if (own) Layer.OwnProgress else Layer.ForeignProgress,
                    if (ok) Pattern.Solid else Pattern.Blink, intArrayOf(if (ok) HiLight.GREEN else HiLight.RED), 3000, speedMs = 600),
            )
        }
    }

    fun active(key: String) = synchronized(this) { key in tracks }
}
