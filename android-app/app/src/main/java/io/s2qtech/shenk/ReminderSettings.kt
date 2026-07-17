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
import java.time.Duration
import java.time.LocalDate
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
        }
        ReminderScheduler(context).schedule(value)
    }

    private companion object {
        val MORNING_ENABLED = booleanPreferencesKey("morning_enabled")
        val MORNING_HOUR = intPreferencesKey("morning_hour")
        val MORNING_MINUTE = intPreferencesKey("morning_minute")
        val MIDDAY_ENABLED = booleanPreferencesKey("midday_enabled")
        val MIDDAY_HOUR = intPreferencesKey("midday_hour")
        val MIDDAY_MINUTE = intPreferencesKey("midday_minute")
    }
}

class ReminderScheduler(private val context: Context) {
    fun schedule(settings: ReminderSettings) {
        scheduleOne(
            name = MORNING_WORK,
            enabled = settings.morningEnabled,
            hour = settings.morningHour,
            minute = settings.morningMinute,
            kind = "morning",
        )
        scheduleOne(
            name = MIDDAY_WORK,
            enabled = settings.middayEnabled,
            hour = settings.middayHour,
            minute = settings.middayMinute,
            kind = "midday",
        )
    }

    private fun scheduleOne(name: String, enabled: Boolean, hour: Int, minute: Int, kind: String) {
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
        workManager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val MORNING_WORK = "shenk-morning-checkin"
        const val MIDDAY_WORK = "shenk-midday-checkin"
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
        val enabled = if (kind == "morning") settings.morningEnabled else settings.middayEnabled
        if (!enabled) return Result.success()

        val today = app.todayRepository.observe(LocalDate.now()).first()
        if (today.morning != null) return Result.success()
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
        val text = if (kind == "morning") {
            "花一分钟记录睡眠和身体状态"
        } else {
            "今天的晨起状态还未记录，可以留空不确定的数据"
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
            .notify(if (kind == "morning") 301 else 302, notification)
    }

    private companion object {
        const val CHANNEL_ID = "daily_checkin"
    }
}
