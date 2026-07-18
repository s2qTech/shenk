package io.s2qtech.shenk

import android.app.Application
import io.s2qtech.shenk.sync.LocalFirstRepository
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.ShenkDatabase
import io.s2qtech.shenk.sync.TodayRecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShenkApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val localFirstRepository: LocalFirstRepository by lazy {
        LocalFirstRepository(
            database = ShenkDatabase.get(this),
            localDeviceId = null,
        )
    }

    val todayRepository: TodayRecordRepository by lazy {
        TodayRecordRepository(localFirstRepository)
    }

    val calendarRepository: CalendarRecordRepository by lazy {
        CalendarRecordRepository(localFirstRepository)
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            ReminderScheduler(this@ShenkApplication).schedule(
                ReminderSettingsStore(this@ShenkApplication).settings.first(),
            )
        }
    }
}
