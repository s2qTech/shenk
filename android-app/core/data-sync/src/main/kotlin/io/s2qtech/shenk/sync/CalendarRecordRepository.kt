package io.s2qtech.shenk.sync

import io.s2qtech.shenk.model.BodyMetric
import io.s2qtech.shenk.model.BodyTrends
import io.s2qtech.shenk.model.CalendarDay
import io.s2qtech.shenk.model.CalendarMonth
import io.s2qtech.shenk.model.DailyMetricResolver
import io.s2qtech.shenk.model.MetricTrendResolver
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import io.s2qtech.shenk.model.TodayGuidance
import io.s2qtech.shenk.model.TrainingLog
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

data class CalendarDayDetails(
    val date: LocalDate,
    val guidance: TodayGuidance,
    val actualLogs: List<TrainingLog>,
)

class CalendarRecordRepository(
    private val records: LocalFirstRepository,
) {
    fun observeRange(start: LocalDate, endInclusive: LocalDate): Flow<List<CalendarDay>> {
        require(!endInclusive.isBefore(start))
        return combine(
            records.observeActive("training_logs"),
            records.observeActive("daily_plan_items"),
            records.observeActive("plan_adjustments"),
            records.observeActive("body_metrics"),
        ) { logs, plans, adjustments, metricRecords ->
            val metricsByDate = DailyMetricResolver.resolve(metricRecords.mapNotNull(::decodeBodyMetric))
            generateSequence(start) { current ->
                current.plusDays(1).takeIf { it <= endInclusive }
            }.map { date ->
                val resolved = GuidanceResolution.resolve(date, logs, plans, adjustments)
                CalendarDay(
                    date = date,
                    guidance = resolved.guidance,
                    actualLogs = resolved.actualLogs,
                    isInMonth = true,
                    bodyMetrics = metricsByDate[date].orEmpty(),
                )
            }.toList()
        }
    }

    fun observeMonth(month: YearMonth): Flow<CalendarMonth> = combine(
        records.observeActive("training_logs"),
        records.observeActive("daily_plan_items"),
        records.observeActive("plan_adjustments"),
        records.observeActive("body_metrics"),
    ) { logs, plans, adjustments, metricRecords ->
        buildMonth(month, logs, plans, adjustments, metricRecords)
    }

    fun observeDay(date: LocalDate): Flow<CalendarDayDetails> = combine(
        records.observeActive("training_logs"),
        records.observeActive("daily_plan_items"),
        records.observeActive("plan_adjustments"),
    ) { logs, plans, adjustments ->
        GuidanceResolution.resolve(date, logs, plans, adjustments).let {
            CalendarDayDetails(date, it.guidance, it.actualLogs)
        }
    }

    fun observeTrainingLogs(): Flow<List<TrainingLog>> = records.observeActive("training_logs")
        .map { values ->
            values.mapNotNull(::decodeTrainingLog)
                .sortedWith(compareByDescending<TrainingLog> { it.date }.thenByDescending { it.updatedAt })
        }

    fun observeBodyTrends(today: LocalDate): Flow<BodyTrends> = records.observeActive("body_metrics")
        .map { values -> MetricTrendResolver.resolve(values.mapNotNull(::decodeBodyMetric), today) }

    suspend fun saveTrainingLog(log: TrainingLog): SyncFoundationState {
        val existing = records.get("training_logs", log.id)
        val knownData = log.toJson()
        val merged = buildJsonObject {
            existing?.data?.forEach { (key, value) -> if (key !in TRAINING_LOG_KEYS) put(key, value) }
            knownData.forEach { (key, value) -> put(key, value) }
        }
        val outgoing = if (existing == null) {
            SharedRecord.create("training_logs", log.id, merged, contractVersion = "2.0")
        } else {
            SharedRecord(buildJsonObject {
                existing.envelope.forEach { (key, value) -> put(key, value) }
                put("contractVersion", JsonPrimitive("2.0"))
                put("deletedAt", JsonNull)
                put("data", merged)
            })
        }
        return records.persistAndEnqueue(outgoing, SharedEntityOwner.RECORD)
    }

    suspend fun deleteTrainingLog(id: String): SyncFoundationState {
        val existing = requireNotNull(records.get("training_logs", id)) { "training log not found" }
        val tombstone = SharedRecord(buildJsonObject {
            existing.envelope.forEach { (key, value) -> put(key, value) }
            put("deletedAt", JsonPrimitive(Instant.now().toString()))
        })
        return records.persistAndEnqueue(tombstone, SharedEntityOwner.RECORD)
    }

    suspend fun restoreTrainingLog(log: TrainingLog): SyncFoundationState = saveTrainingLog(log)
}

private fun buildMonth(
    month: YearMonth,
    logs: List<SharedRecord>,
    plans: List<SharedRecord>,
    adjustments: List<SharedRecord>,
    metricRecords: List<SharedRecord>,
): CalendarMonth {
    val metricsByDate = DailyMetricResolver.resolve(metricRecords.mapNotNull(::decodeBodyMetric))
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val start = first.minusDays(((first.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7).toLong())
    val end = last.plusDays(((DayOfWeek.SUNDAY.value - last.dayOfWeek.value + 7) % 7).toLong())
    val days = generateSequence(start) { current -> current.plusDays(1).takeIf { it <= end } }
        .map { date ->
            val resolved = GuidanceResolution.resolve(date, logs, plans, adjustments)
            CalendarDay(
                date = date,
                guidance = resolved.guidance,
                actualLogs = resolved.actualLogs,
                isInMonth = YearMonth.from(date) == month,
                bodyMetrics = metricsByDate[date].orEmpty(),
            )
        }
        .toList()
    return CalendarMonth(month, days.chunked(7).map { week -> week.map { it.takeIf(CalendarDay::isInMonth) } })
}

private fun TrainingLog.toJson(): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(id))
    put("date", JsonPrimitive(date))
    put("type", JsonPrimitive(type))
    put("status", JsonPrimitive(status))
    put("source", JsonPrimitive(source))
    put("calendarVisible", JsonPrimitive(calendarVisible))
    put("countsTowardTraining", JsonPrimitive(countsTowardTraining))
    title?.takeIf(String::isNotBlank)?.let { put("title", JsonPrimitive(it)) }
    durationSec?.let { put("durationSec", JsonPrimitive(it)) }
    distanceKm?.let { put("distanceKm", JsonPrimitive(it)) }
    averageHeartRate?.let { put("averageHeartRate", JsonPrimitive(it)) }
    perceivedEffort?.let { put("perceivedEffort", JsonPrimitive(it)) }
    subjectiveResult?.takeIf(String::isNotBlank)?.let { put("subjectiveResult", JsonPrimitive(it)) }
    notes?.takeIf(String::isNotBlank)?.let { put("notes", JsonPrimitive(it)) }
    timerSessionId?.let { put("timerSessionId", JsonPrimitive(it)) }
    if (timerSessionIds.isNotEmpty()) put("timerSessionIds", buildJsonArray {
        timerSessionIds.forEach { add(JsonPrimitive(it)) }
    })
}

internal fun decodeBodyMetric(record: SharedRecord): BodyMetric? = runCatching {
    val data = record.data
    val date = requireNotNull(data.fieldString("date"))
    BodyMetric(
        id = data.fieldString("id") ?: record.id,
        date = date,
        observedAt = data.fieldString("observedAt")
            ?: data.fieldString("updatedAt")
            ?: data.fieldString("createdAt")
            ?: record.updatedAt
            ?: record.createdAt
            ?: "${date}T00:00:00Z",
        context = data.fieldString("context") ?: "morning",
        source = data.fieldString("source") ?: "legacy",
        sourceRecordId = data.fieldString("sourceRecordId"),
        weightKg = data.fieldDouble("weightKg"),
        bodyFatPct = data.fieldDouble("bodyFatPct"),
        muscleKg = data.fieldDouble("muscleKg"),
        waistCm = data.fieldDouble("waistCm"),
    )
}.getOrNull()

private val TRAINING_LOG_KEYS = setOf(
    "id", "date", "type", "status", "source", "title", "durationSec", "distanceKm",
    "averageHeartRate", "perceivedEffort", "subjectiveResult", "notes", "timerSessionId",
    "timerSessionIds", "calendarVisible", "countsTowardTraining",
)
