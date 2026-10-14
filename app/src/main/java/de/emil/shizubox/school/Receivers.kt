package de.emil.shizubox.school

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/** After a reboot: bring the black screen back immediately (Shizuku is not needed for that). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        SchoolMode.scope.launch {
            try {
                val s = SchoolMode.store.current()
                if (s.active) {
                    SchoolService.start(context)
                    SchoolMode.scheduleAutoEnd(s.endAt)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Fired by AlarmManager when the configured school time is over. */
class AutoEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = exitIfActive(context, goAsync())
}

/**
 * Emergency exit from the PC, only callable by adb (protected by the DUMP permission):
 * adb shell am broadcast -n de.emil.shizubox/.school.AdbExitReceiver
 */
class AdbExitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = exitIfActive(context, goAsync())
}

private fun exitIfActive(context: Context, pending: BroadcastReceiver.PendingResult) {
    SchoolMode.scope.launch {
        try {
            if (SchoolMode.store.current().active) SchoolService.requestExit(context)
        } finally {
            pending.finish()
        }
    }
}
