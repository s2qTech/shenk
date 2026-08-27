package io.s2qtech.shenk

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import io.s2qtech.shenk.sync.DailyReviewRepository
import io.s2qtech.shenk.sync.DailyReviewState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DailyReviewSheet(
    date: LocalDate,
    repository: DailyReviewRepository,
    state: DailyReviewState,
    onQueued: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onMessage: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var missing by remember { mutableStateOf<List<String>>(emptyList()) }
    var providerReady by remember { mutableStateOf(false) }
    var preparationLoaded by remember(date) { mutableStateOf(false) }
    var autoGenerationAttempted by remember(date) { mutableStateOf(false) }
    var generationRequested by remember(date) { mutableStateOf(false) }
    val processing = state.jobState in setOf("PENDING", "RUNNING", "AWAITING_SERVER")
    val generating = generationRequested || processing
    val isToday = date == LocalDate.now()
    val reviewLabel = if (isToday) "今日简评" else "当日简评"

    fun requestGeneration(startedMessage: String, failedMessage: String) {
        if (generationRequested || processing) return
        generationRequested = true
        scope.launch {
            var queued = false
            try {
                val result = repository.enqueue(date, allowIncomplete = missing.isNotEmpty())
                queued = result.queued
                when {
                    result.queued -> {
                        onQueued()
                        onMessage(startedMessage)
                    }
                    result.configurationMissing -> onMessage("请先完成 AI 服务配置")
                    else -> onMessage("这一天的相同版本已经生成")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                onMessage(failedMessage)
            } finally {
                if (!queued) generationRequested = false
            }
        }
    }

    LaunchedEffect(date) {
        repository.recoverInterruptedJobs()
        val ready = repository.providerSettings().configured && repository.hasProviderKey()
        val prepared = repository.prepare(date)
        providerReady = ready
        missing = prepared.missingCriticalFields
        preparationLoaded = true
    }
    LaunchedEffect(state.jobState, state.review) {
        if (state.jobState != null || state.review != null) generationRequested = false
    }
    LaunchedEffect(preparationLoaded, providerReady, missing, state.review, state.jobState) {
        if (shouldAutoStartDailyReview(
                preparationLoaded = preparationLoaded,
                providerReady = providerReady,
                missing = missing,
                reviewPresent = state.review != null,
                jobState = state.jobState,
                attempted = autoGenerationAttempted,
            )
        ) {
            autoGenerationAttempted = true
            requestGeneration("正在生成$reviewLabel", "无法生成，请检查 AI 服务配置")
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        onBack?.let { goBack ->
            TextButton(onClick = goBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                Text("返回日期详情")
            }
            Spacer(Modifier.height(4.dp))
        }
        Text(reviewLabel, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text(
            date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "复盘当天执行，给出后续修正；不会修改正式计划。",
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))

        state.review?.let { review ->
            ReviewSectionCard(
                title = "今日评价",
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    review.conclusion,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (review.assessment.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                ReviewSectionCard(title = "复盘分析") {
                    Text(review.assessment, style = MaterialTheme.typography.bodyLarge)
                }
            }

            review.localSuggestion?.let { suggestion ->
                Spacer(Modifier.height(12.dp))
                ReviewSectionCard(title = "本地建议") {
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
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "仅在没有正式计划时生效，不会修改计划。",
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (review.actions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ReviewSectionCard(title = "接下来怎么做") {
                    review.actions.forEachIndexed { index, action ->
                        NumberedReviewLine(index + 1, action)
                    }
                }
            }

            if (review.cautions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ReviewSectionCard(
                    title = "需要留意",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    labelColor = MaterialTheme.colorScheme.error,
                ) {
                    review.cautions.forEach { ReviewFactLine(it) }
                }
            }

            if (review.evidence.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ReviewSectionCard(
                    title = "判断依据",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    review.evidence.forEach { ReviewFactLine(humanizeDailyReviewEvidence(it), subdued = true) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "第 ${review.version} 版 · DeepSeek V4 Flash",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(20.dp))
        }

        if (missing.isNotEmpty()) {
            ReviewSectionCard(
                title = "资料不完整",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                labelColor = MaterialTheme.colorScheme.tertiary,
            ) {
                Text("缺少 ${missing.joinToString("、")}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(5.dp))
                Text("仍可按现有事实生成，缺失值不会被当作正常。", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(14.dp))
        }

        if (state.review == null && !preparationLoaded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                Text("正在检查简评条件", color = MaterialTheme.colorScheme.secondary)
            }
        } else if (state.review == null && generating) {
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
                        Text("正在生成$reviewLabel", fontWeight = FontWeight.SemiBold)
                        Text("服务端会继续处理，可以先返回日期详情。", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        } else if (state.review == null && state.jobState in setOf("RETRY", "FAILED")) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("简评暂未完成", fontWeight = FontWeight.SemiBold)
                    Text(
                        dailyReviewFailureMessage(state.jobError, state.jobState == "RETRY"),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            requestGeneration("已重新开始生成$reviewLabel", "无法重试，请检查网络或 AI 服务")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("立即重试")
                    }
                }
            }
        } else if (state.review == null && !providerReady) {
            OutlinedButton(onClick = onOpenAiSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Text("配置 AI 服务")
            }
        } else if (state.review == null) {
            Button(
                onClick = { requestGeneration("正在生成$reviewLabel", "无法生成，请检查 AI 服务配置") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            ) {
                Text(if (missing.isEmpty()) "生成$reviewLabel" else "按现有事实生成$reviewLabel")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

internal fun shouldAutoStartDailyReview(
    preparationLoaded: Boolean,
    providerReady: Boolean,
    missing: List<String>,
    reviewPresent: Boolean,
    jobState: String?,
    attempted: Boolean,
): Boolean = preparationLoaded && providerReady && missing.isEmpty() && !reviewPresent && jobState == null && !attempted

internal fun dailyReviewFailureMessage(error: String?, retrying: Boolean): String = when (error) {
    "ai_provider_http_401", "ai_provider_http_403" -> "DeepSeek 拒绝了当前 API Key，请到设置中重新测试或更换。"
    "ai_provider_http_402" -> "DeepSeek 账户余额或调用额度不足，请充值后重试。"
    "ai_provider_http_400", "ai_provider_http_404" -> "DeepSeek V4 Flash 当前不可用或账户无权使用。"
    "ai_provider_http_429" -> "DeepSeek 当前请求过多，稍后会自动重试。"
    "ai_provider_response_invalid", "ai_provider_review_invalid", "ai_provider_review_actions_missing" ->
        "DeepSeek 返回的简评不完整，稍后会自动重试。"
    "ai_provider_output_truncated" -> "DeepSeek 返回内容被截断，结构修复仍未完成，请重新尝试。"
    "ai_provider_job_expired", "ai_provider_job_abandoned" ->
        "上一次生成连接意外中断，服务端已确认任务不再运行，可以重新尝试。"
    "generation_timeout", "ai_provider_timeout" -> if (retrying) {
        "DeepSeek 本次生成时间较长，稍后会自动重试。"
    } else {
        "DeepSeek 多次生成超时，请稍后重试。"
    }
    "ai_provider_unreachable" -> "云端暂时无法连接 DeepSeek，稍后会自动重试。"
    else -> if (retrying) "网络或 AI 服务暂时不可用，稍后会自动重试。" else "生成失败，请检查 AI 服务后重试。"
}

@Composable
private fun ReviewSectionCard(
    title: String,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                title,
                modifier = Modifier.semantics { heading() },
                color = labelColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun NumberedReviewLine(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            number.toString().padStart(2, '0'),
            modifier = Modifier.defaultMinSize(minWidth = 26.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReviewFactLine(text: String, subdued: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("•", modifier = Modifier.width(10.dp), color = if (subdued) MaterialTheme.colorScheme.outline else Color.Unspecified)
        Text(
            text,
            modifier = Modifier.weight(1f),
            color = if (subdued) MaterialTheme.colorScheme.secondary else Color.Unspecified,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

internal fun humanizeDailyReviewEvidence(raw: String): String {
    var text = raw.trim()
    text = Regex("计划\\s*(?:为)?\\s*estimatedMinutes\\s*[=:]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
        .replace(text) { "计划时长为 ${it.groupValues[1]} 分钟" }
    text = Regex("(?:实际训练日志|训练日志|实际记录)?\\s*durationSec\\s*[=:]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
        .replace(text) { match ->
            val seconds = match.groupValues[1].toLongOrNull() ?: 0L
            "实际记录时长为 ${formatReviewDuration(seconds)}"
        }
    val replacements = linkedMapOf(
        "sleepDurationMinutes" to "睡眠时长",
        "deepSleepMinutes" to "深睡时长",
        "sleepQuality" to "睡眠质量",
        "workPressure" to "工作压力",
        "averageHeartRate" to "平均心率",
        "perceivedEffort" to "主观强度",
        "distanceKm" to "距离",
        "estimatedMinutes" to "计划时长",
        "durationSec" to "记录时长",
        "status_checkin" to "状态记录",
        "status checkin" to "状态记录",
        "training_logs" to "实际训练记录",
        "body_metrics" to "身体测量",
        "daily_plan_items" to "正式计划",
        "neck_shoulder" to "颈肩",
        "lower_back" to "腰背部",
        "hip_glute" to "髋臀部",
        "thigh_knee" to "大腿与膝部",
        "calf_ankle" to "小腿与踝部",
        "left" to "左侧",
        "right" to "右侧",
        "severity" to "程度",
    )
    replacements.forEach { (machine, display) ->
        text = Regex("(?<![A-Za-z_])${Regex.escape(machine)}(?![A-Za-z_])", RegexOption.IGNORE_CASE)
            .replace(text, display)
    }
    text = Regex("(\\d{4})-(\\d{2})-(\\d{2})").replace(text) {
        "${it.groupValues[2].toInt()}月${it.groupValues[3].toInt()}日"
    }
    text = Regex("程度\\s*[=:]?\\s*(\\d+)(?!\\s*/5)").replace(text) {
        "程度 ${it.groupValues[1]}/5"
    }
    text = Regex(
        "状态记录\\s+(\\d+月\\d+日)\\s+记录\\s+(.+?)\\s+(左侧|右侧)\\s+程度\\s+(\\d+/5)",
    ).replace(text) {
        "${it.groupValues[1]}状态记录：${it.groupValues[3]}${it.groupValues[2]}不适，程度 ${it.groupValues[4]}"
    }
    text = Regex("(\\d+(?:\\.\\d+)?)\\s*km\\b", RegexOption.IGNORE_CASE).replace(text) {
        "${it.groupValues[1]} 公里"
    }
    text = text
        .replace(Regex("\\s*[,，]\\s*"), "，")
        .replace(Regex("\\s*[;；]\\s*"), "；")
        .replace(Regex("\\s+"), " ")
        .replace("计划 计划时长", "计划时长")
        .trim()
    return text
}

private fun formatReviewDuration(seconds: Long): String {
    val minutes = (seconds / 60).coerceAtLeast(0)
    val hours = minutes / 60
    val remainder = minutes % 60
    return when {
        hours == 0L -> "$minutes 分钟"
        remainder == 0L -> "$hours 小时"
        else -> "$hours 小时 $remainder 分钟"
    }
}
