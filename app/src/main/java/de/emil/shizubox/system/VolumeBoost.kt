package de.emil.shizubox.system

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import de.emil.shizubox.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BoostSettings(
    val enabled: Boolean = false,
    /** Boost in percent (0–100) for the speaker, or for everything if [split] is off. */
    val speaker: Int = 30,
    /** Boost in percent for headphones (wired, Bluetooth, USB). */
    val headphones: Int = 15,
    /** Separate levels for speaker and headphones, switched automatically. */
    val split: Boolean = true,
    /** Upper limit of the sliders (protects hearing and speakers). */
    val limit: Int = 60,
)

/**
 * Volume above the maximum via [LoudnessEnhancer] on the global output mix (session 0).
 * 100 % = +10 dB, roughly twice as loud as perceived. Runs inside [BoostService].
 */
object VolumeBoost {
    /** Above this headphone boost the UI warns about hearing damage. */
    const val HEADPHONE_WARN = 40
    private lateinit var prefs: SharedPreferences
    private lateinit var audio: AudioManager
    private var enhancer: LoudnessEnhancer? = null

    private val _settings = MutableStateFlow(BoostSettings())
    val settings: StateFlow<BoostSettings> = _settings.asStateFlow()
    private val _headphones = MutableStateFlow(false)
    /** True while headphones are the active output. */
    val headphonesConnected: StateFlow<Boolean> = _headphones.asStateFlow()

    private val headphoneTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_USB_HEADSET,
    )

    fun init(context: Context) {
        audio = context.getSystemService(AudioManager::class.java)
        prefs = context.createDeviceProtectedStorageContext().getSharedPreferences("boost", Context.MODE_PRIVATE)
        load()
        audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = refreshOutput()
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = refreshOutput()
        }, null)
        refreshOutput()
    }

    fun load() {
        val d = BoostSettings()
        _settings.value = BoostSettings(
            enabled = prefs.getBoolean("enabled", false),
            speaker = prefs.getInt("speaker", d.speaker),
            headphones = prefs.getInt("headphones", d.headphones),
            split = prefs.getBoolean("split", d.split),
            limit = prefs.getInt("limit", d.limit),
        )
    }

    fun update(block: (BoostSettings) -> BoostSettings) {
        val s = block(_settings.value).let { it.copy(speaker = it.speaker.coerceIn(0, it.limit), headphones = it.headphones.coerceIn(0, it.limit)) }
        _settings.value = s
        prefs.edit().putBoolean("enabled", s.enabled).putInt("speaker", s.speaker).putInt("headphones", s.headphones)
            .putBoolean("split", s.split).putInt("limit", s.limit).apply()
        apply()
    }

    private fun refreshOutput() {
        // Where media is routed right now; fall back to "anything with headphones connected".
        val media = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build()
        val routed = runCatching { audio.getAudioDevicesForAttributes(media) }.getOrNull()
        _headphones.value = if (!routed.isNullOrEmpty()) routed.any { it.type in headphoneTypes }
        else audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headphoneTypes }
        apply()
    }

    /** Boost in percent that applies right now. */
    fun currentPercent(s: BoostSettings = _settings.value): Int =
        if (s.split && _headphones.value) s.headphones else s.speaker

    /** Called by the service and on every change: (re)creates the effect with the current gain. */
    @Synchronized
    fun apply() {
        val s = _settings.value
        if (!s.enabled) {
            release()
            return
        }
        val e = enhancer ?: runCatching { LoudnessEnhancer(0) }.getOrNull()?.also { enhancer = it } ?: return
        runCatching {
            e.setTargetGain(currentPercent(s) * 10) // millibel: 100 % = +10 dB
            e.enabled = true
        }
    }

    @Synchronized
    fun release() {
        enhancer?.let { runCatching { it.enabled = false; it.release() } }
        enhancer = null
    }

    /** For diagnosis: is the effect alive and does it have control over session 0? */
    fun status(): String = enhancer?.let { "gain=${runCatching { it.targetGain }.getOrNull()} enabled=${it.enabled} control=${it.hasControl()}" } ?: "off"

    // ------------------------------------------------------------- sound dose (CSD)

    /**
     * Android 17's hearing protection counts a "sound dose" and warns at 100 %. It cannot be
     * switched off via the shell, only reset (`cmd audio set-sound-dose-value`, tested); the
     * previous value is kept so it can be restored.
     */
    suspend fun soundDose(): Float? = runCatching {
        ShizukuManager.exec("cmd audio get-sound-dose-value").out.substringAfter(":").trim().toFloat()
    }.getOrNull()

    suspend fun resetSoundDose() {
        soundDose()?.let { prefs.edit().putFloat("dose_before_reset", it).apply() }
        ShizukuManager.exec("cmd audio set-sound-dose-value 0")
    }

    fun canRestoreDose() = ::prefs.isInitialized && prefs.contains("dose_before_reset")

    suspend fun restoreSoundDose() {
        val v = prefs.getFloat("dose_before_reset", -1f)
        if (v >= 0) ShizukuManager.exec("cmd audio set-sound-dose-value $v")
        prefs.edit().remove("dose_before_reset").apply()
    }
}
