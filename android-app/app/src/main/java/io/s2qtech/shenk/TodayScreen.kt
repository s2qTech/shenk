package io.s2qtech.shenk

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.model.GuidanceSource
import io.s2qtech.shenk.model.EntityOwnership
import io.s2qtech.shenk.model.SharedRecordKey
import io.s2qtech.shenk.model.TodayPrimaryAction
import io.s2qtech.shenk.model.TodayPrimaryActionResolver
import io.s2qtech.shenk.model.TodayGuidance
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.TodayRecordRepository
import io.s2qtech.shenk.sync.TodayRecords
import io.s2qtech.shenk.sync.CloudConnectionException
import io.s2qtech.shenk.sync.CloudConnectionFailure
import io.s2qtech.shenk.sync.CloudConnectionManager
import io.s2qtech.shenk.sync.CloudConnectionState
import io.s2qtech.shenk.sync.SyncScheduler
import io.s2qtech.shenk.sync.SafBusinessBackup
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class TodaySheet { MORNING, PRE_WORKOUT, RECORD_DAY, REMINDERS, CONNECTION, DAILY_REVIEW, SETTINGS, AI_SETTINGS, DATA_BACKUP, SYNC_CONFLICTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayRoute(
    repository: TodayRecordRepository,
    recordRepository: CalendarRecordRepository,
    reminderStore: ReminderSettingsStore,
    cloudConnectionManager: CloudConnectionManager,
    onReady: () -> Unit = {},
    onData: () -> Unit = {},
    onPlanning: () -> Unit = {},
    onTraining: (TodayGuidance?) -> Unit = {},
) {
    val date = remember { LocalDate.now() }
    val records by repository.observe(date).collectAsState(initial = null)
    var sheet by remember { mutableStateOf<TodaySheet?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val application = context.applicationContext as ShenkApplication
    val dailyReviewRepository = remember(context) {
        application.dailyReviewRepository
    }
    val dailyReviewState by dailyReviewRepository.observe(date).collectAsState(
        initial = io.s2qtech.shenk.sync.DailyReviewState(),
    )
    var reminders by remember { mutableStateOf(ReminderSettings()) }
    var connection by remember { mutableStateOf(CloudConnectionState(false, "")) }
    var connectionBusy by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    val conflicts by application.localFirstRepository.observeConflicts().collectAsState(initial = emptyList())
    var resolvingConflictKey by remember { mutableStateOf<String?>(null) }
    var reminderSystemRefresh by remember { mutableIntStateOf(0) }
    val businessBackup = remember(context) {
        SafBusinessBackup(
            contentResolver = context.contentResolver,
            repository = (context.applicationContext as ShenkApplication).localFirstRepository,
        )
    }
    LaunchedEffect(Unit) {
        reminders = reminderStore.settings.first()
        connection = cloudConnectionManager.state()
    }
    LaunchedEffect(records) {
        if (records != null) onReady()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { reminderSystemRefresh += 1 }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            backupBusy = true
            try {
                withContext(Dispatchers.IO) { businessBackup.exportTo(uri) }
                snackbar.showSnackbar("业务备份已导出")
                sheet = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                snackbar.showSnackbar("备份导出失败，本机数据未改变")
            } finally {
                backupBusy = false
            }
        }
    }
    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            backupBusy = true
            try {
                val result = withContext(Dispatchers.IO) { businessBackup.restoreFrom(uri) }
                if (result.restored > 0) SyncScheduler(context).enqueue()
                val skipped = if (result.skippedExisting > 0) "，跳过 ${result.skippedExisting} 条本机已有记录" else ""
                snackbar.showSnackbar("已恢复 ${result.restored} 条，${result.unchanged} 条无需更改$skipped")
                sheet = null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                snackbar.showSnackbar("备份无效或恢复失败，本机数据未改变")
            } finally {
                backupBusy = false
            }
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reminderSystemRefresh += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            TodayDestinationBar(
                onData = onData,
                onPlanning = onPlanning,
                onSettings = { sheet = TodaySheet.SETTINGS },
            )
        },
    ) { innerPadding ->
        TodayScreen(
            records = records,
            date = date,
            modifier = Modifier.padding(innerPadding),
            onMorning = { sheet = TodaySheet.MORNING },
            onPreWorkout = { sheet = TodaySheet.PRE_WORKOUT },
            onConnect = { sheet = TodaySheet.CONNECTION },
            cloudConfigured = connection.configured,
            dailyReviewState = dailyReviewState,
            onDailyReview = { sheet = TodaySheet.DAILY_REVIEW },
            onTraining = { onTraining(records?.guidance) },
            onRecordDay = { sheet = TodaySheet.RECORD_DAY },
        )
    }

    when (sheet) {
        TodaySheet.MORNING -> ShenkModalBottomSheet(onDismissRequest = { sheet = null }) {
            MorningCheckInSheet(
                date = date,
                existing = records,
                onSave = { checkin, metric ->
                    scope.launch {
                        runCatching { repository.saveMorning(checkin, metric) }
                            .onSuccess {
                                val reviewRepository = (context.applicationContext as ShenkApplication).dailyReviewRepository
                                reviewRepository.requeueIfReviewed(date)
                                    ?.takeIf { it.queued }
                                    ?.let { DailyReviewScheduler.enqueue(context) }
                                SyncScheduler(context).enqueue()
                                sheet = null
                                snackbar.showSnackbar("晨起状态已保存在本机，等待同步")
                            }
                            .onFailure { snackbar.showSnackbar(it.message ?: "保存失败") }
                    }
                },
            )
        }
        TodaySheet.PRE_WORKOUT -> ShenkModalBottomSheet(onDismissRequest = { sheet = null }) {
            PreWorkoutSheet(
                date = date,
                morning = records?.morning,
                existing = records?.preWorkout,
                onSave = { checkin ->
                    scope.launch {
                        runCatching { repository.savePreWorkout(checkin) }
                            .onSuccess {
                                val reviewRepository = (context.applicationContext as ShenkApplication).dailyReviewRepository
                                reviewRepository.requeueIfReviewed(date)
                                    ?.takeIf { it.queued }
                                    ?.let { DailyReviewScheduler.enqueue(context) }
                                SyncScheduler(context).enqueue()
                                sheet = null
                                snackbar.showSnackbar("训练前变化已记录")
                            }
                            .onFailure { snackbar.showSnackbar(it.message ?: "保存失败") }
                    }
                },
            )
        }
        TodaySheet.REMINDERS -> ShenkModalBottomSheet(onDismissRequest = { sheet = null }) {
            val reminderSystemStatus = remember(reminderSystemRefresh) {
                readReminderSystemStatus(context)
            }
            ReminderSettingsSheet(
                settings = reminders,
                systemStatus = reminderSystemStatus,
                onRequestNotificationPermission = {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onOpenNotificationSettings = {
                    runCatching { context.startActivity(notificationSettingsIntent(context)) }
                        .onFailure { context.startActivity(applicationDetailsIntent(context)) }
                },
                onOpenApplicationSettings = {
                    try {
                        context.startActivity(applicationDetailsIntent(context))
                    } catch (_: ActivityNotFoundException) {
                        scope.launch { snackbar.showSnackbar("无法打开系统应用设置") }
                    }
                },
                onSave = { value ->
                    scope.launch {
                        reminderStore.save(value)
                        reminders = value
                        sheet = null
                        if ((value.morningEnabled || value.middayEnabled || value.eveningEnabled || value.weeklyEnabled) &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        snackbar.showSnackbar("提醒设置已更新")
                    }
                },
            )
        }
        TodaySheet.RECORD_DAY -> ShenkModalBottomSheet(onDismissRequest = { sheet = null }) {
            TrainingLogEditorSheet(
                date = date,
                existing = null,
                readOnly = false,
                initialType = records?.guidance?.trainingType,
                createTitle = "记录今日情况",
                onSave = { log ->
                    scope.launch {
                        runCatching { recordRepository.saveTrainingLog(log) }
                            .onSuccess {
                                val review = dailyReviewRepository.enqueue(date, allowIncomplete = false)
                                if (review.queued) DailyReviewScheduler.enqueue(context)
                                SyncScheduler(context).enqueue()
                                sheet = null
                                snackbar.showSnackbar("今日情况已保存在本机")
                            }
                            .onFailure { snackbar.showSnackbar(it.message ?: "保存失败") }
                    }
                },
            )
        }
        TodaySheet.CONNECTION -> ShenkModalBottomSheet(onDismissRequest = { if (!connectionBusy) sheet = null }) {
            CloudConnectionSheet(
                state = connection,
                busy = connectionBusy,
                error = connectionError,
                onConnect = { code ->
                    connectionBusy = true
                    connectionError = null
                    scope.launch {
                        try {
                            val result = cloudConnectionManager.connectWithMigrationCode(code)
                            connection = cloudConnectionManager.state()
                            sheet = null
                            snackbar.showSnackbar("同步完成，读取 ${result.pulled} 条云端数据")
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            connectionError = cloudConnectionMessage(error)
                        } finally {
                            connectionBusy = false
                        }
                    }
                },
                onSync = {
                    connectionBusy = true
                    connectionError = null
                    scope.launch {
                        try {
                            val result = cloudConnectionManager.synchronizeNow()
                            sheet = null
                            snackbar.showSnackbar("同步完成，读取 ${result.pulled} 条，写入 ${result.pushed} 条")
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            connectionError = "同步失败，本地数据不受影响，请稍后重试。"
                        } finally {
                            connectionBusy = false
                        }
                    }
                },
            )
        }
        TodaySheet.DAILY_REVIEW -> ShenkModalBottomSheet(onDismissRequest = { sheet = null }) {
            DailyReviewSheet(
                date = date,
                repository = dailyReviewRepository,
                state = dailyReviewState,
                onQueued = { DailyReviewScheduler.enqueue(context) },
                onOpenAiSettings = { sheet = TodaySheet.AI_SETTINGS },
                onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
            )
        }
        TodaySheet.SETTINGS -> ShenkModalBottomSheet(onDismissRequest = { sheet = null }) {
            AppSettingsSheet(
                onReminders = { sheet = TodaySheet.REMINDERS },
                onAiService = { sheet = TodaySheet.AI_SETTINGS },
                onBackup = { sheet = TodaySheet.DATA_BACKUP },
                conflictCount = conflicts.size,
                onConflicts = { sheet = TodaySheet.SYNC_CONFLICTS },
            )
        }
        TodaySheet.AI_SETTINGS -> ShenkModalBottomSheet(onDismissRequest = { sheet = null }) {
            AiProviderSettingsSheet(
                repository = dailyReviewRepository,
                onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
            )
        }
        TodaySheet.DATA_BACKUP -> ShenkModalBottomSheet(onDismissRequest = { if (!backupBusy) sheet = null }) {
            DataBackupSheet(
                busy = backupBusy,
                onExport = { exportBackupLauncher.launch("shenk-business-${LocalDate.now()}.json") },
                onImport = { importBackupLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
            )
        }
        TodaySheet.SYNC_CONFLICTS -> ShenkModalBottomSheet(
            onDismissRequest = { if (resolvingConflictKey == null) sheet = null },
        ) {
            SyncConflictSheet(
                conflicts = conflicts,
                resolvingKey = resolvingConflictKey,
                onKeepLocal = { conflict ->
                    resolvingConflictKey = conflict.recordKey
                    scope.launch {
                        runCatching {
                            application.localFirstRepository.resolveWithLocal(
                                SharedRecordKey(conflict.entity, conflict.recordId),
                                EntityOwnership.ownerOf(conflict.entity),
                            )
                            SyncScheduler(context).enqueue()
                        }.onSuccess {
                            snackbar.showSnackbar("已保留本机版本，等待重新同步")
                        }.onFailure {
                            snackbar.showSnackbar("暂时无法处理，本机和云端版本均已保留")
                        }
                        resolvingConflictKey = null
                    }
                },
                onUseCloud = { conflict ->
                    resolvingConflictKey = conflict.recordKey
                    scope.launch {
                        runCatching {
                            application.localFirstRepository.resolveWithRemote(
                                SharedRecordKey(conflict.entity, conflict.recordId),
                            )
                        }.onSuccess {
                            snackbar.showSnackbar("已采用云端版本")
                        }.onFailure {
                            snackbar.showSnackbar("暂时无法处理，本机和云端版本均已保留")
                        }
                        resolvingConflictKey = null
                    }
                },
            )
        }
        null -> Unit
    }
}

@Composable
private fun TodayScreen(
    records: TodayRecords?,
    date: LocalDate,
    modifier: Modifier = Modifier,
    onMorning: () -> Unit,
    onPreWorkout: () -> Unit,
    onConnect: () -> Unit,
    cloudConfigured: Boolean,
    dailyReviewState: io.s2qtech.shenk.sync.DailyReviewState,
    onDailyReview: () -> Unit,
    onTraining: () -> Unit,
    onRecordDay: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("today-screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 18.dp),
        ) {
            Column {
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(16.dp))

            if (!cloudConfigured) {
                ShenkStatePanel(
                    title = "尚未连接已有数据",
                    message = "本机仍可正常记录；连接后会取回计划、记录和训练方案。",
                    tone = ShenkStateTone.NEUTRAL,
                    actionLabel = "连接",
                    onAction = onConnect,
                    modifier = Modifier.fillMaxWidth().testTag("cloud-setup-prompt"),
                )
                Spacer(Modifier.height(18.dp))
            }

            val guidance = records?.guidance
            if (guidance == null) {
                ShenkStatePanel(
                    title = "正在读取今天",
                    message = "先从本机组合正式记录、有效计划和可用建议。",
                    tone = ShenkStateTone.PROGRESS,
                    modifier = Modifier.fillMaxWidth().testTag("today-loading"),
                )
            } else {
                GuidanceBlock(
                    guidance = guidance,
                    onTraining = onTraining,
                    onRecordDay = onRecordDay,
                )
            }
            Spacer(Modifier.height(24.dp))

            MorningStatusSection(
                records = records,
                onMorning = onMorning,
                onPreWorkout = onPreWorkout,
            )
            Spacer(Modifier.height(22.dp))
            CoachReviewSection(
                state = dailyReviewState,
                onOpen = onDailyReview,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TodayDestinationBar(
    onData: () -> Unit,
    onPlanning: () -> Unit,
    onSettings: () -> Unit,
) {
    ThumbActionDock(
        actions = listOf(
            ThumbAction(
                label = "数据",
                onClick = onData,
                testTag = "today-open-data",
                icon = Icons.AutoMirrored.Rounded.ShowChart,
            ),
            ThumbAction(
                label = "计划",
                onClick = onPlanning,
                testTag = "today-open-planning",
                icon = Icons.Rounded.AutoAwesome,
            ),
            ThumbAction(
                label = "设置",
                onClick = onSettings,
                testTag = "today-open-settings",
                icon = Icons.Rounded.Settings,
            ),
        ),
        modifier = Modifier.testTag("today-destination-bar"),
    )
}

@Composable
private fun GuidanceBlock(
    guidance: io.s2qtech.shenk.model.TodayGuidance,
    onTraining: () -> Unit,
    onRecordDay: () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val source = when (guidance.source) {
        GuidanceSource.ACTUAL -> "今日已完成"
        GuidanceSource.FORMAL_PLAN -> "今日计划"
        GuidanceSource.LOCAL_SUGGESTION -> "本地建议"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 1.dp,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.98f),
                        ),
                    ),
                ),
        ) {
            Image(
                painter = painterResource(R.drawable.today_path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier = Modifier
                    .matchParentSize()
                    .alpha(if (darkTheme) 0.08f else 0.45f),
            )
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(modifier = Modifier.size(7.dp), color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {}
                    Text(source, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        guidance.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        modifier = Modifier.size(46.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(17.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(guidanceIcon(guidance.trainingType), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                guidance.estimatedMinutes?.let {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (it <= 0) "不安排训练" else "约 $it 分钟",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                guidance.note?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (TodayPrimaryActionResolver.resolve(guidance)) {
                    TodayPrimaryAction.NONE -> Unit
                    TodayPrimaryAction.OPEN_TIMER -> {
                        Spacer(Modifier.height(17.dp))
                        TodayGuidanceButton("进入训练", onTraining, "today-open-training")
                    }
                    TodayPrimaryAction.RECORD_DAY -> {
                        Spacer(Modifier.height(17.dp))
                        TodayGuidanceButton("记录今日情况", onRecordDay, "today-record-day")
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayGuidanceButton(label: String, onClick: () -> Unit, testTag: String) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(testTag),
        shape = RoundedCornerShape(19.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.align(Alignment.Center), fontWeight = FontWeight.SemiBold)
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterEnd).size(20.dp),
            )
        }
    }
}

@Composable
private fun TodaySecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 10.dp),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(6.dp))
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun guidanceIcon(type: String): ImageVector = when (type) {
    "strength", "travel_strength" -> Icons.Rounded.FitnessCenter
    "quality_walk" -> Icons.AutoMirrored.Rounded.DirectionsRun
    "easy_walk" -> Icons.AutoMirrored.Rounded.DirectionsWalk
    "rest" -> Icons.Rounded.Bedtime
    else -> Icons.Rounded.SelfImprovement
}

private sealed interface CoachReviewDisplayState {
    data class Completed(val review: io.s2qtech.shenk.sync.DailyReview) : CoachReviewDisplayState
    data object Running : CoachReviewDisplayState
    data class Failure(val message: String, val manualRetry: Boolean) : CoachReviewDisplayState
    data object Empty : CoachReviewDisplayState
}

private fun io.s2qtech.shenk.sync.DailyReviewState.toCoachReviewDisplayState(): CoachReviewDisplayState {
    review?.let { return CoachReviewDisplayState.Completed(it) }
    return when {
        jobState in setOf("PENDING", "RUNNING", "AWAITING_SERVER") -> CoachReviewDisplayState.Running
        jobState in setOf("RETRY", "FAILED") -> CoachReviewDisplayState.Failure(
            message = dailyReviewFailureMessage(jobError, jobState == "RETRY"),
            manualRetry = dailyReviewAllowsManualRetry(jobState),
        )
        else -> CoachReviewDisplayState.Empty
    }
}

@Composable
internal fun CoachReviewSection(
    state: io.s2qtech.shenk.sync.DailyReviewState,
    onOpen: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("today-coach-review")) {
        Text("教练简评", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            AnimatedContent(
                targetState = state.toCoachReviewDisplayState(),
                transitionSpec = { shenkStateContentTransform() },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = "coach-review-state",
            ) { displayState ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (displayState) {
                        is CoachReviewDisplayState.Completed -> {
                            DeepSeekCoachIdentity(
                                title = "今日简评已生成",
                                subtitle = "DeepSeek · 结合今天与近期记录",
                            )
                            Text(
                                displayState.review.conclusion,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TodaySecondaryActionButton(
                                label = "查看完整简评",
                                onClick = onOpen,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                        CoachReviewDisplayState.Running -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DeepSeekCoachIdentity(
                                    title = "正在生成今日简评",
                                    subtitle = "完成后会自动出现在这里",
                                    modifier = Modifier.weight(1f),
                                )
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                        is CoachReviewDisplayState.Failure -> {
                            DeepSeekCoachIdentity(
                                title = "简评暂未完成",
                                subtitle = "DeepSeek · 今日复盘",
                            )
                            Text(
                                displayState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            TodaySecondaryActionButton(
                                label = if (displayState.manualRetry) "查看并重试" else "查看状态",
                                onClick = onOpen,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                        CoachReviewDisplayState.Empty -> {
                            DeepSeekCoachIdentity(
                                title = "等待今天的记录",
                                subtitle = "DeepSeek · 今日复盘",
                            )
                            Text(
                                "记录训练、休息或今天的身体感受后，再结合近期状态生成简评。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            TodaySecondaryActionButton(
                                label = "生成今日简评",
                                onClick = onOpen,
                                modifier = Modifier.align(Alignment.End),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun cloudConnectionMessage(error: Exception): String {
    val failure = (error as? CloudConnectionException)?.failure
    return when (failure) {
        CloudConnectionFailure.INVALID_MIGRATION_CODE -> "迁移码格式不正确，请完整粘贴后重试。"
        CloudConnectionFailure.PROFILE_UNAVAILABLE -> "迁移码无效或已过期，请在 Web 身刻重新生成。"
        CloudConnectionFailure.NETWORK_UNAVAILABLE -> "无法连接云端，请检查网络后重试。"
        CloudConnectionFailure.INVALID_PROFILE -> "云端配置无法解密或内容不完整，请重新生成迁移码。"
        CloudConnectionFailure.CLOUD_SYNC_FAILED -> "配置已验证，但云端数据同步失败，请稍后重试。"
        CloudConnectionFailure.LOCAL_CONFIGURATION_FAILED -> "配置已验证，但未能安全保存到本机，请重试。"
        null -> "连接失败，请检查迁移码和网络后重试。"
    }
}

@Composable
private fun CloudSetupPrompt(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("cloud-setup-prompt"),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("连接已有数据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("粘贴迁移码，取回计划、记录和方案", color = MaterialTheme.colorScheme.secondary)
            }
            FilledTonalButton(onClick = onClick, modifier = Modifier.testTag("cloud-connect-action")) { Text("连接") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MorningStatusSection(
    records: TodayRecords?,
    onMorning: () -> Unit,
    onPreWorkout: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("body-status-card"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 17.dp),
        ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("身体状态", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (records?.morning == null) "今天还没有记录" else "今天身体的起点",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        TodaySecondaryActionButton(
            label = if (records?.morning == null) "记录" else "修改",
            onClick = onMorning,
            modifier = Modifier.testTag("morning-action"),
        )
    }
    Spacer(Modifier.height(16.dp))

    val state = records?.effectiveStatus
    val largeText = LocalDensity.current.fontScale >= 1.3f
    val firstPain = state?.pain?.maxByOrNull { it.severity }
    FlowRow(
        Modifier.fillMaxWidth().testTag("morning-status-values"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = if (largeText) 1 else 4,
    ) {
        StatusValue(
            label = "睡眠",
            value = state?.sleepDurationMinutes?.let(::formatSleep) ?: "未记录",
            modifier = (if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1f)).heightIn(min = 72.dp),
            icon = Icons.Rounded.Bedtime,
            iconColor = MaterialTheme.colorScheme.primary,
            showDivider = !largeText,
        )
        StatusValue(
            label = "精力",
            value = state?.energy?.let { "$it/5" } ?: "未记录",
            modifier = (if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1f)).heightIn(min = 72.dp),
            emphasis = statusColor(state?.energy, higherIsBetter = true),
            icon = Icons.Rounded.Bolt,
            iconColor = Color(0xFF93AD45),
            showDivider = !largeText,
        )
        StatusValue(
            label = "疲劳",
            value = state?.fatigue?.let { "$it/5" } ?: "未记录",
            modifier = (if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1f)).heightIn(min = 72.dp),
            emphasis = statusColor(state?.fatigue, higherIsBetter = false),
            icon = Icons.Rounded.WaterDrop,
            iconColor = MaterialTheme.colorScheme.error,
            showDivider = !largeText,
        )
        StatusValue(
            label = firstPain?.region?.displayName ?: "身体感受",
            value = firstPain?.let { "${it.severity}/5" } ?: when (state?.pain) {
                null -> "未记录"
                else -> "正常"
            },
            modifier = (if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1f)).heightIn(min = 72.dp),
            emphasis = when {
                firstPain != null -> MaterialTheme.colorScheme.error
                state?.pain != null -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
            iconColor = if (firstPain != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
    state?.pain?.takeIf { it.isNotEmpty() }?.let { pain ->
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("morning-pain-alert"),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.38f),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                "需要留意 · ${pain.joinToString("、") { "${it.region.displayName} ${it.severity}/5" }}",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    val measurements = records?.metric?.takeIf { it.hasMeasurements }
    measurements?.let { metric ->
        Spacer(Modifier.height(14.dp))
        val values = buildList {
            metric.weightKg?.let { add("体重" to "%.1f kg".format(it)) }
            metric.bodyFatPct?.let { add("体脂" to "%.1f%%".format(it)) }
            metric.muscleKg?.let { add("肌肉" to "%.1f kg".format(it)) }
            metric.waistCm?.let { add("腰围" to "%.1f cm".format(it)) }
        }
        MorningMeasurementSummary(
            values = values,
            largeText = largeText,
            preWorkoutRecorded = records.preWorkout != null,
            onPreWorkout = onPreWorkout,
        )
    }
    if (records?.morning != null && measurements == null) {
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        PreWorkoutActionRow(
            recorded = records.preWorkout != null,
            onClick = onPreWorkout,
        )
    }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MorningMeasurementSummary(
    values: List<Pair<String, String>>,
    largeText: Boolean,
    preWorkoutRecorded: Boolean,
    onPreWorkout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("morning-measurements-summary"),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(34.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.MonitorWeight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                    Column {
                        Text("晨间测量", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("今天记录的身体数据", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Text("${values.size} 项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                maxItemsInEachRow = if (largeText) 1 else 2,
            ) {
                values.forEach { (label, value) ->
                    Column(
                        modifier = (if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1f))
                            .padding(horizontal = 5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreWorkoutActionRow(
                recorded = preWorkoutRecorded,
                onClick = onPreWorkout,
            )
    }
}

@Composable
private fun PreWorkoutActionRow(
    recorded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("训练前状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (recorded) "已补充训练前的身体变化" else "身体有变化时在这里补充",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        TodaySecondaryActionButton(
            label = if (recorded) "修改" else "记录",
            onClick = onClick,
            modifier = Modifier.testTag("pre-workout-action"),
        )
    }
}

@Composable
private fun StatusValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector,
    iconColor: Color,
    showDivider: Boolean = false,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier
            .drawBehind {
                if (showDivider) {
                    drawLine(
                        color = dividerColor,
                        start = Offset(size.width, 4.dp.toPx()),
                        end = Offset(size.width, size.height - 4.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }
            .padding(horizontal = 5.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = iconColor)
        Spacer(Modifier.height(9.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = emphasis)
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun statusColor(value: Int?, higherIsBetter: Boolean): androidx.compose.ui.graphics.Color {
    if (value == null) return MaterialTheme.colorScheme.onSurface
    val favorable = if (higherIsBetter) value >= 4 else value <= 1
    val unfavorable = if (higherIsBetter) value <= 2 else value >= 4
    return when {
        favorable -> MaterialTheme.colorScheme.primary
        unfavorable -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
}

private fun formatSleep(minutes: Int): String = "${minutes / 60}时${minutes % 60}分"
