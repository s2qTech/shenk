package io.s2qtech.shenk.sync

import io.s2qtech.shenk.model.RoutineScene
import io.s2qtech.shenk.model.RoutineTemplate
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import io.s2qtech.shenk.model.TimerSessionFact
import io.s2qtech.shenk.model.TimerStepResult
import io.s2qtech.shenk.model.decodeRoutineTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RoutineLibrary(
    val byScene: Map<RoutineScene, List<RoutineTemplate>>,
    val rejectedCount: Int,
) {
    val routines: List<RoutineTemplate> get() = byScene.values.flatten()
}

data class PendingTimerCompletion(
    val session: TimerSessionFact,
    val routineTitle: String,
)

class RoutineLibraryRepository(
    private val records: LocalFirstRepository,
) {
    fun observeLibrary(): Flow<RoutineLibrary> = records.observeActive("routine_templates").map { rows ->
        val decoded = rows.map(::decodeRoutineTemplate)
        val routines = decoded.mapNotNull { it.routine }.filter(RoutineTemplate::executable)
        RoutineLibrary(
            byScene = RoutineScene.entries.associateWith { scene ->
                routines.filter { it.scene == scene }.sortedBy(RoutineTemplate::title)
            },
            rejectedCount = decoded.count { it.routine == null },
        )
    }
}

class NativeTimerSessionRepository(
    private val records: LocalFirstRepository,
) {
    suspend fun persistIfAbsent(session: TimerSessionFact): Boolean {
        if (records.get("timer_sessions", session.id) != null) return false
        records.persistAndEnqueue(
            SharedRecord.create(
                entity = "timer_sessions",
                id = session.id,
                data = session.toJson(),
                contractVersion = "2.0",
            ),
            SharedEntityOwner.TIMER,
        )
        return true
    }

    fun observePendingCompletion(): Flow<List<PendingTimerCompletion>> = combine(
        records.observeActive("timer_sessions"),
        records.observeActive("training_logs"),
    ) { sessions, logs ->
        val linkedSessionIds = buildSet {
            logs.forEach { record ->
                record.data.string("timerSessionId")?.let(::add)
                (record.data["timerSessionIds"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                    ?.let(::addAll)
            }
        }
        sessions.mapNotNull(::decodeTimerSession)
            .filter { it.id !in linkedSessionIds && it.completion in setOf("completed", "stopped") }
            .sortedByDescending(TimerSessionFact::startedAt)
            .map { session ->
                PendingTimerCompletion(
                    session = session,
                    routineTitle = session.routineSnapshot.string("title") ?: "训练流程",
                )
            }
    }
}

private fun TimerSessionFact.toJson(): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(id))
    put("date", JsonPrimitive(date))
    put("routineId", JsonPrimitive(routineId))
    put("routineVersion", JsonPrimitive(routineVersion))
    put("routineDigest", JsonPrimitive(routineDigest))
    put("routineSnapshot", routineSnapshot)
    dailyPlanItemId?.let { put("dailyPlanItemId", JsonPrimitive(it)) }
    planTemplateId?.let { put("planTemplateId", JsonPrimitive(it)) }
    put("trainingType", JsonPrimitive(trainingType))
    put("startedAt", JsonPrimitive(startedAt))
    put("endedAt", endedAt?.let(::JsonPrimitive) ?: JsonNull)
    put("completion", JsonPrimitive(completion))
    put("actualSeconds", JsonPrimitive(actualSeconds))
    put("activeSeconds", JsonPrimitive(activeSeconds))
    put("elapsedSeconds", JsonPrimitive(elapsedSeconds))
    put("pausedSeconds", JsonPrimitive(pausedSeconds))
    put("calendarVisible", JsonPrimitive(calendarVisible))
    put("countsTowardTraining", JsonPrimitive(countsTowardTraining))
    interruptionReason?.let { put("interruptionReason", JsonPrimitive(it)) }
    put("stepResults", buildJsonArray {
        stepResults.forEach { result ->
            add(buildJsonObject {
                put("stepId", JsonPrimitive(result.stepId))
                put("logicalIndex", JsonPrimitive(result.logicalIndex))
                put("plannedSeconds", JsonPrimitive(result.plannedSeconds))
                put("completedSeconds", JsonPrimitive(result.completedSeconds))
                put("completed", JsonPrimitive(result.completed))
            })
        }
    })
    put("devicePlatform", JsonPrimitive(devicePlatform))
    put("idempotencyKey", JsonPrimitive(idempotencyKey))
}

internal fun decodeTimerSession(record: SharedRecord): TimerSessionFact? = runCatching {
    val data = record.data
    TimerSessionFact(
        id = data.string("id") ?: record.id,
        date = requireNotNull(data.string("date")),
        routineId = requireNotNull(data.string("routineId")),
        routineVersion = requireNotNull(data.string("routineVersion")),
        routineDigest = requireNotNull(data.string("routineDigest")),
        routineSnapshot = data["routineSnapshot"]?.jsonObject ?: buildJsonObject {},
        dailyPlanItemId = data.string("dailyPlanItemId"),
        planTemplateId = data.string("planTemplateId"),
        trainingType = data.string("trainingType") ?: "recovery",
        startedAt = requireNotNull(data.string("startedAt")),
        endedAt = data.string("endedAt"),
        completion = requireNotNull(data.string("completion")),
        actualSeconds = data.int("actualSeconds") ?: data.int("activeSeconds") ?: 0,
        activeSeconds = data.int("activeSeconds") ?: data.int("actualSeconds") ?: 0,
        elapsedSeconds = data.int("elapsedSeconds") ?: data.int("actualSeconds") ?: 0,
        pausedSeconds = data.int("pausedSeconds") ?: 0,
        calendarVisible = data.boolean("calendarVisible") ?: true,
        countsTowardTraining = data.boolean("countsTowardTraining") ?: true,
        interruptionReason = data.string("interruptionReason"),
        stepResults = (data["stepResults"] as? JsonArray).orEmpty().mapNotNull { value ->
            runCatching {
                val item = value.jsonObject
                TimerStepResult(
                    stepId = requireNotNull(item.string("stepId")),
                    logicalIndex = requireNotNull(item.int("logicalIndex")),
                    plannedSeconds = requireNotNull(item.int("plannedSeconds")),
                    completedSeconds = requireNotNull(item.int("completedSeconds")),
                    completed = requireNotNull(item.boolean("completed")),
                )
            }.getOrNull()
        },
        devicePlatform = data.string("devicePlatform") ?: "web",
        idempotencyKey = data.string("idempotencyKey") ?: record.id,
    )
}.getOrNull()

private fun JsonObject.string(key: String): String? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

private fun JsonObject.int(key: String): Int? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull

private fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull
