package de.emil.shizubox.school

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import de.emil.shizubox.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs while the school mode is active: keeps the screen black and watches for the
 * secret exit (3 quick presses of the power button = 3 screen on/off changes).
 * Works without Shizuku, so it also works right after a reboot.
 */
class SchoolService : Service() {

    private val transitions = ArrayDeque<Long>()
    private var startedAt = 0L
    private var overlay: View? = null
    private var exiting = false
    private val mainScope = MainScope()

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var powerManager: PowerManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private var interactive = true
    private var pollUntil = 0L

    /**
     * SCREEN_ON/OFF broadcasts arrive up to ~0.7 s late (measured on the Pixel), which makes
     * quick presses impossible to time. So after any broadcast we poll the real interactive
     * state for a few seconds; the broadcast only wakes us up.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (exiting) return
            if (intent.action != Intent.ACTION_USER_PRESENT) startPolling()
            // Re-cover the screen right away, so it is already black when it turns on.
            BlackActivity.show(this@SchoolService)
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            checkInteractive()
            if (!exiting && SystemClock.elapsedRealtime() < pollUntil) {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            } else if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    private fun startPolling() {
        checkInteractive()
        val wasPolling = SystemClock.elapsedRealtime() < pollUntil
        pollUntil = SystemClock.elapsedRealtime() + POLL_DURATION_MS
        wakeLock.acquire(POLL_DURATION_MS + 500)
        if (!wasPolling) handler.post(poll)
    }

    private fun checkInteractive() {
        val now = powerManager.isInteractive
        if (now != interactive) {
            interactive = now
            onScreenTransition()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        startedAt = SystemClock.elapsedRealtime()
        running = true
        covering = true
        powerManager = getSystemService(PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShizuBox:powerPresses")
        interactive = powerManager.isInteractive
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        // Started by the system (boot, sticky restart): only cover the screen if still active.
        mainScope.launch {
            val active = SchoolMode.store.current().active
            SchoolLog.log(TAG, "service created, active=$active, overlay=${Settings.canDrawOverlays(this@SchoolService)}")
            if (active) {
                addOverlay()
                BlackActivity.show(this@SchoolService)
            } else if (!exiting) {
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT) exit()
        return START_STICKY
    }

    private fun onScreenTransition() {
        if (exiting) return
        val now = SystemClock.elapsedRealtime()
        // Ignore the screen-off right after activation.
        if (now - startedAt < START_GRACE_MS) return
        // A slow press resets the sequence: only quick, deliberate triple presses count.
        if (transitions.isNotEmpty() && now - transitions.last() > EXIT_MAX_GAP_MS) transitions.clear()
        transitions.addLast(now)
        while (transitions.isNotEmpty() && now - transitions.first() > EXIT_WINDOW_MS) transitions.removeFirst()
        SchoolLog.log(TAG, "screen ${if (interactive) "on" else "off"}, ${transitions.size} in sequence")
        if (transitions.size >= EXIT_PRESSES) exit()
    }

    private fun exit() {
        if (exiting) return
        exiting = true
        covering = false
        SchoolLog.log(TAG, "exit requested")
        removeOverlay()
        BlackActivity.dismiss()
        SchoolMode.scope.launch {
            val restored = SchoolMode.exit()
            if (!restored) {
                SchoolLog.log(TAG, "showing exit guide")
                startActivity(
                    Intent(this@SchoolService, ExitGuideActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        covering = false
        running = false
        handler.removeCallbacks(poll)
        if (wakeLock.isHeld) wakeLock.release()
        mainScope.cancel()
        unregisterReceiver(screenReceiver)
        removeOverlay()
        super.onDestroy()
    }

    /** Black, touch-eating window above all apps (the activity covers the lock screen). */
    private fun addOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        val view = View(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnTouchListener { _, _ -> true }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE,
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            screenBrightness = 0f
        }
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            overlay = view
        } catch (e: Exception) {
            SchoolLog.log(TAG, "overlay failed", e)
        }
    }

    private fun removeOverlay() {
        overlay?.let { getSystemService(WindowManager::class.java).removeViewImmediate(it) }
        overlay = null
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Schulmodus", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Schulmodus aktiv")
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .build()
    }

    companion object {
        private const val TAG = "SchoolService"
        private const val CHANNEL_ID = "school_mode"
        private const val NOTIFICATION_ID = 1
        const val ACTION_EXIT = "de.emil.shizubox.action.EXIT_SCHOOL_MODE"

        /**
         * 3 screen changes = 3 power presses. 5 would be the emergency SOS, so stay well below.
         * Tested: pressing power "to check" has ~1 s gaps, a deliberate quick triple ~0.1–0.45 s.
         */
        private const val EXIT_PRESSES = 3
        private const val EXIT_WINDOW_MS = 2000L
        private const val EXIT_MAX_GAP_MS = 700L
        private const val POLL_INTERVAL_MS = 25L
        private const val POLL_DURATION_MS = 3000L

        /** True from service creation until it is destroyed (including the exit phase). */
        @Volatile
        var running = false
            private set

        /** True while the black screen should be shown; the activity checks this. */
        @Volatile
        var covering = false
            private set
        private const val START_GRACE_MS = 2000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SchoolService::class.java))
        }

        fun requestExit(context: Context) {
            context.startForegroundService(
                Intent(context, SchoolService::class.java).setAction(ACTION_EXIT),
            )
        }
    }
}
