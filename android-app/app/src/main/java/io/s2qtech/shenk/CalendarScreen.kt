package io.s2qtech.shenk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.SelfImprovement
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
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    repository: CalendarRecordRepository,
    onToday: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val rangeStart = remember(today) { today.minusMonths(6).withDayOfMonth(1) }
    val rangeEnd = remember(today) { today.plusMonths(6).withDayOfMonth(1).plusMonths(1).minusDays(1) }
    val days by repository.observeRange(rangeStart, rangeEnd).collectAsState(initial = emptyList())
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var editing by remember { mutableStateOf<TrainingLog?>(null) }
    var creating by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()
    val visibleDate by remember(days) {
        derivedStateOf { days.getOrNull(listState.firstVisibleItemIndex)?.date ?: today }
    }
    val visibleMonth by remember {
        derivedStateOf { YearMonth.from(visibleDate) }
    }
    val weekDistance by remember {
        derivedStateOf {
            val thisWeek = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            val visibleWeek = visibleDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
            ChronoUnit.WEEKS.between(thisWeek, visibleWeek).toInt()
        }
    }

    LaunchedEffect(days.size) {
        if (days.isNotEmpty()) {
            val todayIndex = days.indexOfFirst { it.date == today }.coerceAtLeast(0)
            listState.scrollToItem((todayIndex - 2).coerceAtLeast(0))
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("calendar-screen"),
        ) {
            Column(Modifier.fillMaxSize()) {
                CalendarHeader(month = visibleMonth)
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("calendar-agenda"),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 6.dp,
                        bottom = 92.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        items = days,
                        key = { it.date },
                    ) { day ->
                        AgendaDayRow(day = day, today = today) {
                            selectedDate = day.date
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = listState.isScrollInProgress,
                modifier = Modifier.align(Alignment.Center).testTag("calendar-week-distance"),
                enter = fadeIn() + scaleIn(initialScale = 0.88f),
                exit = fadeOut() + scaleOut(targetScale = 0.94f),
            ) {
                WeekDistanceHud(weekDistance)
            }
            ExtendedFloatingActionButton(
                onClick = onToday,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .testTag("calendar-return-today"),
                icon = { Icon(Icons.Rounded.CalendarToday, contentDescription = null) },
                text = { Text("今天") },
            )
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("月历", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "${month.year}年${month.monthValue}月",
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun WeekDistanceHud(distance: Int) {
    val amount = kotlin.math.abs(distance)
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (distance == 0) "本周" else amount.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (distance != 0) {
                Text("周${if (distance < 0) "前" else "后"}", style = MaterialTheme.typography.titleMedium)
                Text("距今", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AgendaDayRow(
    day: CalendarDay,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val isToday = day.date == today
    val source = day.guidance.source
    val accent = trainingColor(day.guidance.trainingType)
    val container = when (source) {
        GuidanceSource.ACTUAL -> accent.copy(alpha = 0.17f)
        GuidanceSource.FORMAL_PLAN -> accent.copy(alpha = 0.075f)
        GuidanceSource.LOCAL_SUGGESTION -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    }
    val stripeColor = when (source) {
        GuidanceSource.ACTUAL -> Color.Transparent
        GuidanceSource.FORMAL_PLAN -> accent.copy(alpha = 0.11f)
        GuidanceSource.LOCAL_SUGGESTION -> MaterialTheme.colorScheme.outline.copy(alpha = 0.055f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .testTag("calendar-day-${day.date}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateRail(day.date, isToday)
        Spacer(Modifier.width(10.dp))
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            color = container,
            shape = RoundedCornerShape(10.dp),
            border = when {
                isToday -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                source == GuidanceSource.ACTUAL -> BorderStroke(1.dp, accent.copy(alpha = 0.34f))
                else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            },
            tonalElevation = if (isToday) 3.dp else 0.dp,
        ) {
            Box {
                if (source != GuidanceSource.ACTUAL) {
                    Canvas(Modifier.fillMaxSize()) {
                        val gap = 16.dp.toPx()
                        var x = -size.height
                        while (x < size.width) {
                            drawLine(
                                color = stripeColor,
                                start = Offset(x, size.height),
                                end = Offset(x + size.height, 0f),
                                strokeWidth = 3.dp.toPx(),
                            )
                            x += gap
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(54.dp)
                            .background(
                                color = if (source == GuidanceSource.LOCAL_SUGGESTION) {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                                } else {
                                    accent
                                },
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        imageVector = trainingIcon(day.guidance.trainingType),
                        contentDescription = day.guidance.title,
                        modifier = Modifier.size(30.dp),
                        tint = if (source == GuidanceSource.LOCAL_SUGGESTION) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            accent
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                day.guidance.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (source == GuidanceSource.ACTUAL) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (source) {
                                    GuidanceSource.ACTUAL -> "记录"
                                    GuidanceSource.FORMAL_PLAN -> "计划"
                                    GuidanceSource.LOCAL_SUGGESTION -> "建议"
                                },
                                color = if (source == GuidanceSource.LOCAL_SUGGESTION) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    accent
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        day.guidance.estimatedMinutes?.let { minutes ->
                            Text("约 $minutes 分钟", color = MaterialTheme.colorScheme.secondary)
                        }
                        day.guidance.note?.takeIf(String::isNotBlank)?.let { note ->
                            Text(
                                note,
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateRail(
    date: LocalDate,
    isToday: Boolean,
) {
    Surface(
        modifier = Modifier.width(58.dp),
        color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                date.format(DateTimeFormatter.ofPattern("EEE", Locale.CHINA)),
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
            )
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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

private fun trainingIcon(type: String): ImageVector = when (type) {
    "strength", "travel_strength" -> Icons.Rounded.FitnessCenter
    "quality_walk" -> Icons.AutoMirrored.Rounded.DirectionsRun
    "easy_walk" -> Icons.AutoMirrored.Rounded.DirectionsWalk
    "indoor_cardio" -> Icons.AutoMirrored.Rounded.DirectionsBike
    "recovery", "stretch", "cooldown" -> Icons.Rounded.SelfImprovement
    "warmup", "seat_recovery" -> Icons.Rounded.AccessibilityNew
    "rest" -> Icons.Rounded.Bedtime
    else -> Icons.AutoMirrored.Rounded.DirectionsWalk
}

private fun logSummary(log: TrainingLog): String = buildList {
    log.durationMinutes?.let { add("$it 分") }
    log.distanceKm?.let { add("%.2f km".format(it)) }
    log.averageHeartRate?.let { add("均心 $it") }
}.ifEmpty { listOf("暂无训练数据") }.joinToString(" · ")
