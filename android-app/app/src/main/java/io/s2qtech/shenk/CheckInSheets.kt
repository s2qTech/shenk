package io.s2qtech.shenk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private data class PainDraft(val severity: Int = 1, val side: PainSide = PainSide.UNSPECIFIED)

private const val DEFAULT_WELLNESS_SCORE = 3

private enum class DurationField { SLEEP, DEEP_SLEEP }

private enum class MeasurementField { WEIGHT, BODY_FAT, MUSCLE, WAIST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningCheckInSheet(
    date: LocalDate,
    existing: TodayRecords?,
    onSave: (StatusCheckin, BodyMetric?) -> Unit,
) {
    val previous = existing?.morning
    var sleepMinutes by remember(previous) { mutableStateOf(previous?.sleepDurationMinutes) }
    var deepSleepMinutes by remember(previous) { mutableStateOf(previous?.deepSleepMinutes) }
    var sleepQuality by remember(previous) {
        mutableStateOf<Int?>(previous?.sleepQuality ?: DEFAULT_WELLNESS_SCORE)
    }
    var energy by remember(previous) {
        mutableStateOf<Int?>(previous?.energy ?: DEFAULT_WELLNESS_SCORE)
    }
    var fatigue by remember(previous) {
        mutableStateOf<Int?>(previous?.fatigue ?: DEFAULT_WELLNESS_SCORE)
    }
    var painRecorded by remember(previous) { mutableStateOf(previous?.pain != null) }
    var painHasDiscomfort by remember(previous) { mutableStateOf(previous?.pain?.isNotEmpty() == true) }
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
    var activeDuration by remember { mutableStateOf<DurationField?>(null) }
    var activeMeasurement by remember { mutableStateOf<MeasurementField?>(null) }
    val latest = existing?.latestMetric

    SheetFrame(
        eyebrow = "晨起记录",
        title = "今天身体怎么样？",
        subtitle = "不确定的项目可以留空，不需要事后补齐。",
    ) {
        SheetSection("睡眠") {
            DurationInputGrid(
                sleepMinutes = sleepMinutes,
                deepSleepMinutes = deepSleepMinutes,
                active = activeDuration,
                onSelect = { field ->
                    activeDuration = field
                    when (field) {
                        DurationField.SLEEP -> if (sleepMinutes == null) sleepMinutes = 8 * 60
                        DurationField.DEEP_SLEEP -> if (deepSleepMinutes == null) {
                            deepSleepMinutes = minOf(2 * 60, sleepMinutes ?: 2 * 60)
                        }
                    }
                },
            )
            WellnessScoreSlider("睡眠感受", sleepQuality, "差", "好") { sleepQuality = it }
        }
        SheetSection("今日状态") {
            WellnessScoreSlider("精力", energy, "低", "高") { energy = it }
            WellnessScoreSlider(
                label = "疲劳",
                value = fatigue,
                startLabel = "很累",
                endLabel = "轻松",
                storedValueInverted = true,
                onValueChange = { fatigue = it },
            )
        }
        PainEditor(
            recorded = painRecorded,
            hasDiscomfort = painHasDiscomfort,
            pain = pain,
            onRecordedChange = { painRecorded = it },
            onHasDiscomfortChange = { painHasDiscomfort = it },
        )
        SheetSection("身体测量") {
            Text(
                "晨起没有测量就直接跳过。第一次调整会从最近值开始。",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
            MeasurementInputGrid(
                weight = weight,
                bodyFat = bodyFat,
                muscle = muscle,
                waist = waist,
                latest = latest,
                active = activeMeasurement,
                onSelect = { field ->
                    activeMeasurement = field
                    when (field) {
                        MeasurementField.WEIGHT -> if (weight == null) weight = latest?.weightKg ?: 70.0
                        MeasurementField.BODY_FAT -> if (bodyFat == null) bodyFat = latest?.bodyFatPct ?: 20.0
                        MeasurementField.MUSCLE -> if (muscle == null) muscle = latest?.muscleKg ?: 50.0
                        MeasurementField.WAIST -> if (waist == null) waist = latest?.waistCm ?: 80.0
                    }
                },
            )
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
                    id = previous?.id ?: "status_checkin:${date}:morning",
                    date = date.toString(),
                    kind = CheckinKind.MORNING,
                    observedAt = now,
                    sleepDurationMinutes = sleepMinutes,
                    deepSleepMinutes = deepSleepMinutes?.coerceAtMost(sleepMinutes ?: Int.MAX_VALUE),
                    sleepQuality = sleepQuality,
                    energy = energy,
                    fatigue = fatigue,
                    pain = when {
                        !painRecorded -> null
                        !painHasDiscomfort -> emptyList()
                        pain.isNotEmpty() -> pain.map { (region, draft) ->
                            PainEntry(region, draft.severity, draft.side)
                        }
                        else -> null
                    },
                    note = note.takeIf { it.isNotBlank() },
                )
                val metric = BodyMetric(
                    id = existing?.metric?.id ?: "body_metric:${date}:morning",
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

    activeDuration?.let { field ->
        val duration = when (field) {
            DurationField.SLEEP -> sleepMinutes ?: 8 * 60
            DurationField.DEEP_SLEEP -> deepSleepMinutes ?: minOf(2 * 60, sleepMinutes ?: 2 * 60)
        }
        ModalBottomSheet(onDismissRequest = { activeDuration = null }) {
            DurationWheelEditor(
                label = if (field == DurationField.SLEEP) "睡眠时长" else "深睡时长",
                valueMinutes = duration,
                maxMinutes = if (field == DurationField.DEEP_SLEEP) sleepMinutes else 16 * 60,
                onValueChange = { changed ->
                    when (field) {
                        DurationField.SLEEP -> {
                            sleepMinutes = changed
                            deepSleepMinutes = deepSleepMinutes?.coerceAtMost(changed)
                        }
                        DurationField.DEEP_SLEEP -> deepSleepMinutes = changed.coerceAtMost(sleepMinutes ?: changed)
                    }
                },
                onClear = {
                    when (field) {
                        DurationField.SLEEP -> {
                            sleepMinutes = null
                            deepSleepMinutes = null
                        }
                        DurationField.DEEP_SLEEP -> deepSleepMinutes = null
                    }
                    activeDuration = null
                },
                onDone = { activeDuration = null },
            )
        }
    }

    activeMeasurement?.let { field ->
        val config = measurementConfig(field)
        val current = when (field) {
            MeasurementField.WEIGHT -> weight ?: latest?.weightKg ?: config.defaultValue
            MeasurementField.BODY_FAT -> bodyFat ?: latest?.bodyFatPct ?: config.defaultValue
            MeasurementField.MUSCLE -> muscle ?: latest?.muscleKg ?: config.defaultValue
            MeasurementField.WAIST -> waist ?: latest?.waistCm ?: config.defaultValue
        }
        ModalBottomSheet(onDismissRequest = { activeMeasurement = null }) {
            MeasurementWheelEditor(
                config = config,
                value = current,
                onValueChange = { changed ->
                    when (field) {
                        MeasurementField.WEIGHT -> weight = changed
                        MeasurementField.BODY_FAT -> bodyFat = changed
                        MeasurementField.MUSCLE -> muscle = changed
                        MeasurementField.WAIST -> waist = changed
                    }
                },
                onClear = {
                    when (field) {
                        MeasurementField.WEIGHT -> weight = null
                        MeasurementField.BODY_FAT -> bodyFat = null
                        MeasurementField.MUSCLE -> muscle = null
                        MeasurementField.WAIST -> waist = null
                    }
                    activeMeasurement = null
                },
                onDone = { activeMeasurement = null },
            )
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
            WellnessScoreSlider("当前精力", energy, "低", "高") { energy = it }
            WellnessScoreSlider(
                label = "当前疲劳",
                value = fatigue,
                startLabel = "很累",
                endLabel = "轻松",
                storedValueInverted = true,
                onValueChange = { fatigue = it },
            )
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
    hasDiscomfort: Boolean,
    pain: MutableMap<PainRegion, PainDraft>,
    onRecordedChange: (Boolean) -> Unit,
    onHasDiscomfortChange: (Boolean) -> Unit,
) {
    SheetSection("疼痛与不适") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = recorded && !hasDiscomfort,
                onClick = {
                    pain.clear()
                    onRecordedChange(true)
                    onHasDiscomfortChange(false)
                },
                label = { Text("无异常") },
            )
            FilterChip(
                selected = recorded && hasDiscomfort,
                onClick = {
                    onRecordedChange(true)
                    onHasDiscomfortChange(true)
                },
                label = { Text("有不适") },
            )
        }
        if (recorded && hasDiscomfort) {
            Text("选择不适部位", color = MaterialTheme.colorScheme.secondary)
            PainRegionEditor(pain)
        }
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
            IntensityScoreSlider("程度", draft.severity, "轻微", "明显") {
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
private fun WellnessScoreSlider(
    label: String,
    value: Int?,
    startLabel: String,
    endLabel: String,
    storedValueInverted: Boolean = false,
    onValueChange: (Int?) -> Unit,
) {
    val displayValue = value?.coerceIn(1, 5)?.let { if (storedValueInverted) 6 - it else it }
    val shown = displayValue ?: 3
    val stateColor = when (displayValue) {
        1, 2 -> MaterialTheme.colorScheme.error
        3 -> Color(0xFFC58A16)
        4, 5 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(displayValue?.let { "$it/5" } ?: "未记录", fontWeight = FontWeight.Medium, color = stateColor)
        }
        Slider(
            value = shown.toFloat(),
            onValueChange = {
                val changed = it.roundToInt().coerceIn(1, 5)
                onValueChange(if (storedValueInverted) 6 - changed else changed)
            },
            valueRange = 1f..5f,
            steps = 3,
            colors = SliderDefaults.colors(
                thumbColor = stateColor,
                activeTrackColor = stateColor,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.58f),
                inactiveTickColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun IntensityScoreSlider(
    label: String,
    value: Int?,
    startLabel: String,
    endLabel: String,
    onValueChange: (Int?) -> Unit,
) {
    val shown = value?.coerceIn(1, 5) ?: 1
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value?.coerceIn(1, 5)?.let { "$it/5" } ?: "未记录", fontWeight = FontWeight.Medium)
        }
        Slider(
            value = shown.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(1, 5)) },
            valueRange = 1f..5f,
            steps = 3,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(startLabel, style = MaterialTheme.typography.labelSmall)
            Text(endLabel, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private data class MeasurementWheelConfig(
    val field: MeasurementField,
    val label: String,
    val unit: String,
    val min: Double,
    val max: Double,
    val step: Double,
    val defaultValue: Double,
)

private fun measurementConfig(field: MeasurementField): MeasurementWheelConfig = when (field) {
    MeasurementField.WEIGHT -> MeasurementWheelConfig(field, "体重", "kg", 30.0, 250.0, 0.1, 70.0)
    MeasurementField.BODY_FAT -> MeasurementWheelConfig(field, "体脂率", "%", 3.0, 70.0, 0.1, 20.0)
    MeasurementField.MUSCLE -> MeasurementWheelConfig(field, "肌肉量", "kg", 10.0, 150.0, 0.1, 50.0)
    MeasurementField.WAIST -> MeasurementWheelConfig(field, "腰围", "cm", 40.0, 200.0, 0.5, 80.0)
}

@Composable
private fun DurationInputGrid(
    sleepMinutes: Int?,
    deepSleepMinutes: Int?,
    active: DurationField?,
    onSelect: (DurationField) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ValueInputCell(
            label = "睡眠时长",
            value = sleepMinutes?.let(::formatDuration) ?: "未记录",
            selected = active == DurationField.SLEEP,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(DurationField.SLEEP) },
        )
        ValueInputCell(
            label = "深睡时长",
            value = deepSleepMinutes?.let(::formatDuration) ?: "未记录",
            selected = active == DurationField.DEEP_SLEEP,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(DurationField.DEEP_SLEEP) },
        )
    }
}

@Composable
private fun MeasurementInputGrid(
    weight: Double?,
    bodyFat: Double?,
    muscle: Double?,
    waist: Double?,
    latest: BodyMetric?,
    active: MeasurementField?,
    onSelect: (MeasurementField) -> Unit,
) {
    val rows = listOf(
        listOf(
            Triple(MeasurementField.WEIGHT, weight, latest?.weightKg),
            Triple(MeasurementField.BODY_FAT, bodyFat, latest?.bodyFatPct),
        ),
        listOf(
            Triple(MeasurementField.MUSCLE, muscle, latest?.muscleKg),
            Triple(MeasurementField.WAIST, waist, latest?.waistCm),
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (field, value, anchor) ->
                    val config = measurementConfig(field)
                    ValueInputCell(
                        label = config.label,
                        value = value?.let { "%.1f %s".format(it, config.unit) } ?: "未记录",
                        supporting = if (value == null && anchor != null) {
                            "上次 %.1f %s".format(anchor, config.unit)
                        } else null,
                        selected = active == field,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(field) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ValueInputCell(
    label: String,
    value: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(92.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label，$value" },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun DurationWheelEditor(
    label: String,
    valueMinutes: Int,
    maxMinutes: Int?,
    onValueChange: (Int) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    val safeMaximum = (maxMinutes ?: (16 * 60)).coerceAtLeast(1)
    val safeValue = valueMinutes.coerceIn(1, safeMaximum)
    WheelEditorFrame(label = label, onClear = onClear, onDone = onDone) {
        key(label) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IntWheelColumn(
                    values = 0..(safeMaximum / 60),
                    selected = safeValue / 60,
                    suffix = "小时",
                    modifier = Modifier.weight(1f),
                    onSelected = { hour ->
                        val minute = safeValue % 60
                        onValueChange((hour * 60 + minute).coerceIn(1, safeMaximum))
                    },
                )
                IntWheelColumn(
                    values = 0..59,
                    selected = safeValue % 60,
                    suffix = "分",
                    modifier = Modifier.weight(1f),
                    onSelected = { minute ->
                        val hour = safeValue / 60
                        onValueChange((hour * 60 + minute).coerceIn(1, safeMaximum))
                    },
                )
            }
        }
    }
}

@Composable
private fun MeasurementWheelEditor(
    config: MeasurementWheelConfig,
    value: Double,
    onValueChange: (Double) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
) {
    val itemCount = (((config.max - config.min) / config.step).roundToInt() + 1).coerceAtLeast(1)
    val selectedIndex = (((value - config.min) / config.step).roundToInt()).coerceIn(0, itemCount - 1)
    WheelEditorFrame(label = config.label, onClear = onClear, onDone = onDone) {
        key(config.field) {
            WheelColumn(
                itemCount = itemCount,
                selectedIndex = selectedIndex,
                textAt = { index -> "%.1f %s".format(config.min + index * config.step, config.unit) },
                modifier = Modifier.fillMaxWidth(),
                onSelected = { index ->
                    val selected = config.min + index * config.step
                    onValueChange((selected * 10.0).roundToInt() / 10.0)
                },
            )
        }
    }
}

@Composable
private fun WheelEditorFrame(
    label: String,
    onClear: () -> Unit,
    onDone: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
            .padding(bottom = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("上下滑动选择", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onClear) { Text("不记录") }
                TextButton(onClick = onDone) { Text("完成") }
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun IntWheelColumn(
    values: IntRange,
    selected: Int,
    suffix: String,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit,
) {
    val items = remember(values.first, values.last) { values.toList() }
    WheelColumn(
        itemCount = items.size,
        selectedIndex = items.indexOf(selected).coerceAtLeast(0),
        textAt = { index -> "${items[index]} $suffix" },
        modifier = modifier,
        onSelected = { index -> onSelected(items[index]) },
    )
}

@Composable
private fun WheelColumn(
    itemCount: Int,
    selectedIndex: Int,
    textAt: (Int) -> String,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit,
) {
    val initialIndex = selectedIndex.coerceIn(0, itemCount - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val scope = rememberCoroutineScope()
    val centeredIndex by remember(listState, initialIndex) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { item ->
                abs(item.offset + item.size / 2 - center)
            }?.index ?: initialIndex
        }
    }

    LaunchedEffect(listState, itemCount) {
        snapshotFlow { listState.isScrollInProgress to centeredIndex }
            .map { (scrolling, index) -> if (scrolling) null else index }
            .filter { it != null }
            .map { requireNotNull(it) }
            .distinctUntilChanged()
            .collect(onSelected)
    }

    Box(modifier.height(152.dp)) {
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            Spacer(Modifier.height(48.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(152.dp),
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = 52.dp),
        ) {
            items(count = itemCount, key = { it }) { index ->
                Text(
                    text = textAt(index),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { scope.launch { listState.animateScrollToItem(index) } }
                        .padding(top = 10.dp)
                        .alpha(if (index == centeredIndex) 1f else 0.34f),
                    textAlign = TextAlign.Center,
                    style = if (index == centeredIndex) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = if (index == centeredIndex) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

private fun formatDuration(minutes: Int): String = buildString {
    if (minutes >= 60) append("${minutes / 60}小时")
    if (minutes % 60 > 0 || minutes < 60) append("${minutes % 60}分")
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
