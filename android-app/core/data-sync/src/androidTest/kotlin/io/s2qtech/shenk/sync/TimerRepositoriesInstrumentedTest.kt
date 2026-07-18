package io.s2qtech.shenk.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import io.s2qtech.shenk.model.TimerSessionFact
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimerRepositoriesInstrumentedTest {
    private lateinit var database: ShenkDatabase
    private lateinit var local: LocalFirstRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ShenkDatabase::class.java,
        ).allowMainThreadQueries().build()
        local = LocalFirstRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun libraryRequiresExplicitMetadataAndSessionWriteIsIdempotent() {
        runBlocking {
            local.persistAndEnqueue(routine("valid", includeScene = true), SharedEntityOwner.PLANNING)
            local.persistAndEnqueue(routine("invalid", includeScene = false), SharedEntityOwner.PLANNING)

            val library = RoutineLibraryRepository(local).observeLibrary().first()
            assertEquals(1, library.routines.size)
            assertEquals(1, library.rejectedCount)

            val sessions = NativeTimerSessionRepository(local)
            val fact = TimerSessionFact(
                id = "session-1",
                date = "2026-07-18",
                routineId = "valid",
                routineVersion = "1",
                routineDigest = "sha256:test",
                routineSnapshot = buildJsonObject { put("title", JsonPrimitive("恢复拉伸")) },
                trainingType = "recovery",
                startedAt = "2026-07-18T10:00:00Z",
                endedAt = "2026-07-18T10:10:00Z",
                completion = "completed",
                actualSeconds = 600,
                activeSeconds = 600,
                elapsedSeconds = 600,
                pausedSeconds = 0,
                calendarVisible = true,
                countsTowardTraining = true,
                idempotencyKey = "session-1",
            )
            assertTrue(sessions.persistIfAbsent(fact))
            assertFalse(sessions.persistIfAbsent(fact))
            assertEquals(1, database.outbox().due(Long.MAX_VALUE, 100).count { it.entity == "timer_sessions" })
            assertEquals(1, sessions.observePendingCompletion().first().size)
        }
    }

    private fun routine(id: String, includeScene: Boolean): SharedRecord = SharedRecord.create(
        "routine_templates",
        id,
        buildJsonObject {
            put("id", JsonPrimitive(id))
            put("title", JsonPrimitive("恢复拉伸"))
            put("trainingType", JsonPrimitive("recovery"))
            if (includeScene) put("scene", JsonPrimitive("recovery"))
            put("role", JsonPrimitive("recovery"))
            put("lifecycle", JsonPrimitive("published"))
            put("timerVisible", JsonPrimitive(true))
            put("calendarVisible", JsonPrimitive(true))
            put("countsTowardTraining", JsonPrimitive(true))
            put("steps", buildJsonArray {
                add(buildJsonObject {
                    put("stepId", JsonPrimitive("march"))
                    put("name", JsonPrimitive("原地慢走"))
                    put("durationSeconds", JsonPrimitive(60))
                })
            })
        },
        contractVersion = "2.0",
    )
}
