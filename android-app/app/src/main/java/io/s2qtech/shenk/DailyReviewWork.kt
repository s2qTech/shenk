package io.s2qtech.shenk

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.s2qtech.shenk.sync.DailyReviewProcessResult
import java.time.Duration
import java.util.concurrent.TimeUnit

object DailyReviewScheduler {
    fun enqueue(
        context: Context,
        initialDelay: Duration = Duration.ZERO,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE,
    ) {
        val request = OneTimeWorkRequestBuilder<DailyReviewWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(initialDelay)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            policy,
            request,
        )
    }

    private const val WORK_NAME = "shenk-daily-review"
}

class DailyReviewWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as ShenkApplication).dailyReviewRepository
        repository.recoverInterruptedJobs()
        var completedAny = false
        repeat(4) {
            when (repository.processNext()) {
                DailyReviewProcessResult.NONE -> {
                    if (completedAny) showNotification()
                    scheduleNext(repository)
                    return Result.success()
                }
                DailyReviewProcessResult.COMPLETED -> completedAny = true
                DailyReviewProcessResult.WAITING -> {
                    scheduleNext(repository)
                    return Result.success()
                }
                DailyReviewProcessResult.RETRY -> {
                    scheduleNext(repository)
                    return Result.success()
                }
                DailyReviewProcessResult.FAILED -> {
                    scheduleNext(repository)
                    return Result.success()
                }
            }
        }
        if (completedAny) showNotification()
        DailyReviewScheduler.enqueue(
            applicationContext,
            Duration.ofSeconds(2),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
        )
        return Result.success()
    }

    private suspend fun scheduleNext(repository: io.s2qtech.shenk.sync.DailyReviewRepository) {
        val delay = repository.nextRunDelayMillis() ?: return
        DailyReviewScheduler.enqueue(
            applicationContext,
            Duration.ofMillis(delay.coerceAtLeast(1_000L)),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
        )
    }

    private fun showNotification() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "每日简评", NotificationManager.IMPORTANCE_DEFAULT),
        )
        if (applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(applicationContext, MainActivity::class.java)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("今日简评已生成")
            .setContentText("打开身刻查看今天的训练与恢复反馈")
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    701,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(701, notification)
    }

    private companion object {
        const val CHANNEL_ID = "daily_review"
    }
}
