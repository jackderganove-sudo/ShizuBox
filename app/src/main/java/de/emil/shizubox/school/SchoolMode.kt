package de.emil.shizubox.school

import de.emil.shizubox.R
import de.emil.shizubox.t
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.emil.shizubox.customization.Customization
import de.emil.shizubox.debug.ErrorKind
import de.emil.shizubox.debug.ErrorLog
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.shizuku.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

object SchoolMode {
    private const val TAG = "SchoolMode"

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private lateinit var appContext: Context
    lateinit var store: SchoolStore
        private set
    lateinit var state: StateFlow<SchoolState>
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        SchoolLog.init(appContext)
        store = SchoolStore(appContext)
        state = store.state.stateIn(scope, SharingStarted.Eagerly, SchoolState())

        scope.launch {
            combine(store.state, ShizukuManager.status) { s, status -> s to (status == ShizukuStatus.Ready) }
                .collect { (s, shizukuReady) ->
                    when {
                        // Restore automatically as soon as Shizuku is back (e.g. after a reboot).
                        s.pendingRestore && shizukuReady -> restore()
                        // Status bar flags are lost on reboot; re-apply once Shizuku is back.
                        !s.active && shizukuReady && Customization.hasStatusBarCustomization() ->
                            runCatching { Customization.applyStatusBar() }
                        s.active && shizukuReady -> {
                            // The status bar lock lives only in system_server's memory (dumpsys statusbar:
                            // mDisableRecords) and is gone after a reboot, unlike the settings and the
                            // suspended apps – set it again once Shizuku is back (0.11.1). Idempotent.
                            runCatching { SchoolCommands.lockStatusBar() }
                            // Optional reboot behavior: black screen returns together with Shizuku.
                            if (!SchoolService.running) {
                                SchoolLog.log(TAG, "Shizuku back while active, starting black screen")
                                SchoolService.start(appContext)
                            }
                        }
                    }
                }
        }
    }

    /** Turns the school mode on. Requires a working Shizuku connection. */
    suspend fun activate(): Result<Unit> = mutex.withLock {
        runCatching {
            check(ShizukuManager.isReady) { t(R.string.shizuku_not_connected) }
            val current = store.current()
            if (current.active) return@runCatching
            check(!current.pendingRestore) { t(R.string.school_last_restore_open) }

            SchoolCommands.grantSelfPermissions()
            val snapshot = SchoolCommands.takeSnapshot()
            val suspend = SchoolCommands.packagesToSuspend(ShizukuManager.shizukuPackage)
            // Persist before changing anything, so a crash can never lose the old state.
            store.saveSnapshot(snapshot.toString(), suspend)

            val endAt = current.autoEndMinutes.takeIf { it > 0 }
                ?.let { System.currentTimeMillis() + it * 60_000L } ?: 0L
            store.setActive(endAt)
            scheduleAutoEnd(endAt)
            SchoolService.start(appContext)

            SchoolCommands.apply(suspend)
            SchoolLog.log(TAG, "activated, ${suspend.size} apps suspended, endAt=$endAt")
            delay(600)
            SchoolCommands.screenOff()
        }.onFailure { SchoolLog.log(TAG, "activate failed", it) }
    }

    /**
     * Ends the school mode (3× power, auto-end or adb). The black screen goes away immediately;
     * settings are restored now if Shizuku is connected, otherwise once it is back.
     * Returns true if everything was restored.
     */
    suspend fun exit(): Boolean {
        mutex.withLock {
            val s = store.current()
            if (!s.active && !s.pendingRestore) return true
            store.setPendingRestore()
            cancelAutoEnd()
        }
        return restore()
    }

    /**
     * Every step on its own (see [RestorePlan]); only what was really restored leaves the snapshot,
     * which is deleted only once nothing is left (fail safe, Daniel's review 0.11.1).
     */
    private suspend fun restore(): Boolean = mutex.withLock {
        val s = store.current()
        if (!s.pendingRestore) return true
        if (!ShizukuManager.isReady) {
            SchoolLog.log(TAG, "restore pending: Shizuku not ready")
            return false
        }
        try {
            val snapshot = JSONObject(s.snapshot ?: "{}")
            val steps = SchoolCommands.restoreSteps(snapshot, s.suspended)
            val out = RestorePlan.run(snapshot, s.suspended, steps, SchoolCommands::runRestoreStep)
            // The restore released all status bar flags; bring back the user's own ones.
            if (Customization.hasStatusBarCustomization()) runCatching { Customization.applyStatusBar() }
            if (out.done) {
                store.clear()
                RestoreNotice.cancel(appContext)
                SchoolLog.log(TAG, "restored")
                true
            } else {
                if (out.restored.any { it.keys.isNotEmpty() }) store.saveSnapshot(out.snapshot.toString(), out.suspended)
                val failed = out.failed.joinToString("\n") { it.keys.joinToString().ifEmpty { "statusbar" } }
                SchoolLog.log(TAG, "restore incomplete: ${out.failed.size} left\n$failed")
                ErrorLog.add(ErrorKind.School, "School mode: ${out.failed.size} settings not restored", failed)
                RestoreNotice.show(appContext, out.failed.size)
                false
            }
        } catch (e: Exception) {
            SchoolLog.log(TAG, "restore failed", e)
            false
        }
    }

    /** "Erneut versuchen" in the notification. */
    fun retryRestore() {
        scope.launch { restore() }
    }

    suspend fun setAutoEndMinutes(minutes: Int) = store.setAutoEndMinutes(minutes)

    suspend fun setWaitForShizukuAfterReboot(wait: Boolean) = store.setWaitForShizukuAfterReboot(wait)

    fun scheduleAutoEnd(endAt: Long) {
        if (endAt <= 0) return
        val am = appContext.getSystemService(AlarmManager::class.java)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, autoEndIntent())
    }

    private fun cancelAutoEnd() {
        appContext.getSystemService(AlarmManager::class.java).cancel(autoEndIntent())
    }

    private fun autoEndIntent() = PendingIntent.getBroadcast(
        appContext,
        0,
        Intent(appContext, AutoEndReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
