package io.s2qtech.shenk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.CloudConnectionManager
import io.s2qtech.shenk.sync.NativeTimerSessionRepository
import io.s2qtech.shenk.sync.PlanCollaborationRepository
import io.s2qtech.shenk.sync.RoutineLibraryRepository
import io.s2qtech.shenk.sync.TodayRecordRepository
import io.s2qtech.shenk.model.TodayGuidance
import io.s2qtech.shenk.timer.TimerEngineState
import java.time.LocalDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val coordinator = remember { timerCoordinator() }
    val initialTimerEngineState = remember(coordinator) { coordinator.snapshot.value.state }
    val initialPrimaryPage = remember(initialTimerEngineState) {
        initialPrimaryPageForTimerState(initialTimerEngineState)
    }
    val timerEngineState by remember(coordinator) {
        coordinator.snapshot.map { it.state }.distinctUntilChanged()
    }.collectAsState(initial = initialTimerEngineState)
    val pager = rememberPagerState(initialPage = initialPrimaryPage, pageCount = { 3 })
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
            pager.scrollToPage(initialPrimaryPage)
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
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = PRIMARY_PAGE_RETENTION_RADIUS,
            userScrollEnabled = secondary == null,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .semantics {
                    stateDescription = when (pager.currentPage) {
                        CALENDAR_PAGE -> "日历，第 1 页，共 3 页"
                        TODAY_PAGE -> "今天，第 2 页，共 3 页"
                        else -> "训练，第 3 页，共 3 页"
                    }
                    customActions = buildList {
                        if (pager.currentPage != CALENDAR_PAGE) {
                            add(CustomAccessibilityAction("转到日历") {
                                scope.launch { pager.animatePrimaryPage(CALENDAR_PAGE) }
                                true
                            })
                        }
                        if (pager.currentPage != TODAY_PAGE) {
                            add(CustomAccessibilityAction("转到今天") {
                                scope.launch { pager.animatePrimaryPage(TODAY_PAGE) }
                                true
                            })
                        }
                        if (pager.currentPage != TRAINING_PAGE) {
                            add(CustomAccessibilityAction("转到训练") {
                                scope.launch { pager.animatePrimaryPage(TRAINING_PAGE) }
                                true
                            })
                        }
                    }
                }
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
                        coordinator = coordinator,
                        launchRequest = trainingLaunch,
                        onLaunchConsumed = { trainingLaunch = null },
                        onReady = { primaryPagesReady[TRAINING_PAGE] = true },
                    )
                }
            }
        }
        if (timerEngineState == TimerEngineState.IDLE || pager.settledPage != TRAINING_PAGE) {
            PagerDrivenPrimaryPageIndicator(
                pagerState = pager,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 4.dp),
            )
        }
    }

    when (secondary) {
        SecondarySpace.DATA -> ShenkModalBottomSheet(
            onDismissRequest = { secondary = null },
            explicitDismissOnly = true,
        ) {
            DataScreen(
                repository = calendarRepository,
            )
        }
        SecondarySpace.PLANNING -> ShenkModalBottomSheet(
            onDismissRequest = {
                pendingFeedback = false
                secondary = null
            },
        ) {
            PlanningRoute(
                repository = planCollaborationRepository,
                initialFeedback = pendingFeedback,
                onBack = {
                    pendingFeedback = false
                    secondary = null
                },
            )
        }
        null -> Unit
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

internal fun initialPrimaryPageForTimerState(state: TimerEngineState): Int =
    if (state == TimerEngineState.IDLE) TODAY_PAGE else TRAINING_PAGE

private suspend fun androidx.compose.foundation.pager.PagerState.animatePrimaryPage(page: Int) {
    animateScrollToPage(page = page)
}
