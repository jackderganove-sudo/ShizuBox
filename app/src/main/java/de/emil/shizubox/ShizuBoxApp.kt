package de.emil.shizubox

import android.app.Application
import de.emil.shizubox.school.SchoolMode
import de.emil.shizubox.shizuku.ShizukuManager

class ShizuBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuManager.init(this)
        SchoolMode.init(this)
    }
}
