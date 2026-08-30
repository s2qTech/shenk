package io.s2qtech.shenk

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.s2qtech.shenk.model.RoutineScene
import io.s2qtech.shenk.model.RoutineRole
import io.s2qtech.shenk.model.RoutineStep
import io.s2qtech.shenk.model.RoutineTemplate
import io.s2qtech.shenk.model.TimerSessionFact
import io.s2qtech.shenk.model.TrainingLog
import io.s2qtech.shenk.model.GuidanceSource
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.NativeTimerSessionRepository
import io.s2qtech.shenk.sync.PendingTimerCompletion
import io.s2qtech.shenk.sync.RoutineLibrary
import io.s2qtech.shenk.sync.RoutineLibraryRepository
import io.s2qtech.shenk.sync.SyncScheduler
import io.s2qtech.shenk.timer.TimerEngineState
import io.s2qtech.shenk.timer.RuntimeStep
import io.s2qtech.shenk.timer.TimerSnapshot
import io.s2qtech.shenk.timer.expandRoutine
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingRoute(
    routineRepository: RoutineLibraryRepository,
    sessionRepository: NativeTimerSessionRepository,
    recordRepository: CalendarRecordRepository,
    coordinator: NativeTimerCoordinator,
    launchRequest: TrainingLaunchRequest?,
    onLaunchConsumed: () -> Unit,
    onReturnToToday: () -> Unit,
    onReady: () -> Unit = {},
) {
    val library by routineRepository.observeLibrary().collectAsState(initial = null)
    val pending by sessionRepository.observePendingCompletion().collectAsState(initial = emptyList())
    val snapshot by coordinator.snapshot.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    var voiceNotice by remember { mutableStateOf<String?>(null) }
    val timerPlatformActive = snapshot.state != TimerEngineState.IDLE
    val cuePlayer = remember(timerPlatformActive) {
        if (timerPlatformActive) TimerCuePlayer(context) { message -> voiceNotice = message } else null
    }
    val callMonitor = remember(timerPlatformActive) {
        if (timerPlatformActive) TimerCallMonitor(context, coordinator::pauseForPhoneCall) else null
    }
    var completion by remember { mutableStateOf<TimerSessionFact?>(null) }
    var preferredScene by remember { mutableStateOf<RoutineScene?>(null) }
    var launchNotice by remember { mutableStateOf<String?>(null) }
    var launchContext by remember { mutableStateOf<TrainingLaunchRequest?>(null) }

    LaunchedEffect(library) {
        if (library != null) onReady()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { callMonitor?.registerIfPermitted() }

    LaunchedEffect(library?.routines) {
        library?.let { coordinator.restoreIfPossible(it.routines) }
    }
    LaunchedEffect(library, launchRequest, snapshot.state) {
        val available = library ?: return@LaunchedEffect
        val request = launchRequest ?: return@LaunchedEffect
        if (snapshot.state != TimerEngineState.IDLE) return@LaunchedEffect
        val guidance = request.guidance
        launchContext = request
        launchNotice = null
        if (guidance?.source == GuidanceSource.FORMAL_PLAN && !guidance.routineId.isNullOrBlank()) {
            val routineId = guidance.routineId
            val routine = available.routines.firstOrNull { it.id == routineId }
            preferredScene = routine?.scene
            when {
                routine != null -> coordinator.select(
                    routine = routine,
                    date = request.date,
                    dailyPlanItemId = guidance.dailyPlanItemId,
                    planTemplateId = guidance.planTemplateId,
                )
                else -> launchNotice = "计划指定的方案尚未缓存，请联网同步后重试。"
            }
        }
        onLaunchConsumed()
    }
    LaunchedEffect(coordinator, cuePlayer) {
        cuePlayer?.let { player -> coordinator.cues.collect(player::speak) }
    }
    LaunchedEffect(snapshot.state, snapshot.request?.sessionId) {
        if (snapshot.state in setOf(TimerEngineState.COMPLETED, TimerEngineState.STOPPED)) {
            completion = coordinator.terminalFact()
        }
    }
    DisposableEffect(cuePlayer, callMonitor) {
        callMonitor?.registerIfPermitted()
        onDispose {
            callMonitor?.close()
            cuePlayer?.close()
        }
    }
    DisposableEffect(snapshot.state) {
        if (snapshot.state == TimerEngineState.RUNNING) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    BackHandler(enabled = snapshot.state == TimerEngineState.PREVIEW) {
        coordinator.reset()
    }

    when (snapshot.state) {
        TimerEngineState.IDLE -> if (library == null) {
            Box(
                Modifier.fillMaxSize().testTag("training-screen").padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                ShenkStatePanel(
                    title = "正在读取方案库",
                    message = "优先打开本机缓存的可执行方案，暂时不需要等待网络。",
                    tone = ShenkStateTone.PROGRESS,
                    modifier = Modifier.testTag("training-loading"),
                )
            }
        } else RoutineLibraryScreen(
            library = requireNotNull(library),
            pending = pending,
            preferredScene = preferredScene,
            notice = launchNotice,
            onSelect = { routine ->
                val request = launchContext
                coordinator.select(
                    routine = routine,
                    date = request?.date ?: java.time.LocalDate.now(),
                    dailyPlanItemId = request?.guidance?.dailyPlanItemId,
                    planTemplateId = request?.guidance?.planTemplateId,
                )
                launchContext = null
            },
            onPending = { completion = it.session },
            onIgnorePending = { item ->
                scope.launch {
                    sessionRepository.ignoreCompletion(item.session)
                    SyncScheduler(context).enqueue()
                }
            },
            onDeleteRoutine = { routine ->
                scope.launch {
                    if (routineRepository.deleteRoutine(routine.id)) {
                        SyncScheduler(context).enqueue()
                    }
                }
            },
            onReturnToToday = onReturnToToday,
        )
        TimerEngineState.PREVIEW -> RoutinePreviewScreen(
            snapshot = snapshot,
            onBack = coordinator::reset,
            onStart = {
                val missing = buildList {
                    if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                        add(Manifest.permission.READ_PHONE_STATE)
                    }
                    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
                coordinator.start()
            },
        )
        else -> ActiveTimerScreen(
            snapshot = snapshot,
            voiceNotice = voiceNotice,
            onPause = { if (snapshot.state == TimerEngineState.RUNNING) coordinator.pause() else coordinator.resume() },
            onPrevious = coordinator::previous,
            onNext = coordinator::next,
            onStop = { coordinator.stop() },
            onFinish = { completion = coordinator.terminalFact() },
        )
    }

    completion?.let { session ->
        PostWorkoutSheet(
            session = session,
            onDismiss = {
                completion = null
                if (snapshot.request?.sessionId == session.id) coordinator.reset()
            },
            onSave = { log ->
                scope.launch {
                    recordRepository.saveTrainingLog(log)
                    val reviewRepository = (context.applicationContext as ShenkApplication).dailyReviewRepository
                    val review = reviewRepository.enqueue(java.time.LocalDate.parse(log.date), allowIncomplete = false)
                    if (review.queued) DailyReviewScheduler.enqueue(context)
                    SyncScheduler(context).enqueue()
                    completion = null
                    if (snapshot.request?.sessionId == session.id) coordinator.reset()
                }
            },
        )
    }
}

@Composable
internal fun RoutineLibraryScreen(
    library: RoutineLibrary,
    pending: List<PendingTimerCompletion>,
    preferredScene: RoutineScene?,
    notice: String?,
    onSelect: (RoutineTemplate) -> Unit,
    onPending: (PendingTimerCompletion) -> Unit,
    onIgnorePending: (PendingTimerCompletion) -> Unit,
    onDeleteRoutine: (RoutineTemplate) -> Unit,
    onReturnToToday: () -> Unit,
) {
    val scenes = RoutineScene.entries
    val scope = rememberCoroutineScope()
    val scenePager = rememberPagerState(
        initialPage = (preferredScene ?: RoutineScene.HOME).ordinal,
        pageCount = { scenes.size },
    )
    var routinePendingDelete by remember { mutableStateOf<RoutineTemplate?>(null) }

    LaunchedEffect(preferredScene) {
        preferredScene?.let { scenePager.scrollToPage(it.ordinal) }
    }
    LaunchedEffect(library.routines) {
        val visibleScene = scenes[scenePager.currentPage]
        if (library.byScene[visibleScene].isNullOrEmpty()) {
            scenes.firstOrNull { library.byScene[it].orEmpty().isNotEmpty() }
                ?.let { scenePager.scrollToPage(it.ordinal) }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("training-screen"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            TrainingSceneDock(
                pagerState = scenePager,
                scenes = scenes,
                onSelect = { page -> scope.launch { scenePager.animateScrollToPage(page) } },
                modifier = Modifier.testTag("training-scene-dock"),
            )
        },
    ) { scaffoldPadding ->
        Column(Modifier.fillMaxSize().padding(scaffoldPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 4.dp),
            ) {
                Text("训练", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(
                    "选择今天要执行的流程",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            TrainingScenePager(
                pagerState = scenePager,
                scenes = scenes,
                library = library,
                pending = pending,
                notice = notice,
                onSelect = onSelect,
                onPending = onPending,
                onIgnorePending = onIgnorePending,
                onDelete = { routinePendingDelete = it },
                onReturnToToday = onReturnToToday,
                modifier = Modifier.weight(1f),
            )
        }
    }

    routinePendingDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { routinePendingDelete = null },
            title = { Text("删除“${routine.title}”？") },
            text = { Text("删除后，该方案会从本机和同步设备的计时器中移除。已经完成的训练和计时记录会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        routinePendingDelete = null
                        onDeleteRoutine(routine)
                    },
                    modifier = Modifier.testTag("confirm-delete-routine"),
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { routinePendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun TrainingScenePager(
    pagerState: PagerState,
    scenes: List<RoutineScene>,
    library: RoutineLibrary,
    pending: List<PendingTimerCompletion>,
    notice: String?,
    onSelect: (RoutineTemplate) -> Unit,
    onPending: (PendingTimerCompletion) -> Unit,
    onIgnorePending: (PendingTimerCompletion) -> Unit,
    onDelete: (RoutineTemplate) -> Unit,
    onReturnToToday: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1),
        ),
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(pagerState, scenes.size) {
                val returnThreshold = 56.dp.toPx()
                awaitEachGesture {
                    val startPage = pagerState.currentPage
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var horizontal = 0f
                    var vertical = 0f
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.positionChange()
                        horizontal += delta.x
                        vertical += delta.y
                        pressed = change.pressed
                    }
                    if (
                        startPage == RoutineScene.HOME.ordinal &&
                        horizontal > returnThreshold &&
                        abs(horizontal) > abs(vertical) * 1.2f
                    ) {
                        onReturnToToday()
                    }
                }
            }
            .testTag("training-scene-pager"),
        beyondViewportPageCount = 1,
    ) { page ->
        val scene = scenes[page]
        RoutineScenePage(
            scene = scene,
            routines = library.byScene[scene].orEmpty(),
            pending = pending,
            notice = notice,
            rejectedCount = library.rejectedCount,
            onSelect = onSelect,
            onPending = onPending,
            onIgnorePending = onIgnorePending,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun RoutineScenePage(
    scene: RoutineScene,
    routines: List<RoutineTemplate>,
    pending: List<PendingTimerCompletion>,
    notice: String?,
    rejectedCount: Int,
    onSelect: (RoutineTemplate) -> Unit,
    onPending: (PendingTimerCompletion) -> Unit,
    onIgnorePending: (PendingTimerCompletion) -> Unit,
    onDelete: (RoutineTemplate) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("training-scene-${scene.name.lowercase()}"),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (pending.isNotEmpty()) {
            item {
                Text("待补训练记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(pending.take(3), key = { "${scene.name}-${it.session.id}" }) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.routineTitle, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.session.date} · ${formatDuration(item.session.actualSeconds)}",
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Row {
                            TextButton(onClick = { onIgnorePending(item) }) { Text("忽略") }
                            TextButton(onClick = { onPending(item) }) { Text("补记录") }
                        }
                    }
                }
            }
        }
        notice?.let { message ->
            item {
                ShenkStatePanel(
                    title = "计划方案暂不可用",
                    message = message,
                    tone = ShenkStateTone.OFFLINE,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (routines.isEmpty()) {
            item {
                ShenkStatePanel(
                    title = "这个场景还没有可执行方案",
                    message = "联网同步后会缓存 AI 管理的现行方案；本地不会用旧流程替代。",
                    tone = ShenkStateTone.NEUTRAL,
                    modifier = Modifier.fillMaxWidth().testTag("routine-scene-empty-${scene.name.lowercase()}"),
                )
            }
        } else {
            items(routines, key = RoutineTemplate::id) { routine ->
                RoutineListCard(
                    routine = routine,
                    expandedSeconds = remember(routine) { expandRoutine(routine).sumOf { it.seconds } },
                    onSelect = { onSelect(routine) },
                    onDelete = { onDelete(routine) },
                )
            }
        }
        if (rejectedCount > 0) {
            item {
                ShenkStatePanel(
                    title = "有 $rejectedCount 个方案未加入计时器",
                    message = "这些方案缺少权威字段或格式无效，身刻没有猜测其分类或执行方式。",
                    tone = ShenkStateTone.WARNING,
                    compact = true,
                    modifier = Modifier.fillMaxWidth().testTag("routine-rejected-warning"),
                )
            }
        }
    }
}

@Composable
private fun RoutineListCard(
    routine: RoutineTemplate,
    expandedSeconds: Int,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("删除${routine.title}") {
                        onDelete()
                        true
                    },
                )
            }
            .testTag("routine-${routine.id}"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 13.dp, end = 8.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(routine.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${formatDuration(expandedSeconds)} · ${routine.steps.size} 个动作", color = MaterialTheme.colorScheme.secondary)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete-routine-${routine.id}"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "删除${routine.title}",
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "打开${routine.title}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TrainingSceneDock(
    pagerState: PagerState,
    scenes: List<RoutineScene>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(25.dp),
        tonalElevation = 1.dp,
        shadowElevation = 5.dp,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(6.dp)) {
            val tabWidth = maxWidth / scenes.size
            val indicatorPosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, (scenes.size - 1).toFloat())
            Box(
                modifier = Modifier
                    .offset(x = tabWidth * indicatorPosition)
                    .fillMaxWidth(1f / scenes.size)
                    .height(52.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
            )
            Row(Modifier.fillMaxWidth()) {
                scenes.forEachIndexed { index, scene ->
                    val activeAmount = (1f - abs(indicatorPosition - index)).coerceIn(0f, 1f)
                    val labelColor = lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.onPrimary,
                        activeAmount,
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clickable(role = Role.Tab) { onSelect(index) }
                            .semantics {
                                role = Role.Tab
                                selected = pagerState.currentPage == index
                            }
                            .testTag("scene-${scene.name.lowercase()}"),
                    ) {
                        Text(scene.displayName, color = labelColor, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutinePreviewScreen(
    snapshot: TimerSnapshot,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val routine = requireNotNull(snapshot.request).routine
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                tonalElevation = 0.dp,
                shadowElevation = 3.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .testTag("timer-preview-back"),
                        shape = RoundedCornerShape(19.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("返回方案", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .weight(1.35f)
                            .heightIn(min = 56.dp)
                            .testTag("timer-start"),
                        shape = RoundedCornerShape(19.dp),
                    ) {
                        Text("开始训练", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(7.dp))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "${routine.scene.displayName} · ${routine.role.previewLabel}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(routine.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text(
                    "${formatDuration(snapshot.totalPlannedSeconds)} · ${snapshot.logicalActionCount} 个逻辑动作",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(20.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
                    shape = RoundedCornerShape(17.dp),
                ) {
                    Text(
                        "预览按逻辑动作分组；准备、换侧等执行细节会在计时时自动展开。点击动作可查看完整说明。",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(routine.steps, key = RoutineStep::stepId) { step ->
                ExercisePreviewCard(step, routine)
            }
        }
    }
}

@Composable
private fun ExercisePreviewCard(step: RoutineStep, routine: RoutineTemplate) {
    var expanded by remember(step.stepId) { mutableStateOf(false) }
    val runtime = remember(step, routine) { expandRoutine(routine).filter { it.sourceStepId == step.stepId } }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
    ) {
        Column(Modifier.padding(horizontal = 17.dp, vertical = 15.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(step.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    step.dose?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                }
                Text(formatDuration(runtime.sumOf { it.seconds }), color = MaterialTheme.colorScheme.primary)
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = if (expanded) "收起说明" else "展开说明",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 90f else 0f },
                )
            }
            AnimatedVisibility(expanded) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    if (step.cues.isNotEmpty()) DetailList("动作要领", step.cues)
                    if (step.warnings.isNotEmpty()) DetailList("注意事项", step.warnings)
                    step.breath?.takeIf(String::isNotBlank)?.let { DetailList("呼吸", listOf(it)) }
                    if (runtime.size > 1) {
                        Text("执行细节", fontWeight = FontWeight.SemiBold)
                        runtime.forEach { Text("${it.name} · ${formatDuration(it.seconds)}", color = MaterialTheme.colorScheme.secondary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailList(title: String, values: List<String>) {
    Text(title, fontWeight = FontWeight.SemiBold)
    values.forEach { Text("· $it", color = MaterialTheme.colorScheme.secondary) }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ActiveTimerScreen(
    snapshot: TimerSnapshot,
    voiceNotice: String?,
    onPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit,
) {
    val step = snapshot.currentStep
    val progress = step?.let { 1f - snapshot.currentStepRemainingMillis.toFloat() / (it.seconds * 1000f) } ?: 1f
    val nextLogicalStep = snapshot.nextLogicalStep()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .testTag("timer-active"),
    ) {
        if (maxWidth > maxHeight) {
            LandscapeActiveTimerScreen(
                snapshot = snapshot,
                progress = progress,
                step = step,
                nextLogicalStep = nextLogicalStep,
                voiceNotice = voiceNotice,
                onPause = onPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onStop = onStop,
                onFinish = onFinish,
            )
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    TimerControlBar(snapshot, onPause, onPrevious, onNext, onStop, onFinish)
                },
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TimerTopline(snapshot, Modifier.fillMaxWidth())
                    TimerHero(snapshot, progress, Modifier.fillMaxWidth())
                    TimerDetails(step, voiceNotice, Modifier.fillMaxWidth().weight(1f))
                    NextActionStrip(nextLogicalStep, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
internal fun LandscapeActiveTimerScreen(
    snapshot: TimerSnapshot,
    progress: Float,
    step: RuntimeStep?,
    nextLogicalStep: RuntimeStep?,
    voiceNotice: String?,
    onPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 16.dp, top = 26.dp, end = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            TimerTopline(snapshot, Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(
                    Modifier.weight(1.06f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    TimerHero(
                        snapshot = snapshot,
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        compact = true,
                    )
                    NextActionStrip(nextLogicalStep, Modifier.fillMaxWidth())
                }
                TimerDetails(
                    step = step,
                    voiceNotice = voiceNotice,
                    modifier = Modifier.weight(0.94f).fillMaxHeight(),
                )
            }
        }
        TimerControlRail(
            snapshot = snapshot,
            onPause = onPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onStop = onStop,
            onFinish = onFinish,
            modifier = Modifier.width(76.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun TimerTopline(snapshot: TimerSnapshot, modifier: Modifier = Modifier) {
    val routineTitle = snapshot.request?.routine?.title ?: "训练"
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                routineTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${snapshot.currentLogicalAction} / ${snapshot.logicalActionCount}",
                modifier = Modifier.padding(horizontal = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "还剩 ${formatClock(snapshot.totalRemainingSeconds())}",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

@Composable
private fun TimerControlBar(
    snapshot: TimerSnapshot,
    onPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit,
) {
    val active = snapshot.state in ACTIVE_TIMER_STATES
    val terminal = snapshot.state in TERMINAL_TIMER_STATES
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedIconButton(
                onClick = onPrevious,
                enabled = active && snapshot.currentStepIndex > 0,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一个动作")
            }
            Button(
                onClick = if (terminal) onFinish else onPause,
                modifier = Modifier.weight(1.45f).heightIn(min = 60.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                when (snapshot.state) {
                    TimerEngineState.RUNNING -> {
                        Icon(Icons.Rounded.Pause, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("暂停", maxLines = 1)
                    }
                    TimerEngineState.PAUSED -> {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("继续", maxLines = 1)
                    }
                    else -> Text("补训练记录", maxLines = 1)
                }
            }
            OutlinedIconButton(
                onClick = onNext,
                enabled = active,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "下一个动作")
            }
            FilledIconButton(
                onClick = onStop,
                enabled = active,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Rounded.StopCircle, contentDescription = "结束训练")
            }
        }
    }
}

@Composable
private fun TimerControlRail(
    snapshot: TimerSnapshot,
    onPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = snapshot.state in ACTIVE_TIMER_STATES
    val terminal = snapshot.state in TERMINAL_TIMER_STATES
    Surface(
        modifier = modifier,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedIconButton(
                onClick = onPrevious,
                enabled = active && snapshot.currentStepIndex > 0,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "上一个动作")
            }
            Button(
                onClick = if (terminal) onFinish else onPause,
                modifier = Modifier.fillMaxWidth().heightIn(min = 94.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                shape = RoundedCornerShape(21.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    when (snapshot.state) {
                        TimerEngineState.RUNNING -> {
                            Icon(Icons.Rounded.Pause, contentDescription = null)
                            Text("暂停", maxLines = 1)
                        }
                        TimerEngineState.PAUSED -> {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Text("继续", maxLines = 1)
                        }
                        else -> {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                            Text("补记录", maxLines = 1)
                        }
                    }
                }
            }
            OutlinedIconButton(
                onClick = onNext,
                enabled = active,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "下一个动作")
            }
            FilledIconButton(
                onClick = onStop,
                enabled = active,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Rounded.StopCircle, contentDescription = "结束训练")
            }
        }
    }
}

@Composable
private fun TimerHero(
    snapshot: TimerSnapshot,
    progress: Float,
    modifier: Modifier,
    compact: Boolean = false,
) {
    Surface(modifier = modifier.testTag("timer-hero"), color = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(0.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = if (compact) 2.dp else 8.dp),
        ) {
            Surface(
                color = if (snapshot.state == TimerEngineState.PAUSED) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
                },
                shape = RoundedCornerShape(50),
            ) {
                Row(
                    Modifier.padding(horizontal = 13.dp, vertical = if (compact) 5.dp else 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(7.dp),
                        color = if (snapshot.state == TimerEngineState.PAUSED) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50),
                    ) {}
                    Text(
                        if (snapshot.state == TimerEngineState.PAUSED) "已暂停" else "训练中",
                        color = if (snapshot.state == TimerEngineState.PAUSED) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 7.dp else 15.dp))
            Text(
                snapshot.currentStep?.name ?: "训练完成",
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (compact) 2.dp else 5.dp))
            Text(
                "第 ${snapshot.currentLogicalAction} 项，共 ${snapshot.logicalActionCount} 项",
                color = MaterialTheme.colorScheme.secondary,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            )
            Text(
                formatClock(snapshot.remainingSeconds),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = if (compact) 58.sp else 84.sp,
                    lineHeight = if (compact) 60.sp else 88.sp,
                    letterSpacing = if (compact) (-2).sp else (-3).sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
                maxLines = 1,
            )
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
            )
        }
    }
}

@Composable
private fun NextActionStrip(next: RuntimeStep?, modifier: Modifier) {
    Surface(
        modifier = modifier.testTag("timer-next-action"),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("接下来", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    next?.name ?: "这是最后一项",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            next?.let {
                Text(formatClock(it.seconds), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun TimerDetails(step: RuntimeStep?, voiceNotice: String?, modifier: Modifier) {
    val cues = step?.cues.orEmpty()
    val breath = step?.breath?.takeIf(String::isNotBlank)
    val warnings = step?.warnings.orEmpty()
    Surface(
        modifier = modifier.testTag("timer-details"),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(20.dp),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            voiceNotice?.let { notice ->
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f), shape = RoundedCornerShape(14.dp)) {
                        Text(
                            notice,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Text("动作要领", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            if (cues.isEmpty()) {
                item { Text("保持稳定、自然呼吸。", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(cues) { cue -> Text(cue, style = MaterialTheme.typography.bodyMedium) }
            }
            breath?.let {
                item { Text("呼吸：$breath", color = MaterialTheme.colorScheme.primary) }
            }
            if (warnings.isNotEmpty()) {
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(9.dp))
                        Text("需要留意", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(warnings) { warning -> Text(warning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostWorkoutSheet(
    session: TimerSessionFact,
    onDismiss: () -> Unit,
    onSave: (TrainingLog) -> Unit,
) {
    var heartRate by remember(session.id) { mutableStateOf("") }
    var effort by remember(session.id) { mutableStateOf(5f) }
    var result by remember(session.id) { mutableStateOf("合适") }
    var notes by remember(session.id) { mutableStateOf("") }
    val title = session.routineSnapshot["title"]?.jsonPrimitive?.content ?: "训练"
    val completedActions = session.stepResults.count { it.completed }
    val totalActions = session.stepResults.size
    ShenkModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Surface(
                modifier = Modifier.size(58.dp).align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(21.dp),
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("计时事实已保存", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("完成训练", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("$title · ${formatDuration(session.actualSeconds)}", color = MaterialTheme.colorScheme.secondary)
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    CompletionFact("完成动作", if (totalActions > 0) "$completedActions / $totalActions" else "—")
                    CompletionFact("有效训练", formatClock(session.activeSeconds))
                    CompletionFact("暂停", formatClock(session.pausedSeconds))
                }
            }
            OutlinedTextField(
                value = heartRate,
                onValueChange = { heartRate = it.filter(Char::isDigit).take(3) },
                label = { Text("平均心率（可选）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                Text("体感强度 ${effort.toInt()} / 10", fontWeight = FontWeight.SemiBold)
                Slider(value = effort, onValueChange = { effort = it }, valueRange = 1f..10f, steps = 8)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("轻松", "合适", "吃力").forEach { value ->
                    if (value == result) Button(onClick = { result = value }, modifier = Modifier.weight(1f)) { Text(value) }
                    else FilledTonalButton(onClick = { result = value }, modifier = Modifier.weight(1f)) { Text(value) }
                }
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("备注（可选）") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Text(
                "正式训练记录将在确认后保存；选择稍后补充只会保留上面的计时事实。",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    onSave(
                        TrainingLog(
                            id = "android-timer-log-${session.id}",
                            date = session.date,
                            type = session.trainingType,
                            status = if (session.completion == "completed") "completed" else "short_version",
                            source = "timer",
                            title = title,
                            durationSec = session.actualSeconds,
                            averageHeartRate = heartRate.toIntOrNull(),
                            perceivedEffort = effort.toInt(),
                            subjectiveResult = result,
                            notes = notes.takeIf(String::isNotBlank),
                            timerSessionId = session.id,
                            timerSessionIds = listOf(session.id),
                            calendarVisible = session.calendarVisible,
                            countsTowardTraining = session.countsTowardTraining,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("post-workout-save"),
                shape = RoundedCornerShape(18.dp),
            ) { Text("保存正式记录") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("稍后补充") }
        }
    }
}

@Composable
private fun CompletionFact(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(5.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private val ACTIVE_TIMER_STATES = setOf(TimerEngineState.RUNNING, TimerEngineState.PAUSED)
private val TERMINAL_TIMER_STATES = setOf(TimerEngineState.COMPLETED, TimerEngineState.STOPPED)

private val RoutineRole.previewLabel: String
    get() = when (this) {
        RoutineRole.MAIN -> "主训练"
        RoutineRole.WARMUP -> "热身"
        RoutineRole.STRETCH -> "拉伸"
        RoutineRole.COOLDOWN -> "放松"
        RoutineRole.RECOVERY -> "恢复"
        RoutineRole.AUXILIARY -> "辅助"
    }

internal fun TimerSnapshot.nextLogicalStep(): RuntimeStep? {
    val currentLogicalIndex = currentStep?.logicalIndex ?: return null
    return steps.asSequence()
        .drop(currentStepIndex + 1)
        .firstOrNull { it.logicalIndex > currentLogicalIndex }
}

internal fun TimerSnapshot.totalRemainingSeconds(): Int {
    if (steps.isEmpty()) return 0
    val futureSeconds = steps.drop(currentStepIndex + 1).sumOf(RuntimeStep::seconds)
    return remainingSeconds + futureSeconds
}

private fun formatDuration(seconds: Int): String = when {
    seconds >= 3600 -> "${seconds / 3600} 小时 ${(seconds % 3600) / 60} 分"
    seconds >= 60 -> "${seconds / 60} 分 ${(seconds % 60).takeIf { it > 0 }?.let { "$it 秒" }.orEmpty()}".trim()
    else -> "$seconds 秒"
}

private fun formatClock(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
