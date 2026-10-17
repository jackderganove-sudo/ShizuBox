package de.emil.shizubox.cleaner

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.emil.shizubox.shizuku.ShizukuManager
import java.util.concurrent.TimeUnit

/** Once a day, battery-friendly (only when not low on battery): items past their days leave the trash for good. */
class TrashWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Trash.items.value.isEmpty()) return Result.success()
        if (!ShizukuManager.isReady) return Result.success() // next day
        runCatching { Trash.purgeExpired() }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "cleaner_trash", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TrashWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        }
    }
}
