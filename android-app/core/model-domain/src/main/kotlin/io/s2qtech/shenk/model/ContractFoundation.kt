package io.s2qtech.shenk.model

import kotlinx.serialization.Serializable

object ContractVersion {
    const val ACTIVE = "1.0"
    const val PLANNED = "2.0"
}

enum class SharedEntityOwner {
    PLANNING_RECORD,
    TIMER,
}

object EntityOwnership {
    private val timerOwned = setOf("timer_sessions")

    fun ownerOf(entity: String): SharedEntityOwner =
        if (entity in timerOwned) SharedEntityOwner.TIMER else SharedEntityOwner.PLANNING_RECORD

    fun canWrite(owner: SharedEntityOwner, entity: String): Boolean = ownerOf(entity) == owner
}

@Serializable
data class PackageZeroFixture(
    val fixtureSchema: String,
    val synthetic: Boolean,
    val contractVersion: String,
    val expectedDayPriority: List<String>,
    val routine: FixtureRoutine,
    val timerSession: FixtureTimerSession,
    val trainingLog: FixtureTrainingLog,
)

@Serializable
data class FixtureRoutine(
    val id: String,
    val title: String,
    val scene: String,
    val role: String,
)

@Serializable
data class FixtureTimerSession(
    val id: String,
    val entity: String,
    val completion: String,
)

@Serializable
data class FixtureTrainingLog(
    val id: String,
    val entity: String,
    val timerSessionId: String,
)

@Serializable
data class PackageOneFixture(
    val fixtureSchema: String,
    val synthetic: Boolean,
    val contractVersion: String,
    val supportedContractVersions: List<String>,
    val routine: PackageOneRoutine,
    val statusCheckin: PackageOneEntity,
    val timerSession: PackageOneTimerSession,
    val legacyBodyMetric: LegacyCombinedMetric,
)

@Serializable
data class PackageOneRoutine(
    val id: String,
    val title: String,
    val scene: String,
    val role: String,
    val lifecycle: String,
    val timerVisible: Boolean,
    val calendarVisible: Boolean,
    val countsTowardTraining: Boolean,
)

@Serializable
data class PackageOneEntity(
    val id: String,
    val entity: String,
    val kind: String,
    val energy: Int? = null,
)

@Serializable
data class PackageOneTimerSession(
    val id: String,
    val entity: String,
    val devicePlatform: String,
)

@Serializable
data class LegacyCombinedMetric(
    val id: String,
    val date: String,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val bodyFatPct: Double? = null,
    val muscleKg: Double? = null,
    val energy: Int? = null,
    val fatigue: Int? = null,
)

data class MigratedBodyMetric(
    val id: String,
    val date: String,
    val sourceRecordId: String,
    val weightKg: Double?,
    val waistCm: Double?,
    val bodyFatPct: Double?,
    val muscleKg: Double?,
)

data class MigratedStatusCheckin(
    val id: String,
    val date: String,
    val sourceRecordId: String,
    val energy: Int?,
    val fatigue: Int?,
)

data class LegacyMetricMigrationResult(
    val bodyMetric: MigratedBodyMetric,
    val statusCheckin: MigratedStatusCheckin,
)

object LegacyMetricMigration {
    fun split(source: LegacyCombinedMetric): LegacyMetricMigrationResult = LegacyMetricMigrationResult(
        bodyMetric = MigratedBodyMetric(
            id = "body_metric:${source.id}",
            date = source.date,
            sourceRecordId = source.id,
            weightKg = source.weightKg,
            waistCm = source.waistCm,
            bodyFatPct = source.bodyFatPct,
            muscleKg = source.muscleKg,
        ),
        statusCheckin = MigratedStatusCheckin(
            id = "status_checkin:${source.id}",
            date = source.date,
            sourceRecordId = source.id,
            energy = source.energy,
            fatigue = source.fatigue,
        ),
    )
}
