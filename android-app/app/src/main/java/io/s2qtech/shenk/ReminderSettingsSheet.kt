package io.s2qtech.shenk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
    systemStatus: ReminderSystemStatus,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenApplicationSettings: () -> Unit,
    onSave: (ReminderSettings) -> Unit,
) {
    var value by remember(settings) { mutableStateOf(settings) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        Text("提醒", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "只提醒一次；已经记录后不会再出现。",
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(20.dp))
        ReminderSystemStatusCard(
            status = systemStatus,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenNotificationSettings = onOpenNotificationSettings,
            onOpenApplicationSettings = onOpenApplicationSettings,
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
        Spacer(Modifier.height(22.dp))
        ReminderRow(
            title = "晚间未记录",
            enabled = value.eveningEnabled,
            hour = value.eveningHour,
            minute = value.eveningMinute,
            onEnabled = { value = value.copy(eveningEnabled = it) },
            onTime = { hour, minute -> value = value.copy(eveningHour = hour, eveningMinute = minute) },
        )
        Text("没有训练、休息或跳过记录时提醒一次。", color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(28.dp))
        Button(onClick = { onSave(value) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("保存提醒")
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun ReminderSystemStatusCard(
    status: ReminderSystemStatus,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenApplicationSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("系统投递状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    !status.notificationPermissionGranted -> "通知：未授权，提醒不会显示"
                    !status.notificationsEnabled -> "通知：已在系统中关闭"
                    else -> "通知：已允许"
                },
                color = if (status.notificationsAllowed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
            Text(
                if (status.batteryOptimizationExempt) "后台：系统未限制电池使用" else "后台：系统可能延迟提醒",
                color = MaterialTheme.colorScheme.secondary,
            )
            if (status.isXiaomiDevice) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "HyperOS 仍可能限制后台任务。可在应用设置中把电池策略设为“无限制”，并允许后台活动；具体名称随系统版本变化。",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!status.notificationPermissionGranted) {
                    OutlinedButton(onClick = onRequestNotificationPermission, modifier = Modifier.weight(1f)) {
                        Text("允许通知")
                    }
                }
                OutlinedButton(
                    onClick = if (status.notificationPermissionGranted) onOpenNotificationSettings else onOpenApplicationSettings,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (status.notificationPermissionGranted) "通知设置" else "应用设置")
                }
            }
            if (!status.batteryOptimizationExempt || status.isXiaomiDevice) {
                OutlinedButton(
                    onClick = onOpenApplicationSettings,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("检查后台与电池设置")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "提醒由 Android 后台任务投递，系统可能延迟；身刻不会为此常驻后台。",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
