package io.s2qtech.shenk.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.s2qtech.shenk.model.GuidanceSource
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import io.s2qtech.shenk.model.TrainingLog
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarRecordRepositoryInstrumentedTest {
    private lateinit var database: ShenkDatabase
    private lateinit var local: LocalFirstRepository
    private lateinit var repository: CalendarRecordRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ShenkDatabase::class.java).build()
        var sequence = 0
        local = LocalFirstRepository(
            database = database,
            localDeviceId = "synthetic-device",
            nextId = { "synthetic-operation-${sequence++}" },
        )
        repository = CalendarRecordRepository(local)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun actualRecordWinsOverFormalPlanAndAllActualsRemainAvailable() {
        runBlocking {
            val date = LocalDate.of(2100, 2, 2)
            local.applyRemote(record("daily_plan_items", "plan", buildJsonObject {
                put("date", JsonPrimitive(date.toString()))
                put("title", JsonPrimitive("力量训练"))
                put("trainingType", JsonPrimitive("strength"))
            }))
            repository.saveTrainingLog(log("walk", date, "easy_walk"))
            repository.saveTrainingLog(log("stretch", date, "stretch"))

            val details = repository.observeDay(date).first()
            assertEquals(GuidanceSource.ACTUAL, details.guidance.source)
            assertEquals(2, details.actualLogs.size)
            assertEquals("拉伸", details.actualLogs.first().displayTitle)

            val month = repository.observeMonth(YearMonth.from(date)).first()
            val day = month.weeks.flatten().filterNotNull().first { it.date == date }
            assertEquals(GuidanceSource.ACTUAL, day.guidance.source)
        }
    }

    @Test
    fun editingPreservesUnknownFieldsAndDeleteImmediatelyHidesRecord() {
        runBlocking {
            val date = LocalDate.of(2100, 2, 3)
            val id = "synthetic-log"
            local.persistAndEnqueue(
                record("training_logs", id, buildJsonObject {
                    put("id", JsonPrimitive(id))
                    put("date", JsonPrimitive(date.toString()))
                    put("type", JsonPrimitive("strength"))
                    put("status", JsonPrimitive("completed"))
                    put("source", JsonPrimitive("manual"))
                    put("calendarVisible", JsonPrimitive(true))
                    put("countsTowardTraining", JsonPrimitive(true))
                    put("futureWebField", JsonPrimitive("preserve-me"))
                }),
                SharedEntityOwner.RECORD,
            )

            repository.saveTrainingLog(log(id, date, "strength").copy(durationSec = 2700))
            assertEquals("preserve-me", local.get("training_logs", id)?.data?.get("futureWebField")?.toString()?.trim('"'))

            repository.deleteTrainingLog(id)
            assertFalse(repository.observeTrainingLogs().first().any { it.id == id })
            assertNotNull(local.get("training_logs", id)?.deletedAt)
            assertEquals(1, database.outbox().count())
        }
    }

    private fun log(id: String, date: LocalDate, type: String) = TrainingLog(
        id = id,
        date = date.toString(),
        type = type,
        status = "completed",
        source = "manual",
    )

    private fun record(entity: String, id: String, data: kotlinx.serialization.json.JsonObject) =
        SharedRecord.create(entity, id, data, contractVersion = "2.0")
}
