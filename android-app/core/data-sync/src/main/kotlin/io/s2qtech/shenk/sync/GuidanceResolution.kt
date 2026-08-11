package io.s2qtech.shenk.sync

import io.s2qtech.shenk.model.DefaultSuggestionResolver
import io.s2qtech.shenk.model.GuidanceSource
import io.s2qtech.shenk.model.SharedRecord
import io.s2qtech.shenk.model.TodayGuidance
import io.s2qtech.shenk.model.TodayGuidanceResolver
import io.s2qtech.shenk.model.TrainingLog
import io.s2qtech.shenk.model.trainingTypeTitle
import java.time.LocalDate
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class ResolvedDay(
    val guidance: TodayGuidance,
    val actualLogs: List<TrainingLog>,
)

internal object GuidanceResolution {
    fun index(
        logs: List<SharedRecord>,
        plans: List<SharedRecord>,
        adjustments: List<SharedRecord>,
        reviews: List<SharedRecord> = emptyList(),
    ): GuidanceResolutionIndex = GuidanceResolutionIndex(logs, plans, adjustments, reviews)

    fun resolve(
        date: LocalDate,
        logs: List<SharedRecord>,
        plans: List<SharedRecord>,
        adjustments: List<SharedRecord>,
        reviews: List<SharedRecord> = emptyList(),
    ): ResolvedDay = index(logs, plans, adjustments, reviews).resolve(date)
}

internal class GuidanceResolutionIndex(
    logs: List<SharedRecord>,
    plans: List<SharedRecord>,
    adjustments: List<SharedRecord>,
    reviews: List<SharedRecord>,
) {
    private val logsByDate = logs
        .asSequence()
        .mapNotNull(::decodeTrainingLog)
        .filter(TrainingLog::calendarVisible)
        .groupBy(TrainingLog::date)
        .mapValues { (_, values) ->
            values.sortedWith(compareByDescending<TrainingLog> { it.updatedAt.orEmpty() }.thenBy { it.id })
        }
    private val plansByDate = plans.latestByDate { it.updatedAt.orEmpty() }
    private val adjustmentsByDate = adjustments.latestByDate {
        it.data.fieldString("adjustedAt") ?: it.updatedAt.orEmpty()
    }
    private val reviewsByDate = reviews
        .asSequence()
        .filter { it.data.fieldString("status") == "generated" }
        .latestByDate { it.data.fieldInt("version") ?: 0 }

    fun resolve(date: LocalDate): ResolvedDay {
        val dateText = date.toString()
        val actualLogs = logsByDate[dateText].orEmpty()
        val actual = actualLogs.firstOrNull()?.let { log ->
            TodayGuidance(
                source = GuidanceSource.ACTUAL,
                title = log.displayTitle,
                trainingType = log.type,
                estimatedMinutes = log.durationMinutes,
                note = log.subjectiveResult ?: log.notes,
            )
        }

        val planRecord = plansByDate[dateText]
        val latestAdjustment = adjustmentsByDate[dateText]
        val effectiveData = latestAdjustment?.data?.let { adjustment ->
            adjustment["toSnapshot"]
                ?.takeUnless { it is JsonNull }
                ?.let { runCatching { it.jsonObject }.getOrNull() }
                ?: adjustment
        } ?: planRecord?.data
        val plan = effectiveData?.let { data ->
            val type = data.fieldString("trainingType") ?: data.fieldString("type") ?: "other"
            TodayGuidance(
                source = GuidanceSource.FORMAL_PLAN,
                title = data.fieldString("title") ?: trainingTypeTitle(type),
                trainingType = type,
                estimatedMinutes = data.fieldDouble("estimatedMinutes")?.toInt(),
                note = data.fieldString("notes") ?: data.fieldString("reason"),
                routineId = data.fieldString("routineId"),
                dailyPlanItemId = data.fieldString("dailyPlanItemId")
                    ?: data.fieldString("id")
                    ?: planRecord?.id,
                planTemplateId = data.fieldString("planTemplateId"),
            )
        }

        val aiSuggestion = reviewsByDate[dateText]
            ?.data
            ?.get("localSuggestion")
            ?.takeUnless { it is JsonNull }
            ?.let { runCatching { it.jsonObject }.getOrNull() }
            ?.takeIf { it.fieldString("date") == dateText }
            ?.let { suggestion ->
                val type = suggestion.fieldString("trainingType") ?: return@let null
                TodayGuidance(
                    source = GuidanceSource.LOCAL_SUGGESTION,
                    title = suggestion.fieldString("title") ?: trainingTypeTitle(type),
                    trainingType = type,
                    estimatedMinutes = suggestion.fieldInt("estimatedMinutes"),
                    note = suggestion.fieldString("reason"),
                )
            }

        return ResolvedDay(
            guidance = TodayGuidanceResolver.resolve(actual, plan, aiSuggestion, DefaultSuggestionResolver.resolve(date)),
            actualLogs = actualLogs,
        )
    }
}

private fun <T : Comparable<T>> List<SharedRecord>.latestByDate(
    selector: (SharedRecord) -> T,
): Map<String, SharedRecord> = asSequence().latestByDate(selector)

private fun <T : Comparable<T>> Sequence<SharedRecord>.latestByDate(
    selector: (SharedRecord) -> T,
): Map<String, SharedRecord> = groupBy { it.data.fieldString("date") }
    .mapNotNull { (date, values) -> date?.let { it to values.maxByOrNull(selector)!! } }
    .toMap()

internal fun decodeTrainingLog(record: SharedRecord): TrainingLog? = runCatching {
    val data = record.data
    TrainingLog(
        id = data.fieldString("id") ?: record.id,
        date = requireNotNull(data.fieldString("date")),
        type = requireNotNull(data.fieldString("type")),
        status = data.fieldString("status") ?: "completed",
        source = data.fieldString("source") ?: "manual",
        title = data.fieldString("title"),
        durationSec = data.fieldInt("durationSec"),
        distanceKm = data.fieldDouble("distanceKm"),
        averageHeartRate = data.fieldInt("averageHeartRate"),
        perceivedEffort = data.fieldInt("perceivedEffort"),
        subjectiveResult = data.fieldString("subjectiveResult"),
        notes = data.fieldString("notes"),
        timerSessionId = data.fieldString("timerSessionId"),
        timerSessionIds = data["timerSessionIds"]
            ?.takeUnless { it is JsonNull }
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
            .orEmpty(),
        calendarVisible = data.fieldBoolean("calendarVisible") ?: true,
        countsTowardTraining = data.fieldBoolean("countsTowardTraining") ?: true,
        updatedAt = record.updatedAt,
    )
}.getOrNull()

internal fun JsonObject.fieldString(key: String): String? =
    (this[key] as? JsonPrimitive)?.content

internal fun JsonObject.fieldInt(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.fieldDouble(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.fieldBoolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull
