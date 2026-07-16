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
