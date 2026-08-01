package io.s2qtech.shenk.model

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class PatchChangeAction { ADD, UPDATE, DELETE }

data class PlanPatchChange(
    val entity: String,
    val id: String,
    val title: String,
    val action: PatchChangeAction,
)

data class PlanPatchPreview(
    val patch: JsonObject?,
    val sourceText: String,
    val changes: List<PlanPatchChange>,
    val errors: List<String>,
    val warnings: List<String>,
) {
    val valid: Boolean get() = patch != null && errors.isEmpty() && changes.isNotEmpty()
    val added: Int get() = changes.count { it.action == PatchChangeAction.ADD }
    val updated: Int get() = changes.count { it.action == PatchChangeAction.UPDATE }
    val deleted: Int get() = changes.count { it.action == PatchChangeAction.DELETE }
}

object CoachPlanPatchEngine {
    private val json = Json { ignoreUnknownKeys = false }
    private val entityArrays = linkedMapOf(
        "planTemplates" to "plan_templates",
        "routineTemplates" to "routine_templates",
        "dailyPlanItems" to "daily_plan_items",
        "planAdjustments" to "plan_adjustments",
    )
    private val trainingTypes = setOf(
        "strength", "easy_walk", "quality_walk", "indoor_cardio", "warmup",
        "cooldown", "recovery", "travel_strength", "seat_recovery", "stretch", "rest",
    )
    private val completionStatuses = setOf(
        "planned", "completed", "short_version", "stretch_only", "skipped", "rested", "modified_by_user",
    )
    private val scenes = setOf("home", "walk", "recovery", "travel")
    private val roles = setOf("main", "warmup", "stretch", "cooldown", "recovery", "auxiliary")
    private val lifecycles = setOf("draft", "published", "archived")
    private val executionModes = setOf("simple", "prepare_only", "alternating", "bilateral_hold", "bilateral_reps")

    fun preview(text: String, existing: List<SharedRecord>): PlanPatchPreview {
        val source = text.trim()
        val patch = runCatching { extractPatch(source) }.getOrElse { error ->
            return PlanPatchPreview(null, source, emptyList(), listOf(error.message ?: "无法识别计划草案"), emptyList())
        }
        val errors = validate(patch).toMutableList()
        val existingByKey = existing.associateBy { it.key.storageKey }
        val changes = mutableListOf<PlanPatchChange>()
        entityArrays.forEach { (arrayName, entity) ->
            val values = patch[arrayName] as? JsonArray ?: return@forEach
            values.forEachIndexed { index, element ->
                val item = element as? JsonObject ?: return@forEachIndexed
                val id = item.text("id")
                if (id.isNullOrBlank()) return@forEachIndexed
                val current = existingByKey["$entity:$id"]
                val deletion = item.isExplicitDelete()
                if (deletion && current == null) {
                    errors += "$arrayName[$index] 要删除的记录不存在：$id"
                    return@forEachIndexed
                }
                changes += PlanPatchChange(
                    entity = entity,
                    id = id,
                    title = item.text("title") ?: current?.data?.text("title") ?: id,
                    action = when {
                        deletion -> PatchChangeAction.DELETE
                        current == null || current.deletedAt != null -> PatchChangeAction.ADD
                        else -> PatchChangeAction.UPDATE
                    },
                )
            }
        }
        val warnings = buildList {
            if (changes.any { it.action == PatchChangeAction.DELETE }) add("草案包含删除，应用前需要再次确认。")
            if ((patch["dailyPlanItems"] as? JsonArray)?.isEmpty() == true) add("dailyPlanItems 为空，本次不会修改日历。")
            if ((patch["planAdjustments"] as? JsonArray)?.isEmpty() == true) add("planAdjustments 为空，本次不会修改调整记录。")
        }
        if (changes.isEmpty() && errors.isEmpty()) errors += "草案没有需要写入的内容。"
        return PlanPatchPreview(patch, source, changes, errors.distinct(), warnings)
    }

    fun extractPatch(text: String): JsonObject {
        require(text.isNotBlank()) { "请先粘贴计划草案。" }
        val candidates = buildList {
            Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
                .findAll(text)
                .forEach { add(it.groupValues[1].trim()) }
            addAll(extractBalancedObjects(text))
            add(text.trim())
        }.distinct()
        candidates.forEach { candidate ->
            val root = runCatching { json.parseToJsonElement(candidate).jsonObject }.getOrNull() ?: return@forEach
            unwrap(root)?.let { return it }
        }
        error("没有找到 schema 为 coach_plan_patch 的 JSON。")
    }

    fun arrays(patch: JsonObject): Map<String, List<JsonObject>> = entityArrays.mapValues { (name, _) ->
        (patch[name] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    }

    fun entityForArray(name: String): String = requireNotNull(entityArrays[name])

    private fun validate(patch: JsonObject): List<String> = buildList {
        if (patch.text("schema") != "coach_plan_patch") add("schema 必须是 coach_plan_patch。")
        if (patch.text("contractVersion") != ContractVersion.PLANNED) add("contractVersion 必须是 2.0。")
        if (!patch.text("effectiveFrom").isIsoDate()) add("effectiveFrom 必须是 YYYY-MM-DD。")
        patch.text("effectiveTo")?.let { if (!it.isIsoDate()) add("effectiveTo 必须是 YYYY-MM-DD。") }
        if ((patch["replaceMode"] as? JsonPrimitive)?.booleanOrNull == true) {
            add("Android 当前只接受 merge/upsert 草案，不接受 replaceMode。")
        }
        entityArrays.forEach { (name, _) ->
            val value = patch[name] ?: return@forEach
            if (value !is JsonArray) {
                add("$name 必须是数组。")
                return@forEach
            }
            value.forEachIndexed { index, element ->
                val item = element as? JsonObject
                if (item == null) add("$name[$index] 不是对象。")
                else validateItem(name, index, item, this)
            }
        }
    }

    private fun validateItem(name: String, index: Int, item: JsonObject, errors: MutableList<String>) {
        val path = "$name[$index]"
        val id = item.text("id")
        if (id.isNullOrBlank()) errors += "$path.id 必填。"
        item.text("operation")?.let { operation ->
            if (operation !in setOf("upsert", "delete")) errors += "$path.operation 只能是 upsert 或 delete。"
        }
        if (item.isExplicitDelete()) return
        when (name) {
            "planTemplates" -> if (item.text("title").isNullOrBlank()) errors += "$path.title 必填。"
            "routineTemplates" -> validateRoutine(path, item, errors)
            "dailyPlanItems" -> {
                if (!item.text("date").isIsoDate()) errors += "$path.date 必须是 YYYY-MM-DD。"
                if (item.text("title").isNullOrBlank()) errors += "$path.title 必填。"
                if (item.text("trainingType") !in trainingTypes) errors += "$path.trainingType 无效。"
                if (item.text("status") !in completionStatuses) errors += "$path.status 无效。"
            }
            "planAdjustments" -> {
                if (!item.text("date").isIsoDate()) errors += "$path.date 必须是 YYYY-MM-DD。"
                if (item.text("reason").isNullOrBlank()) errors += "$path.reason 必填。"
                if (item["toSnapshot"] !is JsonObject) errors += "$path.toSnapshot 必须是完整计划快照。"
            }
        }
    }

    private fun validateRoutine(path: String, item: JsonObject, errors: MutableList<String>) {
        if (item.text("title").isNullOrBlank()) errors += "$path.title 必填。"
        if (item.text("trainingType") !in trainingTypes) errors += "$path.trainingType 无效。"
        if (item.text("scene") !in scenes) errors += "$path.scene 必须显式填写 home / walk / recovery / travel。"
        if (item.text("role") !in roles) errors += "$path.role 必须显式填写。"
        if (item.text("lifecycle") !in lifecycles) errors += "$path.lifecycle 必须是 draft / published / archived。"
        listOf("timerVisible", "calendarVisible", "countsTowardTraining").forEach { field ->
            if ((item[field] as? JsonPrimitive)?.booleanOrNull == null) errors += "$path.$field 必须显式填写 true 或 false。"
        }
        val steps = item["steps"] as? JsonArray
        if (steps.isNullOrEmpty()) {
            errors += "$path.steps 必须是非空动作数组。"
            return
        }
        steps.forEachIndexed { index, element ->
            val step = element as? JsonObject
            val stepPath = "$path.steps[$index]"
            if (step == null) {
                errors += "$stepPath 不是对象。"
                return@forEachIndexed
            }
            if (step.text("stepId").isNullOrBlank()) errors += "$stepPath.stepId 必填。"
            if ((step["durationSeconds"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.let { it > 0 } != true) {
                errors += "$stepPath.durationSeconds 必须大于 0。"
            }
            val execution = step["execution"] ?: return@forEachIndexed
            if (execution !is JsonObject) {
                errors += "$stepPath.execution 必须是对象。"
                return@forEachIndexed
            }
            val mode = execution.text("mode") ?: "simple"
            if (mode !in executionModes) errors += "$stepPath.execution.mode 无效。"
        }
    }

    private fun unwrap(root: JsonObject): JsonObject? = when {
        root.text("schema") == "coach_plan_patch" -> root
        root["coach_plan_patch"] is JsonObject -> unwrap(root["coach_plan_patch"]!!.jsonObject)
        root["patch"] is JsonObject -> unwrap(root["patch"]!!.jsonObject)
        else -> null
    }

    private fun extractBalancedObjects(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = -1
        var depth = 0
        var quoted = false
        var escaped = false
        text.forEachIndexed { index, char ->
            if (quoted) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> quoted = false
                }
            } else when (char) {
                '"' -> quoted = true
                '{' -> {
                    if (depth == 0) start = index
                    depth += 1
                }
                '}' -> {
                    depth -= 1
                    if (depth == 0 && start >= 0) {
                        result += text.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return result
    }
}

fun JsonObject.isExplicitDelete(): Boolean =
    text("operation") == "delete" || this["deletedAt"]?.let { it !is JsonNull } == true

fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun String?.isIsoDate(): Boolean =
    this != null && runCatching { LocalDate.parse(this) }.isSuccess && length == 10
