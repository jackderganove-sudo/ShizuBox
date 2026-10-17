package de.emil.shizubox.school

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.t
import de.emil.shizubox.tp

/** "X settings could not be restored" after school mode, with "Try again". */
object RestoreNotice {
    private const val CHANNEL = "school_restore"
    private const val ID = 5

    fun show(context: Context, count: Int) {
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, t(R.string.sr_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, ID,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, "School"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val retry = PendingIntent.getBroadcast(
            context, ID, Intent(context, RestoreRetryReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile_school)
                .setContentTitle(tp(R.plurals.sr_title, count, count))
                .setContentText(t(R.string.sr_text))
                .setStyle(Notification.BigTextStyle().bigText(t(R.string.sr_text)))
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(null, t(R.string.sr_retry), retry).build())
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}

/** "Try again" in [RestoreNotice]. Not exported: only our own notification can send it. */
class RestoreRetryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = SchoolMode.retryRestore()
}
