package io.s2qtech.shenk.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class RoutineScene(val displayName: String) {
    HOME("居家"),
    WALK("健走"),
    RECOVERY("恢复"),
    TRAVEL("外出"),
}

enum class RoutineRole {
    MAIN,
    WARMUP,
    STRETCH,
    COOLDOWN,
    RECOVERY,
    AUXILIARY,
}

enum class RoutineLifecycle { DRAFT, PUBLISHED, ARCHIVED }

enum class ExecutionMode {
    SIMPLE,
    PREPARE_ONLY,
    ALTERNATING,
    BILATERAL_HOLD,
    BILATERAL_REPS,
}

data class StepExecution(
    val mode: ExecutionMode = ExecutionMode.SIMPLE,
    val prepareSeconds: Int = 0,
    val sideSeconds: Int? = null,
    val switchSeconds: Int = 0,
    val sides: List<String> = emptyList(),
)

data class RoutineStep(
    val stepId: String,
    val name: String,
    val phase: String?,
    val durationSeconds: Int,
    val dose: String?,
    val cues: List<String>,
    val warnings: List<String>,
    val breath: String?,
    val mediaAssetId: String?,
    val execution: StepExecution,
    val raw: JsonObject,
)

data class RoutineTemplate(
    val id: String,
    val title: String,
    val version: String,
    val trainingType: String,
    val scene: RoutineScene,
    val role: RoutineRole,
    val lifecycle: RoutineLifecycle,
    val estimatedMinutes: Int?,
    val timerVisible: Boolean,
    val calendarVisible: Boolean,
    val countsTowardTraining: Boolean,
    val steps: List<RoutineStep>,
    val raw: JsonObject,
) {
    val executable: Boolean
        get() = lifecycle == RoutineLifecycle.PUBLISHED && timerVisible && steps.isNotEmpty()
}

data class RoutineDecodeResult(
    val routine: RoutineTemplate?,
    val error: String?,
)

fun decodeRoutineTemplate(record: SharedRecord): RoutineDecodeResult {
    if (record.entity != "routine_templates") return RoutineDecodeResult(null, "实体不是 routine_templates")
    val data = record.data
    return runCatching {
        val id = data.requiredString("id")
        require(id == record.id) { "方案 ID 与记录 ID 不一致" }
        val scene = data.requiredEnum<RoutineScene>("scene")
        val role = data.requiredEnum<RoutineRole>("role")
        val lifecycle = data.requiredEnum<RoutineLifecycle>("lifecycle")
        val steps = (data["steps"] as? JsonArray)
            ?.mapIndexed { index, value -> decodeRoutineStep(value.jsonObject, index) }
            ?: error("缺少 steps")
        require(steps.isNotEmpty()) { "方案没有动作" }
        RoutineTemplate(
            id = id,
            title = data.requiredString("title"),
            version = data.string("version") ?: "1",
            trainingType = data.requiredString("trainingType"),
            scene = scene,
            role = role,
            lifecycle = lifecycle,
            estimatedMinutes = data.number("estimatedMinutes")?.toInt(),
            timerVisible = data.requiredBoolean("timerVisible"),
            calendarVisible = data.requiredBoolean("calendarVisible"),
            countsTowardTraining = data.requiredBoolean("countsTowardTraining"),
            steps = steps,
            raw = data,
        )
    }.fold(
        onSuccess = { RoutineDecodeResult(it, null) },
        onFailure = { RoutineDecodeResult(null, it.message ?: "方案格式错误") },
    )
}

private fun decodeRoutineStep(data: JsonObject, index: Int): RoutineStep {
    val seconds = data.number("durationSeconds")?.toInt() ?: error("第 ${index + 1} 个动作缺少时长")
    require(seconds > 0) { "第 ${index + 1} 个动作时长必须大于 0" }
    val executionData = data["execution"] as? JsonObject
    return RoutineStep(
        stepId = data.requiredString("stepId"),
        name = data.string("name")?.takeIf(String::isNotBlank) ?: data.requiredString("stepId"),
        phase = data.string("phase"),
        durationSeconds = seconds,
        dose = data.string("dose"),
        cues = data.stringList("cues") + data.stringList("tips"),
        warnings = data.stringList("warnings") + data.stringList("safetyNotes"),
        breath = data.string("breath"),
        mediaAssetId = data.string("mediaAssetId"),
        execution = executionData?.let(::decodeExecution) ?: StepExecution(),
        raw = data,
    )
}

private fun decodeExecution(data: JsonObject): StepExecution {
    val mode = data.string("mode")?.let {
        ExecutionMode.entries.firstOrNull { mode -> mode.name.equals(it, ignoreCase = true) }
            ?: error("不支持的 execution.mode: $it")
    } ?: ExecutionMode.SIMPLE
    val prepare = data.number("prepareSeconds")?.toInt() ?: 0
    val side = data.number("sideSeconds")?.toInt()
    val switch = data.number("switchSeconds")?.toInt() ?: 0
    require(prepare >= 0 && switch >= 0) { "准备和换侧时长不能为负数" }
    side?.let { require(it > 0) { "单侧时长必须大于 0" } }
    return StepExecution(
        mode = mode,
        prepareSeconds = prepare,
        sideSeconds = side,
        switchSeconds = switch,
        sides = data.stringList("sides"),
    )
}

private inline fun <reified T : Enum<T>> JsonObject.requiredEnum(key: String): T {
    val value = requiredString(key)
    return enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: error("$key 值无效: $value")
}

private fun JsonObject.requiredString(key: String): String =
    string(key)?.takeIf(String::isNotBlank) ?: error("缺少 $key")

private fun JsonObject.requiredBoolean(key: String): Boolean =
    this[key]?.jsonPrimitive?.booleanOrNull ?: error("缺少 $key")

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.content

private fun JsonObject.number(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

private fun JsonObject.stringList(key: String): List<String> = when (val value = this[key]) {
    is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) }
    is JsonPrimitive -> value.content.takeIf(String::isNotBlank)?.let(::listOf).orEmpty()
    else -> emptyList()
}
