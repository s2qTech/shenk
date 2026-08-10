package io.s2qtech.shenk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SelfImprovement
import io.s2qtech.shenk.model.CalendarDay
import io.s2qtech.shenk.model.GuidanceSource
import io.s2qtech.shenk.model.DailyMetric
import io.s2qtech.shenk.model.MetricChangeDirection
import io.s2qtech.shenk.model.MetricKind
import io.s2qtech.shenk.model.RecordEditPolicy
import io.s2qtech.shenk.model.TodayGuidance
import io.s2qtech.shenk.model.TrainingLog
import io.s2qtech.shenk.sync.CalendarDayDetails
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.DailyReviewState
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
) {
    val today = remember { LocalDate.now() }
    val rangeStart = remember(today) { today.minusMonths(6).withDayOfMonth(1) }
    val rangeEnd = remember(today) { today.plusMonths(6).withDayOfMonth(1).plusMonths(1).minusDays(1) }
    val days by repository.observeRange(rangeStart, rangeEnd).collectAsState(initial = emptyList())
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var editing by remember { mutableStateOf<TrainingLog?>(null) }
    var creating by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    var configuringAi by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val dailyReviewRepository = remember(context) {
        (context.applicationContext as ShenkApplication).dailyReviewRepository
    }
    val listState = rememberLazyListState()
    val visibleDate by remember(days) {
        derivedStateOf { days.getOrNull(listState.firstVisibleItemIndex)?.date ?: today }
    }
    val visibleMonth by remember {
        derivedStateOf { YearMonth.from(visibleDate) }
    }
    val todayVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { item -> item.key == today }
        }
    }
    val agendaLaidOut by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount > 0 }
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
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(116.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
            AnimatedVisibility(
                visible = agendaLaidOut && !todayVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.94f),
            ) {
                ThumbDockButton(
                    onClick = {
                        scope.launch {
                            val todayIndex = days.indexOfFirst { it.date == today }
                            if (todayIndex >= 0) {
                                listState.animateScrollToItem((todayIndex - 2).coerceAtLeast(0))
                            }
                        }
                    },
                    modifier = Modifier.padding(bottom = 14.dp).testTag("calendar-return-today"),
                    icon = Icons.Rounded.CalendarToday,
                    label = "今天",
                )
            }
        }
    }

    selectedDate?.let { date ->
        val details by repository.observeDay(date).collectAsState(initial = null)
        val reviewState by dailyReviewRepository.observe(date).collectAsState(initial = DailyReviewState())
        ModalBottomSheet(
            onDismissRequest = {
                selectedDate = null
                editing = null
                creating = false
                reviewing = false
                configuringAi = false
            },
        ) {
            when {
                configuringAi -> Column {
                    TextButton(
                        onClick = {
                            configuringAi = false
                            reviewing = true
                        },
                        modifier = Modifier.padding(horizontal = 14.dp),
                    ) {
                        Text("返回当日简评")
                    }
                    AiProviderSettingsSheet(
                        repository = dailyReviewRepository,
                        onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                    )
                }
                reviewing -> DailyReviewSheet(
                    date = date,
                    repository = dailyReviewRepository,
                    state = reviewState,
                    onQueued = { DailyReviewScheduler.enqueue(context) },
                    onOpenAiSettings = {
                        reviewing = false
                        configuringAi = true
                    },
                    onMessage = { message -> scope.launch { snackbar.showSnackbar(message) } },
                    onBack = { reviewing = false },
                )
                editing != null -> TrainingLogEditorSheet(
                    date = date,
                    existing = editing,
                    readOnly = !RecordEditPolicy.canEdit(date, today),
                    onSave = { log ->
                        scope.launch {
                            repository.saveTrainingLog(log)
                            val reviewRepository = (context.applicationContext as ShenkApplication).dailyReviewRepository
                            val review = reviewRepository.enqueue(date, allowIncomplete = false)
                            if (review.queued) DailyReviewScheduler.enqueue(context)
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
                            val reviewRepository = (context.applicationContext as ShenkApplication).dailyReviewRepository
                            val review = reviewRepository.enqueue(date, allowIncomplete = false)
                            if (review.queued) DailyReviewScheduler.enqueue(context)
                            SyncScheduler(context).enqueue()
                            creating = false
                            snackbar.showSnackbar("训练记录已保存在本机")
                        }
                    },
                )
                else -> DayDetails(
                    details = details,
                    reviewState = reviewState,
                    canEdit = RecordEditPolicy.canEdit(date, today),
                    canReview = !date.isAfter(today),
                    onEdit = { editing = it },
                    onCreate = { creating = true },
                    onOpenReview = { reviewing = true },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgendaDayRow(
    day: CalendarDay,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val isToday = day.date == today
    val source = day.guidance.source
    val accent = trainingColor(day.guidance.trainingType)
    val suggestionStripe = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag("calendar-day-${day.date}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateRail(day.date, isToday)
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = if (day.bodyMetrics.isEmpty()) 104.dp else 126.dp)
                .clickable(onClick = onClick),
        ) {
            if (source == GuidanceSource.LOCAL_SUGGESTION) {
                Canvas(Modifier.fillMaxSize()) {
                    val gap = 14.dp.toPx()
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(
                            color = suggestionStripe,
                            start = Offset(x, size.height),
                            end = Offset(x + size.height, 0f),
                            strokeWidth = 3.dp.toPx(),
                        )
                        x += gap
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (day.bodyMetrics.isEmpty()) 104.dp else 126.dp)
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(if (day.bodyMetrics.isEmpty()) 62.dp else 82.dp)
                        .background(
                            color = if (source == GuidanceSource.LOCAL_SUGGESTION) {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                            } else if (source == GuidanceSource.FORMAL_PLAN) {
                                accent.copy(alpha = 0.72f)
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
                    modifier = Modifier.size(32.dp),
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
                    if (source == GuidanceSource.ACTUAL) {
                        day.guidance.estimatedMinutes?.let { minutes ->
                            Text("$minutes 分钟", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    day.guidance.note
                        ?.takeIf { source != GuidanceSource.LOCAL_SUGGESTION && it.isNotBlank() }
                        ?.let { note ->
                            Text(
                                note,
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    if (day.bodyMetrics.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            day.bodyMetrics.forEach { metric -> DailyMetricValue(metric) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyMetricValue(metric: DailyMetric) {
    val isImprovement = when (metric.kind) {
        MetricKind.WEIGHT, MetricKind.BODY_FAT, MetricKind.WAIST ->
            metric.changeDirection == MetricChangeDirection.DECREASED
        MetricKind.MUSCLE -> metric.changeDirection == MetricChangeDirection.INCREASED
    }
    val isWorse = metric.changeDirection != null &&
        metric.changeDirection != MetricChangeDirection.UNCHANGED &&
        !isImprovement
    val color = when {
        isImprovement -> Color(0xFF3C8B60)
        isWorse -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }
    val icon = when (metric.changeDirection) {
        MetricChangeDirection.INCREASED -> Icons.Rounded.ArrowUpward
        MetricChangeDirection.DECREASED -> Icons.Rounded.ArrowDownward
        MetricChangeDirection.UNCHANGED -> Icons.Rounded.Remove
        null -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${metricLabel(metric.kind)} ${"%.1f".format(metric.value)}${metric.kind.unit}",
            color = color,
            style = MaterialTheme.typography.labelMedium,
        )
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color,
            )
        }
    }
}

private fun metricLabel(kind: MetricKind): String = when (kind) {
    MetricKind.WEIGHT -> "体重"
    MetricKind.BODY_FAT -> "体脂"
    MetricKind.MUSCLE -> "肌肉"
    MetricKind.WAIST -> "腰围"
}

@Composable
private fun DateRail(
    date: LocalDate,
    isToday: Boolean,
) {
    Surface(
        modifier = Modifier.width(62.dp),
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
    reviewState: DailyReviewState,
    canEdit: Boolean,
    canReview: Boolean,
    onEdit: (TrainingLog) -> Unit,
    onCreate: () -> Unit,
    onOpenReview: () -> Unit,
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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                GuidanceSummary(
                    guidance = details.guidance,
                    onEdit = details.actualLogs.firstOrNull()?.takeIf { canEdit }?.let { log ->
                        { onEdit(log) }
                    },
                )
                if (details.bodyMetrics.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "身体数据",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        details.bodyMetrics.forEach { metric -> DailyMetricValue(metric) }
                    }
                }
                if (canReview || reviewState.review != null) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(16.dp))
                    CalendarReviewSummary(
                        state = reviewState,
                        onOpenReview = onOpenReview,
                    )
                }
            }
        }
        if (canEdit && details.actualLogs.isEmpty()) {
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                Text("补一条训练记录")
            }
        } else if (!canEdit && details.actualLogs.isEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("训练记录已超过可修正范围。", color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun CalendarReviewSummary(
    state: DailyReviewState,
    onOpenReview: () -> Unit,
) {
    val generating = state.jobState in setOf("PENDING", "RUNNING")
    val review = state.review
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text("当日简评", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            when {
                review != null -> {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        review.conclusion,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (generating) {
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("正在根据最新记录更新", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                generating -> {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("正在生成，完成后会自动更新", color = MaterialTheme.colorScheme.secondary)
                    }
                }
                state.jobState in setOf("RETRY", "FAILED") -> {
                    Spacer(Modifier.height(5.dp))
                    Text("简评暂未完成，可以重新生成。", color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    Spacer(Modifier.height(5.dp))
                    Text("还没有这一天的教练简评。", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        TextButton(onClick = onOpenReview) {
            Text(
                when {
                    review != null -> "查看"
                    state.jobState in setOf("RETRY", "FAILED") -> "重试"
                    else -> "生成"
                },
            )
        }
    }
}

@Composable
private fun GuidanceSummary(
    guidance: TodayGuidance,
    onEdit: (() -> Unit)? = null,
) {
    val label = when (guidance.source) {
        GuidanceSource.ACTUAL -> "实际完成"
        GuidanceSource.FORMAL_PLAN -> "正式计划"
        GuidanceSource.LOCAL_SUGGESTION -> "兜底建议"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text(guidance.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            guidance.estimatedMinutes?.let { Text("约 $it 分钟", color = MaterialTheme.colorScheme.secondary) }
            guidance.note?.takeIf(String::isNotBlank)?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.secondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        onEdit?.let {
            IconButton(onClick = it) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "修正训练记录",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
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
