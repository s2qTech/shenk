package io.s2qtech.shenk.model

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineModelsTest {
    @Test
    fun decodesExplicitSceneRoleAndExecutionWithoutDroppingRawFields() {
        val data = routineData()
        val result = decodeRoutineTemplate(SharedRecord.create("routine_templates", "routine-1", data))

        assertNull(result.error)
        assertEquals(RoutineScene.RECOVERY, result.routine?.scene)
        assertEquals(RoutineRole.RECOVERY, result.routine?.role)
        assertEquals(ExecutionMode.BILATERAL_HOLD, result.routine?.steps?.single()?.execution?.mode)
        assertEquals("keep-me", result.routine?.steps?.single()?.raw?.get("futureField")?.jsonPrimitive?.content)
    }

    @Test
    fun rejectsMissingSceneInsteadOfInferringIt() {
        val data = buildJsonObject {
            routineData().forEach { (key, value) -> if (key != "scene") put(key, value) }
        }
        val result = decodeRoutineTemplate(SharedRecord.create("routine_templates", "routine-1", data))

        assertNull(result.routine)
        assertTrue(result.error.orEmpty().contains("scene"))
    }

    private fun routineData() = buildJsonObject {
        put("id", JsonPrimitive("routine-1"))
        put("title", JsonPrimitive("恢复拉伸"))
        put("version", JsonPrimitive("2.0"))
        put("trainingType", JsonPrimitive("recovery"))
        put("scene", JsonPrimitive("recovery"))
        put("role", JsonPrimitive("recovery"))
        put("lifecycle", JsonPrimitive("published"))
        put("timerVisible", JsonPrimitive(true))
        put("calendarVisible", JsonPrimitive(true))
        put("countsTowardTraining", JsonPrimitive(true))
        put("steps", buildJsonArray {
            add(buildJsonObject {
                put("stepId", JsonPrimitive("calf"))
                put("name", JsonPrimitive("小腿拉伸"))
                put("durationSeconds", JsonPrimitive(30))
                put("futureField", JsonPrimitive("keep-me"))
                put("execution", buildJsonObject {
                    put("mode", JsonPrimitive("bilateral_hold"))
                    put("prepareSeconds", JsonPrimitive(8))
                    put("sideSeconds", JsonPrimitive(30))
                    put("switchSeconds", JsonPrimitive(6))
                })
            })
        })
    }
}
