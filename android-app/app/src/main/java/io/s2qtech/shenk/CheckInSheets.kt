package io.s2qtech.shenk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.model.BodyMetric
import io.s2qtech.shenk.model.CheckinKind
import io.s2qtech.shenk.model.PainEntry
import io.s2qtech.shenk.model.PainRegion
import io.s2qtech.shenk.model.PainSide
import io.s2qtech.shenk.model.StatusCheckin
import io.s2qtech.shenk.sync.TodayRecords
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

private data class PainDraft(val severity: Int = 1, val side: PainSide = PainSide.UNSPECIFIED)

@Composable
fun MorningCheckInSheet(
    date: LocalDate,
    existing: TodayRecords?,
    onSave: (StatusCheckin, BodyMetric?) -> Unit,
) {
    val previous = existing?.morning
    var sleepMinutes by remember(previous) { mutableStateOf(previous?.sleepDurationMinutes) }
    var deepSleepMinutes by remember(previous) { mutableStateOf(previous?.deepSleepMinutes) }
    var sleepQuality by remember(previous) { mutableStateOf(previous?.sleepQuality) }
    var energy by remember(previous) { mutableStateOf(previous?.energy) }
    var fatigue by remember(previous) { mutableStateOf(previous?.fatigue) }
    var painRecorded by remember(previous) { mutableStateOf(previous?.pain != null) }
    val pain = remember(previous) {
        mutableStateMapOf<PainRegion, PainDraft>().apply {
            previous?.pain?.forEach { put(it.region, PainDraft(it.severity, it.side)) }
        }
    }
    var note by remember(previous) { mutableStateOf(previous?.note.orEmpty()) }
    var weight by remember(existing?.metric) { mutableStateOf(existing?.metric?.weightKg) }
    var bodyFat by remember(existing?.metric) { mutableStateOf(existing?.metric?.bodyFatPct) }
    var muscle by remember(existing?.metric) { mutableStateOf(existing?.metric?.muscleKg) }
    var waist by remember(existing?.metric) { mutableStateOf(existing?.metric?.waistCm) }
    val latest = existing?.latestMetric

    SheetFrame(
        eyebrow = "晨起记录",
        title = "今天身体怎么样？",
        subtitle = "不确定的项目可以留空，不需要事后补齐。",
    ) {
        SheetSection("睡眠") {
            DurationStepper("睡眠时长", sleepMinutes, 15) { sleepMinutes = it }
            DurationStepper("深睡时长", deepSleepMinutes, 15, max = sleepMinutes) { deepSleepMinutes = it }
            ScoreSlider("睡眠感受", sleepQuality, "差", "好") { sleepQuality = it }
        }
        SheetSection("今日状态") {
            ScoreSlider("精力", energy, "低", "高") { energy = it }
            ScoreSlider("疲劳", fatigue, "轻松", "很累", zeroBased = true) { fatigue = it }
        }
        PainEditor(
            recorded = painRecorded,
            pain = pain,
            onRecordedChange = { painRecorded = it },
        )
        SheetSection("身体测量") {
            Text(
                "晨起没有测量就直接跳过。第一次调整会从最近值开始。",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
            NumericStepper("体重", "kg", weight, latest?.weightKg, 0.1) { weight = it }
            NumericStepper("体脂率", "%", bodyFat, latest?.bodyFatPct, 0.1) { bodyFat = it }
            NumericStepper("肌肉量", "kg", muscle, latest?.muscleKg, 0.1) { muscle = it }
            NumericStepper("腰围", "cm", waist, latest?.waistCm, 0.5) { waist = it }
        }
        SheetSection("补充") {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("一句话即可（可不填）") },
                minLines = 2,
            )
        }
        Button(
            onClick = {
                val now = Instant.now().toString()
                val checkin = StatusCheckin(
                    id = "status:${date}:morning",
                    date = date.toString(),
                    kind = CheckinKind.MORNING,
                    observedAt = now,
                    sleepDurationMinutes = sleepMinutes,
                    deepSleepMinutes = deepSleepMinutes?.coerceAtMost(sleepMinutes ?: Int.MAX_VALUE),
                    sleepQuality = sleepQuality,
                    energy = energy,
                    fatigue = fatigue,
                    pain = if (painRecorded) pain.map { (region, draft) ->
                        PainEntry(region, draft.severity, draft.side)
                    } else null,
                    note = note.takeIf { it.isNotBlank() },
                )
                val metric = BodyMetric(
                    id = "metric:${date}:morning",
                    date = date.toString(),
                    observedAt = now,
                    weightKg = weight,
                    bodyFatPct = bodyFat,
                    muscleKg = muscle,
                    waistCm = waist,
                ).takeIf { it.hasMeasurements }
                onSave(checkin, metric)
            },
            enabled = sleepMinutes != null || deepSleepMinutes != null || sleepQuality != null ||
                energy != null || fatigue != null || painRecorded || note.isNotBlank() ||
                weight != null || bodyFat != null || muscle != null || waist != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("保存晨起状态")
        }
    }
}

@Composable
fun PreWorkoutSheet(
    date: LocalDate,
    morning: StatusCheckin?,
    existing: StatusCheckin?,
    onSave: (StatusCheckin) -> Unit,
) {
    var energy by remember(existing) { mutableStateOf(existing?.energy) }
    var fatigue by remember(existing) { mutableStateOf(existing?.fatigue) }
    var painChanged by remember(existing) { mutableStateOf(existing?.pain != null) }
    val pain = remember(existing) {
        mutableStateMapOf<PainRegion, PainDraft>().apply {
            existing?.pain?.forEach { put(it.region, PainDraft(it.severity, it.side)) }
        }
    }
    var note by remember(existing) { mutableStateOf(existing?.note.orEmpty()) }

    SheetFrame(
        eyebrow = "训练前补充",
        title = "只记晨起后的变化",
        subtitle = "没有变化就关闭；留空项继续沿用晨起状态。",
    ) {
        SheetSection("状态变化") {
            ScoreSlider("当前精力", energy, "低", "高") { energy = it }
            ScoreSlider("当前疲劳", fatigue, "轻松", "很累", zeroBased = true) { fatigue = it }
        }
        SheetSection("疼痛变化") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("更新疼痛状态", fontWeight = FontWeight.Medium)
                    Text("关闭表示沿用晨起记录", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = painChanged, onCheckedChange = {
                    painChanged = it
                    if (!it) pain.clear()
                })
            }
            if (painChanged) {
                PainRegionEditor(pain)
                if (pain.isEmpty()) Text("已明确记录为无疼痛异常", color = MaterialTheme.colorScheme.secondary)
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("变化说明（可不填）") },
        )
        Button(
            onClick = {
                onSave(
                    StatusCheckin(
                        id = "status:${date}:pre_workout",
                        date = date.toString(),
                        kind = CheckinKind.PRE_WORKOUT,
                        observedAt = Instant.now().toString(),
                        baseCheckinId = requireNotNull(morning).id,
                        energy = energy,
                        fatigue = fatigue,
                        pain = if (painChanged) pain.map { (region, draft) ->
                            PainEntry(region, draft.severity, draft.side)
                        } else null,
                        note = note.takeIf { it.isNotBlank() },
                    ),
                )
            },
            enabled = morning != null && (energy != null || fatigue != null || painChanged || note.isNotBlank()),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("保存变化") }
    }
}

@Composable
private fun PainEditor(
    recorded: Boolean,
    pain: MutableMap<PainRegion, PainDraft>,
    onRecordedChange: (Boolean) -> Unit,
) {
    SheetSection("疼痛与不适") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = recorded && pain.isEmpty(),
                onClick = {
                    pain.clear()
                    onRecordedChange(true)
                },
                label = { Text("无异常") },
            )
            FilterChip(
                selected = pain.isNotEmpty(),
                onClick = { onRecordedChange(true) },
                label = { Text("有不适") },
            )
        }
        if (recorded && pain.isNotEmpty()) PainRegionEditor(pain)
        if (!recorded) Text("尚未记录", color = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PainRegionEditor(pain: MutableMap<PainRegion, PainDraft>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PainRegion.entries.forEach { region ->
            FilterChip(
                selected = region in pain,
                onClick = {
                    if (region in pain) pain.remove(region) else pain[region] = PainDraft()
                },
                label = { Text(region.displayName) },
            )
        }
    }
    pain.toMap().forEach { (region, draft) ->
        Column(Modifier.padding(top = 10.dp)) {
            Text(region.displayName, fontWeight = FontWeight.Medium)
            ScoreSlider("程度", draft.severity, "轻微", "明显", zeroBased = true) {
                pain[region] = draft.copy(severity = it ?: 1)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PainSide.LEFT, PainSide.RIGHT, PainSide.BILATERAL, PainSide.UNSPECIFIED).forEach { side ->
                    FilterChip(
                        selected = draft.side == side,
                        onClick = { pain[region] = draft.copy(side = side) },
                        label = { Text(side.displayName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreSlider(
    label: String,
    value: Int?,
    startLabel: String,
    endLabel: String,
    zeroBased: Boolean = false,
    onValueChange: (Int?) -> Unit,
) {
    val min = if (zeroBased) 0 else 1
    val shown = value ?: if (zeroBased) 0 else 3
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value?.let { "$it/5" } ?: "未记录", fontWeight = FontWeight.Medium)
        }
        Slider(
            value = shown.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(min, 5)) },
            valueRange = min.toFloat()..5f,
            steps = 4 - min,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall)
            Text(endLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DurationStepper(
    label: String,
    value: Int?,
    step: Int,
    max: Int? = null,
    onValueChange: (Int?) -> Unit,
) {
    StepperRow(
        label = label,
        value = value?.let { "${it / 60}小时${it % 60}分" } ?: "未记录",
        onMinus = { onValueChange(value?.minus(step)?.takeIf { it > 0 }) },
        onPlus = { onValueChange(((value ?: 0) + step).coerceAtMost(max ?: 1440)) },
        onClear = { onValueChange(null) },
    )
}

@Composable
private fun NumericStepper(
    label: String,
    unit: String,
    value: Double?,
    anchor: Double?,
    step: Double,
    onValueChange: (Double?) -> Unit,
) {
    StepperRow(
        label = label,
        value = value?.let { "%.1f %s".format(it, unit) }
            ?: anchor?.let { "未记录 · 最近 %.1f".format(it) }
            ?: "未记录",
        onMinus = {
            val base = value ?: anchor ?: step
            onValueChange((base - step).coerceAtLeast(0.0))
        },
        onPlus = {
            val base = value ?: anchor ?: 0.0
            onValueChange(base + step)
        },
        onClear = { onValueChange(null) },
    )
}

@Composable
private fun StepperRow(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, color = MaterialTheme.colorScheme.secondary)
        }
        OutlinedButton(onClick = onMinus) { Text("−") }
        OutlinedButton(onClick = onPlus) { Text("+") }
        OutlinedButton(onClick = onClear) { Text("清空") }
    }
}

@Composable
private fun SheetFrame(
    eyebrow: String,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(bottom = 26.dp),
    ) {
        Text(eyebrow, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(22.dp))
        content()
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Column(content = content)
    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(18.dp))
}
