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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.sync.DailyReviewRepository
import io.s2qtech.shenk.sync.DailyReviewState
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun DailyReviewSheet(
    date: LocalDate,
    repository: DailyReviewRepository,
    state: DailyReviewState,
    onQueued: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var missing by remember { mutableStateOf<List<String>>(emptyList()) }
    var providerReady by remember { mutableStateOf(false) }
    var generationRequested by remember { mutableStateOf(false) }
    val queued = state.jobState in setOf("PENDING", "RUNNING", "RETRY")
    val generating = generationRequested || queued

    LaunchedEffect(date) {
        providerReady = repository.providerSettings().configured && repository.hasProviderKey()
        missing = repository.prepare(date).missingCriticalFields
    }
    LaunchedEffect(state.jobState) {
        when (state.jobState) {
            "PENDING", "RUNNING", "RETRY" -> generationRequested = true
            "COMPLETED", "FAILED" -> generationRequested = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        Text("今日简评", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("专业判断与下一步行动，不会修改正式计划。", color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(20.dp))

        state.review?.let { review ->
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("教练结论", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(6.dp))
                    Text(review.conclusion, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

                    if (review.assessment.isNotBlank()) {
                        Spacer(Modifier.height(20.dp))
                        Text("专业判断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(review.assessment, style = MaterialTheme.typography.bodyLarge)
                    }

                    review.localSuggestion?.let { suggestion ->
                        Spacer(Modifier.height(20.dp))
                        Text("本地建议", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            buildString {
                                append(suggestion.title)
                                suggestion.estimatedMinutes?.let { append(" · 约 ${it} 分钟") }
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        suggestion.reason?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("仅在没有正式计划时生效，不会修改计划。", color = MaterialTheme.colorScheme.secondary)
                    }

                    if (review.actions.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text("今天怎么做", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        review.actions.forEach { ReviewLine(it) }
                    }
                    if (review.cautions.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("需要留意", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        review.cautions.forEach { ReviewLine(it, error = true) }
                    }
                }
            }

            if (review.evidence.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("判断依据", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                review.evidence.forEach { ReviewLine(it, subdued = true) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "第 ${review.version} 版 · DeepSeek V4 Flash",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(18.dp))
        }

        if (missing.isNotEmpty()) {
            Text("缺少 ${missing.joinToString("、")}", color = MaterialTheme.colorScheme.error)
            Text("仍可按现有事实生成，缺失值不会被当作正常。", color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(14.dp))
        }

        if (generating) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                    Column {
                        Text("正在生成今日简评", fontWeight = FontWeight.SemiBold)
                        Text("通常需要十几秒，可以先返回今天。", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        } else if (!providerReady) {
            OutlinedButton(onClick = onOpenAiSettings, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Text("配置 AI 服务")
            }
        } else {
            Button(
                onClick = {
                    generationRequested = true
                    scope.launch {
                        runCatching { repository.enqueue(date, allowIncomplete = missing.isNotEmpty()) }
                            .onSuccess { result ->
                                when {
                                    result.queued -> {
                                        onQueued()
                                        onMessage("正在生成今日简评")
                                    }
                                    result.configurationMissing -> {
                                        generationRequested = false
                                        onMessage("请先完成 AI 服务配置")
                                    }
                                    else -> {
                                        generationRequested = false
                                        onMessage("今天的相同版本已经生成")
                                    }
                                }
                            }
                            .onFailure {
                                generationRequested = false
                                onMessage("无法生成，请检查 AI 服务配置")
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(if (state.review == null) "生成今日简评" else "根据最新记录重新生成")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReviewLine(text: String, error: Boolean = false, subdued: Boolean = false) {
    Text(
        text = "· $text",
        modifier = Modifier.padding(top = 8.dp),
        color = when {
            error -> MaterialTheme.colorScheme.error
            subdued -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        style = MaterialTheme.typography.bodyLarge,
    )
}
