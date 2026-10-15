package de.emil.shizubox.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Keeps the loudness effect alive while the boost is on, also when ShizuBox is in the background. */
class BoostService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            VolumeBoost.update { it.copy(enabled = false) }
        }
        startForeground(ID, notification(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        scope.launch {
            combine(VolumeBoost.settings, VolumeBoost.headphonesConnected) { s, _ -> s }.collect { s ->
                if (!s.enabled) {
                    VolumeBoost.release()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                VolumeBoost.apply()
                getSystemService(NotificationManager::class.java).notify(ID, notification(this@BoostService))
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        VolumeBoost.release()
        super.onDestroy()
    }

    companion object {
        private const val ID = 9
        private const val CHANNEL = "boost"
        private const val ACTION_STOP = "de.emil.shizubox.BOOST_STOP"

        fun sync(context: Context) {
            if (VolumeBoost.settings.value.enabled) context.startForegroundService(Intent(context, BoostService::class.java))
            else context.stopService(Intent(context, BoostService::class.java))
        }

        private fun notification(context: Context): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, t(R.string.vb_title), NotificationManager.IMPORTANCE_LOW))
            val pct = VolumeBoost.currentPercent()
            val stop = PendingIntent.getService(
                context, 91, Intent(context, BoostService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
            )
            val open = PendingIntent.getActivity(
                context, 92, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "System"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile_boost)
                .setContentTitle(t(R.string.vb_notif, pct))
                .setContentText(if (VolumeBoost.headphonesConnected.value) t(R.string.vb_out_headphones) else t(R.string.vb_out_speaker))
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(null, t(R.string.vb_stop), stop).build())
                .build()
        }
    }
}

/** Brings back what was running before a reboot or an app update (boost; later automations). */
class StartupReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // specialUse services may be started from BOOT_COMPLETED and MY_PACKAGE_REPLACED.
        if (VolumeBoost.settings.value.enabled) runCatching { BoostService.sync(context) }
    }
}
