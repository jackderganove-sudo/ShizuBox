package de.emil.shizubox.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.t
import java.io.File

data class BatteryInfo(
    val level: Int,
    val status: String,
    val plugged: String?,
    /** Null for vendor-specific codes (Pixel reports 11). */
    val health: String?,
    val temperatureC: Float,
    val voltageMv: Int,
    val technology: String?,
    val cycleCount: Int?,
    /** Current in mA, negative while discharging (sign depends on the device). */
    val currentMa: Int?,
)

data class MemoryInfo(val totalBytes: Long, val availBytes: Long, val lowMemory: Boolean)
data class StorageInfo(val totalBytes: Long, val freeBytes: Long)
data class CpuCore(val index: Int, val curKhz: Int?, val maxKhz: Int?)

object DeviceInfo {
    fun battery(context: Context): BatteryInfo? {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val bm = context.getSystemService(BatteryManager::class.java)
        return BatteryInfo(
            level = level,
            status = when (i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> t(R.string.bat_charging)
                BatteryManager.BATTERY_STATUS_DISCHARGING -> t(R.string.bat_discharging)
                BatteryManager.BATTERY_STATUS_FULL -> t(R.string.bat_full)
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> t(R.string.bat_not_charging)
                else -> "?"
            },
            plugged = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                BatteryManager.BATTERY_PLUGGED_AC -> t(R.string.bat_ac)
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> t(R.string.bat_wireless)
                BatteryManager.BATTERY_PLUGGED_DOCK -> "Dock"
                else -> null
            },
            health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> t(R.string.bat_good)
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> t(R.string.bat_overheat)
                BatteryManager.BATTERY_HEALTH_DEAD -> t(R.string.bat_dead)
                BatteryManager.BATTERY_HEALTH_COLD -> t(R.string.bat_cold)
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> t(R.string.bat_over_voltage)
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> t(R.string.bat_unknown)
                else -> null
            },
            temperatureC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            technology = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            cycleCount = if (Build.VERSION.SDK_INT >= 34) i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 } else null,
            currentMa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                .takeIf { it != Int.MIN_VALUE && it != 0 }?.let { it / 1000 },
        )
    }

    fun memory(context: Context): MemoryInfo {
        val mi = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        return MemoryInfo(mi.totalMem, mi.availMem, mi.lowMemory)
    }

    fun storage(): StorageInfo {
        val fs = StatFs(Environment.getDataDirectory().path)
        return StorageInfo(fs.totalBytes, fs.availableBytes)
    }

    /** Core frequencies; sysfs is read directly if allowed, otherwise through Shizuku. */
    suspend fun cpu(): List<CpuCore> {
        val n = Runtime.getRuntime().availableProcessors()
        fun direct(i: Int, f: String) = runCatching { File("/sys/devices/system/cpu/cpu$i/cpufreq/$f").readText().trim().toInt() }.getOrNull()
        val cores = (0 until n).map { CpuCore(it, direct(it, "scaling_cur_freq"), direct(it, "cpuinfo_max_freq")) }
        if (cores.any { it.curKhz != null } || !ShizukuManager.isReady) return cores
        val out = runCatching {
            ShizukuManager.exec(
                (0 until n).joinToString("; ") { "echo $(cat /sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq) $(cat /sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq)" },
            ).out.lines()
        }.getOrDefault(emptyList())
        return (0 until n).map { i ->
            val parts = out.getOrNull(i)?.trim()?.split(' ').orEmpty()
            CpuCore(i, parts.getOrNull(0)?.toIntOrNull(), parts.getOrNull(1)?.toIntOrNull())
        }
    }

    fun uptimeMs() = SystemClock.elapsedRealtime()

    fun kernel(): String = System.getProperty("os.version").orEmpty()

    fun soc(): String = if (Build.VERSION.SDK_INT >= 31) listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != Build.UNKNOWN }.joinToString(" ") else Build.HARDWARE

    fun androidName(sdk: Int = Build.VERSION.SDK_INT): String = "${Build.VERSION.RELEASE_OR_CODENAME} (API $sdk)"
}

fun formatDuration(ms: Long): String {
    val m = ms / 60_000
    val d = m / (60 * 24)
    val h = (m / 60) % 24
    val min = m % 60
    return buildString {
        if (d > 0) append(t(R.string.dur_days_short, d.toInt())).append(' ')
        if (d > 0 || h > 0) append(t(R.string.dur_hours, h.toInt())).append(' ')
        append(t(R.string.dur_min, min.toInt()))
    }
}
