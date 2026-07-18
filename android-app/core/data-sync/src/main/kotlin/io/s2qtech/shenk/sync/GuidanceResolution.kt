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
    fun resolve(
        date: LocalDate,
        logs: List<SharedRecord>,
        plans: List<SharedRecord>,
        adjustments: List<SharedRecord>,
    ): ResolvedDay {
        val dateText = date.toString()
        val actualLogs = logs
            .filter { it.data.string("date") == dateText && it.data.boolean("calendarVisible") != false }
            .mapNotNull(::decodeTrainingLog)
            .sortedWith(compareByDescending<TrainingLog> { it.updatedAt.orEmpty() }.thenBy { it.id })
        val actual = actualLogs.firstOrNull()?.let { log ->
            TodayGuidance(
                source = GuidanceSource.ACTUAL,
                title = log.displayTitle,
                trainingType = log.type,
                estimatedMinutes = log.durationMinutes,
                note = log.subjectiveResult ?: log.notes,
            )
        }

        val planRecord = plans
            .filter { it.data.string("date") == dateText }
            .maxByOrNull { it.updatedAt.orEmpty() }
        val latestAdjustment = adjustments
            .filter { it.data.string("date") == dateText }
            .maxByOrNull { it.data.string("adjustedAt") ?: it.updatedAt.orEmpty() }
        val effectiveData = latestAdjustment?.data?.let { adjustment ->
            adjustment["toSnapshot"]
                ?.takeUnless { it is JsonNull }
                ?.let { runCatching { it.jsonObject }.getOrNull() }
                ?: adjustment
        } ?: planRecord?.data
        val plan = effectiveData?.let { data ->
            val type = data.string("trainingType") ?: data.string("type") ?: "other"
            TodayGuidance(
                source = GuidanceSource.FORMAL_PLAN,
                title = data.string("title") ?: trainingTypeTitle(type),
                trainingType = type,
                estimatedMinutes = data.double("estimatedMinutes")?.toInt(),
                note = data.string("notes") ?: data.string("reason"),
            )
        }

        return ResolvedDay(
            guidance = TodayGuidanceResolver.resolve(actual, plan, DefaultSuggestionResolver.resolve(date)),
            actualLogs = actualLogs,
        )
    }
}

internal fun decodeTrainingLog(record: SharedRecord): TrainingLog? = runCatching {
    val data = record.data
    TrainingLog(
        id = data.string("id") ?: record.id,
        date = requireNotNull(data.string("date")),
        type = requireNotNull(data.string("type")),
        status = data.string("status") ?: "completed",
        source = data.string("source") ?: "manual",
        title = data.string("title"),
        durationSec = data.int("durationSec"),
        distanceKm = data.double("distanceKm"),
        averageHeartRate = data.int("averageHeartRate"),
        perceivedEffort = data.int("perceivedEffort"),
        subjectiveResult = data.string("subjectiveResult"),
        notes = data.string("notes"),
        timerSessionId = data.string("timerSessionId"),
        timerSessionIds = data["timerSessionIds"]
            ?.takeUnless { it is JsonNull }
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }
            .orEmpty(),
        calendarVisible = data.boolean("calendarVisible") ?: true,
        countsTowardTraining = data.boolean("countsTowardTraining") ?: true,
        updatedAt = record.updatedAt,
    )
}.getOrNull()

internal fun JsonObject.string(key: String): String? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

internal fun JsonObject.int(key: String): Int? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull

internal fun JsonObject.double(key: String): Double? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull

internal fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull
