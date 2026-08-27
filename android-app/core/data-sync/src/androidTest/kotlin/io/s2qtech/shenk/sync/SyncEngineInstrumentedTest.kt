package io.s2qtech.shenk.sync

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
class SyncEngineInstrumentedTest {
    private lateinit var database: ShenkDatabase
    private lateinit var repository: LocalFirstRepository
    private val clock = FixedClock()

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ShenkDatabase::class.java).build()
        repository = LocalFirstRepository(
            database = database,
            localDeviceId = "synthetic-device",
            timeSource = clock,
            nextId = { "synthetic-operation" },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun acceptedCloudWriteClearsOutboxAndAdvancesRevision() {
        runBlocking {
            repository.persistAndEnqueue(trainingLog("synthetic-sync"), SharedEntityOwner.RECORD)
            val api = FakeWorkerApi(
                upsertResponse = buildJsonObject {
                    put("accepted", JsonArray(listOf(buildJsonObject {
                        put("entity", JsonPrimitive("training_logs"))
                        put("id", JsonPrimitive("synthetic-sync"))
                        put("revision", JsonPrimitive(4))
                        put("updatedAt", JsonPrimitive("2100-01-01T00:05:00Z"))
                    })))
                    put("conflicts", JsonArray(emptyList()))
                },
            )

            val result = engine(api).synchronize()

            assertEquals(1, result.pushed)
            assertEquals(0, database.outbox().count())
            assertEquals(4, repository.get("training_logs", "synthetic-sync")?.revision)
            assertEquals(SyncFoundationState.SYNCED.name, database.records().get("training_logs", "synthetic-sync")?.syncState)
            assertEquals("2.0", api.lastUpsert?.get("contractVersion")?.toString()?.trim('"'))
        }
    }

    @Test
    fun unacknowledgedCloudWriteStaysRetryable() {
        runBlocking {
            repository.persistAndEnqueue(trainingLog("synthetic-unacknowledged"), SharedEntityOwner.RECORD)
            val api = FakeWorkerApi()

            engine(api).synchronize()

            val pending = database.outbox().get("training_logs:synthetic-unacknowledged")
            assertEquals(1, pending?.attempts)
            assertEquals("unacknowledged_response", pending?.lastError)
            assertTrue((pending?.nextAttemptAt ?: 0L) > clock.epochMillis())
        }
    }

    @Test
    fun restoredBusinessRecordReplaysThroughOutboxAndBecomesSynced() {
        runBlocking {
            val restored = repository.restoreBackup(listOf(trainingLog("synthetic-restored-sync")))
            val api = FakeWorkerApi(
                upsertResponse = buildJsonObject {
                    put("accepted", JsonArray(listOf(buildJsonObject {
                        put("entity", JsonPrimitive("training_logs"))
                        put("id", JsonPrimitive("synthetic-restored-sync"))
                        put("revision", JsonPrimitive(7))
                        put("updatedAt", JsonPrimitive("2100-01-01T00:05:00Z"))
                    })))
                    put("conflicts", JsonArray(emptyList()))
                },
            )

            val result = engine(api).synchronize()

            assertEquals(1, restored.restored)
            assertEquals(1, result.pushed)
            assertEquals(0, database.outbox().count())
            assertEquals(7, repository.get("training_logs", "synthetic-restored-sync")?.revision)
            assertEquals(
                SyncFoundationState.SYNCED.name,
                database.records().get("training_logs", "synthetic-restored-sync")?.syncState,
            )
        }
    }

    @Test
    fun timerFactUsesTimerRoleApiWhileOtherRecordsUseShenkRoleApi() {
        runBlocking {
            repository.persistAndEnqueue(trainingLog("synthetic-role-log"), SharedEntityOwner.RECORD)
            repository.persistAndEnqueue(
                SharedRecord.create(
                    entity = "timer_sessions",
                    id = "synthetic-role-timer",
                    data = buildJsonObject { put("completion", JsonPrimitive("completed")) },
                ),
                SharedEntityOwner.TIMER,
            )
            val shenkApi = AcceptingWorkerApi()
            val timerApi = AcceptingWorkerApi()

            val result = SyncEngine(
                database = database,
                repository = repository,
                api = shenkApi,
                timerApi = timerApi,
                deviceId = "synthetic-device",
                timeSource = clock,
            ).synchronize()

            assertEquals(2, result.pushed)
            assertEquals(listOf("training_logs"), shenkApi.upsertEntities)
            assertEquals(listOf("timer_sessions"), timerApi.upsertEntities)
            assertEquals(0, database.outbox().count())
        }
    }

    private fun engine(api: WorkerRecordApi) = SyncEngine(
        database = database,
        repository = repository,
        api = api,
        deviceId = "synthetic-device",
        timeSource = clock,
    )

    private fun trainingLog(id: String) = SharedRecord.create(
        entity = "training_logs",
        id = id,
        data = buildJsonObject { put("status", JsonPrimitive("completed")) },
    )

    private class FixedClock : TimeSource {
        override fun epochMillis(): Long = 4_102_444_800_000L
        override fun isoInstant(): String = "2100-01-01T00:00:00Z"
    }

    private class FakeWorkerApi(
        private val upsertResponse: JsonObject = buildJsonObject {
            put("accepted", JsonArray(emptyList()))
            put("conflicts", JsonArray(emptyList()))
        },
    ) : WorkerRecordApi {
        var lastUpsert: JsonObject? = null

        override suspend fun query(request: JsonObject): JsonObject = buildJsonObject {
            put("serverTime", JsonPrimitive("2100-01-01T00:10:00Z"))
            put("records", JsonArray(emptyList()))
        }

        override suspend fun upsert(request: JsonObject): JsonObject {
            lastUpsert = request
            return upsertResponse
        }
    }

    private class AcceptingWorkerApi : WorkerRecordApi {
        val upsertEntities = mutableListOf<String>()

        override suspend fun query(request: JsonObject): JsonObject = buildJsonObject {
            put("serverTime", JsonPrimitive("2100-01-01T00:10:00Z"))
            put("records", JsonArray(emptyList()))
        }

        override suspend fun upsert(request: JsonObject): JsonObject {
            val records = request.getValue("records").jsonArray.map { it.jsonObject }
            upsertEntities += records.map { it.getValue("entity").jsonPrimitive.content }
            return buildJsonObject {
                put("accepted", JsonArray(records.map { envelope ->
                    buildJsonObject {
                        put("entity", envelope.getValue("entity"))
                        put("id", envelope.getValue("id"))
                        put("revision", JsonPrimitive(2))
                        put("updatedAt", JsonPrimitive("2100-01-01T00:05:00Z"))
                    }
                }))
                put("conflicts", JsonArray(emptyList()))
            }
        }
    }
}
