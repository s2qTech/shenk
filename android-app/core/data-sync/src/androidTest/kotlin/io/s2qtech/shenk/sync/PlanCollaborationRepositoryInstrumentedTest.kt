package io.s2qtech.shenk.sync

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanCollaborationRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: ShenkDatabase
    private lateinit var records: LocalFirstRepository
    private lateinit var plans: PlanCollaborationRepository

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ShenkDatabase::class.java).build()
        records = LocalFirstRepository(database, localDeviceId = "synthetic-device")
        plans = PlanCollaborationRepository(
            records = records,
            now = { Instant.parse("2100-01-01T00:00:00Z") },
            nextId = { "synthetic-batch" },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun routineOnlyPatchLeavesExistingCalendarUntouchedAndQueuesAtomically() {
        runBlocking {
            records.persistAndEnqueue(existingDailyPlan(), SharedEntityOwner.PLANNING)
            plans.apply(validRoutinePatch())

            assertNotNull(records.get("daily_plan_items", "daily-existing"))
            assertNotNull(records.get("routine_templates", "routine-new"))
            assertNotNull(records.get("plan_import_batches", "plan_batch_synthetic-batch"))
            assertEquals(3, database.records().count())
            assertEquals(3, database.outbox().count())
        }
    }

    @Test
    fun invalidPatchRejectsWholeBatchWithoutAnyWrite() {
        runBlocking {
            val invalid = validRoutinePatch().replace("\"scene\":\"home\",", "")
            val failure = runCatching { plans.apply(invalid) }.exceptionOrNull()

            assertNotNull(failure)
            assertEquals(0, database.records().count())
            assertEquals(0, database.outbox().count())
        }
    }

    @Test
    fun latestApplyCanBeUndoneOnceWithoutRemovingHistory() {
        runBlocking {
            plans.apply(validRoutinePatch())
            plans.undoLatest()

            assertNotNull(records.get("routine_templates", "routine-new")?.deletedAt)
            assertEquals("undone", records.get("plan_import_batches", "plan_batch_synthetic-batch")?.data?.get("status")?.let { (it as JsonPrimitive).content })
            assertFalse(runCatching { plans.undoLatest() }.isSuccess)
        }
    }

    @Test
    fun pendingCloudPatchIsAppliedAndMarkedHandledInOneLocalBatch() {
        runBlocking {
            records.persistAndEnqueue(pendingCoachPatch(), SharedEntityOwner.PLANNING_EXCHANGE)

            val pending = plans.observePendingCoachPatches().first()
            assertEquals(listOf("cloud-patch"), pending.map { it.id })

            plans.applyPending("cloud-patch")

            assertNotNull(records.get("routine_templates", "routine-new"))
            assertEquals(
                "applied",
                records.get("coach_plan_patches", "cloud-patch")?.data?.get("status")?.let { (it as JsonPrimitive).content },
            )
            assertTrue(plans.observePendingCoachPatches().first().isEmpty())
            assertEquals(3, database.records().count())
            assertEquals(3, database.outbox().count())
        }
    }

    @Test
    fun rejectingCloudPatchDoesNotChangeFormalPlanningRecords() {
        runBlocking {
            records.persistAndEnqueue(pendingCoachPatch(), SharedEntityOwner.PLANNING_EXCHANGE)

            plans.rejectPending("cloud-patch")

            assertEquals(
                "rejected",
                records.get("coach_plan_patches", "cloud-patch")?.data?.get("status")?.let { (it as JsonPrimitive).content },
            )
            assertEquals(null, records.get("routine_templates", "routine-new"))
            assertTrue(plans.observePendingCoachPatches().first().isEmpty())
            assertEquals(1, database.records().count())
            assertEquals(1, database.outbox().count())
        }
    }

    private fun existingDailyPlan() = SharedRecord.create(
        entity = "daily_plan_items",
        id = "daily-existing",
        data = buildJsonObject {
            put("id", JsonPrimitive("daily-existing"))
            put("date", JsonPrimitive("2100-01-01"))
            put("title", JsonPrimitive("普通走"))
            put("trainingType", JsonPrimitive("easy_walk"))
            put("status", JsonPrimitive("planned"))
        },
        contractVersion = "2.0",
    )

    private fun validRoutinePatch() = """
        {
          "schema":"coach_plan_patch",
          "contractVersion":"2.0",
          "effectiveFrom":"2100-01-01",
          "routineTemplates":[{
            "id":"routine-new",
            "title":"力量训练",
            "trainingType":"strength",
            "scene":"home",
            "role":"main",
            "lifecycle":"published",
            "timerVisible":true,
            "calendarVisible":true,
            "countsTowardTraining":true,
            "steps":[{"stepId":"step-1","name":"原地慢走","durationSeconds":60}]
          }],
          "dailyPlanItems":[]
        }
    """.trimIndent()

    private fun pendingCoachPatch() = SharedRecord.create(
        entity = "coach_plan_patches",
        id = "cloud-patch",
        data = buildJsonObject {
            put("id", JsonPrimitive("cloud-patch"))
            put("runId", JsonPrimitive("cloud-run"))
            put("status", JsonPrimitive("pending"))
            put("receivedAt", JsonPrimitive("2100-01-01T00:00:00Z"))
            put("snapshotDigest", JsonPrimitive("synthetic-digest"))
            put("patch", Json.parseToJsonElement(validRoutinePatch()))
        },
        contractVersion = "2.0",
    )
}
