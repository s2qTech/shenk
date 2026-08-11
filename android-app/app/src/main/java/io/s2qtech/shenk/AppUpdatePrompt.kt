package io.s2qtech.shenk

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppUpdatePrompt(
    state: AppUpdateState,
    onDismiss: () -> Unit,
    onDownload: (AppUpdateRelease) -> Unit,
    onInstall: (java.io.File) -> Unit,
) {
    when (state) {
        AppUpdateState.Idle -> Unit
        is AppUpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("发现身刻更新") },
            text = { Text("版本 ${state.release.versionName} 已就绪。下载由你确认，完成校验后仍需在 Android 系统界面确认安装。") },
            dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
            confirmButton = { TextButton(onClick = { onDownload(state.release) }) { Text("下载更新") } },
        )
        is AppUpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                androidx.compose.foundation.layout.Row {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text(" 下载后会验证包名、版本、文件摘要和签名证书。", modifier = Modifier)
                }
            },
            confirmButton = {},
        )
        is AppUpdateState.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("更新已验证") },
            text = { Text("安全校验已通过。下一步将打开 Android 系统安装界面，是否安装仍由你确认。") },
            dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
            confirmButton = { TextButton(onClick = { onInstall(state.apk) }) { Text("打开安装界面") } },
        )
        is AppUpdateState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("无法准备更新") },
            text = { Text(state.message) },
            dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
            confirmButton = { TextButton(onClick = { onDownload(state.release) }) { Text("重试") } },
        )
    }
}
