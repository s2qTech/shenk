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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import io.s2qtech.shenk.sync.TodayRecordRepository
import io.s2qtech.shenk.sync.SyncScheduler
import java.time.Duration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.reminderDataStore by preferencesDataStore(name = "reminder_settings")

data class ReminderSettings(
    val morningEnabled: Boolean = true,
    val morningHour: Int = 8,
    val morningMinute: Int = 45,
    val middayEnabled: Boolean = true,
    val middayHour: Int = 12,
    val middayMinute: Int = 30,
    val eveningEnabled: Boolean = true,
    val eveningHour: Int = 23,
    val eveningMinute: Int = 15,
    val weeklyEnabled: Boolean = true,
    val weeklyDay: Int = DayOfWeek.SATURDAY.value,
    val weeklyHour: Int = 22,
    val weeklyMinute: Int = 30,
)

class ReminderSettingsStore(private val context: Context) {
    val settings: Flow<ReminderSettings> = context.reminderDataStore.data.map { values ->
        ReminderSettings(
            morningEnabled = values[MORNING_ENABLED] ?: true,
            morningHour = values[MORNING_HOUR] ?: 8,
            morningMinute = values[MORNING_MINUTE] ?: 45,
            middayEnabled = values[MIDDAY_ENABLED] ?: true,
            middayHour = values[MIDDAY_HOUR] ?: 12,
            middayMinute = values[MIDDAY_MINUTE] ?: 30,
            eveningEnabled = values[EVENING_ENABLED] ?: true,
            eveningHour = values[EVENING_HOUR] ?: 23,
            eveningMinute = values[EVENING_MINUTE] ?: 15,
            weeklyEnabled = values[WEEKLY_ENABLED] ?: true,
            weeklyDay = values[WEEKLY_DAY] ?: DayOfWeek.SATURDAY.value,
            weeklyHour = values[WEEKLY_HOUR] ?: 22,
            weeklyMinute = values[WEEKLY_MINUTE] ?: 30,
        )
    }

    suspend fun save(value: ReminderSettings) {
        context.reminderDataStore.edit { values ->
            values[MORNING_ENABLED] = value.morningEnabled
            values[MORNING_HOUR] = value.morningHour
            values[MORNING_MINUTE] = value.morningMinute
            values[MIDDAY_ENABLED] = value.middayEnabled
            values[MIDDAY_HOUR] = value.middayHour
            values[MIDDAY_MINUTE] = value.middayMinute
            values[EVENING_ENABLED] = value.eveningEnabled
            values[EVENING_HOUR] = value.eveningHour
            values[EVENING_MINUTE] = value.eveningMinute
            values[WEEKLY_ENABLED] = value.weeklyEnabled
            values[WEEKLY_DAY] = value.weeklyDay
            values[WEEKLY_HOUR] = value.weeklyHour
            values[WEEKLY_MINUTE] = value.weeklyMinute
        }
        ReminderScheduler(context).schedule(value, replaceExisting = true)
    }

    private companion object {
        val MORNING_ENABLED = booleanPreferencesKey("morning_enabled")
        val MORNING_HOUR = intPreferencesKey("morning_hour")
        val MORNING_MINUTE = intPreferencesKey("morning_minute")
        val MIDDAY_ENABLED = booleanPreferencesKey("midday_enabled")
        val MIDDAY_HOUR = intPreferencesKey("midday_hour")
        val MIDDAY_MINUTE = intPreferencesKey("midday_minute")
        val EVENING_ENABLED = booleanPreferencesKey("evening_enabled")
        val EVENING_HOUR = intPreferencesKey("evening_hour")
        val EVENING_MINUTE = intPreferencesKey("evening_minute")
        val WEEKLY_ENABLED = booleanPreferencesKey("weekly_enabled")
        val WEEKLY_DAY = intPreferencesKey("weekly_day")
        val WEEKLY_HOUR = intPreferencesKey("weekly_hour")
        val WEEKLY_MINUTE = intPreferencesKey("weekly_minute")
    }
}

class ReminderScheduler(private val context: Context) {
    fun schedule(settings: ReminderSettings, replaceExisting: Boolean = false) {
        migrateLegacyWork()
        scheduleOne(
            name = MORNING_WORK,
            enabled = settings.morningEnabled,
            hour = settings.morningHour,
            minute = settings.morningMinute,
            kind = "morning",
            replaceExisting = replaceExisting,
        )
        scheduleOne(
            name = MIDDAY_WORK,
            enabled = settings.middayEnabled,
            hour = settings.middayHour,
            minute = settings.middayMinute,
            kind = "midday",
            replaceExisting = replaceExisting,
        )
        scheduleOne(
            name = EVENING_WORK,
            enabled = settings.eveningEnabled,
            hour = settings.eveningHour,
            minute = settings.eveningMinute,
            kind = "evening",
            replaceExisting = replaceExisting,
        )
        scheduleWeekly(settings, replaceExisting)
    }

    private fun migrateLegacyWork() {
        val workManager = WorkManager.getInstance(context)
        LEGACY_WORK_NAMES.forEach(workManager::cancelUniqueWork)
    }

    private fun scheduleWeekly(settings: ReminderSettings, replaceExisting: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.weeklyEnabled) {
            workManager.cancelUniqueWork(WEEKLY_WORK)
            return
        }
        val now = ZonedDateTime.now()
        var next = now
            .with(DayOfWeek.of(settings.weeklyDay.coerceIn(1, 7)))
            .withHour(settings.weeklyHour)
            .withMinute(settings.weeklyMinute)
            .withSecond(0)
            .withNano(0)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val request = PeriodicWorkRequestBuilder<WeeklyReviewWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next))
            .setConstraints(Constraints.NONE)
            .build()
        workManager.enqueueUniquePeriodicWork(WEEKLY_WORK, periodicPolicy(replaceExisting), request)
    }

    private fun scheduleOne(
        name: String,
        enabled: Boolean,
        hour: Int,
        minute: Int,
        kind: String,
        replaceExisting: Boolean,
    ) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(name)
            return
        }
        val now = ZonedDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<MissingMorningWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next))
            .setConstraints(Constraints.NONE)
            .setInputData(Data.Builder().putString("kind", kind).build())
            .build()
        workManager.enqueueUniquePeriodicWork(name, periodicPolicy(replaceExisting), request)
    }

    private fun periodicPolicy(replaceExisting: Boolean): ExistingPeriodicWorkPolicy =
        if (replaceExisting) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP

    private companion object {
        const val MORNING_WORK = "shenk-morning-checkin-v2"
        const val MIDDAY_WORK = "shenk-midday-checkin-v2"
        const val EVENING_WORK = "shenk-evening-review-v2"
        const val WEEKLY_WORK = "shenk-weekly-review-v2"
        val LEGACY_WORK_NAMES = listOf(
            "shenk-morning-checkin",
            "shenk-midday-checkin",
            "shenk-evening-review",
            "shenk-weekly-review",
        )
    }
}

class WeeklyReviewWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ShenkApplication
        val settings = ReminderSettingsStore(applicationContext).settings.first()
        if (!settings.weeklyEnabled) return Result.success()
        return runCatching {
            app.planCollaborationRepository.generateWeeklyFeedback()
            SyncScheduler(applicationContext).enqueue()
            showNotification()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun showNotification() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "每周复盘", NotificationManager.IMPORTANCE_DEFAULT),
        )
        if (applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_SPACE, "feedback")
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("本周复盘资料已就绪")
            .setContentText("打开身刻，复制资料到健身计划任务完成本周复盘")
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    401,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(401, notification)
    }

    private companion object {
        const val CHANNEL_ID = "weekly_review"
    }
}

class MissingMorningWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ShenkApplication
        val settings = ReminderSettingsStore(applicationContext).settings.first()
        val kind = inputData.getString("kind") ?: return Result.success()
        val enabled = when (kind) {
            "morning" -> settings.morningEnabled
            "midday" -> settings.middayEnabled
            "evening" -> settings.eveningEnabled
            else -> false
        }
        if (!enabled) return Result.success()
        if (!isDailyReminderInDeliveryWindow(kind, LocalDateTime.now(), settings)) return Result.success()

        val todayDate = LocalDate.now()
        if (kind == "evening") {
            val records = app.localFirstRepository.allRecords().filter { it.deletedAt == null }
            val hasTraining = records.any {
                it.entity == "training_logs" && it.data["date"]?.toString()?.trim('"') == todayDate.toString()
            }
            val hasReview = records.any {
                it.entity == "daily_reviews" && it.data["date"]?.toString()?.trim('"') == todayDate.toString() &&
                    it.data["status"]?.toString()?.trim('"') == "generated"
            }
            if (hasTraining || hasReview) return Result.success()
        } else {
            val today = app.todayRepository.observe(todayDate).first()
            if (today.morning != null) return Result.success()
        }
        showNotification(kind)
        return Result.success()
    }

    private fun showNotification(kind: String) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "每日状态", NotificationManager.IMPORTANCE_DEFAULT),
        )
        if (applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val text = when (kind) {
            "morning" -> "花一分钟记录睡眠和身体状态"
            "midday" -> "今天的晨起状态还未记录，可以留空不确定的数据"
            else -> "今天还没有训练、休息或跳过记录，确认后可生成每日简评"
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("身刻")
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    Intent(applicationContext, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(when (kind) { "morning" -> 301; "midday" -> 302; else -> 303 }, notification)
    }

    private companion object {
        const val CHANNEL_ID = "daily_checkin"
    }
}

internal fun isDailyReminderInDeliveryWindow(
    kind: String,
    now: LocalDateTime,
    settings: ReminderSettings,
): Boolean {
    val scheduledTime = when (kind) {
        "morning" -> settings.morningHour to settings.morningMinute
        "midday" -> settings.middayHour to settings.middayMinute
        "evening" -> settings.eveningHour to settings.eveningMinute
        else -> return false
    }
    val start = now.toLocalDate().atTime(scheduledTime.first, scheduledTime.second)
    val end = minOf(start.plusHours(3), now.toLocalDate().plusDays(1).atStartOfDay())
    return !now.isBefore(start) && now.isBefore(end)
}
