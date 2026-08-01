package io.s2qtech.shenk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.model.MetricKind
import io.s2qtech.shenk.model.MetricTrend
import io.s2qtech.shenk.sync.CalendarRecordRepository
import java.time.LocalDate
import kotlin.math.abs

@Composable
fun DataScreen(
    repository: CalendarRecordRepository,
    onBack: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val trends by repository.observeBodyTrends(today).collectAsState(initial = null)
    Column(
        Modifier
            .fillMaxSize()
            .testTag("data-screen")
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SpaceHeader("数据", "最近 30 天身体变化", onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            trends?.let { value ->
                item { TrendPanel(value.weight, Color(0xFF4F7A61)) }
                item { TrendPanel(value.bodyFat, Color(0xFFC58645)) }
                item { TrendPanel(value.muscle, Color(0xFF4C82A6)) }
                item { WaistSummary(value.waist) }
            } ?: item { Text("正在读取趋势…", modifier = Modifier.padding(20.dp)) }
        }
    }
}

@Composable
private fun SpaceHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.secondary)
        }
        TextButton(onClick = onBack, modifier = Modifier.testTag("space-back")) { Text("返回今天") }
    }
}

@Composable
private fun TrendPanel(trend: MetricTrend, color: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(trend.kind.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("一个月", color = MaterialTheme.colorScheme.outline)
                }
                Column {
                    Text(
                        trend.latest?.let { "%.1f %s".format(it.value, trend.kind.unit) } ?: "未记录",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    trend.change?.let { change ->
                        Text(
                            "%+.1f %s".format(change, trend.kind.unit),
                            color = trend.changeColor(change),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (trend.points.size < 2) {
                Column(Modifier.fillMaxWidth().height(100.dp), verticalArrangement = Arrangement.Center) {
                    Text(if (trend.points.isEmpty()) "还没有数据" else "再记录一次后显示变化", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                TrendCanvas(trend, color)
            }
        }
    }
}

@Composable
private fun TrendCanvas(trend: MetricTrend, color: Color) {
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val values = trend.points.map { it.value }
        val rawMin = values.min()
        val rawMax = values.max()
        val padding = maxOf((rawMax - rawMin) * 0.2, 0.4)
        val min = rawMin - padding
        val max = rawMax + padding
        val range = max - min
        val stepX = if (trend.points.size == 1) 0f else size.width / (trend.points.size - 1)
        val points = trend.points.mapIndexed { index, point ->
            Offset(index * stepX, size.height - ((point.value - min) / range).toFloat() * size.height)
        }
        for (index in 1 until points.size) {
            drawLine(color, points[index - 1], points[index], strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
        points.forEachIndexed { index, point ->
            if (index == points.lastIndex) {
                drawCircle(surfaceColor, 7.dp.toPx(), point)
                drawCircle(color, 5.dp.toPx(), point)
            }
        }
    }
}

@Composable
private fun WaistSummary(trend: MetricTrend) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("腰围", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("辅助观察", color = MaterialTheme.colorScheme.outline)
            }
            Text(
                trend.latest?.let { latest ->
                    val change = trend.change
                    if (change == null || abs(change) < 0.05) "%.1f cm".format(latest.value)
                    else "%.1f cm  %+.1f".format(latest.value, change)
                } ?: "未记录",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
private fun MetricTrend.changeColor(change: Double): Color {
    if (abs(change) < 0.05) return MaterialTheme.colorScheme.secondary
    val lowerIsBetter = kind in setOf(MetricKind.WEIGHT, MetricKind.BODY_FAT, MetricKind.WAIST)
    val favorable = if (lowerIsBetter) change < 0 else change > 0
    return if (favorable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
}
