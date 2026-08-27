package io.s2qtech.shenk

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.sync.AiProviderSettings
import io.s2qtech.shenk.sync.AiProviderConnectionException
import io.s2qtech.shenk.sync.AiProviderConnectionFailure
import io.s2qtech.shenk.sync.DailyReviewRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun AppSettingsSheet(onReminders: () -> Unit, onAiService: () -> Unit, onBackup: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetHeader("设置", "提醒、AI 服务与本机数据")
        Spacer(Modifier.height(2.dp))
        SettingsRow("提醒", "晨起、午间和周复盘", Icons.Rounded.Alarm, onReminders)
        SettingsRow("AI 服务", "DeepSeek V4 Flash · 每日简评", Icons.Rounded.AutoAwesome, onAiService)
        SettingsRow("数据备份", "导出或安全合并业务记录", Icons.Rounded.Storage, onBackup)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DataBackupSheet(
    busy: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetHeader("数据备份", "通过系统文件选择器保存或恢复业务记录")
        Spacer(Modifier.height(2.dp))
        SecondarySectionCard {
            Text("隐私与恢复规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("备份不包含 API Key、云端令牌、迁移码、同步队列或冲突记录。", color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
            Text("恢复只补充缺失记录；同 ID 内容不同时会跳过，不覆盖本机现有修改。", color = MaterialTheme.colorScheme.secondary)
        }
        Button(
            onClick = onExport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        ) { Text(if (busy) "正在处理" else "导出 JSON 备份") }
        OutlinedButton(
            onClick = onImport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        ) { Text("从 JSON 安全恢复") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun AiProviderSettingsSheet(repository: DailyReviewRepository, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val settings = remember { AiProviderSettings() }
    var hasKey by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        hasKey = repository.hasProviderKey()
        editing = !hasKey
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        SheetHeader("AI 服务", "每日简评由 DeepSeek V4 Flash 生成")
        Spacer(Modifier.height(14.dp))
        SecondarySectionCard {
            Text("DeepSeek V4 Flash", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(if (hasKey) "已配置，可用于每日简评" else "尚未配置 API Key", color = MaterialTheme.colorScheme.secondary)
        }

        if (editing) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(if (hasKey) "新 API Key" else "API Key") },
                supportingText = { Text(if (hasKey) "测试失败会保留当前可用密钥" else "密钥只保存在本机安全存储中") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    keyboard?.hide()
                    busy = true
                    status = "正在验证 DeepSeek 连接…"
                    success = null
                    scope.launch {
                        try {
                            val ok = withTimeout(35_000) {
                                repository.testAndConfigureProvider(settings, apiKey.takeIf { it.isNotBlank() })
                            }
                            success = ok
                            status = if (ok) "连接成功，配置已保存" else "连接失败，原有配置未更改"
                            if (ok) {
                                hasKey = true
                                editing = false
                                apiKey = ""
                                onMessage("AI 服务连接成功")
                            }
                        } catch (_: TimeoutCancellationException) {
                            success = false
                            status = "连接超时，原有配置未更改"
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: AiProviderConnectionException) {
                            success = false
                            status = when (error.failure) {
                                AiProviderConnectionFailure.CLOUD_AUTH -> "身刻云端授权已失效，请先重新同步配置；原有密钥未更改"
                                AiProviderConnectionFailure.KEY_REJECTED -> "DeepSeek 拒绝了该 API Key，请确认复制完整；原有密钥未更改"
                                AiProviderConnectionFailure.BALANCE_OR_QUOTA -> "DeepSeek 账户余额或调用额度不足；原有密钥未更改"
                                AiProviderConnectionFailure.RATE_LIMITED -> "DeepSeek 当前请求过多，请稍后再试；原有密钥未更改"
                                AiProviderConnectionFailure.MODEL_UNAVAILABLE -> "DeepSeek V4 Flash 当前不可用或账户无权使用；原有密钥未更改"
                                AiProviderConnectionFailure.PROVIDER_UNAVAILABLE -> "云端暂时无法连接 DeepSeek，请稍后再试；原有密钥未更改"
                                AiProviderConnectionFailure.INVALID_RESPONSE -> "DeepSeek 已响应，但连接测试结果异常；原有密钥未更改"
                                AiProviderConnectionFailure.NETWORK -> "手机无法连接身刻云端，请检查网络或代理；原有密钥未更改"
                                AiProviderConnectionFailure.UNKNOWN -> "连接测试失败，原有密钥未更改"
                            }
                        } catch (_: Exception) {
                            success = false
                            status = "连接失败，请检查密钥或网络；原有配置未更改"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && (hasKey || apiKey.isNotBlank()),
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("正在测试")
                } else {
                    Text("保存并测试")
                }
            }
        } else {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = {
                    status = null
                    success = null
                    editing = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("更换 API Key") }
        }

        status?.let {
            Text(
                it,
                color = when (success) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
    }
}
