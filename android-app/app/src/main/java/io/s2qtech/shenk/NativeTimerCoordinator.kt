package io.s2qtech.shenk

import android.app.Application
import android.content.Context
import android.content.Intent
import io.s2qtech.shenk.model.RoutineTemplate
import io.s2qtech.shenk.model.TimerSessionFact
import io.s2qtech.shenk.sync.NativeTimerSessionRepository
import io.s2qtech.shenk.sync.SyncScheduler
import io.s2qtech.shenk.timer.NativeTimerEngine
import io.s2qtech.shenk.timer.TimerCheckpoint
import io.s2qtech.shenk.timer.TimerEngineState
import io.s2qtech.shenk.timer.TimerPreviewRequest
import io.s2qtech.shenk.timer.TimerSnapshot
import io.s2qtech.shenk.timer.restoreTimerSnapshot
import io.s2qtech.shenk.timer.toCheckpoint
import io.s2qtech.shenk.timer.toSessionFact
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NativeTimerCoordinator(
    private val application: Application,
    private val sessions: NativeTimerSessionRepository,
    private val scope: CoroutineScope,
) {
    private var engine = NativeTimerEngine()
    private val preferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutableSnapshot = MutableStateFlow(engine.snapshot)
    private val mutableCues = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val persistedSessions = ConcurrentHashMap.newKeySet<String>()
    private val successfullyPersistedSessions = ConcurrentHashMap.newKeySet<String>()
    private var ticker: Job? = null
    private var foregroundServiceActive = false
    private var lastCheckpointAt = 0L
    private var lastCueStep = -1
    private var lastCountdown = -1
    private var lastUpcomingStep = -1

    val snapshot: StateFlow<TimerSnapshot> = mutableSnapshot.asStateFlow()
    val cues: SharedFlow<String> = mutableCues.asSharedFlow()

    fun select(
        routine: RoutineTemplate,
        date: LocalDate = LocalDate.now(),
        dailyPlanItemId: String? = null,
        planTemplateId: String? = null,
    ) {
        val sessionId = "android-timer-${UUID.randomUUID()}"
        publish(
            engine.preview(
                TimerPreviewRequest(
                    routine = routine,
                    sessionId = sessionId,
                    idempotencyKey = sessionId,
                    date = date.toString(),
                    dailyPlanItemId = dailyPlanItemId,
                    planTemplateId = planTemplateId,
                ),
            ),
        )
    }

    fun restoreIfPossible(routines: List<RoutineTemplate>) {
        if (mutableSnapshot.value.state != TimerEngineState.IDLE) return
        val checkpoint = readCheckpoint() ?: return
        val routine = routines.firstOrNull { it.id == checkpoint.routineId } ?: return
        val restored = restoreTimerSnapshot(routine, checkpoint, System.currentTimeMillis())
        engine = NativeTimerEngine(restored)
        publish(restored, announce = false)
    }

    fun start() {
        publish(engine.start(System.currentTimeMillis()))
        ensureTicker()
    }

    fun pause(reason: String? = null) = publish(engine.pause(System.currentTimeMillis(), reason))

    fun resume() {
        publish(engine.resume(System.currentTimeMillis()))
        ensureTicker()
    }

    fun next() = publish(engine.next(System.currentTimeMillis()))

    fun previous() = publish(engine.previous(System.currentTimeMillis()))

    fun stop(reason: String = "user_stopped") = publish(engine.stop(System.currentTimeMillis(), reason))

    fun reset() {
        val current = mutableSnapshot.value
        ticker?.cancel()
        ticker = null
        val sessionId = current.request?.sessionId
        val keepRecoveryPoint = current.state in TERMINAL_STATES &&
            sessionId != null && sessionId !in successfullyPersistedSessions
        if (!keepRecoveryPoint) preferences.edit().clear().apply()
        NativeTimerForegroundService.stop(application)
        foregroundServiceActive = false
        lastCueStep = -1
        lastCountdown = -1
        lastUpcomingStep = -1
        publish(engine.reset(), announce = false)
    }

    fun pauseForPhoneCall() {
        if (mutableSnapshot.value.state == TimerEngineState.RUNNING) pause("phone_call")
    }

    fun terminalFact(): TimerSessionFact? = mutableSnapshot.value
        .takeIf { it.state in setOf(TimerEngineState.COMPLETED, TimerEngineState.STOPPED) }
        ?.let { runCatching(it::toSessionFact).getOrNull() }

    private fun ensureTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (mutableSnapshot.value.state in setOf(TimerEngineState.RUNNING, TimerEngineState.PAUSED)) {
                publish(engine.tick(System.currentTimeMillis()))
                delay(250)
            }
        }
    }

    private fun publish(value: TimerSnapshot, announce: Boolean = true) {
        val previous = mutableSnapshot.value
        mutableSnapshot.value = value
        if (value.state in ACTIVE_STATES) {
            val now = System.currentTimeMillis()
            val importantChange = previous.state != value.state ||
                previous.currentStepIndex != value.currentStepIndex ||
                previous.request?.sessionId != value.request?.sessionId
            if (importantChange || now - lastCheckpointAt >= CHECKPOINT_INTERVAL_MILLIS) {
                writeCheckpoint(value.toCheckpoint())
                lastCheckpointAt = now
            }
            if (!foregroundServiceActive) {
                NativeTimerForegroundService.start(application)
                foregroundServiceActive = true
            }
        } else if (value.state in TERMINAL_STATES) {
            writeCheckpoint(value.toCheckpoint())
            if (foregroundServiceActive) NativeTimerForegroundService.stop(application)
            foregroundServiceActive = false
            persistTerminal(value)
        }

        if (!announce || value.state != TimerEngineState.RUNNING) return
        val step = value.currentStep ?: return
        if (step.runtimeIndex != lastCueStep) {
            lastCueStep = step.runtimeIndex
            lastCountdown = -1
            lastUpcomingStep = -1
            val detail = buildList {
                add(step.speechText)
                addAll(step.cues)
                step.breath?.takeIf(String::isNotBlank)?.let(::add)
            }.joinToString("。")
            mutableCues.tryEmit(detail)
        }
        if (value.remainingSeconds == 5 && lastUpcomingStep != step.runtimeIndex) {
            value.nextStep?.let { next ->
                lastUpcomingStep = step.runtimeIndex
                mutableCues.tryEmit("下一项，${next.speechText}")
            }
        }
        if (value.remainingSeconds in 1..3 && value.remainingSeconds != lastCountdown) {
            lastCountdown = value.remainingSeconds
            mutableCues.tryEmit(value.remainingSeconds.toString())
        }
    }

    private fun persistTerminal(value: TimerSnapshot) {
        val fact = runCatching(value::toSessionFact).getOrNull() ?: return
        if (!persistedSessions.add(fact.id)) return
        scope.launch {
            runCatching { sessions.persistIfAbsent(fact) }
                .onSuccess {
                    successfullyPersistedSessions.add(fact.id)
                    preferences.edit().clear().apply()
                    SyncScheduler(application).enqueue()
                }
                .onFailure { persistedSessions.remove(fact.id) }
        }
    }

    private fun writeCheckpoint(checkpoint: TimerCheckpoint?) {
        if (checkpoint == null) return
        preferences.edit()
            .putString("sessionId", checkpoint.sessionId)
            .putString("idempotencyKey", checkpoint.idempotencyKey)
            .putString("routineId", checkpoint.routineId)
            .putString("date", checkpoint.date)
            .putNullableString("dailyPlanItemId", checkpoint.dailyPlanItemId)
            .putNullableString("planTemplateId", checkpoint.planTemplateId)
            .putString("state", checkpoint.state.name)
            .putInt("currentStepIndex", checkpoint.currentStepIndex)
            .putLong("currentStepRemainingMillis", checkpoint.currentStepRemainingMillis)
            .putLong("activeMillis", checkpoint.activeMillis)
            .putLong("elapsedMillis", checkpoint.elapsedMillis)
            .putLong("pausedMillis", checkpoint.pausedMillis)
            .putNullableLong("startedAtEpochMillis", checkpoint.startedAtEpochMillis)
            .putNullableLong("endedAtEpochMillis", checkpoint.endedAtEpochMillis)
            .putNullableLong("lastUpdatedEpochMillis", checkpoint.lastUpdatedEpochMillis)
            .putNullableString("interruptionReason", checkpoint.interruptionReason)
            .apply()
    }

    private fun readCheckpoint(): TimerCheckpoint? {
        val sessionId = preferences.getString("sessionId", null) ?: return null
        return TimerCheckpoint(
            sessionId = sessionId,
            idempotencyKey = preferences.getString("idempotencyKey", sessionId) ?: sessionId,
            routineId = preferences.getString("routineId", null) ?: return null,
            date = preferences.getString("date", null) ?: return null,
            dailyPlanItemId = preferences.getString("dailyPlanItemId", null),
            planTemplateId = preferences.getString("planTemplateId", null),
            state = runCatching {
                TimerEngineState.valueOf(preferences.getString("state", TimerEngineState.PAUSED.name)!!)
            }.getOrDefault(TimerEngineState.PAUSED),
            currentStepIndex = preferences.getInt("currentStepIndex", 0),
            currentStepRemainingMillis = preferences.getLong("currentStepRemainingMillis", 0),
            activeMillis = preferences.getLong("activeMillis", 0),
            elapsedMillis = preferences.getLong("elapsedMillis", 0),
            pausedMillis = preferences.getLong("pausedMillis", 0),
            startedAtEpochMillis = preferences.nullableLong("startedAtEpochMillis"),
            endedAtEpochMillis = preferences.nullableLong("endedAtEpochMillis"),
            lastUpdatedEpochMillis = preferences.nullableLong("lastUpdatedEpochMillis"),
            interruptionReason = preferences.getString("interruptionReason", null),
        )
    }

    companion object {
        private const val PREFS_NAME = "native_timer_checkpoint"
        private const val CHECKPOINT_INTERVAL_MILLIS = 5_000L
        private val ACTIVE_STATES = setOf(TimerEngineState.RUNNING, TimerEngineState.PAUSED)
        private val TERMINAL_STATES = setOf(TimerEngineState.COMPLETED, TimerEngineState.STOPPED)
    }
}

private fun android.content.SharedPreferences.Editor.putNullableString(key: String, value: String?) = apply {
    if (value == null) remove(key) else putString(key, value)
}

private fun android.content.SharedPreferences.Editor.putNullableLong(key: String, value: Long?) = apply {
    if (value == null) remove(key) else putLong(key, value)
}

private fun android.content.SharedPreferences.nullableLong(key: String): Long? =
    if (contains(key)) getLong(key, 0) else null
