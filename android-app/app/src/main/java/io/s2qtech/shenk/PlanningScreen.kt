package io.s2qtech.shenk

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.model.PatchChangeAction
import io.s2qtech.shenk.model.PlanPatchPreview
import io.s2qtech.shenk.sync.PlanCollaborationRepository
import io.s2qtech.shenk.sync.PlanImportStatus
import io.s2qtech.shenk.sync.SyncScheduler
import io.s2qtech.shenk.sync.WeeklyFeedback
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private enum class PlanningAction { IMPORT, FEEDBACK }

@Composable
fun PlanningRoute(
    repository: PlanCollaborationRepository,
    initialFeedback: Boolean = false,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val importStatus by repository.observeImportStatus().collectAsState(PlanImportStatus(null, false, null))
    val latestFeedback by repository.observeLatestFeedback().collectAsState(null)
    var action by remember {
        mutableStateOf<PlanningAction?>(if (initialFeedback) PlanningAction.FEEDBACK else null)
    }
    var patchText by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<PlanPatchPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    BackHandler {
        if (action == null) onBack() else action = null
    }

    Box(
        modifier = Modifier.fillMaxSize().testTag("planning-screen"),
    ) {
        when (action) {
            null -> PlanningActionChooser(
                onImport = { action = PlanningAction.IMPORT },
                onFeedback = { action = PlanningAction.FEEDBACK },
            )
            PlanningAction.IMPORT -> PlanInbox(
                modifier = Modifier,
                patchText = patchText,
                preview = preview,
                status = importStatus,
                busy = busy,
                onBack = { action = null },
                onTextChange = {
                    patchText = it
                    preview = null
                },
                onPaste = {
                    patchText = clipboard.getText()?.text.orEmpty()
                    preview = null
                },
                onValidate = {
                    scope.launch {
                        preview = repository.preview(patchText)
                    }
                },
                onApply = {
                    val ready = preview ?: return@PlanInbox
                    if (ready.deleted > 0) confirmingDelete = true
                    else scope.applyPatch(repository, patchText, context, snackbar, onBusy = { busy = it }) {
                            preview = null
                            patchText = ""
                    }
                },
                onUndo = {
                    scope.launch {
                        busy = true
                        runCatching { repository.undoLatest() }
                            .onSuccess {
                                SyncScheduler(context).enqueue()
                                busy = false
                                snackbar.showSnackbar("最近一次计划草案已撤销")
                            }
                            .onFailure {
                                busy = false
                                snackbar.showSnackbar(it.message ?: "撤销失败")
                            }
                    }
                },
            )
            PlanningAction.FEEDBACK -> FeedbackWorkspace(
                modifier = Modifier,
                latest = latestFeedback,
                busy = busy,
                onBack = { action = null },
                onGenerate = {
                    scope.launch {
                        busy = true
                        runCatching { repository.generateWeeklyFeedback() }
                            .onSuccess {
                                SyncScheduler(context).enqueue()
                                snackbar.showSnackbar("复盘资料已生成")
                            }
                            .onFailure { snackbar.showSnackbar(it.message ?: "生成失败") }
                        busy = false
                    }
                },
                onCopy = {
                    latestFeedback?.let { feedback ->
                        clipboard.setText(AnnotatedString(feedback.markdown))
                        scope.launch {
                            snackbar.showSnackbar("复盘资料已复制，可粘贴到健身计划任务")
                        }
                    }
                },
            )
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text("确认删除 ${preview?.deleted ?: 0} 条记录？") },
            text = { Text("只有草案中显式声明删除的记录会被处理。应用后仍可撤销最近一次导入。") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmingDelete = false
                        scope.applyPatch(repository, patchText, context, snackbar, onBusy = { busy = it }) {
                                preview = null
                                patchText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("确认应用") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PlanningActionChooser(
    onImport: () -> Unit,
    onFeedback: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 22.dp,
            top = 2.dp,
            end = 22.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { SheetHeader("计划", "导入指定计划，或生成近期复盘资料。") }
        item {
            PlanActionRow(
                icon = { Icon(Icons.Rounded.ContentPaste, contentDescription = null) },
                title = "导入指定计划",
                subtitle = "粘贴并校验计划内容",
                onClick = onImport,
                testTag = "open-plan-import",
            )
        }
        item {
            PlanActionRow(
                icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                title = "生成近期复盘",
                subtitle = "整理近期执行与身体反馈",
                onClick = onFeedback,
                testTag = "open-plan-feedback",
            )
        }
    }
}

@Composable
private fun PlanActionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag(testTag),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) { Box(contentAlignment = Alignment.Center) { icon() } }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun PlanInbox(
    modifier: Modifier,
    patchText: String,
    preview: PlanPatchPreview?,
    status: PlanImportStatus,
    busy: Boolean,
    onBack: () -> Unit,
    onTextChange: (String) -> Unit,
    onPaste: () -> Unit,
    onValidate: () -> Unit,
    onApply: () -> Unit,
    onUndo: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PlanningSubpageHeader(
                title = "导入指定计划",
                subtitle = "粘贴高级 AI 返回的计划内容。身刻会先完整校验并展示变更预览，不会直接覆盖当前计划。",
                onBack = onBack,
            )
        }
        item {
            OutlinedTextField(
                value = patchText,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().height(126.dp).testTag("plan-patch-input"),
                label = { Text("计划草案") },
                placeholder = { Text("在这里粘贴计划内容……") },
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPaste, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                    Spacer(Modifier.padding(3.dp))
                    Text("粘贴")
                }
                Button(onClick = onValidate, enabled = patchText.isNotBlank() && !busy, modifier = Modifier.weight(1f).testTag("validate-plan-patch")) {
                    Text("校验草案")
                }
            }
        }
        preview?.let { result ->
            item { PreviewSummary(result) }
            if (result.errors.isNotEmpty()) {
                items(result.errors) { error -> MessageLine(error, error = true) }
            }
            items(result.warnings) { warning -> MessageLine(warning, error = false) }
            if (result.valid) {
                items(result.changes, key = { "${it.entity}:${it.id}" }) { change ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(change.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entityName(change.entity), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(actionName(change.action), color = actionColor(change.action))
                    }
                }
                item {
                    Button(onClick = onApply, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("apply-plan-patch")) {
                        if (busy) CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp)
                        else Text(if (result.deleted > 0) "确认变更" else "应用草案")
                    }
                }
            }
        }
        if (status.canUndo) {
            item {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("最近一次导入", fontWeight = FontWeight.Medium)
                        Text(
                            formatPlanAppliedAt(status.appliedAt.orEmpty()),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = onUndo, enabled = !busy, modifier = Modifier.testTag("undo-plan-patch")) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Text("撤销")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(22.dp)) }
    }
}

@Composable
private fun PlanningSubpageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = (-12).dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            Text("返回计划")
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun PreviewSummary(preview: PlanPatchPreview) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("plan-patch-preview"),
        shape = RoundedCornerShape(22.dp),
        color = if (preview.valid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(if (preview.valid) "可以应用" else "草案未通过", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PreviewCount("新增", preview.added)
                PreviewCount("更新", preview.updated)
                PreviewCount("删除", preview.deleted)
            }
        }
    }
}

@Composable
private fun PreviewCount(label: String, count: Int) {
    Column {
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MessageLine(text: String, error: Boolean) {
    Text(
        text = text,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun FeedbackWorkspace(
    modifier: Modifier,
    latest: WeeklyFeedback?,
    busy: Boolean,
    onBack: () -> Unit,
    onGenerate: () -> Unit,
    onCopy: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PlanningSubpageHeader(
                title = "生成近期复盘",
                subtitle = "汇总最近训练执行、晨检、疼痛与恢复趋势，生成可交给高级 AI 的规范化复盘资料。",
                onBack = onBack,
            )
        }
        item {
            ShenkStatePanel(
                title = if (latest == null) "还没有本周资料" else "复盘资料已就绪",
                message = latest?.let { "${it.from} 至 ${it.to}" } ?: "生成不会修改计划，只整理已经记录的事实。",
                tone = if (latest == null) ShenkStateTone.NEUTRAL else ShenkStateTone.SUCCESS,
                modifier = Modifier.fillMaxWidth().testTag(if (latest == null) "weekly-feedback-empty" else "weekly-feedback-ready"),
            )
        }
        item {
            Button(onClick = onGenerate, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("generate-weekly-feedback")) {
                if (busy) CircularProgressIndicator(Modifier.height(22.dp), strokeWidth = 2.dp)
                else Text(if (latest == null) "生成复盘资料" else "重新生成")
            }
        }
        if (latest != null) {
            item {
                FilledTonalButton(onClick = onCopy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("copy-weekly-feedback")) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("复制复盘资料")
                }
            }
            item {
                Text("资料包含训练、计时、身体趋势、疼痛变化与当前计划版本；缺失值仍保持缺失。", color = MaterialTheme.colorScheme.secondary)
            }
        }
        item { Spacer(Modifier.height(22.dp)) }
    }
}

private fun kotlinx.coroutines.CoroutineScope.applyPatch(
    repository: PlanCollaborationRepository,
    text: String,
    context: Context,
    snackbar: SnackbarHostState,
    onBusy: (Boolean) -> Unit,
    onSuccess: () -> Unit,
) {
    launch {
        onBusy(true)
        runCatching { repository.apply(text) }
            .onSuccess { result ->
                SyncScheduler(context).enqueue()
                onSuccess()
                onBusy(false)
                snackbar.showSnackbar("已写入：新增 ${result.added}，更新 ${result.updated}，删除 ${result.deleted}")
            }
            .onFailure {
                onBusy(false)
                snackbar.showSnackbar(it.message ?: "应用失败")
            }
    }
}

private fun entityName(entity: String): String = when (entity) {
    "plan_templates" -> "计划模板"
    "routine_templates" -> "训练方案"
    "daily_plan_items" -> "日计划"
    "plan_adjustments" -> "计划调整"
    else -> entity
}

private fun actionName(action: PatchChangeAction): String = when (action) {
    PatchChangeAction.ADD -> "新增"
    PatchChangeAction.UPDATE -> "更新"
    PatchChangeAction.DELETE -> "删除"
}

internal fun formatPlanAppliedAt(raw: String, zoneId: ZoneId = ZoneId.systemDefault()): String =
    runCatching {
        DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA)
            .format(Instant.parse(raw).atZone(zoneId))
    }.getOrElse { raw }

@Composable
private fun actionColor(action: PatchChangeAction) = when (action) {
    PatchChangeAction.ADD -> MaterialTheme.colorScheme.primary
    PatchChangeAction.UPDATE -> MaterialTheme.colorScheme.tertiary
    PatchChangeAction.DELETE -> MaterialTheme.colorScheme.error
}
