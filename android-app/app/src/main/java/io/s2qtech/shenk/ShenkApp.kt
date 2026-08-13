package io.s2qtech.shenk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.CloudConnectionManager
import io.s2qtech.shenk.sync.NativeTimerSessionRepository
import io.s2qtech.shenk.sync.PlanCollaborationRepository
import io.s2qtech.shenk.sync.RoutineLibraryRepository
import io.s2qtech.shenk.sync.TodayRecordRepository
import io.s2qtech.shenk.model.TodayGuidance
import java.time.LocalDate
import kotlinx.coroutines.launch

private enum class SecondarySpace { DATA, PLANNING }

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
    timerCoordinator: () -> NativeTimerCoordinator,
    planCollaborationRepository: PlanCollaborationRepository,
    reminderStore: ReminderSettingsStore,
    cloudConnectionManager: CloudConnectionManager,
    requestedSpace: String? = null,
    onExternalRequestConsumed: () -> Unit = {},
    onPrimaryPagesReady: () -> Unit = {},
) {
    val pager = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var secondary by remember { mutableStateOf<SecondarySpace?>(null) }
    var trainingLaunch by remember { mutableStateOf<TrainingLaunchRequest?>(null) }
    var pendingFeedback by remember { mutableStateOf(false) }
    var primaryPagerWarmed by remember { mutableStateOf(false) }
    val primaryPagesReady = remember { mutableStateListOf(false, false, false) }

    LaunchedEffect(primaryPagesReady.toList(), primaryPagerWarmed) {
        if (primaryPagesReady.all { it } && !primaryPagerWarmed) {
            pager.scrollToPage(CALENDAR_PAGE)
            withFrameNanos { }
            pager.scrollToPage(TRAINING_PAGE)
            withFrameNanos { }
            pager.scrollToPage(TODAY_PAGE)
            withFrameNanos { }
            primaryPagerWarmed = true
            onPrimaryPagesReady()
        }
    }
    LaunchedEffect(requestedSpace) {
        if (requestedSpace in setOf("plan", "feedback")) {
            onPrimaryPagesReady()
            pendingFeedback = requestedSpace == "feedback"
            secondary = SecondarySpace.PLANNING
            onExternalRequestConsumed()
        }
    }

    BackHandler(enabled = secondary != null) {
        secondary = null
    }
    BackHandler(enabled = secondary == null && pager.currentPage != 1) {
        scope.launch {
            pager.animateScrollToPage(page = 1)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (secondary) {
            SecondarySpace.DATA -> DataScreen(
                repository = calendarRepository,
                onBack = { secondary = null },
            )
            SecondarySpace.PLANNING -> PlanningRoute(
                repository = planCollaborationRepository,
                initialFeedback = pendingFeedback,
                onBack = {
                    pendingFeedback = false
                    secondary = null
                },
            )
            null -> HorizontalPager(
                state = pager,
                beyondViewportPageCount = PRIMARY_PAGE_RETENTION_RADIUS,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("primary-pager"),
            ) { page ->
                PrimaryPageSlot(page = page) {
                    when (page) {
                        CALENDAR_PAGE -> CalendarScreen(
                            repository = calendarRepository,
                            onReady = { primaryPagesReady[CALENDAR_PAGE] = true },
                        )
                        TODAY_PAGE -> TodayRoute(
                            repository = todayRepository,
                            recordRepository = calendarRepository,
                            reminderStore = reminderStore,
                            cloudConnectionManager = cloudConnectionManager,
                            onReady = { primaryPagesReady[TODAY_PAGE] = true },
                            onData = { secondary = SecondarySpace.DATA },
                            onPlanning = { secondary = SecondarySpace.PLANNING },
                            onTraining = { guidance ->
                                trainingLaunch = TrainingLaunchRequest(LocalDate.now(), guidance)
                                scope.launch { pager.animatePrimaryPage(2) }
                            },
                        )
                        TRAINING_PAGE -> TrainingRoute(
                            routineRepository = routineLibraryRepository,
                            sessionRepository = timerSessionRepository,
                            recordRepository = calendarRepository,
                            coordinator = timerCoordinator(),
                            launchRequest = trainingLaunch,
                            onLaunchConsumed = { trainingLaunch = null },
                            onReady = { primaryPagesReady[TRAINING_PAGE] = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryPageSlot(
    page: Int,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("primary-page-slot-$page"),
    ) {
        content()
    }
}

private const val CALENDAR_PAGE = 0
private const val TODAY_PAGE = 1
private const val TRAINING_PAGE = 2
private const val PRIMARY_PAGE_RETENTION_RADIUS = 2

private suspend fun androidx.compose.foundation.pager.PagerState.animatePrimaryPage(page: Int) {
    animateScrollToPage(page = page)
}
