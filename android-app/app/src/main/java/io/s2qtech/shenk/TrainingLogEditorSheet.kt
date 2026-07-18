package io.s2qtech.shenk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.model.TrainingLog
import io.s2qtech.shenk.model.trainingTypeTitle
import java.time.LocalDate
import java.util.UUID

private val trainingTypes = listOf(
    "easy_walk", "quality_walk", "strength", "indoor_cardio", "recovery", "stretch", "rest",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingLogEditorSheet(
    date: LocalDate,
    existing: TrainingLog?,
    readOnly: Boolean,
    onSave: (TrainingLog) -> Unit,
    onDelete: ((TrainingLog) -> Unit)? = null,
) {
    var type by remember(existing?.id, date) { mutableStateOf(existing?.type ?: "easy_walk") }
    var typeMenu by remember { mutableStateOf(false) }
    var duration by remember(existing?.id) { mutableStateOf(existing?.durationMinutes?.toString().orEmpty()) }
    var distance by remember(existing?.id) { mutableStateOf(existing?.distanceKm?.toString().orEmpty()) }
    var heartRate by remember(existing?.id) { mutableStateOf(existing?.averageHeartRate?.toString().orEmpty()) }
    var effort by remember(existing?.id) { mutableStateOf(existing?.perceivedEffort?.toString().orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 12.dp),
    ) {
        Text(
            text = if (readOnly) "训练记录" else if (existing == null) "补训练" else "修正训练",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(date.toString(), color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(20.dp))

        ExposedDropdownMenuBox(expanded = typeMenu, onExpandedChange = { if (!readOnly) typeMenu = it }) {
            OutlinedTextField(
                value = trainingTypeTitle(type),
                onValueChange = {},
                readOnly = true,
                enabled = !readOnly,
                label = { Text("训练类型") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeMenu) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                trainingTypes.forEach { value ->
                    DropdownMenuItem(
                        text = { Text(trainingTypeTitle(value)) },
                        onClick = { type = value; typeMenu = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("时长（分）", duration, { duration = it }, readOnly, Modifier.weight(1f))
            NumberField("距离（km）", distance, { distance = it }, readOnly, Modifier.weight(1f), decimal = true)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("平均心率", heartRate, { heartRate = it }, readOnly, Modifier.weight(1f))
            NumberField("体感 1-10", effort, { effort = it }, readOnly, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            enabled = !readOnly,
            label = { Text("备注") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        if (readOnly) {
            Text("该记录已超出前后 14 天修正范围。", color = MaterialTheme.colorScheme.outline)
        } else {
            Button(
                onClick = {
                    onSave(
                        TrainingLog(
                            id = existing?.id ?: "android-${UUID.randomUUID()}",
                            date = date.toString(),
                            type = type,
                            status = existing?.status ?: "completed",
                            source = existing?.source ?: "manual",
                            title = existing?.title,
                            durationSec = duration.toIntOrNull()?.times(60),
                            distanceKm = distance.toDoubleOrNull(),
                            averageHeartRate = heartRate.toIntOrNull(),
                            perceivedEffort = effort.toIntOrNull()?.coerceIn(1, 10),
                            subjectiveResult = existing?.subjectiveResult,
                            notes = notes.takeIf(String::isNotBlank),
                            timerSessionId = existing?.timerSessionId,
                            timerSessionIds = existing?.timerSessionIds.orEmpty(),
                            calendarVisible = existing?.calendarVisible ?: true,
                            countsTowardTraining = existing?.countsTowardTraining ?: true,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("保存记录") }
            existing?.let { log ->
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onDelete(log) }, modifier = Modifier.fillMaxWidth()) {
                        Text("删除记录")
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
    modifier: Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = !readOnly,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}
