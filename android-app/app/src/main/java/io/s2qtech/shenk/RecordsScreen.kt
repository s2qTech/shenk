package io.s2qtech.shenk

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.s2qtech.shenk.model.RecordEditPolicy
import io.s2qtech.shenk.model.TrainingLog
import io.s2qtech.shenk.sync.CalendarRecordRepository
import io.s2qtech.shenk.sync.SyncScheduler
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(
    repository: CalendarRecordRepository,
    onBack: () -> Unit,
) {
    val logs by repository.observeTrainingLogs().collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<TrainingLog?>(null) }
    val today = remember { LocalDate.now() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            SpaceHeader("记录", "正式训练事实", onBack)
            if (logs.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("还没有正式训练记录", style = MaterialTheme.typography.titleLarge)
                    Text("记录会先保存在本机，联网后再同步。", color = MaterialTheme.colorScheme.secondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(logs, key = { it.id }) { log ->
                        RecordRow(log, canEdit = canEdit(log, today), onClick = { selected = log })
                    }
                }
            }
        }
    }

    selected?.let { log ->
        val date = runCatching { LocalDate.parse(log.date) }.getOrElse { today.minusYears(100) }
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            TrainingLogEditorSheet(
                date = date,
                existing = log,
                readOnly = !RecordEditPolicy.canEdit(date, today),
                onSave = { updated ->
                    scope.launch {
                        repository.saveTrainingLog(updated)
                        SyncScheduler(context).enqueue()
                        selected = null
                        snackbar.showSnackbar("记录已修正")
                    }
                },
                onDelete = { deleted ->
                    scope.launch {
                        repository.deleteTrainingLog(deleted.id)
                        SyncScheduler(context).enqueue()
                        selected = null
                        val result = snackbar.showSnackbar(
                            "记录已删除",
                            actionLabel = "撤销",
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            repository.restoreTrainingLog(deleted)
                            SyncScheduler(context).enqueue()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun RecordRow(log: TrainingLog, canEdit: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(log.date, color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelMedium)
                Text(log.displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(recordSummary(log), color = MaterialTheme.colorScheme.secondary)
            }
            Text(if (canEdit) "修正" else "查看", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun SpaceHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.secondary)
        }
        TextButton(onClick = onBack) { Text("返回今天") }
    }
}

private fun canEdit(log: TrainingLog, today: LocalDate): Boolean =
    runCatching { RecordEditPolicy.canEdit(LocalDate.parse(log.date), today) }.getOrDefault(false)

private fun recordSummary(log: TrainingLog): String = buildList {
    log.durationMinutes?.let { add("$it 分") }
    log.distanceKm?.let { add("%.2f km".format(it)) }
    log.averageHeartRate?.let { add("均心 $it") }
    log.perceivedEffort?.let { add("体感 $it/10") }
}.ifEmpty { listOf("暂无数值") }.joinToString(" · ")
