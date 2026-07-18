package io.s2qtech.shenk

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.CloudConnectionManager
import io.s2qtech.shenk.sync.NativeTimerSessionRepository
import io.s2qtech.shenk.sync.RoutineLibraryRepository
import io.s2qtech.shenk.sync.TodayRecordRepository
import io.s2qtech.shenk.model.TodayGuidance
import java.time.LocalDate
import kotlinx.coroutines.launch

private enum class SecondarySpace { RECORDS, DATA }

data class TrainingLaunchRequest(
    val date: LocalDate,
    val guidance: TodayGuidance?,
)

@Composable
fun ShenkApp(
    todayRepository: TodayRecordRepository,
    calendarRepository: CalendarRecordRepository,
    routineLibraryRepository: RoutineLibraryRepository,
    timerSessionRepository: NativeTimerSessionRepository,
    timerCoordinator: NativeTimerCoordinator,
    reminderStore: ReminderSettingsStore,
    cloudConnectionManager: CloudConnectionManager,
) {
    val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var secondary by remember { mutableStateOf<SecondarySpace?>(null) }
    var trainingLaunch by remember { mutableStateOf<TrainingLaunchRequest?>(null) }

    Box(Modifier.fillMaxSize()) {
        when (secondary) {
            SecondarySpace.RECORDS -> RecordsScreen(
                repository = calendarRepository,
                onBack = { secondary = null },
            )
            SecondarySpace.DATA -> DataScreen(
                repository = calendarRepository,
                onBack = { secondary = null },
            )
            null -> HorizontalPager(state = pager, beyondViewportPageCount = 1) { page ->
                when (page) {
                    0 -> CalendarScreen(
                        repository = calendarRepository,
                        onToday = { scope.launch { pager.animateScrollToPage(1) } },
                        onRecords = { secondary = SecondarySpace.RECORDS },
                        onData = { secondary = SecondarySpace.DATA },
                    )
                    1 -> TodayRoute(
                        repository = todayRepository,
                        reminderStore = reminderStore,
                        cloudConnectionManager = cloudConnectionManager,
                        onCalendar = { scope.launch { pager.animateScrollToPage(0) } },
                        onRecords = { secondary = SecondarySpace.RECORDS },
                        onData = { secondary = SecondarySpace.DATA },
                        onTraining = { guidance ->
                            trainingLaunch = TrainingLaunchRequest(LocalDate.now(), guidance)
                            scope.launch { pager.animateScrollToPage(2) }
                        },
                    )
                    else -> TrainingRoute(
                        routineRepository = routineLibraryRepository,
                        sessionRepository = timerSessionRepository,
                        recordRepository = calendarRepository,
                        coordinator = timerCoordinator,
                        launchRequest = trainingLaunch,
                        onLaunchConsumed = { trainingLaunch = null },
                        onToday = { scope.launch { pager.animateScrollToPage(1) } },
                        onCalendar = { scope.launch { pager.animateScrollToPage(0) } },
                    )
                }
            }
        }
    }
}
