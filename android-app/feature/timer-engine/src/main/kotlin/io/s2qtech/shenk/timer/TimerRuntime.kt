package io.s2qtech.shenk.timer

import io.s2qtech.shenk.model.ExecutionMode
import io.s2qtech.shenk.model.RoutineStep
import io.s2qtech.shenk.model.RoutineTemplate
import io.s2qtech.shenk.model.TimerSessionFact
import io.s2qtech.shenk.model.TimerStepResult
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

enum class RuntimePart { ACTION, PREPARE, SIDE, SWITCH }

data class RuntimeStep(
    val runtimeIndex: Int,
    val logicalIndex: Int,
    val sourceStepId: String,
    val name: String,
    val phase: String?,
    val seconds: Int,
    val part: RuntimePart,
    val side: String? = null,
    val speechText: String,
    val cues: List<String>,
    val warnings: List<String>,
    val breath: String?,
)

data class TimerPreviewRequest(
    val routine: RoutineTemplate,
    val sessionId: String,
    val idempotencyKey: String,
    val date: String,
    val dailyPlanItemId: String? = null,
    val planTemplateId: String? = null,
)

data class TimerSnapshot(
    val state: TimerEngineState = TimerEngineState.IDLE,
    val request: TimerPreviewRequest? = null,
    val steps: List<RuntimeStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val currentStepRemainingMillis: Long = 0,
    val activeMillis: Long = 0,
    val elapsedMillis: Long = 0,
    val pausedMillis: Long = 0,
    val startedAtEpochMillis: Long? = null,
    val endedAtEpochMillis: Long? = null,
    val lastUpdatedEpochMillis: Long? = null,
    val interruptionReason: String? = null,
) {
    val currentStep: RuntimeStep? get() = steps.getOrNull(currentStepIndex)
    val nextStep: RuntimeStep? get() = steps.getOrNull(currentStepIndex + 1)
    val logicalActionCount: Int get() = steps.maxOfOrNull { it.logicalIndex + 1 } ?: 0
    val currentLogicalAction: Int get() = currentStep?.logicalIndex?.plus(1) ?: logicalActionCount
    val totalPlannedSeconds: Int get() = steps.sumOf(RuntimeStep::seconds)
    val remainingSeconds: Int get() = ((currentStepRemainingMillis + 999) / 1000).toInt()
}

data class TimerCheckpoint(
    val sessionId: String,
    val idempotencyKey: String,
    val routineId: String,
    val date: String,
    val dailyPlanItemId: String?,
    val planTemplateId: String?,
    val state: TimerEngineState,
    val currentStepIndex: Int,
    val currentStepRemainingMillis: Long,
    val activeMillis: Long,
    val elapsedMillis: Long,
    val pausedMillis: Long,
    val startedAtEpochMillis: Long?,
    val endedAtEpochMillis: Long?,
    val lastUpdatedEpochMillis: Long?,
    val interruptionReason: String?,
)

fun TimerSnapshot.toCheckpoint(): TimerCheckpoint? {
    val request = request ?: return null
    return TimerCheckpoint(
        sessionId = request.sessionId,
        idempotencyKey = request.idempotencyKey,
        routineId = request.routine.id,
        date = request.date,
        dailyPlanItemId = request.dailyPlanItemId,
        planTemplateId = request.planTemplateId,
        state = state,
        currentStepIndex = currentStepIndex,
        currentStepRemainingMillis = currentStepRemainingMillis,
        activeMillis = activeMillis,
        elapsedMillis = elapsedMillis,
        pausedMillis = pausedMillis,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        lastUpdatedEpochMillis = lastUpdatedEpochMillis,
        interruptionReason = interruptionReason,
    )
}

fun restoreTimerSnapshot(
    routine: RoutineTemplate,
    checkpoint: TimerCheckpoint,
    nowEpochMillis: Long,
): TimerSnapshot {
    require(routine.id == checkpoint.routineId)
    val steps = expandRoutine(routine)
    val index = checkpoint.currentStepIndex.coerceIn(0, steps.lastIndex)
    val wasActive = checkpoint.state in setOf(TimerEngineState.RUNNING, TimerEngineState.PAUSED)
    val last = checkpoint.lastUpdatedEpochMillis ?: nowEpochMillis
    val offlineGap = max(0L, nowEpochMillis - last)
    return TimerSnapshot(
        state = if (wasActive) TimerEngineState.PAUSED else checkpoint.state,
        request = TimerPreviewRequest(
            routine = routine,
            sessionId = checkpoint.sessionId,
            idempotencyKey = checkpoint.idempotencyKey,
            date = checkpoint.date,
            dailyPlanItemId = checkpoint.dailyPlanItemId,
            planTemplateId = checkpoint.planTemplateId,
        ),
        steps = steps,
        currentStepIndex = index,
        currentStepRemainingMillis = checkpoint.currentStepRemainingMillis.coerceAtLeast(0),
        activeMillis = checkpoint.activeMillis,
        elapsedMillis = checkpoint.elapsedMillis + if (wasActive) offlineGap else 0,
        pausedMillis = checkpoint.pausedMillis + if (wasActive) offlineGap else 0,
        startedAtEpochMillis = checkpoint.startedAtEpochMillis,
        endedAtEpochMillis = checkpoint.endedAtEpochMillis,
        lastUpdatedEpochMillis = nowEpochMillis,
        interruptionReason = if (wasActive) "recovered_after_process_death" else checkpoint.interruptionReason,
    )
}

class NativeTimerEngine(
    initial: TimerSnapshot = TimerSnapshot(),
) : TimerEnginePort {
    override var snapshot: TimerSnapshot = initial
        private set

    override fun preview(request: TimerPreviewRequest): TimerSnapshot {
        require(request.routine.executable) { "方案不可执行" }
        val expanded = expandRoutine(request.routine)
        require(expanded.isNotEmpty()) { "方案没有可执行动作" }
        snapshot = TimerSnapshot(
            state = TimerEngineState.PREVIEW,
            request = request,
            steps = expanded,
            currentStepRemainingMillis = expanded.first().seconds * 1000L,
        )
        return snapshot
    }

    override fun start(nowEpochMillis: Long): TimerSnapshot {
        require(snapshot.state == TimerEngineState.PREVIEW) { "只能从预览开始" }
        snapshot = snapshot.copy(
            state = TimerEngineState.RUNNING,
            startedAtEpochMillis = nowEpochMillis,
            lastUpdatedEpochMillis = nowEpochMillis,
        )
        return snapshot
    }

    override fun tick(nowEpochMillis: Long): TimerSnapshot {
        snapshot = advance(snapshot, nowEpochMillis)
        return snapshot
    }

    override fun pause(nowEpochMillis: Long, reason: String?): TimerSnapshot {
        val advanced = advance(snapshot, nowEpochMillis)
        if (advanced.state == TimerEngineState.RUNNING) {
            snapshot = advanced.copy(
                state = TimerEngineState.PAUSED,
                interruptionReason = reason ?: advanced.interruptionReason,
            )
        } else {
            snapshot = advanced
        }
        return snapshot
    }

    override fun resume(nowEpochMillis: Long): TimerSnapshot {
        require(snapshot.state == TimerEngineState.PAUSED) { "计时器没有暂停" }
        snapshot = advance(snapshot, nowEpochMillis).copy(
            state = TimerEngineState.RUNNING,
            lastUpdatedEpochMillis = nowEpochMillis,
        )
        return snapshot
    }

    override fun next(nowEpochMillis: Long): TimerSnapshot {
        val advanced = advance(snapshot, nowEpochMillis)
        if (advanced.state !in setOf(TimerEngineState.RUNNING, TimerEngineState.PAUSED)) {
            snapshot = advanced
            return snapshot
        }
        val nextIndex = advanced.currentStepIndex + 1
        snapshot = if (nextIndex >= advanced.steps.size) {
            advanced.copy(
                state = TimerEngineState.COMPLETED,
                currentStepIndex = advanced.steps.lastIndex,
                currentStepRemainingMillis = 0,
                endedAtEpochMillis = nowEpochMillis,
            )
        } else {
            advanced.copy(
                currentStepIndex = nextIndex,
                currentStepRemainingMillis = advanced.steps[nextIndex].seconds * 1000L,
            )
        }
        return snapshot
    }

    override fun previous(nowEpochMillis: Long): TimerSnapshot {
        val advanced = advance(snapshot, nowEpochMillis)
        if (advanced.state !in setOf(TimerEngineState.RUNNING, TimerEngineState.PAUSED)) {
            snapshot = advanced
            return snapshot
        }
        val target = max(0, advanced.currentStepIndex - 1)
        snapshot = advanced.copy(
            currentStepIndex = target,
            currentStepRemainingMillis = advanced.steps[target].seconds * 1000L,
        )
        return snapshot
    }

    override fun stop(nowEpochMillis: Long, reason: String?): TimerSnapshot {
        val advanced = advance(snapshot, nowEpochMillis)
        if (advanced.state in setOf(TimerEngineState.RUNNING, TimerEngineState.PAUSED)) {
            snapshot = advanced.copy(
                state = TimerEngineState.STOPPED,
                endedAtEpochMillis = nowEpochMillis,
                interruptionReason = reason ?: advanced.interruptionReason,
            )
        } else {
            snapshot = advanced
        }
        return snapshot
    }

    override fun reset(): TimerSnapshot {
        snapshot = TimerSnapshot()
        return snapshot
    }

    private fun advance(value: TimerSnapshot, now: Long): TimerSnapshot {
        if (value.state !in setOf(TimerEngineState.RUNNING, TimerEngineState.PAUSED)) return value
        val last = value.lastUpdatedEpochMillis ?: now
        val delta = max(0L, now - last)
        val elapsed = value.startedAtEpochMillis?.let { max(0L, now - it) } ?: value.elapsedMillis
        if (value.state == TimerEngineState.PAUSED) {
            return value.copy(
                elapsedMillis = elapsed,
                pausedMillis = value.pausedMillis + delta,
                lastUpdatedEpochMillis = now,
            )
        }

        var remainingDelta = delta
        var index = value.currentStepIndex
        var stepRemaining = value.currentStepRemainingMillis
        var consumed = 0L
        while (remainingDelta > 0 && index < value.steps.size) {
            val take = min(remainingDelta, stepRemaining)
            remainingDelta -= take
            stepRemaining -= take
            consumed += take
            if (stepRemaining == 0L) {
                index += 1
                if (index < value.steps.size) stepRemaining = value.steps[index].seconds * 1000L
            }
        }
        return if (index >= value.steps.size) {
            value.copy(
                state = TimerEngineState.COMPLETED,
                currentStepIndex = value.steps.lastIndex,
                currentStepRemainingMillis = 0,
                activeMillis = value.activeMillis + consumed,
                elapsedMillis = elapsed,
                pausedMillis = max(value.pausedMillis, elapsed - (value.activeMillis + consumed)),
                endedAtEpochMillis = now,
                lastUpdatedEpochMillis = now,
            )
        } else {
            value.copy(
                currentStepIndex = index,
                currentStepRemainingMillis = stepRemaining,
                activeMillis = value.activeMillis + consumed,
                elapsedMillis = elapsed,
                pausedMillis = max(value.pausedMillis, elapsed - (value.activeMillis + consumed)),
                lastUpdatedEpochMillis = now,
            )
        }
    }
}

fun expandRoutine(routine: RoutineTemplate): List<RuntimeStep> = routine.steps
    .flatMapIndexed { logicalIndex, step -> expandStep(step, logicalIndex) }
    .mapIndexed { runtimeIndex, step -> step.copy(runtimeIndex = runtimeIndex) }

private fun expandStep(step: RoutineStep, logicalIndex: Int): List<RuntimeStep> {
    fun runtime(
        name: String = step.name,
        seconds: Int = step.durationSeconds,
        part: RuntimePart = RuntimePart.ACTION,
        side: String? = null,
        speech: String = step.name,
    ) = RuntimeStep(
        runtimeIndex = 0,
        logicalIndex = logicalIndex,
        sourceStepId = step.stepId,
        name = name,
        phase = step.phase,
        seconds = seconds,
        part = part,
        side = side,
        speechText = speech,
        cues = step.cues,
        warnings = step.warnings,
        breath = step.breath,
    )

    val execution = step.execution
    if (execution.mode == ExecutionMode.SIMPLE) return listOf(runtime())
    val prepare = if (execution.prepareSeconds > 0) listOf(
        runtime(
            name = "准备：${step.name}",
            seconds = execution.prepareSeconds,
            part = RuntimePart.PREPARE,
            speech = "准备，${step.name}",
        ),
    ) else emptyList()
    if (execution.mode in setOf(ExecutionMode.PREPARE_ONLY, ExecutionMode.ALTERNATING)) {
        return prepare + runtime()
    }

    val left = execution.sides.getOrNull(0) ?: "左侧"
    val right = execution.sides.getOrNull(1) ?: "右侧"
    val sideSeconds = execution.sideSeconds ?: max(1, step.durationSeconds / 2)
    val switch = if (execution.switchSeconds > 0) listOf(
        runtime(
            name = "换侧",
            seconds = execution.switchSeconds,
            part = RuntimePart.SWITCH,
            side = right,
            speech = "换${right}",
        ),
    ) else emptyList()
    return prepare +
        runtime(name = "$left：${step.name}", seconds = sideSeconds, part = RuntimePart.SIDE, side = left, speech = "$left，${step.name}") +
        switch +
        runtime(name = "$right：${step.name}", seconds = sideSeconds, part = RuntimePart.SIDE, side = right, speech = "$right，${step.name}")
}

fun TimerSnapshot.toSessionFact(): TimerSessionFact {
    require(state in setOf(TimerEngineState.COMPLETED, TimerEngineState.STOPPED)) { "会话尚未结束" }
    val request = requireNotNull(request)
    val routine = request.routine
    val started = requireNotNull(startedAtEpochMillis)
    val ended = requireNotNull(endedAtEpochMillis)
    val completedRuntime = when (state) {
        TimerEngineState.COMPLETED -> steps.size
        else -> currentStepIndex
    }
    val results = routine.steps.mapIndexed { logicalIndex, logical ->
        val expanded = steps.filter { it.logicalIndex == logicalIndex }
        val planned = expanded.sumOf(RuntimeStep::seconds)
        val finished = expanded.all { it.runtimeIndex < completedRuntime } || state == TimerEngineState.COMPLETED
        val currentCompleted = expanded.firstOrNull { it.runtimeIndex == currentStepIndex }?.let {
            max(0, it.seconds - remainingSeconds)
        } ?: 0
        TimerStepResult(
            stepId = logical.stepId,
            logicalIndex = logicalIndex,
            plannedSeconds = planned,
            completedSeconds = if (finished) planned else currentCompleted,
            completed = finished,
        )
    }
    return TimerSessionFact(
        id = request.sessionId,
        date = request.date,
        routineId = routine.id,
        routineVersion = routine.version,
        routineDigest = routine.digest(),
        routineSnapshot = routine.raw,
        dailyPlanItemId = request.dailyPlanItemId,
        planTemplateId = request.planTemplateId,
        trainingType = routine.trainingType,
        startedAt = Instant.ofEpochMilli(started).toString(),
        endedAt = Instant.ofEpochMilli(ended).toString(),
        completion = if (state == TimerEngineState.COMPLETED) "completed" else "stopped",
        actualSeconds = (activeMillis / 1000).toInt(),
        activeSeconds = (activeMillis / 1000).toInt(),
        elapsedSeconds = (elapsedMillis / 1000).toInt(),
        pausedSeconds = (pausedMillis / 1000).toInt(),
        calendarVisible = routine.calendarVisible,
        countsTowardTraining = routine.countsTowardTraining,
        interruptionReason = interruptionReason,
        stepResults = results,
        idempotencyKey = request.idempotencyKey,
    )
}

private fun RoutineTemplate.digest(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toString().toByteArray())
    return "sha256:" + bytes.joinToString("") { "%02x".format(it) }
}

fun defaultSessionDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .toString()
