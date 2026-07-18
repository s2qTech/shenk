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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.sync.CloudConnectionState

@Composable
fun CloudConnectionSheet(
    state: CloudConnectionState,
    busy: Boolean,
    error: String?,
    onConnect: (String) -> Unit,
    onSync: () -> Unit,
) {
    var migrationCode by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text("连接身刻数据", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.configured) "当前设备已连接云端。可以立即同步，或用新的迁移码更换配置。"
            else "在 Web 身刻的设置中生成迁移码，粘贴一次即可读取云端配置和既有数据。",
            color = MaterialTheme.colorScheme.secondary,
        )
        if (state.configured) {
            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("已连接", fontWeight = FontWeight.SemiBold)
                        Text("密钥保存在本机安全区", color = MaterialTheme.colorScheme.secondary)
                    }
                    OutlinedButton(onClick = onSync, enabled = !busy) { Text("立即同步") }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(
            value = migrationCode,
            onValueChange = { migrationCode = it.trim() },
            modifier = Modifier.fillMaxWidth().testTag("migration-code-input"),
            label = { Text(if (state.configured) "新的迁移码" else "迁移码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            supportingText = { Text("迁移码不会被保存在手机、日志或云端明文中。") },
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("cloud-connection-error"))
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onConnect(migrationCode) },
            enabled = migrationCode.length >= 20 && !busy,
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("connect-cloud-data"),
        ) {
            if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(22.dp))
            else Text(if (state.configured) "读取新配置并同步" else "连接并同步")
        }
        Spacer(Modifier.height(12.dp))
    }
}
