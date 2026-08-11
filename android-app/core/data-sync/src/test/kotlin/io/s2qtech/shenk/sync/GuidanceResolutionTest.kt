package io.s2qtech.shenk.sync

import io.s2qtech.shenk.model.GuidanceSource
import io.s2qtech.shenk.model.SharedRecord
import java.time.LocalDate
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceResolutionTest {
    @Test
    fun indexedThirteenMonthProjectionPreservesDayPriority() {
        val start = LocalDate.of(2099, 1, 1)
        val plans = (0 until 400).map { offset ->
            val date = start.plusDays(offset.toLong())
            SharedRecord.create(
                entity = "daily_plan_items",
                id = "plan-$offset",
                data = buildJsonObject {
                    put("date", JsonPrimitive(date.toString()))
                    put("title", JsonPrimitive("计划 $offset"))
                    put("trainingType", JsonPrimitive("strength"))
                },
            )
        }
        val actualDate = start.plusDays(200)
        val actual = SharedRecord.create(
            entity = "training_logs",
            id = "actual-200",
            data = buildJsonObject {
                put("date", JsonPrimitive(actualDate.toString()))
                put("type", JsonPrimitive("easy_walk"))
                put("status", JsonPrimitive("completed"))
                put("source", JsonPrimitive("manual"))
            },
        )

        val index = GuidanceResolution.index(listOf(actual), plans, emptyList())
        val projected = (0 until 400).map { offset -> index.resolve(start.plusDays(offset.toLong())) }

        assertEquals(400, projected.size)
        assertEquals(GuidanceSource.ACTUAL, projected[200].guidance.source)
        assertEquals(GuidanceSource.FORMAL_PLAN, projected[199].guidance.source)
        assertTrue(projected.all { it.guidance.title.isNotBlank() })
    }

    @Test
    fun generatedReviewSuggestionIsUsedOnlyWithoutFormalPlan() {
        val date = LocalDate.of(2099, 1, 2)
        val review = SharedRecord.create(
            entity = "daily_reviews",
            id = "review-1",
            data = buildJsonObject {
                put("date", JsonPrimitive(date.toString()))
                put("status", JsonPrimitive("generated"))
                put("version", JsonPrimitive(1))
                put("localSuggestion", buildJsonObject {
                    put("date", JsonPrimitive(date.toString()))
                    put("title", JsonPrimitive("轻松走"))
                    put("trainingType", JsonPrimitive("easy_walk"))
                    put("estimatedMinutes", JsonPrimitive(25))
                })
            },
        )

        val suggestionOnly = GuidanceResolution.resolve(
            date = date,
            logs = emptyList(),
            plans = emptyList(),
            adjustments = emptyList(),
            reviews = listOf(review),
        )
        assertEquals("轻松走", suggestionOnly.guidance.title)
        assertEquals(25, suggestionOnly.guidance.estimatedMinutes)

        val plan = SharedRecord.create(
            entity = "daily_plan_items",
            id = "plan-1",
            data = buildJsonObject {
                put("date", JsonPrimitive(date.toString()))
                put("title", JsonPrimitive("力量训练"))
                put("trainingType", JsonPrimitive("strength"))
            },
        )
        val withPlan = GuidanceResolution.resolve(
            date = date,
            logs = emptyList(),
            plans = listOf(plan),
            adjustments = emptyList(),
            reviews = listOf(review),
        )
        assertEquals(GuidanceSource.FORMAL_PLAN, withPlan.guidance.source)
        assertEquals("力量训练", withPlan.guidance.title)
    }

    @Test
    fun structuredLegacyFieldsRemainMissingInsteadOfCrashingCalendar() {
        val malformedDate = buildJsonArray { add(JsonPrimitive("2026-07-18")) }
        val malformedRecords = listOf(
            SharedRecord.create(
                entity = "training_logs",
                id = "legacy-log",
                data = buildJsonObject {
                    put("date", malformedDate)
                    put("type", JsonPrimitive("strength"))
                },
            ),
            SharedRecord.create(
                entity = "daily_plan_items",
                id = "legacy-plan",
                data = buildJsonObject {
                    put("date", malformedDate)
                    put("trainingType", JsonPrimitive("strength"))
                },
            ),
            SharedRecord.create(
                entity = "plan_adjustments",
                id = "legacy-adjustment",
                data = buildJsonObject {
                    put("date", malformedDate)
                    put("trainingType", JsonPrimitive("recovery"))
                },
            ),
        )

        val resolved = GuidanceResolution.resolve(
            date = LocalDate.of(2026, 7, 18),
            logs = listOf(malformedRecords[0]),
            plans = listOf(malformedRecords[1]),
            adjustments = listOf(malformedRecords[2]),
        )

        assertEquals(GuidanceSource.LOCAL_SUGGESTION, resolved.guidance.source)
        assertEquals(emptyList<Any>(), resolved.actualLogs)
        assertNull(buildJsonObject { put("value", malformedDate) }.fieldString("value"))
        assertNull(buildJsonObject { put("value", malformedDate) }.fieldInt("value"))
        assertNull(buildJsonObject { put("value", malformedDate) }.fieldDouble("value"))
        assertNull(buildJsonObject { put("value", malformedDate) }.fieldBoolean("value"))
    }
}
