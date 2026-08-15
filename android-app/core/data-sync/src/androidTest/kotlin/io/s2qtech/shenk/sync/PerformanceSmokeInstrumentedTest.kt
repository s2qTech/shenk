package io.s2qtech.shenk.sync

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.s2qtech.shenk.model.GuidanceSource
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformanceSmokeInstrumentedTest {
    private lateinit var database: ShenkDatabase
    private lateinit var records: LocalFirstRepository

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ShenkDatabase::class.java).build()
        var operation = 0
        records = LocalFirstRepository(
            database = database,
            localDeviceId = "synthetic-performance-device",
            timeSource = PerformanceClock,
            nextId = { "synthetic-performance-${operation++}" },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun thirteenMonthProjectionWithRealisticVolumeStaysWithinBudget() {
        runBlocking {
            val start = LocalDate.of(2099, 1, 1)
            val plans = (0 until 400).map { offset -> plan(start.plusDays(offset.toLong()), offset) }
            val actual = (0 until 160).map { offset -> trainingLog(start.plusDays((offset * 2L)), offset) }
            records.restoreBackup(plans + actual)
            val repository = CalendarRecordRepository(records)

            lateinit var days: List<io.s2qtech.shenk.model.CalendarDay>
            val started = SystemClock.elapsedRealtime()
            days = repository.observeRange(start, start.plusDays(399)).first()
            val elapsed = SystemClock.elapsedRealtime() - started
            Log.i(TAG, "calendar_projection_ms=$elapsed days=${days.size} records=${plans.size + actual.size}")

            assertEquals(400, days.size)
            assertEquals(GuidanceSource.ACTUAL, days.first().guidance.source)
            assertTrue("calendar projection took ${elapsed}ms", elapsed < CALENDAR_BUDGET_MILLIS)
        }
    }

    @Test
    fun fullOutboxBatchSyncStaysWithinBudget() {
        runBlocking {
            val date = LocalDate.of(2099, 1, 1)
            val batch = (0 until SyncEngine.OUTBOX_BATCH_SIZE).map { offset -> trainingLog(date, offset) }
            records.persistBatchAndEnqueue(batch, SharedEntityOwner.RECORD)
            val engine = SyncEngine(
                database = database,
                repository = records,
                api = AcceptingWorkerApi,
                deviceId = "synthetic-performance-device",
                timeSource = PerformanceClock,
            )

            val started = SystemClock.elapsedRealtime()
            val result = engine.synchronize()
            val elapsed = SystemClock.elapsedRealtime() - started
            Log.i(TAG, "sync_batch_ms=$elapsed pushed=${result.pushed}")

            assertEquals(SyncEngine.OUTBOX_BATCH_SIZE, result.pushed)
            assertEquals(0, database.outbox().count())
            assertTrue("sync batch took ${elapsed}ms", elapsed < SYNC_BUDGET_MILLIS)
        }
    }

    private fun plan(date: LocalDate, offset: Int) = SharedRecord.create(
        entity = "daily_plan_items",
        id = "synthetic-plan-$offset",
        data = buildJsonObject {
            put("date", JsonPrimitive(date.toString()))
            put("title", JsonPrimitive("计划 $offset"))
            put("trainingType", JsonPrimitive("strength"))
        },
        contractVersion = "2.0",
    )

    private fun trainingLog(date: LocalDate, offset: Int) = SharedRecord.create(
        entity = "training_logs",
        id = "synthetic-log-$offset-${date}",
        data = buildJsonObject {
            put("date", JsonPrimitive(date.toString()))
            put("type", JsonPrimitive("easy_walk"))
            put("status", JsonPrimitive("completed"))
            put("source", JsonPrimitive("manual"))
            put("calendarVisible", JsonPrimitive(true))
            put("countsTowardTraining", JsonPrimitive(true))
        },
        contractVersion = "2.0",
    )

    private object PerformanceClock : TimeSource {
        override fun epochMillis(): Long = 4_071_686_400_000L
        override fun isoInstant(): String = "2099-01-01T00:00:00Z"
    }

    private object AcceptingWorkerApi : WorkerRecordApi {
        override suspend fun query(request: JsonObject): JsonObject = buildJsonObject {
            put("serverTime", JsonPrimitive("2099-01-01T00:05:00Z"))
            put("records", JsonArray(emptyList()))
            put("nextCursor", JsonNull)
        }

        override suspend fun upsert(request: JsonObject): JsonObject = buildJsonObject {
            put("accepted", buildJsonArray {
                request.getValue("records").jsonArray.forEach { value ->
                    val envelope = value.jsonObject
                    add(buildJsonObject {
                        put("entity", envelope.getValue("entity"))
                        put("id", envelope.getValue("id"))
                        put("revision", JsonPrimitive(2))
                        put("updatedAt", JsonPrimitive("2099-01-01T00:05:00Z"))
                    })
                }
            })
            put("conflicts", JsonArray(emptyList()))
        }
    }

    companion object {
        private const val TAG = "P8Performance"
        private const val CALENDAR_BUDGET_MILLIS = 2_000L
        private const val SYNC_BUDGET_MILLIS = 3_000L
    }
}
