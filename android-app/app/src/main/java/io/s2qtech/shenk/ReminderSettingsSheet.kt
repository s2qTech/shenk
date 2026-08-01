package io.s2qtech.shenk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ReminderSettingsSheet(
    settings: ReminderSettings,
    onSave: (ReminderSettings) -> Unit,
) {
    var value by remember(settings) { mutableStateOf(settings) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        Text("提醒", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "只提醒一次；已经记录后不会再出现。",
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(24.dp))
        ReminderRow(
            title = "晨起提醒",
            enabled = value.morningEnabled,
            hour = value.morningHour,
            minute = value.morningMinute,
            onEnabled = { value = value.copy(morningEnabled = it) },
            onTime = { hour, minute -> value = value.copy(morningHour = hour, morningMinute = minute) },
        )
        Spacer(Modifier.height(22.dp))
        ReminderRow(
            title = "周复盘",
            enabled = value.weeklyEnabled,
            hour = value.weeklyHour,
            minute = value.weeklyMinute,
            onEnabled = { value = value.copy(weeklyEnabled = it) },
            onTime = { hour, minute -> value = value.copy(weeklyHour = hour, weeklyMinute = minute) },
        )
        Text("每周六生成资料，提醒你复制到健身计划任务。", color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(22.dp))
        ReminderRow(
            title = "中午补记",
            enabled = value.middayEnabled,
            hour = value.middayHour,
            minute = value.middayMinute,
            onEnabled = { value = value.copy(middayEnabled = it) },
            onTime = { hour, minute -> value = value.copy(middayHour = hour, middayMinute = minute) },
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = { onSave(value) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("保存提醒")
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun ReminderRow(
    title: String,
    enabled: Boolean,
    hour: Int,
    minute: Int,
    onEnabled: (Boolean) -> Unit,
    onTime: (Int, Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("%02d:%02d".format(hour, minute), color = MaterialTheme.colorScheme.secondary)
        }
        Switch(checked = enabled, onCheckedChange = onEnabled)
    }
    if (enabled) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val total = (hour * 60 + minute - 15 + 1440) % 1440
                    onTime(total / 60, total % 60)
                },
                modifier = Modifier.weight(1f),
            ) { Text("提前15分") }
            OutlinedButton(
                onClick = {
                    val total = (hour * 60 + minute + 15) % 1440
                    onTime(total / 60, total % 60)
                },
                modifier = Modifier.weight(1f),
            ) { Text("延后15分") }
        }
    }
}
