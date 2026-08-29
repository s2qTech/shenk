package io.s2qtech.shenk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.model.BodyTrends
import io.s2qtech.shenk.model.MetricKind
import io.s2qtech.shenk.model.MetricTrend
import io.s2qtech.shenk.sync.CalendarRecordRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun DataScreen(
    repository: CalendarRecordRepository,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val trends by repository.observeBodyTrends(today).collectAsState(initial = null)
    var selectedKind by remember { mutableStateOf(MetricKind.WEIGHT) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, top = 2.dp, end = 22.dp, bottom = 28.dp)
            .testTag("data-screen"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SheetHeader("数据", "查看体重、体脂、肌肉和腰围的时间线图。")
        MetricTabs(selected = selectedKind, onSelect = { selectedKind = it })
        val value = trends
        if (value == null) {
            ShenkStatePanel(
                title = "正在读取身体趋势",
                message = "先读取本机 30 天记录；如有同步更新，会自动出现在这里。",
                tone = ShenkStateTone.PROGRESS,
                modifier = Modifier.fillMaxWidth().testTag("data-loading"),
            )
        } else {
            BodyMetricChart(value.trend(selectedKind), selectedKind.chartColor())
        }
    }
}

@Composable
private fun MetricTabs(
    selected: MetricKind,
    onSelect: (MetricKind) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MetricKind.entries.forEach { kind ->
                val active = kind == selected
                Surface(
                    onClick = { onSelect(kind) },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .semantics {
                            role = Role.Tab
                            this.selected = active
                        }
                        .testTag("data-metric-${kind.name.lowercase()}"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(kind.tabLabel(), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyMetricChart(trend: MetricTrend, lineColor: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("body-metric-chart"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("当前", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Text(
                        trend.latest?.let { "%.1f".format(it.value) } ?: "未记录",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (trend.latest != null) {
                        Text(trend.kind.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                trend.change?.let { change ->
                    Text(
                        "%+.1f %s".format(change, trend.kind.unit),
                        style = MaterialTheme.typography.labelLarge,
                        color = trend.changeColor(change),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (trend.points.size < 2) {
                ShenkStatePanel(
                    title = if (trend.points.isEmpty()) "还没有记录" else "已有一次记录",
                    message = if (trend.points.isEmpty()) {
                        "完成一次晨间测量后，会从本机数据开始绘制趋势。"
                    } else {
                        "再记录一次后，就能显示这项指标的变化。"
                    },
                    tone = ShenkStateTone.NEUTRAL,
                    compact = true,
                    contained = false,
                    modifier = Modifier.fillMaxWidth().testTag("trend-${trend.kind.name.lowercase()}-empty"),
                )
            } else {
                TrendCanvas(trend, lineColor)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(trend.points.first().date.chartDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(trend.points.last().date.chartDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun TrendCanvas(trend: MetricTrend, lineColor: Color) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLow
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(188.dp)
            .semantics {
                contentDescription = "${trend.kind.displayName}最近 30 天折线图，共 ${trend.points.size} 个记录点"
            },
    ) {
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val values = trend.points.map { it.value }
        val rawMin = values.min()
        val rawMax = values.max()
        val padding = maxOf((rawMax - rawMin) * 0.16, 0.35)
        val min = rawMin - padding
        val range = rawMax - rawMin + padding * 2
        val stepX = size.width / (trend.points.size - 1)
        val points = trend.points.mapIndexed { index, point ->
            Offset(index * stepX, size.height - ((point.value - min) / range).toFloat() * size.height)
        }
        for (index in 1 until points.size) {
            drawLine(lineColor, points[index - 1], points[index], strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
        points.forEachIndexed { index, point ->
            if (index == points.lastIndex) {
                drawCircle(surfaceColor, 7.dp.toPx(), point)
                drawCircle(lineColor, 5.dp.toPx(), point)
            } else {
                drawCircle(lineColor, 2.5.dp.toPx(), point)
            }
        }
    }
}

private fun BodyTrends.trend(kind: MetricKind): MetricTrend = when (kind) {
    MetricKind.WEIGHT -> weight
    MetricKind.BODY_FAT -> bodyFat
    MetricKind.MUSCLE -> muscle
    MetricKind.WAIST -> waist
}

private fun MetricKind.tabLabel(): String = when (this) {
    MetricKind.WEIGHT -> "体重"
    MetricKind.BODY_FAT -> "体脂"
    MetricKind.MUSCLE -> "肌肉"
    MetricKind.WAIST -> "腰围"
}

private fun MetricKind.chartColor(): Color = when (this) {
    MetricKind.WEIGHT -> Color(0xFF4F7A61)
    MetricKind.BODY_FAT -> Color(0xFFC58645)
    MetricKind.MUSCLE -> Color(0xFF4C82A6)
    MetricKind.WAIST -> Color(0xFF7867A8)
}

@Composable
private fun MetricTrend.changeColor(change: Double): Color {
    if (abs(change) < 0.05) return MaterialTheme.colorScheme.secondary
    val lowerIsBetter = kind in setOf(MetricKind.WEIGHT, MetricKind.BODY_FAT, MetricKind.WAIST)
    val favorable = if (lowerIsBetter) change < 0 else change > 0
    return if (favorable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
}

private fun LocalDate.chartDate(): String = format(DateTimeFormatter.ofPattern("M/d"))
