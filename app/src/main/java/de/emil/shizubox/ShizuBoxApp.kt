package de.emil.shizubox

import android.app.Application
import de.emil.shizubox.apps.Apps
import de.emil.shizubox.apps.Profiles
import de.emil.shizubox.customization.Customization
import de.emil.shizubox.system.Gaming
import de.emil.shizubox.school.SchoolMode
import de.emil.shizubox.shizuku.ShizukuManager

class ShizuBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Customization.init(this)
        Apps.init(this)
        Profiles.init(this)
        Gaming.init(this)
        ShizukuManager.init(this)
        SchoolMode.init(this)
    }
}
