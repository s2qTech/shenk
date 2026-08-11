package io.s2qtech.shenk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TodayModelsTest {
    @Test
    fun preWorkoutOnlyOverridesFieldsThatChanged() {
        val morning = checkin(
            kind = CheckinKind.MORNING,
            energy = 4,
            fatigue = 1,
            pain = emptyList(),
        )
        val delta = checkin(
            kind = CheckinKind.PRE_WORKOUT,
            energy = null,
            fatigue = 3,
            pain = null,
        )

        val effective = EffectiveStatusResolver.resolve(morning, delta)

        assertEquals(4, effective.energy)
        assertEquals(3, effective.fatigue)
        assertEquals(emptyList<PainEntry>(), effective.pain)
    }

    @Test
    fun omittedDataStaysMissing() {
        val effective = EffectiveStatusResolver.resolve(null, null)

        assertNull(effective.energy)
        assertNull(effective.fatigue)
        assertNull(effective.pain)
    }

    @Test
    fun actualOutranksPlanAndSuggestion() {
        val actual = TodayGuidance(GuidanceSource.ACTUAL, "已完成力量", "strength")
        val plan = TodayGuidance(GuidanceSource.FORMAL_PLAN, "力量训练", "strength")
        val aiSuggestion = TodayGuidance(GuidanceSource.LOCAL_SUGGESTION, "轻松走", "easy_walk")
        val fallback = TodayGuidance(GuidanceSource.LOCAL_SUGGESTION, "普通走", "easy_walk")

        assertEquals(actual, TodayGuidanceResolver.resolve(actual, plan, aiSuggestion, fallback))
        assertEquals(plan, TodayGuidanceResolver.resolve(null, plan, aiSuggestion, fallback))
        assertEquals(aiSuggestion, TodayGuidanceResolver.resolve(null, null, aiSuggestion, fallback))
        assertEquals(fallback, TodayGuidanceResolver.resolve(null, null, null, fallback))
    }

    @Test
    fun onlyRunnableFormalPlanOpensTimer() {
        val strength = TodayGuidance(
            source = GuidanceSource.FORMAL_PLAN,
            title = "力量训练",
            trainingType = "strength",
            routineId = "routine-strength",
        )
        val strengthWithoutRoutine = strength.copy(routineId = null)
        val suggestionWithRoutine = strength.copy(source = GuidanceSource.LOCAL_SUGGESTION)

        assertEquals(TodayPrimaryAction.OPEN_TIMER, TodayPrimaryActionResolver.resolve(strength))
        assertEquals(TodayPrimaryAction.RECORD_DAY, TodayPrimaryActionResolver.resolve(strengthWithoutRoutine))
        assertEquals(TodayPrimaryAction.RECORD_DAY, TodayPrimaryActionResolver.resolve(suggestionWithRoutine))
    }

    @Test
    fun restAndWalkingPlansRecordDayEvenWhenTheyContainRoutineReference() {
        listOf("rest", "easy_walk", "quality_walk").forEach { type ->
            val guidance = TodayGuidance(
                source = GuidanceSource.FORMAL_PLAN,
                title = type,
                trainingType = type,
                routineId = "unexpected-routine-reference",
            )

            assertEquals(TodayPrimaryAction.RECORD_DAY, TodayPrimaryActionResolver.resolve(guidance))
        }
    }

    @Test
    fun completedDayHasNoPrimaryAction() {
        val actual = TodayGuidance(GuidanceSource.ACTUAL, "今日已记录", "rest")

        assertEquals(TodayPrimaryAction.NONE, TodayPrimaryActionResolver.resolve(actual))
    }

    @Test
    fun deepSleepCannotExceedTotalSleep() {
        assertThrows(IllegalArgumentException::class.java) {
            checkin(
                kind = CheckinKind.MORNING,
                sleepDurationMinutes = 300,
                deepSleepMinutes = 301,
            )
        }
    }

    private fun checkin(
        kind: CheckinKind,
        sleepDurationMinutes: Int? = null,
        deepSleepMinutes: Int? = null,
        energy: Int? = null,
        fatigue: Int? = null,
        pain: List<PainEntry>? = null,
    ) = StatusCheckin(
        id = "fixture-${kind.wireValue}",
        date = "2099-01-01",
        kind = kind,
        observedAt = "2099-01-01T08:00:00Z",
        sleepDurationMinutes = sleepDurationMinutes,
        deepSleepMinutes = deepSleepMinutes,
        energy = energy,
        fatigue = fatigue,
        pain = pain,
    )
}
