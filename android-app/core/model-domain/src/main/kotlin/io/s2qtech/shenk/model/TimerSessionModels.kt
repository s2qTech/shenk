package io.s2qtech.shenk.model

import kotlinx.serialization.json.JsonObject

data class TimerSessionFact(
    val id: String,
    val date: String,
    val routineId: String,
    val routineVersion: String,
    val routineDigest: String,
    val routineSnapshot: JsonObject,
    val dailyPlanItemId: String? = null,
    val planTemplateId: String? = null,
    val trainingType: String,
    val startedAt: String,
    val endedAt: String?,
    val completion: String,
    val actualSeconds: Int,
    val activeSeconds: Int,
    val elapsedSeconds: Int,
    val pausedSeconds: Int,
    val calendarVisible: Boolean,
    val countsTowardTraining: Boolean,
    val interruptionReason: String? = null,
    val stepResults: List<TimerStepResult> = emptyList(),
    val devicePlatform: String = "android",
    val idempotencyKey: String,
)

data class TimerStepResult(
    val stepId: String,
    val logicalIndex: Int,
    val plannedSeconds: Int,
    val completedSeconds: Int,
    val completed: Boolean,
)
