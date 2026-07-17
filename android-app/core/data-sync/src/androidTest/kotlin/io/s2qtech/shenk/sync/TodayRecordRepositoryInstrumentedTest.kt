package io.s2qtech.shenk.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.s2qtech.shenk.model.BodyMetric
import io.s2qtech.shenk.model.CheckinKind
import io.s2qtech.shenk.model.StatusCheckin
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayRecordRepositoryInstrumentedTest {
    private lateinit var database: ShenkDatabase
    private lateinit var repository: TodayRecordRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ShenkDatabase::class.java).build()
        var sequence = 0
        repository = TodayRecordRepository(
            LocalFirstRepository(
                database = database,
                localDeviceId = "synthetic-device",
                nextId = { "synthetic-operation-${sequence++}" },
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun morningAndMetricsAreSavedLocallyAndQueuedTogether() {
        runBlocking {
            val date = LocalDate.of(2100, 1, 2)
            repository.saveMorning(
                checkin = StatusCheckin(
                    id = "synthetic-morning",
                    date = date.toString(),
                    kind = CheckinKind.MORNING,
                    observedAt = "2100-01-02T00:00:00Z",
                    sleepDurationMinutes = 420,
                    energy = 4,
                    pain = emptyList(),
                ),
                metric = BodyMetric(
                    id = "synthetic-metric",
                    date = date.toString(),
                    observedAt = "2100-01-02T00:00:00Z",
                    weightKg = 100.0,
                ),
            )

            assertEquals(2, database.records().count())
            assertEquals(2, database.outbox().count())
            val today = repository.observe(date).first()
            assertEquals(420, today.morning?.sleepDurationMinutes)
            assertEquals(100.0, today.metric?.weightKg ?: 0.0, 0.0)
            assertEquals(4, today.effectiveStatus.energy)
        }
    }

    @Test
    fun preWorkoutOnlyOverridesFieldsThatWereActuallyRecorded() {
        runBlocking {
            val date = LocalDate.of(2100, 1, 3)
            repository.saveMorning(
                StatusCheckin(
                    id = "synthetic-morning",
                    date = date.toString(),
                    kind = CheckinKind.MORNING,
                    observedAt = "2100-01-03T00:00:00Z",
                    sleepQuality = 4,
                    energy = 4,
                    fatigue = 1,
                ),
                null,
            )
            repository.savePreWorkout(
                StatusCheckin(
                    id = "synthetic-pre-workout",
                    date = date.toString(),
                    kind = CheckinKind.PRE_WORKOUT,
                    observedAt = "2100-01-03T10:00:00Z",
                    baseCheckinId = "synthetic-morning",
                    fatigue = 3,
                ),
            )

            val status = repository.observe(date).first().effectiveStatus
            assertEquals(4, status.energy)
            assertEquals(3, status.fatigue)
            assertEquals(4, status.sleepQuality)
            assertNull(status.pain)
        }
    }
}
