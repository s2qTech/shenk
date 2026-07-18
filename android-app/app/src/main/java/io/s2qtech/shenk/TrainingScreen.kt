package io.s2qtech.shenk

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.model.RoutineScene
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
import io.s2qtech.shenk.timer.TimerSnapshot
import io.s2qtech.shenk.timer.expandRoutine
import java.util.UUID
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
    onToday: () -> Unit,
    onCalendar: () -> Unit,
) {
    val library by routineRepository.observeLibrary().collectAsState(initial = null)
    val pending by sessionRepository.observePendingCompletion().collectAsState(initial = emptyList())
    val snapshot by coordinator.snapshot.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val cuePlayer = remember { TimerCuePlayer(context) }
    val callMonitor = remember { TimerCallMonitor(context, coordinator::pauseForPhoneCall) }
    var completion by remember { mutableStateOf<TimerSessionFact?>(null) }
    var preferredScene by remember { mutableStateOf<RoutineScene?>(null) }
    var launchNotice by remember { mutableStateOf<String?>(null) }
    var launchContext by remember { mutableStateOf<TrainingLaunchRequest?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { callMonitor.registerIfPermitted() }

    LaunchedEffect(library?.routines) {
        library?.let { coordinator.restoreIfPossible(it.routines) }
    }
    LaunchedEffect(library, launchRequest, snapshot.state) {
        val available = library ?: return@LaunchedEffect
        val request = launchRequest ?: return@LaunchedEffect
        if (snapshot.state != TimerEngineState.IDLE) return@LaunchedEffect
        val guidance = request.guidance
        launchContext = request
        preferredScene = guidance?.trainingType?.let(::sceneForTrainingEntry)
        launchNotice = null
        if (guidance?.source == GuidanceSource.FORMAL_PLAN &&
            guidance.trainingType in setOf("strength", "recovery")
        ) {
            val routineId = guidance.routineId
            val routine = routineId?.let { id -> available.routines.firstOrNull { it.id == id } }
            when {
                routine != null -> coordinator.select(
                    routine = routine,
                    date = request.date,
                    dailyPlanItemId = guidance.dailyPlanItemId,
                    planTemplateId = guidance.planTemplateId,
                )
                routineId == null -> launchNotice = "今天的计划没有指定计时方案，请手动选择。"
                else -> launchNotice = "计划指定的方案尚未缓存，请联网同步后重试。"
            }
        }
        onLaunchConsumed()
    }
    LaunchedEffect(coordinator) { coordinator.cues.collect(cuePlayer::speak) }
    LaunchedEffect(snapshot.state, snapshot.request?.sessionId) {
        if (snapshot.state in setOf(TimerEngineState.COMPLETED, TimerEngineState.STOPPED)) {
            completion = coordinator.terminalFact()
        }
    }
    DisposableEffect(Unit) {
        callMonitor.registerIfPermitted()
        onDispose {
            callMonitor.close()
            cuePlayer.close()
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

    when (snapshot.state) {
        TimerEngineState.IDLE -> if (library == null) {
            Box(
                Modifier.fillMaxSize().testTag("training-screen"),
                contentAlignment = Alignment.Center,
            ) { Text("正在读取离线方案库…") }
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
            onToday = onToday,
            onCalendar = onCalendar,
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
                    SyncScheduler(context).enqueue()
                    completion = null
                    if (snapshot.request?.sessionId == session.id) coordinator.reset()
                }
            },
        )
    }
}

@Composable
private fun RoutineLibraryScreen(
    library: RoutineLibrary,
    pending: List<PendingTimerCompletion>,
    preferredScene: RoutineScene?,
    notice: String?,
    onSelect: (RoutineTemplate) -> Unit,
    onPending: (PendingTimerCompletion) -> Unit,
    onToday: () -> Unit,
    onCalendar: () -> Unit,
) {
    var scene by remember(preferredScene) { mutableStateOf(preferredScene ?: RoutineScene.HOME) }
    LaunchedEffect(library.routines) {
        if (library.byScene[scene].isNullOrEmpty()) {
            scene = RoutineScene.entries.firstOrNull { library.byScene[it].orEmpty().isNotEmpty() } ?: scene
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("training-screen")
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("训练", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    Text("选择今天要执行的流程", color = MaterialTheme.colorScheme.secondary)
                }
                Row {
                    TextButton(onClick = onToday) { Text("今天") }
                    TextButton(onClick = onCalendar) { Text("月历") }
                }
            }
        }
        if (pending.isNotEmpty()) {
            item {
                Text("待补训练记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(pending.take(3), key = { it.session.id }) { item ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onPending(item) },
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(item.routineTitle, fontWeight = FontWeight.SemiBold)
                            Text("${item.session.date} · ${formatDuration(item.session.actualSeconds)}", color = MaterialTheme.colorScheme.secondary)
                        }
                        Text("补记录", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
        notice?.let { message ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(message, modifier = Modifier.padding(16.dp)) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoutineScene.entries.forEach { value ->
                    if (value == scene) {
                        Button(onClick = { scene = value }, modifier = Modifier.weight(1f)) { Text(value.displayName) }
                    } else {
                        FilledTonalButton(onClick = { scene = value }, modifier = Modifier.weight(1f)) { Text(value.displayName) }
                    }
                }
            }
        }
        val routines = library.byScene[scene].orEmpty()
        if (routines.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(24.dp)) {
                        Text("这个场景还没有可执行方案", style = MaterialTheme.typography.titleLarge)
                        Text("联网同步后会缓存 AI 管理的现行方案；本地不会用旧写死流程替代。", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        } else {
            items(routines, key = RoutineTemplate::id) { routine ->
                val expandedSeconds = expandRoutine(routine).sumOf { it.seconds }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(routine) }
                        .testTag("routine-${routine.id}"),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(routine.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text("${formatDuration(expandedSeconds)} · ${routine.steps.size} 个动作", color = MaterialTheme.colorScheme.secondary)
                        }
                        Text("查看", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (library.rejectedCount > 0) {
            item { Text("有 ${library.rejectedCount} 个方案缺少权威字段或格式无效，未加入计时器。", color = MaterialTheme.colorScheme.error) }
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
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("返回") }
                Button(onClick = onStart, modifier = Modifier.weight(2f).testTag("timer-start")) { Text("开始训练") }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(routine.scene.displayName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Text(routine.title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text("${formatDuration(snapshot.totalPlannedSeconds)} · ${snapshot.logicalActionCount} 个动作", color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(10.dp))
                Text("点击动作可查看要领、风险和准备/换侧细节。", color = MaterialTheme.colorScheme.outline)
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(step.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    step.dose?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                }
                Text(formatDuration(runtime.sumOf { it.seconds }), color = MaterialTheme.colorScheme.primary)
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
    onPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onFinish: () -> Unit,
) {
    val step = snapshot.currentStep
    val progress = step?.let { 1f - snapshot.currentStepRemainingMillis.toFloat() / (it.seconds * 1000f) } ?: 1f
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onPrevious, enabled = snapshot.state in ACTIVE_TIMER_STATES, modifier = Modifier.weight(1f)) { Text("上一个") }
                Button(
                    onClick = if (snapshot.state in TERMINAL_TIMER_STATES) onFinish else onPause,
                    modifier = Modifier.weight(1.4f),
                ) { Text(if (snapshot.state == TimerEngineState.RUNNING) "暂停" else if (snapshot.state == TimerEngineState.PAUSED) "继续" else "补训练记录") }
                OutlinedButton(onClick = onNext, enabled = snapshot.state in ACTIVE_TIMER_STATES, modifier = Modifier.weight(1f)) { Text("下一个") }
                TextButton(onClick = onStop, enabled = snapshot.state in ACTIVE_TIMER_STATES) { Text("结束") }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    TimerHero(snapshot, progress, Modifier.weight(1.35f).fillMaxHeight())
                    TimerDetails(step, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TimerHero(snapshot, progress, Modifier.fillMaxWidth().weight(1.1f))
                    TimerDetails(step, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TimerHero(snapshot: TimerSnapshot, progress: Float, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(28.dp)) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${snapshot.currentLogicalAction} / ${snapshot.logicalActionCount}", color = MaterialTheme.colorScheme.secondary)
                Text(if (snapshot.state == TimerEngineState.PAUSED) "已暂停" else "训练中", color = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(snapshot.currentStep?.name ?: "训练完成", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                Text(formatClock(snapshot.remainingSeconds), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TimerDetails(step: io.s2qtech.shenk.timer.RuntimeStep?, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("动作要领", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            if (step?.cues.isNullOrEmpty()) Text("保持稳定、自然呼吸。", color = MaterialTheme.colorScheme.secondary)
            step?.cues.orEmpty().forEach { Text("· $it", style = MaterialTheme.typography.bodyLarge) }
            step?.breath?.let { Text("呼吸：$it", color = MaterialTheme.colorScheme.primary) }
            if (!step?.warnings.isNullOrEmpty()) {
                Spacer(Modifier.height(22.dp))
                HorizontalDivider()
                Spacer(Modifier.height(18.dp))
                Text("注意事项", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                step?.warnings.orEmpty().forEach { Text("· $it", color = MaterialTheme.colorScheme.error) }
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("完成训练记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("$title · ${formatDuration(session.actualSeconds)}", color = MaterialTheme.colorScheme.secondary)
            OutlinedTextField(
                value = heartRate,
                onValueChange = { heartRate = it.filter(Char::isDigit).take(3) },
                label = { Text("平均心率（可选）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                Text("体感强度 ${effort.toInt()} / 10")
                Slider(value = effort, onValueChange = { effort = it }, valueRange = 1f..10f, steps = 8)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("轻松", "合适", "吃力").forEach { value ->
                    if (value == result) Button(onClick = { result = value }, modifier = Modifier.weight(1f)) { Text(value) }
                    else FilledTonalButton(onClick = { result = value }, modifier = Modifier.weight(1f)) { Text(value) }
                }
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("备注（可选）") }, minLines = 2, modifier = Modifier.fillMaxWidth())
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
                modifier = Modifier.fillMaxWidth().testTag("post-workout-save"),
            ) { Text("保存正式记录") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("稍后补充") }
        }
    }
}

private val ACTIVE_TIMER_STATES = setOf(TimerEngineState.RUNNING, TimerEngineState.PAUSED)
private val TERMINAL_TIMER_STATES = setOf(TimerEngineState.COMPLETED, TimerEngineState.STOPPED)

private fun formatDuration(seconds: Int): String = when {
    seconds >= 3600 -> "${seconds / 3600} 小时 ${(seconds % 3600) / 60} 分"
    seconds >= 60 -> "${seconds / 60} 分 ${(seconds % 60).takeIf { it > 0 }?.let { "$it 秒" }.orEmpty()}".trim()
    else -> "$seconds 秒"
}

private fun formatClock(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)

private fun sceneForTrainingEntry(trainingType: String): RoutineScene? = when (trainingType) {
    "easy_walk", "quality_walk" -> RoutineScene.WALK
    "strength" -> RoutineScene.HOME
    "recovery" -> RoutineScene.RECOVERY
    "travel_strength", "seat_recovery" -> RoutineScene.TRAVEL
    else -> null
}
