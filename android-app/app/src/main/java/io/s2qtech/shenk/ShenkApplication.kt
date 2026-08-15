package io.s2qtech.shenk

import android.app.Application
import androidx.work.ExistingWorkPolicy
import io.s2qtech.shenk.sync.LocalFirstRepository
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.CloudConnectionManager
import io.s2qtech.shenk.sync.DailyReviewRepository
import io.s2qtech.shenk.sync.DevicePreferencesStore
import io.s2qtech.shenk.sync.KeystoreSecretStore
import io.s2qtech.shenk.sync.NativeTimerSessionRepository
import io.s2qtech.shenk.sync.PlanCollaborationRepository
import io.s2qtech.shenk.sync.RoutineLibraryRepository
import io.s2qtech.shenk.sync.ShenkDatabase
import io.s2qtech.shenk.sync.SyncScheduler
import io.s2qtech.shenk.sync.TodayRecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ShenkApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val timerRestoreStarted = AtomicBoolean(false)
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

    val routineLibraryRepository: RoutineLibraryRepository by lazy {
        RoutineLibraryRepository(localFirstRepository)
    }

    val timerSessionRepository: NativeTimerSessionRepository by lazy {
        NativeTimerSessionRepository(localFirstRepository)
    }

    val planCollaborationRepository: PlanCollaborationRepository by lazy {
        PlanCollaborationRepository(localFirstRepository)
    }

    val cloudConnectionManager: CloudConnectionManager by lazy {
        CloudConnectionManager(this)
    }

    val dailyReviewRepository: DailyReviewRepository by lazy {
        val preferences = DevicePreferencesStore(this)
        DailyReviewRepository(
            database = ShenkDatabase.get(this),
            records = localFirstRepository,
            preferences = preferences,
            secrets = KeystoreSecretStore(preferences),
        )
    }

    val nativeTimerCoordinator: NativeTimerCoordinator by lazy {
        NativeTimerCoordinator(this, timerSessionRepository, applicationScope)
    }

    val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManager(this)
    }

    val reminderSettingsStore: ReminderSettingsStore by lazy {
        ReminderSettingsStore(this)
    }

    fun restoreTimerAfterFirstFrame() {
        if (!timerRestoreStarted.compareAndSet(false, true)) return
        applicationScope.launch {
            if (!NativeTimerCoordinator.hasRecoverableCheckpoint(this@ShenkApplication)) return@launch
            val library = routineLibraryRepository.observeLibrary().first()
            nativeTimerCoordinator.restoreIfPossible(library.routines)
        }
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            ReminderScheduler(this@ShenkApplication).schedule(
                reminderSettingsStore.settings.first(),
            )
            SyncScheduler(this@ShenkApplication).enqueue()
            DailyReviewScheduler.enqueue(
                this@ShenkApplication,
                policy = ExistingWorkPolicy.KEEP,
            )
        }
    }
}
