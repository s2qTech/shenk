package io.s2qtech.shenk.timer

import io.s2qtech.shenk.model.ExecutionMode
import io.s2qtech.shenk.model.RoutineLifecycle
import io.s2qtech.shenk.model.RoutineRole
import io.s2qtech.shenk.model.RoutineScene
import io.s2qtech.shenk.model.RoutineStep
import io.s2qtech.shenk.model.RoutineTemplate
import io.s2qtech.shenk.model.StepExecution
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

class TimerRuntimeTest {
    @Test
    fun oneHourQuarterSecondTickStressStaysWithinCpuBudget() {
        val steps = (0 until 60).map { index ->
            simpleStep(60).copy(stepId = "step-$index", name = "动作 $index")
        }
        val engine = NativeTimerEngine()
        engine.preview(TimerPreviewRequest(routine(*steps.toTypedArray()), "stress", "stress", "2100-01-01"))
        engine.start(0)

        val elapsed = measureTimeMillis {
            repeat(14_400) { tick -> engine.tick((tick + 1L) * 250L) }
        }

        assertEquals(TimerEngineState.COMPLETED, engine.snapshot.state)
        assertEquals(3_600_000L, engine.snapshot.activeMillis)
        assertTrue("timer CPU loop took ${elapsed}ms", elapsed < 2_000)
    }

    @Test
    fun bilateralActionRemainsOneLogicalActionButExpandsRuntimeTime() {
        val routine = routine(
            RoutineStep(
                stepId = "calf",
                name = "小腿拉伸",
                phase = "stretch",
                durationSeconds = 30,
                dose = "每侧 30 秒",
                cues = listOf("脚跟压实"),
                warnings = emptyList(),
                breath = null,
                mediaAssetId = null,
                execution = StepExecution(ExecutionMode.BILATERAL_HOLD, 8, 30, 6),
                raw = buildJsonObject {},
            ),
        )

        val steps = expandRoutine(routine)
        assertEquals(listOf(8, 30, 6, 30), steps.map(RuntimeStep::seconds))
        assertEquals(1, steps.maxOf { it.logicalIndex } + 1)
        assertEquals(74, steps.sumOf(RuntimeStep::seconds))
    }

    @Test
    fun timerTracksActiveElapsedAndPausedSeparatelyAndCreatesFact() {
        val engine = NativeTimerEngine()
        engine.preview(TimerPreviewRequest(routine(simpleStep(10)), "session-1", "idem-1", "2026-07-18"))
        engine.start(1_000)
        engine.tick(5_000)
        engine.pause(6_000, "phone_call")
        engine.tick(9_000)
        engine.resume(10_000)
        engine.tick(15_000)

        assertEquals(TimerEngineState.COMPLETED, engine.snapshot.state)
        val fact = engine.snapshot.toSessionFact()
        assertEquals(10, fact.activeSeconds)
        assertEquals(14, fact.elapsedSeconds)
        assertEquals(4, fact.pausedSeconds)
        assertEquals("completed", fact.completion)
        assertEquals("phone_call", fact.interruptionReason)
    }

    @Test
    fun previewRejectsNonPublishedRoutine() {
        val engine = NativeTimerEngine()
        val archived = routine(simpleStep(10)).copy(lifecycle = RoutineLifecycle.ARCHIVED)
        val result = runCatching {
            engine.preview(TimerPreviewRequest(archived, "session", "idem", "2026-07-18"))
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun snapshotAlwaysExposesTheImmediateNextRuntimeStep() {
        val engine = NativeTimerEngine()
        engine.preview(
            TimerPreviewRequest(
                routine(simpleStep(10), simpleStep(20).copy(stepId = "next", name = "椅子坐站")),
                "session",
                "idem",
                "2026-07-18",
            ),
        )

        assertEquals("椅子坐站", engine.snapshot.nextStep?.name)
        engine.start(1_000)
        engine.next(2_000)
        assertEquals(null, engine.snapshot.nextStep)
    }

    @Test
    fun processRestorePausesInsteadOfCountingOfflineGapAsActiveTraining() {
        val engine = NativeTimerEngine()
        engine.preview(TimerPreviewRequest(routine(simpleStep(60)), "session", "idem", "2026-07-18"))
        engine.start(1_000)
        engine.tick(11_000)
        val restored = restoreTimerSnapshot(routine(simpleStep(60)), engine.snapshot.toCheckpoint()!!, 21_000)

        assertEquals(TimerEngineState.PAUSED, restored.state)
        assertEquals(10_000, restored.activeMillis)
        assertEquals(10_000, restored.pausedMillis)
        assertEquals("recovered_after_process_death", restored.interruptionReason)
    }

    private fun simpleStep(seconds: Int) = RoutineStep(
        stepId = "march",
        name = "原地慢走",
        phase = "warmup",
        durationSeconds = seconds,
        dose = null,
        cues = emptyList(),
        warnings = emptyList(),
        breath = null,
        mediaAssetId = null,
        execution = StepExecution(),
        raw = buildJsonObject {},
    )

    private fun routine(vararg steps: RoutineStep) = RoutineTemplate(
        id = "routine-1",
        title = "恢复拉伸",
        version = "2.0",
        trainingType = "recovery",
        scene = RoutineScene.RECOVERY,
        role = RoutineRole.RECOVERY,
        lifecycle = RoutineLifecycle.PUBLISHED,
        estimatedMinutes = null,
        timerVisible = true,
        calendarVisible = true,
        countsTowardTraining = true,
        steps = steps.toList(),
        raw = buildJsonObject {},
    )
}
