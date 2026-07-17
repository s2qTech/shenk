package io.s2qtech.shenk.sync

import io.s2qtech.shenk.model.BodyMetric
import io.s2qtech.shenk.model.CheckinKind
import io.s2qtech.shenk.model.GuidanceSource
import io.s2qtech.shenk.model.EffectiveStatus
import io.s2qtech.shenk.model.EffectiveStatusResolver
import io.s2qtech.shenk.model.PainEntry
import io.s2qtech.shenk.model.PainRegion
import io.s2qtech.shenk.model.PainSide
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import io.s2qtech.shenk.model.StatusCheckin
import io.s2qtech.shenk.model.TodayGuidance
import io.s2qtech.shenk.model.TodayGuidanceResolver
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class TodayRecords(
    val date: String,
    val morning: StatusCheckin?,
    val preWorkout: StatusCheckin?,
    val metric: BodyMetric?,
    val latestMetric: BodyMetric?,
    val effectiveStatus: EffectiveStatus,
    val guidance: TodayGuidance,
)

class TodayRecordRepository(
    private val records: LocalFirstRepository,
) {
    fun observe(date: LocalDate): Flow<TodayRecords> = combine(
        records.observeActive("status_checkins"),
        records.observeActive("body_metrics"),
        records.observeActive("training_logs"),
        records.observeActive("daily_plan_items"),
        records.observeActive("plan_adjustments"),
    ) { checkins, metrics, logs, plans, adjustments ->
        val dateText = date.toString()
        val parsedCheckins = checkins.mapNotNull(::decodeCheckin)
        val parsedMetrics = metrics.mapNotNull(::decodeMetric)
        val morning = parsedCheckins.firstOrNull { it.date == dateText && it.kind == CheckinKind.MORNING }
        val preWorkout = parsedCheckins.firstOrNull {
            it.date == dateText && it.kind == CheckinKind.PRE_WORKOUT
        }
        TodayRecords(
            date = dateText,
            morning = morning,
            preWorkout = preWorkout,
            metric = parsedMetrics.firstOrNull { it.date == dateText && it.context == "morning" },
            latestMetric = parsedMetrics.maxByOrNull { it.observedAt },
            effectiveStatus = EffectiveStatusResolver.resolve(morning, preWorkout),
            guidance = resolveGuidance(date, logs, plans, adjustments),
        )
    }

    suspend fun saveMorning(
        checkin: StatusCheckin,
        metric: BodyMetric?,
    ): SyncFoundationState {
        require(checkin.kind == CheckinKind.MORNING)
        val batch = buildList {
            add(checkinRecord(checkin))
            if (metric?.hasMeasurements == true) add(metricRecord(metric))
        }
        return records.persistBatchAndEnqueue(batch, SharedEntityOwner.RECORD)
    }

    suspend fun savePreWorkout(checkin: StatusCheckin): SyncFoundationState {
        require(checkin.kind == CheckinKind.PRE_WORKOUT)
        require(checkin.baseCheckinId != null) { "pre-workout check-in requires its morning base" }
        return records.persistAndEnqueue(checkinRecord(checkin), SharedEntityOwner.RECORD)
    }

    private suspend fun checkinRecord(checkin: StatusCheckin): SharedRecord = mergeRecord(
        entity = "status_checkins",
        id = checkin.id,
        knownKeys = STATUS_KEYS,
        knownData = checkin.toJson(),
    )

    private suspend fun metricRecord(metric: BodyMetric): SharedRecord = mergeRecord(
        entity = "body_metrics",
        id = metric.id,
        knownKeys = METRIC_KEYS,
        knownData = metric.toJson(),
    )

    private suspend fun mergeRecord(
        entity: String,
        id: String,
        knownKeys: Set<String>,
        knownData: JsonObject,
    ): SharedRecord {
        val existing = records.get(entity, id)
        val merged = buildJsonObject {
            existing?.data?.forEach { (key, value) -> if (key !in knownKeys) put(key, value) }
            knownData.forEach { (key, value) -> put(key, value) }
        }
        return SharedRecord.create(
            entity = entity,
            id = id,
            data = merged,
            contractVersion = "2.0",
        ).let { fresh ->
            if (existing == null) fresh else SharedRecord(buildJsonObject {
                existing.envelope.forEach { (key, value) -> put(key, value) }
                put("contractVersion", JsonPrimitive("2.0"))
                put("data", merged)
            })
        }
    }
}

private fun resolveGuidance(
    date: LocalDate,
    logs: List<SharedRecord>,
    plans: List<SharedRecord>,
    adjustments: List<SharedRecord>,
): TodayGuidance {
    val dateText = date.toString()
    val actual = logs
        .filter { it.data.string("date") == dateText && it.data.boolean("calendarVisible") != false }
        .maxByOrNull { it.updatedAt.orEmpty() }
        ?.let { record ->
            TodayGuidance(
                source = GuidanceSource.ACTUAL,
                title = record.data.string("title") ?: trainingTypeTitle(record.data.string("type")),
                trainingType = record.data.string("type") ?: "other",
                estimatedMinutes = record.data.double("durationSec")?.div(60.0)?.toInt(),
                note = record.data.string("subjectiveResult") ?: record.data.string("notes"),
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
            ?.jsonObject
            ?: adjustment
    } ?: planRecord?.data
    val plan = effectiveData?.let { data ->
        TodayGuidance(
            source = GuidanceSource.FORMAL_PLAN,
            title = data.string("title") ?: trainingTypeTitle(data.string("trainingType")),
            trainingType = data.string("trainingType") ?: "other",
            estimatedMinutes = data.double("estimatedMinutes")?.toInt(),
            note = data.string("notes") ?: data.string("reason"),
        )
    }
    return TodayGuidanceResolver.resolve(actual, plan, fallbackFor(date))
}

private fun fallbackFor(date: LocalDate): TodayGuidance = when (date.dayOfWeek) {
    DayOfWeek.MONDAY -> suggestion("普通走", "easy_walk", 35)
    DayOfWeek.TUESDAY -> suggestion("力量训练", "strength", 45)
    DayOfWeek.WEDNESDAY -> suggestion("普通走", "easy_walk", 35)
    DayOfWeek.THURSDAY -> suggestion("提高走", "quality_walk", 45)
    DayOfWeek.FRIDAY -> suggestion("普通走", "easy_walk", 35)
    DayOfWeek.SATURDAY -> suggestion("力量训练", "strength", 45)
    DayOfWeek.SUNDAY -> suggestion("恢复活动", "recovery", 15)
}

private fun suggestion(title: String, type: String, minutes: Int) = TodayGuidance(
    source = GuidanceSource.LOCAL_SUGGESTION,
    title = title,
    trainingType = type,
    estimatedMinutes = minutes,
    note = "当前没有正式计划，这是离线兜底建议。",
)

private fun trainingTypeTitle(type: String?): String = when (type) {
    "strength" -> "力量训练"
    "quality_walk" -> "提高走"
    "easy_walk" -> "普通走"
    "indoor_cardio" -> "室内有氧"
    "recovery" -> "恢复活动"
    "rest" -> "休息"
    else -> "今日记录"
}

private fun StatusCheckin.toJson(): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(id))
    put("date", JsonPrimitive(date))
    put("kind", JsonPrimitive(kind.wireValue))
    put("observedAt", JsonPrimitive(observedAt))
    putOptional("baseCheckinId", baseCheckinId)
    putOptional("sleepDurationMinutes", sleepDurationMinutes)
    putOptional("deepSleepMinutes", deepSleepMinutes)
    putOptional("sleepQuality", sleepQuality)
    putOptional("energy", energy)
    putOptional("fatigue", fatigue)
    putOptional("workPressure", workPressure)
    pain?.let { entries ->
        put("pain", buildJsonArray {
            entries.forEach { entry ->
                add(buildJsonObject {
                    put("region", JsonPrimitive(entry.region.wireValue))
                    put("severity", JsonPrimitive(entry.severity))
                    put("side", JsonPrimitive(entry.side.wireValue))
                })
            }
        })
    }
    putOptional("note", note?.takeIf { it.isNotBlank() })
}

private fun BodyMetric.toJson(): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(id))
    put("date", JsonPrimitive(date))
    put("observedAt", JsonPrimitive(observedAt))
    put("context", JsonPrimitive(context))
    put("source", JsonPrimitive(source))
    putOptional("sourceRecordId", sourceRecordId)
    putOptional("weightKg", weightKg)
    putOptional("bodyFatPct", bodyFatPct)
    putOptional("muscleKg", muscleKg)
    putOptional("waistCm", waistCm)
}

private fun decodeCheckin(record: SharedRecord): StatusCheckin? = runCatching {
    val data = record.data
    StatusCheckin(
        id = data.string("id") ?: record.id,
        date = requireNotNull(data.string("date")),
        kind = CheckinKind.entries.first { it.wireValue == data.string("kind") },
        observedAt = requireNotNull(data.string("observedAt")),
        baseCheckinId = data.string("baseCheckinId"),
        sleepDurationMinutes = data.int("sleepDurationMinutes"),
        deepSleepMinutes = data.int("deepSleepMinutes"),
        sleepQuality = data.int("sleepQuality"),
        energy = data.int("energy"),
        fatigue = data.int("fatigue"),
        workPressure = data.int("workPressure"),
        pain = data["pain"]?.takeUnless { it is JsonNull }?.jsonArray?.map { pain ->
            val item = pain.jsonObject
            PainEntry(
                region = PainRegion.entries.first { it.wireValue == item.string("region") },
                severity = requireNotNull(item.int("severity")),
                side = PainSide.entries.firstOrNull { it.wireValue == item.string("side") }
                    ?: PainSide.UNSPECIFIED,
            )
        },
        note = data.string("note"),
    )
}.getOrNull()

private fun decodeMetric(record: SharedRecord): BodyMetric? = runCatching {
    val data = record.data
    BodyMetric(
        id = data.string("id") ?: record.id,
        date = requireNotNull(data.string("date")),
        observedAt = requireNotNull(data.string("observedAt")),
        context = data.string("context") ?: "other",
        source = data.string("source") ?: "legacy",
        sourceRecordId = data.string("sourceRecordId"),
        weightKg = data.double("weightKg"),
        bodyFatPct = data.double("bodyFatPct"),
        muscleKg = data.double("muscleKg"),
        waistCm = data.double("waistCm"),
    )
}.getOrNull()

private fun JsonObject.string(key: String): String? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content

private fun JsonObject.int(key: String): Int? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull

private fun JsonObject.double(key: String): Double? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull

private fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.toBooleanStrictOrNull()

private fun kotlinx.serialization.json.JsonObjectBuilder.putOptional(key: String, value: String?) {
    value?.let { put(key, JsonPrimitive(it)) }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putOptional(key: String, value: Int?) {
    value?.let { put(key, JsonPrimitive(it)) }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putOptional(key: String, value: Double?) {
    value?.let { put(key, JsonPrimitive(it)) }
}

private val STATUS_KEYS = setOf(
    "id", "date", "kind", "observedAt", "baseCheckinId", "sleepDurationMinutes",
    "deepSleepMinutes", "sleepQuality", "energy", "fatigue", "workPressure", "pain", "note",
)

private val METRIC_KEYS = setOf(
    "id", "date", "observedAt", "context", "source", "sourceRecordId",
    "weightKg", "bodyFatPct", "muscleKg", "waistCm",
)
