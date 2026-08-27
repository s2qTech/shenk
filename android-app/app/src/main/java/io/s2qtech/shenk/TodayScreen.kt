package io.s2qtech.shenk

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.model.GuidanceSource
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

private enum class TodaySheet { MORNING, PRE_WORKOUT, RECORD_DAY, REMINDERS, CONNECTION, DAILY_REVIEW, SETTINGS, AI_SETTINGS, DATA_BACKUP }

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
    val dailyReviewRepository = remember(context) {
        (context.applicationContext as ShenkApplication).dailyReviewRepository
    }
    val dailyReviewState by dailyReviewRepository.observe(date).collectAsState(
        initial = io.s2qtech.shenk.sync.DailyReviewState(),
    )
    var reminders by remember { mutableStateOf(ReminderSettings()) }
    var connection by remember { mutableStateOf(CloudConnectionState(false, "")) }
    var connectionBusy by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
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
        TodaySheet.MORNING -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
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
        TodaySheet.PRE_WORKOUT -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
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
        TodaySheet.REMINDERS -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
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
        TodaySheet.RECORD_DAY -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
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
        TodaySheet.CONNECTION -> ModalBottomSheet(onDismissRequest = { if (!connectionBusy) sheet = null }) {
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
        TodaySheet.DAILY_REVIEW -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            DailyReviewSheet(
                date = date,
                repository = dailyReviewRepository,
                state = dailyReviewState,
                onQueued = { DailyReviewScheduler.enqueue(context) },
                onOpenAiSettings = { sheet = TodaySheet.AI_SETTINGS },
                onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
            )
        }
        TodaySheet.SETTINGS -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            AppSettingsSheet(
                onReminders = { sheet = TodaySheet.REMINDERS },
                onAiService = { sheet = TodaySheet.AI_SETTINGS },
                onBackup = { sheet = TodaySheet.DATA_BACKUP },
            )
        }
        TodaySheet.AI_SETTINGS -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            AiProviderSettingsSheet(
                repository = dailyReviewRepository,
                onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
            )
        }
        TodaySheet.DATA_BACKUP -> ModalBottomSheet(onDismissRequest = { if (!backupBusy) sheet = null }) {
            DataBackupSheet(
                busy = backupBusy,
                onExport = { exportBackupLauncher.launch("shenk-business-${LocalDate.now()}.json") },
                onImport = { importBackupLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
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
                .padding(start = 22.dp, top = 16.dp, end = 22.dp, bottom = 18.dp),
        ) {
            Column {
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(22.dp))

            if (!cloudConfigured) {
                CloudSetupPrompt(onClick = onConnect)
                Spacer(Modifier.height(18.dp))
            }

            val guidance = records?.guidance
            if (guidance == null) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text("正在读取今天…", color = MaterialTheme.colorScheme.secondary)
                }
            } else {
                GuidanceBlock(
                    guidance = guidance,
                    dailyReviewState = dailyReviewState,
                    onTraining = onTraining,
                    onRecordDay = onRecordDay,
                    onDailyReview = onDailyReview,
                )
            }
            Spacer(Modifier.height(26.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))

            MorningStatusSection(
                records = records,
                onMorning = onMorning,
                onPreWorkout = onPreWorkout,
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
    dailyReviewState: io.s2qtech.shenk.sync.DailyReviewState,
    onTraining: () -> Unit,
    onRecordDay: () -> Unit,
    onDailyReview: () -> Unit,
) {
    val source = when (guidance.source) {
        GuidanceSource.ACTUAL -> "今日已完成"
        GuidanceSource.FORMAL_PLAN -> "今日计划"
        GuidanceSource.LOCAL_SUGGESTION -> "本地建议"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = when (guidance.source) {
            GuidanceSource.ACTUAL -> MaterialTheme.colorScheme.primaryContainer
            GuidanceSource.FORMAL_PLAN -> MaterialTheme.colorScheme.secondaryContainer
            GuidanceSource.LOCAL_SUGGESTION -> MaterialTheme.colorScheme.tertiaryContainer
        },
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 21.dp)) {
            Text(source, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(9.dp))
            Text(guidance.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            guidance.estimatedMinutes?.let {
                Spacer(Modifier.height(3.dp))
                Text("约 $it 分钟", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }
            guidance.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (TodayPrimaryActionResolver.resolve(guidance)) {
                TodayPrimaryAction.NONE -> Unit
                TodayPrimaryAction.OPEN_TIMER -> {
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = onTraining,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("today-open-training"),
                    ) { Text("进入训练") }
                }
                TodayPrimaryAction.RECORD_DAY -> {
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = onRecordDay,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("today-record-day"),
                    ) { Text("填写今日情况") }
                }
            }

            when {
                dailyReviewState.review != null -> {
                    val review = requireNotNull(dailyReviewState.review)
                    Spacer(Modifier.height(22.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    Spacer(Modifier.height(18.dp))
                    Text("教练简评", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(6.dp))
                    Text(review.conclusion, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onDailyReview, modifier = Modifier.align(Alignment.End)) {
                        Text("查看完整简评")
                    }
                }
                dailyReviewState.jobState in setOf("PENDING", "RUNNING", "AWAITING_SERVER") -> {
                    Spacer(Modifier.height(22.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Column {
                            Text("正在生成今日简评", fontWeight = FontWeight.SemiBold)
                            Text("完成后会自动出现在这里", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                dailyReviewState.jobState in setOf("RETRY", "FAILED") -> {
                    Spacer(Modifier.height(22.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    Spacer(Modifier.height(16.dp))
                    Column {
                        Text("简评暂未完成", fontWeight = FontWeight.SemiBold)
                        Text(
                            dailyReviewFailureMessage(
                                dailyReviewState.jobError,
                                dailyReviewState.jobState == "RETRY",
                            ),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        TextButton(onClick = onDailyReview, modifier = Modifier.align(Alignment.End)) {
                            Text("查看并重试")
                        }
                    }
                }
                else -> {
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = onDailyReview, modifier = Modifier.align(Alignment.End)) {
                        Text("生成今日简评")
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("晨起状态", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (records?.morning == null) "今天还没有记录" else "今天身体的起点",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        FilledTonalButton(
            onClick = onMorning,
            modifier = Modifier.testTag("morning-action"),
        ) { Text(if (records?.morning == null) "记录" else "修改") }
    }
    Spacer(Modifier.height(16.dp))

    if (records?.morning == null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("睡眠、精力、疲劳与身体感受", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("不确定的项目可以跳过，缺失值不会被当作正常。", color = MaterialTheme.colorScheme.secondary)
            }
        }
    } else {
        val state = records.effectiveStatus
        val largeText = LocalDensity.current.fontScale >= 1.3f
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = if (largeText) 1 else 3,
        ) {
            StatusValue(
                label = "睡眠",
                value = state.sleepDurationMinutes?.let(::formatSleep) ?: "未记录",
                modifier = (if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1.2f))
                    .heightIn(min = 76.dp),
            )
            StatusValue(
                label = "精力",
                value = state.energy?.let { "$it/5" } ?: "未记录",
                modifier = (if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1f))
                    .heightIn(min = 76.dp),
                emphasis = statusColor(state.energy, higherIsBetter = true),
            )
            StatusValue(
                label = "疲劳",
                value = state.fatigue?.let { "$it/5" } ?: "未记录",
                modifier = (if (largeText) Modifier.fillMaxWidth() else Modifier.weight(1f))
                    .heightIn(min = 76.dp),
                emphasis = statusColor(state.fatigue, higherIsBetter = false),
            )
        }
        Spacer(Modifier.height(14.dp))
        val pain = state.pain
        val painText = when {
            pain == null -> "身体感受未记录"
            pain.isEmpty() -> "身体没有疼痛异常"
            else -> pain.joinToString("、") { "${it.region.displayName} ${it.severity}/5" }
        }
        Text("身体感受", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
        Text(
            painText,
            style = MaterialTheme.typography.titleMedium,
            color = if (pain?.isNotEmpty() == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
    records?.metric?.takeIf { it.hasMeasurements }?.let { metric ->
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(12.dp))
        val values = buildList {
            metric.weightKg?.let { add("体重" to "%.1f kg".format(it)) }
            metric.bodyFatPct?.let { add("体脂" to "%.1f%%".format(it)) }
            metric.muscleKg?.let { add("肌肉" to "%.1f kg".format(it)) }
            metric.waistCm?.let { add("腰围" to "%.1f cm".format(it)) }
        }
        Column(Modifier.fillMaxWidth()) {
            Text("晨间测量", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
            Text(
                values.joinToString(" · ") { (label, value) -> "$label $value" },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
    if (records?.morning != null) {
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onPreWorkout) {
                Text(if (records.preWorkout == null) "训练前有变化" else "调整训练前状态")
            }
        }
    }
}

@Composable
private fun StatusValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = emphasis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
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
