package io.s2qtech.shenk

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.model.GuidanceSource
import io.s2qtech.shenk.sync.TodayRecordRepository
import io.s2qtech.shenk.sync.TodayRecords
import io.s2qtech.shenk.sync.SyncScheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class TodaySheet { MORNING, PRE_WORKOUT, REMINDERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayRoute(
    repository: TodayRecordRepository,
    reminderStore: ReminderSettingsStore,
) {
    val date = remember { LocalDate.now() }
    val records by repository.observe(date).collectAsState(initial = null)
    var sheet by remember { mutableStateOf<TodaySheet?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var reminders by remember { mutableStateOf(ReminderSettings()) }
    LaunchedEffect(Unit) { reminders = reminderStore.settings.first() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        TodayScreen(
            records = records,
            date = date,
            modifier = Modifier.padding(innerPadding),
            onMorning = { sheet = TodaySheet.MORNING },
            onPreWorkout = { sheet = TodaySheet.PRE_WORKOUT },
            onReminders = { sheet = TodaySheet.REMINDERS },
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
            ReminderSettingsSheet(
                settings = reminders,
                onSave = { value ->
                    scope.launch {
                        reminderStore.save(value)
                        reminders = value
                        sheet = null
                        if ((value.morningEnabled || value.middayEnabled) &&
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
    onReminders: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            FilledTonalButton(onClick = onReminders, contentPadding = PaddingValues(horizontal = 16.dp)) {
                Text("提醒")
            }
        }
        Spacer(Modifier.height(34.dp))

        AnimatedContent(records?.guidance, label = "today-guidance") { guidance ->
            if (guidance == null) {
                Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text("正在读取今天…", color = MaterialTheme.colorScheme.secondary)
                }
            } else {
                GuidanceBlock(guidance)
            }
        }
        Spacer(Modifier.height(30.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(22.dp))

        SectionHeader(
            title = "晨起状态",
            action = if (records?.morning == null) "记录" else "调整",
            onAction = onMorning,
        )
        Spacer(Modifier.height(14.dp))
        MorningSummary(records)
        Spacer(Modifier.height(26.dp))

        if (records?.morning != null) {
            SectionHeader(
                title = "训练前",
                action = if (records.preWorkout == null) "有变化" else "已补充",
                onAction = onPreWorkout,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = records.preWorkout?.let { "已用训练前感受更新今天的状态判断。" }
                    ?: "晨起后如有疲劳或疼痛变化，再补充一次即可。",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = "缺失的数据会保持缺失，不会被当作正常或休息。",
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun GuidanceBlock(guidance: io.s2qtech.shenk.model.TodayGuidance) {
    val source = when (guidance.source) {
        GuidanceSource.ACTUAL -> "今日已完成"
        GuidanceSource.FORMAL_PLAN -> "今日计划"
        GuidanceSource.LOCAL_SUGGESTION -> "离线建议"
    }
    Surface(
        color = when (guidance.source) {
            GuidanceSource.ACTUAL -> MaterialTheme.colorScheme.primaryContainer
            GuidanceSource.FORMAL_PLAN -> MaterialTheme.colorScheme.secondaryContainer
            GuidanceSource.LOCAL_SUGGESTION -> MaterialTheme.colorScheme.tertiaryContainer
        },
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(source, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(14.dp))
            Text(guidance.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            guidance.estimatedMinutes?.let {
                Text("约 $it 分钟", style = MaterialTheme.typography.titleMedium)
            }
            guidance.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun MorningSummary(records: TodayRecords?) {
    if (records?.morning == null) {
        Text(
            text = "睡眠、精力和身体感受还未记录",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = "不确定的项目可以直接跳过。",
            color = MaterialTheme.colorScheme.outline,
        )
        return
    }
    val state = records.effectiveStatus
    val sleep = state.sleepDurationMinutes?.let { "睡眠 ${it / 60}小时${it % 60}分" } ?: "睡眠未记录"
    val energy = state.energy?.let { "精力 $it/5" } ?: "精力未记录"
    val fatigue = state.fatigue?.let { "疲劳 $it/5" } ?: "疲劳未记录"
    Text("$sleep · $energy · $fatigue", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    val pain = state.pain
    Text(
        text = when {
            pain == null -> "身体感受未记录"
            pain.isEmpty() -> "没有疼痛异常"
            else -> pain.joinToString("、") { "${it.region.displayName} ${it.severity}/5" }
        },
        color = MaterialTheme.colorScheme.secondary,
    )
    records.metric?.let { metric ->
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            metric.weightKg?.let { MetricText("体重", "%.1f kg".format(it)) }
            metric.bodyFatPct?.let { MetricText("体脂", "%.1f%%".format(it)) }
            metric.muscleKg?.let { MetricText("肌肉", "%.1f kg".format(it)) }
        }
    }
}

@Composable
private fun MetricText(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
    }
}
