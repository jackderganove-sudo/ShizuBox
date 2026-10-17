package de.emil.shizubox

import android.app.Application
import kotlinx.coroutines.launch
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.apps.Profiles
import de.emil.shizubox.customization.Customization
import de.emil.shizubox.settings.AppSettings
import de.emil.shizubox.installer.VirusTotal
import de.emil.shizubox.system.Gaming
import de.emil.shizubox.updates.UpdateWorker
import de.emil.shizubox.updates.Updates
import de.emil.shizubox.school.SchoolMode
import de.emil.shizubox.shizuku.ShizukuManager

class ShizuBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Before everything else, so even a crash during the rest of the start is recorded.
        de.emil.shizubox.debug.ErrorLog.init(this)
        Str.init(this)
        AppSettings.init(this)
        Customization.init(this)
        Apps.init(this)
        Profiles.init(this)
        Gaming.init(this)
        de.emil.shizubox.apps.ActivityLauncher.init(this)
        de.emil.shizubox.downloader.Ytdl.init(this)
        de.emil.shizubox.downloader.DownloaderSettings.init(this)
        de.emil.shizubox.downloader.DownloadQueue.init(this)
        de.emil.shizubox.system.VolumeBoost.init(this)
        de.emil.shizubox.notifications.NotifHistory.init(this)
        de.emil.shizubox.apps.NetBlock.init(this)
        de.emil.shizubox.apps.Debloat.init(this)
        de.emil.shizubox.privacy.PrivacyLog.init(this)
        de.emil.shizubox.battery.BatteryReport.init(this)
        de.emil.shizubox.clipboard.ClipHistory.init(this)
        de.emil.shizubox.audio.AppVolume.init(this)
        de.emil.shizubox.cleaner.CleanerSettings.init(this)
        de.emil.shizubox.cleaner.Trash.init(this)
        de.emil.shizubox.cleaner.CleanerJobs.init(this)
        de.emil.shizubox.cleaner.Duplicates.init(this)
        de.emil.shizubox.hilight.HiLightSettings.init(this)
        // Scheduled cleaning: re-armed at every start (also after a reboot).
        runCatching { de.emil.shizubox.cleaner.CleanerSchedule.schedule(this) }
        de.emil.shizubox.util.Unlock.whenUnlocked(this) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                de.emil.shizubox.system.CacheCleaner.cleanStale(this@ShizuBoxApp)
                runCatching { de.emil.shizubox.debug.ErrorLog.importExitReasons(this@ShizuBoxApp) }
            }
        }
        VirusTotal.init(this)
        Updates.init(this)
        de.emil.shizubox.updates.InstallGuard.init(this)
        // WorkManager keeps its database in credential-protected storage: before the first unlock
        // (the app also starts then, for school mode) it crashes the whole app – since 0.4.0.
        if (getSystemService(android.os.UserManager::class.java).isUserUnlocked) {
            UpdateWorker.schedule(this)
            de.emil.shizubox.privacy.PrivacyWorker.schedule(this)
            de.emil.shizubox.battery.BatteryWorker.schedule(this)
            de.emil.shizubox.cleaner.TrashWorker.schedule(this)
        } else {
            registerReceiver(object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, i: android.content.Intent) {
                    runCatching { unregisterReceiver(this) }
                    UpdateWorker.schedule(this@ShizuBoxApp)
                    de.emil.shizubox.privacy.PrivacyWorker.schedule(this@ShizuBoxApp)
                    de.emil.shizubox.battery.BatteryWorker.schedule(this@ShizuBoxApp)
                    de.emil.shizubox.cleaner.TrashWorker.schedule(this@ShizuBoxApp)
                }
            }, android.content.IntentFilter(android.content.Intent.ACTION_USER_UNLOCKED))
        }
        ShizukuManager.init(this)
        SchoolMode.init(this)
        de.emil.shizubox.school.SchoolSchedule.init(this)
        // Needs Shizuku and school mode state (automations pause during school mode).
        de.emil.shizubox.automation.Rules.init(this)
        de.emil.shizubox.automation.AutomationEngine.init(this)
        // Watches school mode's state (read-only) – must come after SchoolMode.init.
        de.emil.shizubox.schoolstats.SchoolStats.init(this)
        // HiLight only reads school mode's state – after SchoolMode.init.
        de.emil.shizubox.hilight.HiLight.init(this)
        de.emil.shizubox.hilight.OwnSources.init()
    }
}
