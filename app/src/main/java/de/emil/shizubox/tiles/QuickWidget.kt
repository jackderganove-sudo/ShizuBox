package de.emil.shizubox.tiles

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import de.emil.shizubox.MainActivity
import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Home screen widget with the four quick actions. */
class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        QuickWidgets.scope.launch {
            try {
                QuickWidgets.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Taps on the widget. Not exported: only the widget's own PendingIntents reach it. */
class QuickWidgetToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(QuickWidgets.EXTRA_ACTION)?.let { name -> QuickAction.entries.firstOrNull { it.name == name } } ?: return
        val pending = goAsync()
        QuickWidgets.scope.launch {
            try {
                if (QuickWidgets.awaitShizuku()) runCatching { QuickActions.toggle(action, QuickActions.read(action)) }
                QuickWidgets.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}

object QuickWidgets {
    const val EXTRA_ACTION = "action"
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val CELLS = mapOf(
        QuickAction.Gaming to Triple(R.id.w_gaming, R.id.w_gaming_icon, R.id.w_gaming_label to R.id.w_gaming_state),
        QuickAction.Animations to Triple(R.id.w_anim, R.id.w_anim_icon, R.id.w_anim_label to R.id.w_anim_state),
        QuickAction.Refresh to Triple(R.id.w_refresh, R.id.w_refresh_icon, R.id.w_refresh_label to R.id.w_refresh_state),
        QuickAction.StayAwake to Triple(R.id.w_awake, R.id.w_awake_icon, R.id.w_awake_label to R.id.w_awake_state),
    )

    suspend fun awaitShizuku() = withTimeoutOrNull(5000) { ShizukuManager.status.first { it == ShizukuStatus.Ready } } != null

    /** Redraws all widgets with the current states (or "Shizuku" if it is not running). */
    suspend fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, QuickWidget::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, build(context))
    }

    suspend fun build(context: Context): RemoteViews {
        val ready = awaitShizuku()
        val views = RemoteViews(context.packageName, R.layout.widget_quick)
        CELLS.forEach { (action, ids2) ->
            val (cell, icon, texts) = ids2
            val info = if (ready) runCatching { QuickActions.read(action) }.getOrNull() else null
            val on = info?.active == true
            views.setTextViewText(texts.first, action.shortLabel)
            views.setTextViewText(texts.second, info?.subtitle ?: context.getString(R.string.shizuku))
            views.setInt(icon, "setBackgroundResource", if (on) R.drawable.widget_circle_on else R.drawable.widget_circle_off)
            views.setInt(icon, "setColorFilter", context.getColor(if (on) R.color.widget_icon_on else R.color.widget_icon_off))
            // Without Shizuku a tap opens the app, where the connection can be fixed.
            val click = if (ready) {
                val intent = Intent(context, QuickWidgetToggleReceiver::class.java).putExtra(EXTRA_ACTION, action.name)
                PendingIntent.getBroadcast(context, action.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } else {
                PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            }
            views.setOnClickPendingIntent(cell, click)
        }
        return views
    }

    /** Called when something changes inside the app (e.g. gaming mode switched on the page). */
    fun refreshAsync(context: Context) {
        scope.launch { runCatching { refresh(context) } }
    }
}
