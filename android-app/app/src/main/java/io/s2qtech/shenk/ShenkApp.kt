package io.s2qtech.shenk

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.CloudConnectionManager
import io.s2qtech.shenk.sync.NativeTimerSessionRepository
import io.s2qtech.shenk.sync.RoutineLibraryRepository
import io.s2qtech.shenk.sync.TodayRecordRepository
import io.s2qtech.shenk.model.TodayGuidance
import java.time.LocalDate
import kotlin.math.absoluteValue
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

    BackHandler(enabled = secondary != null) {
        secondary = null
    }
    BackHandler(enabled = secondary == null && pager.currentPage != 1) {
        scope.launch { pager.animateScrollToPage(1) }
    }

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
            null -> HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                pageSpacing = 12.dp,
                modifier = Modifier.testTag("primary-pager"),
            ) { page ->
                val pageOffset = (
                    (pager.currentPage - page) + pager.currentPageOffsetFraction
                ).absoluteValue.coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = 1f - (pageOffset * 0.035f)
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - (pageOffset * 0.16f)
                        },
                ) {
                    when (page) {
                        0 -> CalendarScreen(repository = calendarRepository)
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
}
