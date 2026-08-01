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

    @Test
    fun correctionUsesLatestSameDayRecordsAndKeepsTheirIds() {
        runBlocking {
            val date = LocalDate.of(2100, 1, 4)
            repository.saveMorning(
                checkin = StatusCheckin(
                    id = "legacy-morning-id",
                    date = date.toString(),
                    kind = CheckinKind.MORNING,
                    observedAt = "2100-01-04T00:00:00Z",
                    energy = 2,
                ),
                metric = BodyMetric(
                    id = "legacy-metric-id",
                    date = date.toString(),
                    observedAt = "2100-01-04T00:00:00Z",
                    weightKg = 101.0,
                ),
            )
            repository.saveMorning(
                checkin = StatusCheckin(
                    id = "current-morning-id",
                    date = date.toString(),
                    kind = CheckinKind.MORNING,
                    observedAt = "2100-01-04T01:00:00Z",
                    energy = 4,
                ),
                metric = BodyMetric(
                    id = "current-metric-id",
                    date = date.toString(),
                    observedAt = "2100-01-04T01:00:00Z",
                    weightKg = 100.0,
                ),
            )

            val beforeCorrection = repository.observe(date).first()
            assertEquals("current-morning-id", beforeCorrection.morning?.id)
            assertEquals("current-metric-id", beforeCorrection.metric?.id)
            assertEquals(4, beforeCorrection.effectiveStatus.energy)
            assertEquals(100.0, beforeCorrection.metric?.weightKg ?: 0.0, 0.0)

            repository.saveMorning(
                checkin = beforeCorrection.morning!!.copy(
                    observedAt = "2100-01-04T02:00:00Z",
                    energy = 5,
                ),
                metric = beforeCorrection.metric!!.copy(
                    observedAt = "2100-01-04T02:00:00Z",
                    weightKg = 99.8,
                ),
            )

            val afterCorrection = repository.observe(date).first()
            assertEquals("current-morning-id", afterCorrection.morning?.id)
            assertEquals("current-metric-id", afterCorrection.metric?.id)
            assertEquals(5, afterCorrection.effectiveStatus.energy)
            assertEquals(99.8, afterCorrection.metric?.weightKg ?: 0.0, 0.0)
            assertEquals(4, database.records().count())
        }
    }
}
