package io.s2qtech.shenk.sync

import io.s2qtech.shenk.model.BodyMetric
import io.s2qtech.shenk.model.CheckinKind
import io.s2qtech.shenk.model.EffectiveStatus
import io.s2qtech.shenk.model.EffectiveStatusResolver
import io.s2qtech.shenk.model.PainEntry
import io.s2qtech.shenk.model.PainRegion
import io.s2qtech.shenk.model.PainSide
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import io.s2qtech.shenk.model.StatusCheckin
import io.s2qtech.shenk.model.TodayGuidance
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
        val morning = parsedCheckins.filter {
            it.date == dateText && it.kind == CheckinKind.MORNING
        }.maxByOrNull { it.observedAt }
        val preWorkout = parsedCheckins.filter {
            it.date == dateText && it.kind == CheckinKind.PRE_WORKOUT
        }.maxByOrNull { it.observedAt }
        TodayRecords(
            date = dateText,
            morning = morning,
            preWorkout = preWorkout,
            metric = parsedMetrics.filter {
                it.date == dateText && it.context == "morning"
            }.maxByOrNull { it.observedAt },
            latestMetric = parsedMetrics.maxByOrNull { it.observedAt },
            effectiveStatus = EffectiveStatusResolver.resolve(morning, preWorkout),
            guidance = GuidanceResolution.resolve(date, logs, plans, adjustments).guidance,
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
        id = data.fieldString("id") ?: record.id,
        date = requireNotNull(data.fieldString("date")),
        kind = CheckinKind.entries.first { it.wireValue == data.fieldString("kind") },
        observedAt = requireNotNull(data.fieldString("observedAt")),
        baseCheckinId = data.fieldString("baseCheckinId"),
        sleepDurationMinutes = data.fieldInt("sleepDurationMinutes"),
        deepSleepMinutes = data.fieldInt("deepSleepMinutes"),
        sleepQuality = data.fieldInt("sleepQuality"),
        energy = data.fieldInt("energy"),
        fatigue = data.fieldInt("fatigue"),
        workPressure = data.fieldInt("workPressure"),
        pain = data["pain"]?.takeUnless { it is JsonNull }?.jsonArray?.map { pain ->
            val item = pain.jsonObject
            PainEntry(
                region = PainRegion.entries.first { it.wireValue == item.fieldString("region") },
                severity = requireNotNull(item.fieldInt("severity")),
                side = PainSide.entries.firstOrNull { it.wireValue == item.fieldString("side") }
                    ?: PainSide.UNSPECIFIED,
            )
        },
        note = data.fieldString("note"),
    )
}.getOrNull()

private fun decodeMetric(record: SharedRecord): BodyMetric? = decodeBodyMetric(record)

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
