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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.sync.ConflictEntity

@Composable
fun SyncConflictSheet(
    conflicts: List<ConflictEntity>,
    resolvingKey: String?,
    onKeepLocal: (ConflictEntity) -> Unit,
    onUseCloud: (ConflictEntity) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetHeader("同步冲突", "逐条选择要保留的版本")
        Spacer(Modifier.height(2.dp))
        if (conflicts.isEmpty()) {
            ShenkStatePanel(
                title = "没有待处理冲突",
                message = "本机修改与云端记录目前一致。",
                tone = ShenkStateTone.SUCCESS,
                modifier = Modifier.testTag("conflict-empty"),
            )
        } else {
            ShenkStatePanel(
                title = "${conflicts.size} 条记录需要确认",
                message = "身刻没有自动覆盖任何一方。选择本机版本会重新排队同步；选择云端版本会替换这一条本机修改。",
                tone = ShenkStateTone.WARNING,
            )
            conflicts.forEachIndexed { index, conflict ->
                val busy = resolvingKey != null
                SecondarySectionCard(modifier = Modifier.testTag("conflict-${conflict.recordKey}")) {
                    Text(
                        "${syncConflictEntityLabel(conflict.entity)} ${if (conflicts.size > 1) index + 1 else ""}".trim(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "本机与云端都基于旧版本产生了不同修改，需要由你决定。",
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onUseCloud(conflict) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("采用云端") }
                        Button(
                            onClick = { onKeepLocal(conflict) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("保留本机") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

internal fun syncConflictEntityLabel(entity: String): String = when (entity) {
    "training_logs" -> "训练记录"
    "body_metrics" -> "身体测量"
    "status_checkins" -> "状态记录"
    "daily_reviews" -> "每日简评"
    "daily_plan_items", "plan_adjustments", "plan_templates" -> "正式计划"
    "routine_templates" -> "训练方案"
    "timer_sessions" -> "计时事实"
    "goal_sets", "coach_strategies" -> "目标与策略"
    "feedback_summaries" -> "复盘资料"
    else -> "同步记录"
}
