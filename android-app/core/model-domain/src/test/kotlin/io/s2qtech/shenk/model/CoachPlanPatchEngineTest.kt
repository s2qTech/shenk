package io.s2qtech.shenk.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachPlanPatchEngineTest {
    @Test
    fun routineOnlyPatchLeavesMissingAndEmptyCollectionsUntouched() {
        val existingPlan = SharedRecord.create(
            "daily_plan_items",
            "daily-existing",
            buildJsonObject {
                put("id", JsonPrimitive("daily-existing"))
                put("date", JsonPrimitive("2026-07-22"))
                put("title", JsonPrimitive("普通走"))
                put("trainingType", JsonPrimitive("easy_walk"))
                put("status", JsonPrimitive("planned"))
            },
        )
        val preview = CoachPlanPatchEngine.preview(
            """
            {
              "schema":"coach_plan_patch",
              "contractVersion":"2.0",
              "effectiveFrom":"2026-07-22",
              "routineTemplates":[${validRoutine()}],
              "dailyPlanItems":[]
            }
            """.trimIndent(),
            listOf(existingPlan),
        )

        assertTrue(preview.valid)
        assertEquals(1, preview.added)
        assertEquals(0, preview.deleted)
        assertTrue(preview.changes.none { it.entity == "daily_plan_items" })
    }

    @Test
    fun invalidAuthorityRejectsWholePatch() {
        val invalid = validRoutine().replace("\"scene\":\"home\",", "")
        val preview = CoachPlanPatchEngine.preview(
            """{"schema":"coach_plan_patch","contractVersion":"2.0","effectiveFrom":"2026-07-22","routineTemplates":[$invalid]}""",
            emptyList(),
        )

        assertFalse(preview.valid)
        assertTrue(preview.errors.any { it.contains("scene") })
    }

    @Test
    fun explicitDeleteIsCountedAndUnknownDeleteIsRejected() {
        val existing = SharedRecord.create(
            "routine_templates",
            "routine-one",
            buildJsonObject {
                put("id", JsonPrimitive("routine-one"))
                put("title", JsonPrimitive("力量训练"))
            },
        )
        val known = CoachPlanPatchEngine.preview(
            """{"schema":"coach_plan_patch","contractVersion":"2.0","effectiveFrom":"2026-07-22","routineTemplates":[{"id":"routine-one","operation":"delete"}]}""",
            listOf(existing),
        )
        val unknown = CoachPlanPatchEngine.preview(
            """{"schema":"coach_plan_patch","contractVersion":"2.0","effectiveFrom":"2026-07-22","routineTemplates":[{"id":"missing","operation":"delete"}]}""",
            emptyList(),
        )

        assertTrue(known.valid)
        assertEquals(1, known.deleted)
        assertFalse(unknown.valid)
    }

    @Test
    fun replaceModeIsRejected() {
        val preview = CoachPlanPatchEngine.preview(
            """{"schema":"coach_plan_patch","contractVersion":"2.0","effectiveFrom":"2026-07-22","replaceMode":true,"routineTemplates":[${validRoutine()}]}""",
            emptyList(),
        )
        assertFalse(preview.valid)
        assertTrue(preview.errors.any { it.contains("replaceMode") })
    }

    @Test
    fun missingContractVersionIsRejected() {
        val preview = CoachPlanPatchEngine.preview(
            """{"schema":"coach_plan_patch","effectiveFrom":"2026-07-22","routineTemplates":[${validRoutine()}]}""",
            emptyList(),
        )
        assertFalse(preview.valid)
        assertTrue(preview.errors.any { it.contains("contractVersion") })
    }

    private fun validRoutine(): String = """
        {
          "id":"routine-new",
          "title":"力量训练",
          "version":"1",
          "trainingType":"strength",
          "scene":"home",
          "role":"main",
          "lifecycle":"published",
          "timerVisible":true,
          "calendarVisible":true,
          "countsTowardTraining":true,
          "steps":[{"stepId":"march","name":"原地慢走","durationSeconds":60}]
        }
    """.trimIndent()
}
