package io.s2qtech.shenk.sync

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyReviewRepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: ShenkDatabase
    private lateinit var records: LocalFirstRepository
    private lateinit var preferences: DevicePreferencesStore
    private lateinit var secrets: MemorySecretStore
    private lateinit var reviews: DailyReviewRepository

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ShenkDatabase::class.java).build()
        records = LocalFirstRepository(database, localDeviceId = "synthetic-device")
        preferences = DevicePreferencesStore(context)
        secrets = MemorySecretStore()
        reviews = DailyReviewRepository(
            database = database,
            records = records,
            preferences = preferences,
            secrets = secrets,
            nowMillis = { 4_102_444_800_000L },
            nowInstant = { "2100-01-01T00:00:00Z" },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun phaseOneProviderIsCanonicalDeepSeekV4Flash() {
        runBlocking {
            val settings = reviews.providerSettings()

            assertEquals("deepseek", settings.provider)
            assertEquals("https://api.deepseek.com", settings.baseUrl)
            assertEquals("deepseek-v4-flash", settings.model)
        }
    }

    @Test
    fun missingMorningStatusRequiresExplicitPartialGeneration() {
        runBlocking {
            secrets.put(SecretName.AI_PROVIDER_KEY, "synthetic-key")

            val result = reviews.enqueue(TEST_DATE)

            assertFalse(result.queued)
            assertFalse(result.configurationMissing)
            assertTrue(result.missingCriticalFields.isNotEmpty())
            assertEquals(null, database.aiReviewJobs().nextDue(Long.MAX_VALUE))
        }
    }

    @Test
    fun missingProviderKeyDoesNotCreateAnUnprocessableJob() {
        runBlocking {
            records.persistAndEnqueue(morningCheckin("morning-1", fatigue = 2), SharedEntityOwner.RECORD)

            val result = reviews.enqueue(TEST_DATE)

            assertFalse(result.queued)
            assertTrue(result.configurationMissing)
            assertEquals(null, database.aiReviewJobs().nextDue(Long.MAX_VALUE))
        }
    }

    @Test
    fun configuredReviewIsQueuedWithDeterministicDigest() {
        runBlocking {
            secrets.put(SecretName.AI_PROVIDER_KEY, "synthetic-key")
            records.persistAndEnqueue(morningCheckin("morning-1", fatigue = 2), SharedEntityOwner.RECORD)
            val prepared = reviews.prepare(TEST_DATE)

            val result = reviews.enqueue(TEST_DATE)

            assertTrue(result.queued)
            assertTrue(result.missingCriticalFields.isEmpty())
            assertEquals(prepared.inputDigest, reviews.prepare(TEST_DATE).inputDigest)
            assertEquals("PENDING", database.aiReviewJobs().find(TEST_DATE.toString(), prepared.inputDigest)?.state)
        }
    }

    @Test
    fun interruptedRunningJobIsRecoveredForImmediateRetry() {
        runBlocking {
            database.aiReviewJobs().put(
                AiReviewJobEntity(
                    jobId = "interrupted-job",
                    date = TEST_DATE.toString(),
                    inputDigest = "digest-interrupted",
                    snapshotJson = "{}",
                    allowIncomplete = false,
                    state = "RUNNING",
                    attempts = 1,
                    nextAttemptAt = NOW,
                    lastError = null,
                    createdAt = NOW - 300_000,
                    updatedAt = NOW - 300_000,
                ),
            )

            assertEquals(1, reviews.recoverInterruptedJobs())

            val recovered = database.aiReviewJobs().nextDue(NOW)
            assertEquals("interrupted-job", recovered?.jobId)
            assertEquals("RETRY", recovered?.state)
            assertEquals("generation_interrupted", recovered?.lastError)
        }
    }

    @Test
    fun explicitRetryReactivatesDelayedJobImmediately() {
        runBlocking {
            secrets.put(SecretName.AI_PROVIDER_KEY, "synthetic-key")
            records.persistAndEnqueue(morningCheckin("morning-retry", fatigue = 2), SharedEntityOwner.RECORD)
            val prepared = reviews.prepare(TEST_DATE)
            database.aiReviewJobs().put(
                AiReviewJobEntity(
                    jobId = "delayed-retry-job",
                    date = TEST_DATE.toString(),
                    inputDigest = prepared.inputDigest,
                    snapshotJson = prepared.snapshot.toString(),
                    allowIncomplete = false,
                    state = "RETRY",
                    attempts = 2,
                    nextAttemptAt = NOW + 3_600_000,
                    lastError = "network_unavailable",
                    createdAt = NOW - 60_000,
                    updatedAt = NOW - 60_000,
                ),
            )

            val result = reviews.enqueue(TEST_DATE)

            assertTrue(result.queued)
            val reactivated = database.aiReviewJobs().nextDue(NOW)
            assertEquals("delayed-retry-job", reactivated?.jobId)
            assertEquals("PENDING", reactivated?.state)
            assertEquals(null, reactivated?.lastError)
        }
    }

    @Test
    fun correctedStatusSupersedesOldQueuedInput() {
        runBlocking {
            secrets.put(SecretName.AI_PROVIDER_KEY, "synthetic-key")
            records.persistAndEnqueue(morningCheckin("morning-1", fatigue = 2), SharedEntityOwner.RECORD)
            val first = reviews.prepare(TEST_DATE)
            reviews.enqueue(TEST_DATE)

            records.persistAndEnqueue(
                morningCheckin("morning-2", fatigue = 4, observedAt = "2100-01-01T01:00:00Z"),
                SharedEntityOwner.RECORD,
            )
            val second = reviews.prepare(TEST_DATE)
            reviews.enqueue(TEST_DATE)

            assertNotEquals(first.inputDigest, second.inputDigest)
            assertEquals("SUPERSEDED", database.aiReviewJobs().find(TEST_DATE.toString(), first.inputDigest)?.state)
            assertEquals("PENDING", database.aiReviewJobs().find(TEST_DATE.toString(), second.inputDigest)?.state)
        }
    }

    @Test
    fun failedReplacementKeyKeepsCurrentWorkingKey() {
        runBlocking {
            secrets.put(SecretName.SHENK_TOKEN, "synthetic-cloud-token")
            secrets.put(SecretName.AI_PROVIDER_KEY, "working-key")
            preferences.setApiBase("https://example.invalid/api")
            reviews = DailyReviewRepository(
                database = database,
                records = records,
                preferences = preferences,
                secrets = secrets,
                apiFactory = { _, _, _ ->
                    object : WorkerAiApi {
                        override suspend fun connectionTest(request: JsonObject): JsonObject =
                            buildJsonObject { put("ok", JsonPrimitive(false)) }

                        override suspend fun dailyReview(request: JsonObject): JsonObject = JsonObject(emptyMap())
                    }
                },
            )

            val succeeded = reviews.testAndConfigureProvider(AiProviderSettings(), "bad-replacement")

            assertFalse(succeeded)
            assertEquals("working-key", secrets.get(SecretName.AI_PROVIDER_KEY))
        }
    }

    private fun morningCheckin(
        id: String,
        fatigue: Int,
        observedAt: String = "2100-01-01T00:00:00Z",
    ) = SharedRecord.create(
        entity = "status_checkins",
        id = id,
        data = buildJsonObject {
            put("id", JsonPrimitive(id))
            put("date", JsonPrimitive(TEST_DATE.toString()))
            put("kind", JsonPrimitive("morning"))
            put("observedAt", JsonPrimitive(observedAt))
            put("sleepDurationMinutes", JsonPrimitive(420))
            put("sleepQuality", JsonPrimitive(4))
            put("energy", JsonPrimitive(3))
            put("fatigue", JsonPrimitive(fatigue))
        },
        contractVersion = "2.0",
    )

    private class MemorySecretStore : SecretStore {
        private val values = mutableMapOf<SecretName, String>()

        override suspend fun put(name: SecretName, value: String) {
            values[name] = value
        }

        override suspend fun get(name: SecretName): String? = values[name]

        override suspend fun remove(name: SecretName) {
            values.remove(name)
        }
    }

    private companion object {
        const val NOW = 4_102_444_800_000L
        val TEST_DATE: LocalDate = LocalDate.parse("2100-01-01")
    }
}
