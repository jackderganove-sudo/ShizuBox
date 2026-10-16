package de.emil.shizubox.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager

/**
 * ShizuBox also starts before the first unlock after a reboot (school mode, direct boot). Then the
 * normal (credential-protected) storage is not readable yet – anything that lives there must wait.
 */
object Unlock {
    fun isUnlocked(context: Context) = context.getSystemService(UserManager::class.java).isUserUnlocked

    /** Runs [block] now if the user is unlocked, otherwise once right after the first unlock. */
    fun whenUnlocked(context: Context, block: () -> Unit) {
        val app = context.applicationContext
        if (isUnlocked(app)) {
            block()
            return
        }
        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                runCatching { app.unregisterReceiver(this) }
                block()
            }
        }, IntentFilter(Intent.ACTION_USER_UNLOCKED))
    }
}
