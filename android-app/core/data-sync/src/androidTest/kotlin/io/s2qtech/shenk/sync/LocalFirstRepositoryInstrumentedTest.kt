package io.s2qtech.shenk.sync

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalFirstRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: ShenkDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ShenkDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun localWriteAndOutboxAreCommittedTogether() {
        runBlocking {
            val repository = repository(database)
            val state = repository.persistAndEnqueue(trainingLog("synthetic-log", "completed"), SharedEntityOwner.RECORD)

            assertEquals(SyncFoundationState.QUEUED, state)
            assertEquals(1, database.records().count())
            assertEquals(1, database.outbox().count())
            assertEquals(SyncFoundationState.QUEUED.name, database.records().get("training_logs", "synthetic-log")?.syncState)
        }
    }

    @Test
    fun unauthorizedOwnerLeavesNoPartialWrite() {
        runBlocking {
            val repository = repository(database)

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    repository.persistAndEnqueue(
                        SharedRecord.create("timer_sessions", "synthetic-session", buildJsonObject {}),
                        SharedEntityOwner.RECORD,
                    )
                }
            }
            assertEquals(0, database.records().count())
            assertEquals(0, database.outbox().count())
        }
    }

    @Test
    fun dirtyLocalRecordIsNotOverwrittenByRemotePull() {
        runBlocking {
            val repository = repository(database)
            repository.persistAndEnqueue(trainingLog("synthetic-conflict", "local"), SharedEntityOwner.RECORD)
            val remote = trainingLog("synthetic-conflict", "remote").withSyncMetadata(revision = 2, baseRevision = 2)

            repository.applyRemote(remote)

            assertEquals(
                SyncFoundationState.CONFLICT.name,
                database.records().get("training_logs", "synthetic-conflict")?.syncState,
            )
            assertNotNull(database.conflicts().get("training_logs:synthetic-conflict"))
            assertEquals(0, database.outbox().count())
        }
    }

    @Test
    fun staleRemoteRecordDoesNotConflictWithQueuedLocalEdit() {
        runBlocking {
            val repository = repository(database)
            repository.persistAndEnqueue(trainingLog("synthetic-stale", "local"), SharedEntityOwner.RECORD)
            repository.markAccepted(
                key = io.s2qtech.shenk.model.SharedRecordKey("training_logs", "synthetic-stale"),
                idempotencyKey = "synthetic-idempotency",
                revision = 3,
                updatedAt = "2100-01-01T00:01:00Z",
            )
            repository.persistAndEnqueue(trainingLog("synthetic-stale", "new-local"), SharedEntityOwner.RECORD)

            repository.applyRemote(trainingLog("synthetic-stale", "old-remote").withSyncMetadata(revision = 3, baseRevision = 3))

            assertEquals(SyncFoundationState.QUEUED.name, database.records().get("training_logs", "synthetic-stale")?.syncState)
            assertNull(database.conflicts().get("training_logs:synthetic-stale"))
            assertEquals(1, database.outbox().count())
        }
    }

    @Test
    fun firstWriteCompletesRequiredEnvelopeMetadata() {
        runBlocking {
            val repository = repository(database)
            repository.persistAndEnqueue(trainingLog("synthetic-metadata", "completed"), SharedEntityOwner.RECORD)

            val record = repository.get("training_logs", "synthetic-metadata")
            assertEquals("synthetic-device", record?.deviceId)
            assertEquals("2100-01-01T00:00:00Z", record?.createdAt)
            assertEquals("2100-01-01T00:00:00Z", record?.updatedAt)
        }
    }

    @Test
    fun queuedWriteSurvivesDatabaseReopen() {
        runBlocking {
            database.close()
            val name = "package2-process-death.db"
            context.deleteDatabase(name)
            var diskDatabase = Room.databaseBuilder(context, ShenkDatabase::class.java, name).build()
            repository(diskDatabase).persistAndEnqueue(
                trainingLog("synthetic-persisted", "completed"),
                SharedEntityOwner.RECORD,
            )
            diskDatabase.close()

            diskDatabase = Room.databaseBuilder(context, ShenkDatabase::class.java, name).build()
            assertEquals(1, diskDatabase.records().count())
            assertEquals(1, diskDatabase.outbox().count())
            diskDatabase.close()
            context.deleteDatabase(name)
        }
    }

    private fun repository(db: ShenkDatabase) = LocalFirstRepository(
        database = db,
        localDeviceId = "synthetic-device",
        timeSource = object : TimeSource {
            override fun epochMillis(): Long = 4_102_444_800_000L
            override fun isoInstant(): String = "2100-01-01T00:00:00Z"
        },
        nextId = { "synthetic-idempotency" },
    )

    private fun trainingLog(id: String, result: String) = SharedRecord.create(
        entity = "training_logs",
        id = id,
        data = buildJsonObject { put("subjectiveResult", JsonPrimitive(result)) },
    )
}
