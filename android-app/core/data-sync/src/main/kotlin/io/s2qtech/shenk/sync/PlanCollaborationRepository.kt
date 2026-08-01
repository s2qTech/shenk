package io.s2qtech.shenk.sync

import io.s2qtech.shenk.model.CoachPlanPatchEngine
import io.s2qtech.shenk.model.PlanPatchPreview
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import io.s2qtech.shenk.model.isExplicitDelete
import io.s2qtech.shenk.model.text
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class AppliedPlanPatch(
    val batchId: String,
    val added: Int,
    val updated: Int,
    val deleted: Int,
)

data class PlanImportStatus(
    val latestBatchId: String?,
    val canUndo: Boolean,
    val appliedAt: String?,
)

data class WeeklyFeedback(
    val id: String,
    val from: LocalDate,
    val to: LocalDate,
    val generatedAt: String,
    val markdown: String,
)

data class PendingCoachPatch(
    val id: String,
    val runId: String?,
    val receivedAt: String,
    val effectiveFrom: String,
    val effectiveTo: String?,
    val reason: String?,
    val generatedBy: String?,
    val changeCount: Int,
    val patchText: String,
)

class PlanCollaborationRepository(
    private val records: LocalFirstRepository,
    private val now: () -> Instant = Instant::now,
    private val nextId: () -> String = { UUID.randomUUID().toString() },
) {
    private val json = Json { ignoreUnknownKeys = false }

    suspend fun preview(text: String): PlanPatchPreview =
        CoachPlanPatchEngine.preview(text, records.allRecords())

    fun observeImportStatus(): Flow<PlanImportStatus> = records.observeActive("plan_import_batches").map { batches ->
        val latest = batches.maxByOrNull { it.data.text("appliedAt").orEmpty() }
        PlanImportStatus(
            latestBatchId = latest?.id,
            canUndo = latest?.data?.text("status") == "applied",
            appliedAt = latest?.data?.text("appliedAt"),
        )
    }

    fun observeLatestFeedback(): Flow<WeeklyFeedback?> = records.observeActive("feedback_summaries").map { values ->
        values.maxByOrNull { it.data.text("generatedAt").orEmpty() }?.toWeeklyFeedback()
    }

    fun observePendingCoachPatches(): Flow<List<PendingCoachPatch>> =
        records.observeActive("coach_plan_patches").map { values ->
            values.mapNotNull(SharedRecord::toPendingCoachPatch)
                .sortedByDescending(PendingCoachPatch::receivedAt)
        }

    suspend fun apply(text: String): AppliedPlanPatch {
        return applyInternal(text, sourceDraft = null)
    }

    suspend fun applyPending(patchRecordId: String): AppliedPlanPatch {
        val source = requireNotNull(records.get("coach_plan_patches", patchRecordId)) {
            "待确认草案不存在或尚未同步。"
        }
        check(source.deletedAt == null && source.data.text("status") == "pending") {
            "这份草案已经处理，不能重复应用。"
        }
        val patch = requireNotNull(source.data["patch"] as? JsonObject) { "云端草案缺少 patch。" }
        return applyInternal(json.encodeToString(JsonObject.serializer(), patch), source)
    }

    suspend fun rejectPending(patchRecordId: String) {
        val source = requireNotNull(records.get("coach_plan_patches", patchRecordId)) {
            "待确认草案不存在或尚未同步。"
        }
        check(source.deletedAt == null && source.data.text("status") == "pending") {
            "这份草案已经处理。"
        }
        val timestamp = now().toString()
        val rejected = source.withData(buildJsonObject {
            source.data.forEach(::put)
            put("status", JsonPrimitive("rejected"))
            put("handledAt", JsonPrimitive(timestamp))
        })
        records.persistAndEnqueue(rejected, SharedEntityOwner.PLANNING_EXCHANGE)
    }

    private suspend fun applyInternal(text: String, sourceDraft: SharedRecord?): AppliedPlanPatch {
        val current = records.allRecords()
        val preview = CoachPlanPatchEngine.preview(text, current)
        require(preview.valid) { preview.errors.joinToString("\n") }
        val patch = requireNotNull(preview.patch)
        val currentByKey = current.associateBy { it.key.storageKey }
        val timestamp = now().toString()
        val before = linkedMapOf<String, JsonElement>()
        val after = linkedMapOf<String, JsonElement>()
        val outgoing = mutableListOf<SharedRecord>()

        CoachPlanPatchEngine.arrays(patch).forEach { (arrayName, items) ->
            val entity = CoachPlanPatchEngine.entityForArray(arrayName)
            items.forEach { item ->
                val id = requireNotNull(item.text("id"))
                val key = "$entity:$id"
                val existing = currentByKey[key]
                before[key] = existing?.envelope ?: JsonNull
                val record = if (item.isExplicitDelete()) {
                    requireNotNull(existing).withDeletedAt(item.text("deletedAt") ?: timestamp)
                } else {
                    mergePlanRecord(entity, id, existing, item)
                }
                outgoing += record
                after[key] = record.envelope
            }
        }

        val batchId = "plan_batch_${nextId()}"
        val batchData = buildJsonObject {
            put("id", JsonPrimitive(batchId))
            put("patchId", JsonPrimitive(patch.text("patchId") ?: digest(text)))
            put("patchSchema", JsonPrimitive("coach_plan_patch"))
            put("patchVersion", JsonPrimitive(patch.text("contractVersion") ?: "2.0"))
            put("receivedAt", JsonPrimitive(timestamp))
            put("appliedAt", JsonPrimitive(timestamp))
            patch.text("generatedBy")?.let { put("generatedBy", JsonPrimitive(it)) }
            patch.text("reason")?.let { put("reason", JsonPrimitive(it)) }
            put("status", JsonPrimitive("applied"))
            put("affectedEntityIds", buildJsonArray { preview.changes.map { it.id }.distinct().forEach { add(JsonPrimitive(it)) } })
            put("counts", buildJsonObject {
                put("added", JsonPrimitive(preview.added))
                put("updated", JsonPrimitive(preview.updated))
                put("deleted", JsonPrimitive(preview.deleted))
            })
            put("beforeSnapshots", buildJsonObject { before.forEach { (key, value) -> put(key, value) } })
            put("afterSnapshots", buildJsonObject { after.forEach { (key, value) -> put(key, value) } })
        }
        outgoing += SharedRecord.create("plan_import_batches", batchId, batchData, contractVersion = "2.0")
        val owned = outgoing.map { it to SharedEntityOwner.PLANNING }.toMutableList()
        if (sourceDraft != null) {
            owned += sourceDraft.withData(buildJsonObject {
                sourceDraft.data.forEach(::put)
                put("status", JsonPrimitive("applied"))
                put("handledAt", JsonPrimitive(timestamp))
                put("appliedBatchId", JsonPrimitive(batchId))
            }) to SharedEntityOwner.PLANNING_EXCHANGE
        }
        records.persistOwnedBatchAndEnqueue(owned)
        return AppliedPlanPatch(batchId, preview.added, preview.updated, preview.deleted)
    }

    suspend fun undoLatest(): String {
        val all = records.allRecords()
        val latest = all
            .filter { it.entity == "plan_import_batches" && it.deletedAt == null }
            .maxByOrNull { it.data.text("appliedAt").orEmpty() }
            ?: error("没有可撤销的计划草案。")
        check(latest.data.text("status") == "applied") { "最近一次计划草案已经撤销。" }
        val before = latest.data["beforeSnapshots"]?.jsonObject ?: error("草案缺少撤销快照。")
        val after = latest.data["afterSnapshots"]?.jsonObject ?: error("草案缺少应用快照。")
        val currentByKey = all.associateBy { it.key.storageKey }
        after.forEach { (key, snapshot) ->
            val current = currentByKey[key] ?: error("计划记录已经变化，不能安全撤销。")
            val expected = snapshot.jsonObject
            if (current.data != expected["data"]?.jsonObject || current.deletedAt != expected.nullableText("deletedAt")) {
                error("计划记录已经在草案应用后被修改，不能覆盖后续变更。")
            }
        }

        val timestamp = now().toString()
        val outgoing = before.map { (key, snapshot) ->
            val split = key.indexOf(':')
            require(split > 0) { "撤销快照键无效" }
            val entity = key.substring(0, split)
            val id = key.substring(split + 1)
            val current = requireNotNull(currentByKey[key])
            if (snapshot is JsonNull) current.withDeletedAt(timestamp)
            else current.restoreBusinessSnapshot(snapshot.jsonObject)
        }.toMutableList()
        val undoneBatch = latest.withData(buildJsonObject {
            latest.data.forEach(::put)
            put("status", JsonPrimitive("undone"))
            put("undoneAt", JsonPrimitive(timestamp))
        })
        outgoing += undoneBatch
        records.persistBatchAndEnqueue(outgoing, SharedEntityOwner.PLANNING)
        return latest.id
    }

    suspend fun generateWeeklyFeedback(today: LocalDate = LocalDate.now()): WeeklyFeedback {
        val all = records.allRecords().filter { it.deletedAt == null }
        val generatedAt = now().toString()
        val detailFrom = today.minusDays(13)
        val trendFrom = today.minusDays(29)
        val feedback = buildFeedback(all, detailFrom, trendFrom, today, generatedAt)
        val data = buildJsonObject {
            put("id", JsonPrimitive(feedback.id))
            put("schema", JsonPrimitive("shenk_feedback_summary"))
            put("schemaVersion", JsonPrimitive("2.0"))
            put("generatedAt", JsonPrimitive(generatedAt))
            put("period", buildJsonObject {
                put("from", JsonPrimitive(detailFrom.toString()))
                put("to", JsonPrimitive(today.toString()))
            })
            put("trendPeriod", buildJsonObject {
                put("from", JsonPrimitive(trendFrom.toString()))
                put("to", JsonPrimitive(today.toString()))
            })
            put("markdown", JsonPrimitive(feedback.markdown))
            put("recordCounts", buildRecordCounts(all, detailFrom, today))
        }
        records.persistAndEnqueue(
            SharedRecord.create("feedback_summaries", feedback.id, data, contractVersion = "2.0"),
            SharedEntityOwner.RECORD,
        )
        return feedback
    }

    private fun buildFeedback(
        all: List<SharedRecord>,
        detailFrom: LocalDate,
        trendFrom: LocalDate,
        today: LocalDate,
        generatedAt: String,
    ): WeeklyFeedback {
        val logs = all.entityInRange("training_logs", detailFrom, today)
        val sessions = all.entityInRange("timer_sessions", detailFrom, today)
        val checkins = all.entityInRange("status_checkins", detailFrom, today)
        val metrics = all.entityInRange("body_metrics", trendFrom, today)
        val plans = all.filter { it.entity == "plan_templates" && it.data.text("lifecycle") != "archived" && it.data.text("status") != "archived" }
        val routines = all.filter { it.entity == "routine_templates" && it.data.text("lifecycle") == "published" }
        val skipped = logs.count { it.data.text("status") in setOf("skipped", "rested") }
        val shortened = logs.count { it.data.text("status") in setOf("short_version", "stretch_only", "modified_by_user") }
        val painLines = checkins.mapNotNull { record ->
            val pain = record.data["pain"] as? JsonArray ?: return@mapNotNull null
            val active = pain.mapNotNull { it as? JsonObject }.filter { (it["severity"] as? JsonPrimitive)?.intOrNull?.let { value -> value > 0 } == true }
            active.takeIf { it.isNotEmpty() }?.joinToString("、") { "${it.text("region") ?: "other"} ${it["severity"]?.jsonPrimitive?.content}" }
                ?.let { "${record.data.text("date")}：$it" }
        }
        val metricLines = listOf(
            "体重" to "weightKg",
            "腰围" to "waistCm",
            "体脂" to "bodyFatPct",
            "肌肉量" to "muscleKg",
        ).map { (label, field) -> metricTrendLine(label, field, metrics) }
        val markdown = buildString {
            appendLine("# 身刻周复盘资料")
            appendLine()
            appendLine("- 明细范围：$detailFrom 至 $today（14 天）")
            appendLine("- 趋势范围：$trendFrom 至 $today（30 天）")
            appendLine("- 生成时间：$generatedAt")
            appendLine()
            appendLine("## 训练事实")
            appendLine("- 正式训练 ${logs.size} 条；跳过/休息 $skipped 条；短版/调整 $shortened 条。")
            appendLine("- 计时器执行 ${sessions.size} 条。")
            logs.sortedBy { it.data.text("date") }.forEach { record ->
                val data = record.data
                val duration = data["durationSec"]?.jsonPrimitive?.intOrNull?.let { "${it / 60} 分钟" } ?: "时长未记"
                appendLine("- ${data.text("date")}｜${data.text("title") ?: data.text("type") ?: "训练"}｜${data.text("status") ?: "completed"}｜$duration${data.text("notes")?.let { "｜$it" }.orEmpty()}")
            }
            appendLine()
            appendLine("## 身体趋势")
            metricLines.forEach { appendLine("- $it") }
            appendLine("- 状态记录 ${checkins.size} 条。")
            appendLine("- 未解决疼痛/不适：${painLines.ifEmpty { listOf("无已记录异常") }.joinToString("；")}。")
            appendLine()
            appendLine("## 计划上下文")
            appendLine("- 当前计划：${plans.joinToString { "${it.data.text("title") ?: it.id} ${it.data.text("version").orEmpty()}" }.ifBlank { "未记录" }}")
            appendLine("- 现行方案：${routines.joinToString { "${it.data.text("title") ?: it.id} ${it.data.text("version").orEmpty()}" }.ifBlank { "未记录" }}")
            appendLine()
            appendLine("请基于事实复盘执行偏差、身体反应和下一周安排；关键数据不足时先追问，不要推断缺失值。")
        }
        return WeeklyFeedback(
            id = "feedback_${detailFrom}_${today}_${nextId()}",
            from = detailFrom,
            to = today,
            generatedAt = generatedAt,
            markdown = markdown,
        )
    }
}

private fun mergePlanRecord(entity: String, id: String, existing: SharedRecord?, item: JsonObject): SharedRecord {
    val mergedData = buildJsonObject {
        existing?.data?.forEach(::put)
        item.forEach { (key, value) -> if (key !in setOf("operation", "deletedAt")) put(key, value) }
        put("id", JsonPrimitive(id))
    }
    return if (existing == null) SharedRecord.create(entity, id, mergedData, contractVersion = "2.0")
    else existing.withData(mergedData).restoreDeletedAt(null)
}

private fun SharedRecord.withData(value: JsonObject): SharedRecord = SharedRecord(buildJsonObject {
    envelope.forEach(::put)
    put("contractVersion", JsonPrimitive("2.0"))
    put("data", value)
})

private fun SharedRecord.withDeletedAt(value: String): SharedRecord = SharedRecord(buildJsonObject {
    envelope.forEach(::put)
    put("contractVersion", JsonPrimitive("2.0"))
    put("deletedAt", JsonPrimitive(value))
})

private fun SharedRecord.restoreDeletedAt(value: String?): SharedRecord = SharedRecord(buildJsonObject {
    envelope.forEach(::put)
    put("deletedAt", value?.let(::JsonPrimitive) ?: JsonNull)
})

private fun SharedRecord.restoreBusinessSnapshot(snapshot: JsonObject): SharedRecord = SharedRecord(buildJsonObject {
    envelope.forEach(::put)
    put("contractVersion", JsonPrimitive("2.0"))
    put("deletedAt", snapshot.nullableText("deletedAt")?.let(::JsonPrimitive) ?: JsonNull)
    put("data", requireNotNull(snapshot["data"] as? JsonObject))
})

private fun JsonObject.nullableText(key: String): String? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray())
    .take(12)
    .joinToString("") { "%02x".format(it) }

private fun List<SharedRecord>.entityInRange(entity: String, from: LocalDate, to: LocalDate): List<SharedRecord> =
    filter { record ->
        if (record.entity != entity) return@filter false
        val date = record.data.text("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@filter false
        date in from..to
    }

private fun metricTrendLine(label: String, field: String, metrics: List<SharedRecord>): String {
    val values = metrics.mapNotNull { record ->
        val date = record.data.text("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
        val value = record.data[field]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
        date to value
    }.sortedBy { it.first }
    if (values.isEmpty()) return "$label：无记录"
    val first = values.first()
    val last = values.last()
    return "$label：${"%.1f".format(last.second)}，${first.first} 至 ${last.first} 变化 ${"%+.1f".format(last.second - first.second)}"
}

private fun buildRecordCounts(all: List<SharedRecord>, from: LocalDate, to: LocalDate): JsonObject = buildJsonObject {
    listOf("training_logs", "timer_sessions", "body_metrics", "status_checkins").forEach { entity ->
        put(entity, JsonPrimitive(all.entityInRange(entity, from, to).size))
    }
}

private fun SharedRecord.toWeeklyFeedback(): WeeklyFeedback? = runCatching {
    val period = data["period"]!!.jsonObject
    WeeklyFeedback(
        id = id,
        from = LocalDate.parse(period.text("from")),
        to = LocalDate.parse(period.text("to")),
        generatedAt = requireNotNull(data.text("generatedAt")),
        markdown = requireNotNull(data.text("markdown")),
    )
}.getOrNull()

private fun SharedRecord.toPendingCoachPatch(): PendingCoachPatch? = runCatching {
    if (data.text("status") != "pending") return null
    val patch = data["patch"] as? JsonObject ?: return null
    val changeCount = listOf("planTemplates", "routineTemplates", "dailyPlanItems", "planAdjustments")
        .sumOf { name -> (patch[name] as? JsonArray)?.size ?: 0 }
    PendingCoachPatch(
        id = id,
        runId = data.text("runId"),
        receivedAt = requireNotNull(data.text("receivedAt")),
        effectiveFrom = requireNotNull(patch.text("effectiveFrom")),
        effectiveTo = patch.text("effectiveTo"),
        reason = patch.text("reason"),
        generatedBy = patch.text("generatedBy"),
        changeCount = changeCount,
        patchText = Json.encodeToString(JsonObject.serializer(), patch),
    )
}.getOrNull()
