package de.emil.shizubox

import android.app.Application
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
        VirusTotal.init(this)
        Updates.init(this)
        UpdateWorker.schedule(this)
        ShizukuManager.init(this)
        SchoolMode.init(this)
        de.emil.shizubox.school.SchoolSchedule.init(this)
    }
}
