package io.s2qtech.shenk

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.s2qtech.shenk.model.CalendarDay
import io.s2qtech.shenk.model.GuidanceSource
import io.s2qtech.shenk.model.RecordEditPolicy
import io.s2qtech.shenk.model.TodayGuidance
import io.s2qtech.shenk.model.TrainingLog
import io.s2qtech.shenk.sync.CalendarDayDetails
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.SyncScheduler
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val weekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    repository: CalendarRecordRepository,
    onToday: () -> Unit,
    onRecords: () -> Unit,
    onData: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val calendar by repository.observeMonth(month).collectAsState(initial = null)
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var editing by remember { mutableStateOf<TrainingLog?>(null) }
    var creating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            CalendarHeader(
                month = month,
                onPrevious = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
                onToday = onToday,
                onRecords = onRecords,
                onData = onData,
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                weekdayNames.forEach { name ->
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                calendar?.weeks?.let { weeks ->
                    items(weeks) { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            week.forEach { day ->
                                if (day == null) {
                                    Spacer(Modifier.weight(1f).height(86.dp))
                                } else {
                                    DayTile(
                                        day = day,
                                        isToday = day.date == today,
                                        modifier = Modifier.weight(1f),
                                        onClick = { selectedDate = day.date },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedDate?.let { date ->
        val details by repository.observeDay(date).collectAsState(initial = null)
        ModalBottomSheet(
            onDismissRequest = {
                selectedDate = null
                editing = null
                creating = false
            },
        ) {
            when {
                editing != null -> TrainingLogEditorSheet(
                    date = date,
                    existing = editing,
                    readOnly = !RecordEditPolicy.canEdit(date, today),
                    onSave = { log ->
                        scope.launch {
                            repository.saveTrainingLog(log)
                            SyncScheduler(context).enqueue()
                            editing = null
                            snackbar.showSnackbar("训练记录已保存在本机")
                        }
                    },
                    onDelete = { log ->
                        scope.launch {
                            repository.deleteTrainingLog(log.id)
                            SyncScheduler(context).enqueue()
                            editing = null
                            val result = snackbar.showSnackbar(
                                message = "训练记录已删除",
                                actionLabel = "撤销",
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                repository.restoreTrainingLog(log)
                                SyncScheduler(context).enqueue()
                            }
                        }
                    },
                )
                creating -> TrainingLogEditorSheet(
                    date = date,
                    existing = null,
                    readOnly = false,
                    onSave = { log ->
                        scope.launch {
                            repository.saveTrainingLog(log)
                            SyncScheduler(context).enqueue()
                            creating = false
                            snackbar.showSnackbar("训练记录已保存在本机")
                        }
                    },
                )
                else -> DayDetails(
                    details = details,
                    canEdit = RecordEditPolicy.canEdit(date, today),
                    onEdit = { editing = it },
                    onCreate = { creating = true },
                )
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onRecords: () -> Unit,
    onData: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("月历", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPrevious) { Text("‹") }
                Text("${month.year}年${month.monthValue}月", fontWeight = FontWeight.Medium)
                TextButton(onClick = onNext) { Text("›") }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row {
                TextButton(onClick = onRecords) { Text("记录") }
                TextButton(onClick = onData) { Text("数据") }
            }
            FilledTonalButton(onClick = onToday) { Text("回到今天") }
        }
    }
}

@Composable
private fun DayTile(
    day: CalendarDay,
    isToday: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val source = day.guidance.source
    val baseColor = trainingColor(day.guidance.trainingType)
    val container = when (source) {
        GuidanceSource.ACTUAL -> baseColor.copy(alpha = 0.24f)
        GuidanceSource.FORMAL_PLAN -> baseColor.copy(alpha = 0.11f)
        GuidanceSource.LOCAL_SUGGESTION -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    Surface(
        modifier = modifier.height(86.dp).clickable(onClick = onClick),
        color = container,
        shape = RoundedCornerShape(if (isToday) 18.dp else 12.dp),
        border = when {
            isToday -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            source == GuidanceSource.FORMAL_PLAN -> BorderStroke(1.dp, baseColor.copy(alpha = 0.35f))
            else -> null
        },
        tonalElevation = if (isToday) 3.dp else 0.dp,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(day.date.dayOfMonth.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (day.actualLogs.size > 1) Text("${day.actualLogs.size}次", fontSize = 9.sp)
            }
            Column {
                Text(
                    day.guidance.title,
                    maxLines = 2,
                    lineHeight = 13.sp,
                    fontSize = 11.sp,
                    fontWeight = if (source == GuidanceSource.ACTUAL) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (source == GuidanceSource.LOCAL_SUGGESTION) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                day.guidance.estimatedMinutes?.let { Text("$it 分", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline) }
            }
        }
    }
}

@Composable
private fun DayDetails(
    details: CalendarDayDetails?,
    canEdit: Boolean,
    onEdit: (TrainingLog) -> Unit,
    onCreate: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 8.dp)) {
        if (details == null) {
            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { Text("正在读取…") }
            return@Column
        }
        Text(
            details.date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(18.dp))
        GuidanceSummary(details.guidance)
        if (details.actualLogs.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Text("当天记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            details.actualLogs.forEach { log ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onEdit(log) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(log.displayTitle, fontWeight = FontWeight.Medium)
                            Text(logSummary(log), color = MaterialTheme.colorScheme.secondary)
                        }
                        Text(if (canEdit) "修正" else "查看", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (canEdit) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("补一条训练记录") }
        } else {
            Spacer(Modifier.height(16.dp))
            Text("历史日期仅供查看。", color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GuidanceSummary(guidance: TodayGuidance) {
    val label = when (guidance.source) {
        GuidanceSource.ACTUAL -> "实际完成"
        GuidanceSource.FORMAL_PLAN -> "正式计划"
        GuidanceSource.LOCAL_SUGGESTION -> "兜底建议"
    }
    Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    Text(guidance.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    guidance.estimatedMinutes?.let { Text("约 $it 分钟", color = MaterialTheme.colorScheme.secondary) }
    guidance.note?.takeIf(String::isNotBlank)?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.secondary)
    }
}

private fun trainingColor(type: String): Color = when (type) {
    "strength", "travel_strength" -> Color(0xFF63A76B)
    "quality_walk", "indoor_cardio" -> Color(0xFF6389C9)
    "easy_walk" -> Color(0xFF62AFC2)
    "recovery", "stretch", "warmup", "cooldown" -> Color(0xFFD6A43A)
    "rest" -> Color(0xFF9B9385)
    else -> Color(0xFF6D8F7A)
}

private fun logSummary(log: TrainingLog): String = buildList {
    log.durationMinutes?.let { add("$it 分") }
    log.distanceKm?.let { add("%.2f km".format(it)) }
    log.averageHeartRate?.let { add("均心 $it") }
}.ifEmpty { listOf("暂无训练数据") }.joinToString(" · ")
