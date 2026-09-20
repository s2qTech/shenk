package io.s2qtech.shenk.sync

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.buildJsonArray
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
    fun phaseOneProviderIsCanonicalDeepSeekV41Flash() {
        runBlocking {
            val settings = reviews.providerSettings()

            assertEquals("deepseek", settings.provider)
            assertEquals("https://api.deepseek.com", settings.baseUrl)
            assertEquals("deepseek-flash", settings.model)
        }
    }

    @Test
    fun missingMorningStatusRequiresExplicitPartialGeneration() {
        runBlocking {
            secrets.put(SecretName.AI_PROVIDER_KEY, "synthetic-key")
            saveDayRecord()

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
            saveDayRecord()
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
            saveDayRecord()
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
    fun interruptedRunningJobResumesPollingTheSameServerJob() {
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
            assertEquals("AWAITING_SERVER", recovered?.state)
            assertEquals(null, recovered?.lastError)
        }
    }

    @Test
    fun explicitRetryReactivatesDelayedJobImmediately() {
        runBlocking {
            secrets.put(SecretName.AI_PROVIDER_KEY, "synthetic-key")
            saveDayRecord()
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
            saveDayRecord()
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

                        override suspend fun dailyReviewJob(jobId: String): JsonObject = JsonObject(emptyMap())
                    }
                },
            )

            val succeeded = reviews.testAndConfigureProvider(AiProviderSettings(), "bad-replacement")

            assertFalse(succeeded)
            assertEquals("working-key", secrets.get(SecretName.AI_PROVIDER_KEY))
        }
    }

    @Test
    fun emptyOrStatusOnlyDayCannotGenerateEvenExplicitly() {
        runBlocking {
            secrets.put(SecretName.AI_PROVIDER_KEY, "synthetic-key")
            assertTrue(reviews.enqueue(TEST_DATE, allowIncomplete = true).dayRecordMissing)
            records.persistAndEnqueue(morningCheckin("morning-only", fatigue = 2), SharedEntityOwner.RECORD)
            assertTrue(reviews.enqueue(TEST_DATE, allowIncomplete = true, regenerate = true).dayRecordMissing)
            saveDayRecord("planned")
            assertTrue(reviews.enqueue(TEST_DATE, allowIncomplete = true).dayRecordMissing)
            assertEquals(null, database.aiReviewJobs().nextDue(Long.MAX_VALUE))
        }
    }

    @Test
    fun confirmedTrainingRestAndSkipPermitPartialReviewButDeletedRecordDoesNot() {
        runBlocking {
            secrets.put(SecretName.AI_PROVIDER_KEY, "synthetic-key")
            listOf("completed", "rested", "skipped").forEach { status ->
                saveDayRecord(status)
                assertTrue(reviews.prepare(TEST_DATE).hasConfirmedDayRecord)
                assertTrue(reviews.enqueue(TEST_DATE, allowIncomplete = true).queued)
            }
            CalendarRecordRepository(records).deleteTrainingLog("synthetic-day-record")
            assertTrue(reviews.enqueue(TEST_DATE, allowIncomplete = true).dayRecordMissing)
            assertFalse(reviews.prepare(TEST_DATE.minusDays(1)).hasConfirmedDayRecord)
        }
    }

    @Test
    fun regenerationUsesNewExecutionWithSameDigestAndPreservesOldReviewUntilSuccess() {
        runBlocking {
            secrets.put(SecretName.AI_PROVIDER_KEY, "synthetic-key")
            secrets.put(SecretName.SHENK_TOKEN, "synthetic-token")
            preferences.setApiBase("https://example.invalid/api")
            var fail = false
            reviews = DailyReviewRepository(database, records, preferences, secrets, apiFactory = { _, _, _ ->
                object : WorkerAiApi {
                    override suspend fun connectionTest(request: JsonObject) = JsonObject(emptyMap())
                    override suspend fun dailyReviewJob(jobId: String) = JsonObject(emptyMap())
                    override suspend fun dailyReview(request: JsonObject) = buildJsonObject {
                        put("state", JsonPrimitive(if (fail) "FAILED" else "SUCCEEDED"))
                        put("error", JsonPrimitive("synthetic_failure"))
                        put("review", buildJsonObject {
                            put("conclusion", JsonPrimitive("已确认休息"))
                            put("actions", buildJsonArray { add(JsonPrimitive("按状态安排明天")) })
                        })
                    }
                }
            })
            saveDayRecord()
            val digest = reviews.prepare(TEST_DATE).inputDigest
            reviews.enqueue(TEST_DATE, allowIncomplete = true)
            assertEquals(DailyReviewProcessResult.COMPLETED, reviews.processNext())
            val originalJob = database.aiReviewJobs().find(TEST_DATE.toString(), digest)!!
            assertFalse(reviews.enqueue(TEST_DATE, allowIncomplete = true).queued)
            listOf(async { reviews.enqueue(TEST_DATE, true, regenerate = true) },
                async { reviews.enqueue(TEST_DATE, true, regenerate = true) }).awaitAll()
            val replacement = database.aiReviewJobs().find(TEST_DATE.toString(), digest)!!
            assertNotEquals(originalJob.jobId, replacement.jobId)
            assertEquals(originalJob.inputDigest, replacement.inputDigest)
            assertEquals(1, reviews.observe(TEST_DATE).first().review?.version)
            reviews.enqueue(TEST_DATE, true, regenerate = true)
            assertEquals(replacement.jobId, database.aiReviewJobs().find(TEST_DATE.toString(), digest)?.jobId)
            fail = true
            assertEquals(DailyReviewProcessResult.FAILED, reviews.processNext())
            assertEquals(1, reviews.observe(TEST_DATE).first().review?.version)
            fail = false
            reviews.enqueue(TEST_DATE, true, regenerate = true)
            assertEquals(DailyReviewProcessResult.COMPLETED, reviews.processNext())
            assertEquals(2, reviews.observe(TEST_DATE).first().review?.version)
            CalendarRecordRepository(records).deleteTrainingLog("synthetic-day-record")
            assertEquals(null, reviews.observe(TEST_DATE).first().review)
        }
    }

    @Test
    fun awaitingServerJobKeepsItsIdentityAndPollingState() {
        runBlocking {
            secrets.put(SecretName.AI_PROVIDER_KEY, "synthetic-key")
            saveDayRecord()
            reviews.enqueue(TEST_DATE, true)
            val digest = reviews.prepare(TEST_DATE).inputDigest
            val job = database.aiReviewJobs().find(TEST_DATE.toString(), digest)!!
            database.aiReviewJobs().updateState(job.jobId, "AWAITING_SERVER", 0, NOW, null, NOW)
            reviews.enqueue(TEST_DATE, true, regenerate = true)
            val kept = database.aiReviewJobs().find(TEST_DATE.toString(), digest)!!
            assertEquals(job.jobId, kept.jobId)
            assertEquals("AWAITING_SERVER", kept.state)
        }
    }

    @Test
    fun snapshotIncludesOnlyEffectivePublishedGoalAndStrategy() {
        runBlocking {
            saveDayRecord()
            val before = reviews.prepare(TEST_DATE).inputDigest
            suspend fun policy(entity: String, id: String, from: String, lifecycle: String = "published") {
                records.persistAndEnqueue(SharedRecord.create(entity, id, buildJsonObject {
                    put("id", JsonPrimitive(id))
                    put("effectiveFrom", JsonPrimitive(from))
                    put("lifecycle", JsonPrimitive(lifecycle))
                }), SharedEntityOwner.PLANNING)
            }
            policy("goal_sets", "old-goal", "2099-01-01")
            policy("goal_sets", "current-goal", "2100-01-01")
            policy("goal_sets", "future-goal", "2100-01-02")
            policy("coach_strategies", "draft-strategy", "2100-01-01", "draft")
            policy("coach_strategies", "current-strategy", "2099-12-01")
            val prepared = reviews.prepare(TEST_DATE)
            val payload = prepared.snapshot.toString()
            assertTrue(payload.contains("current-goal"))
            assertTrue(payload.contains("current-strategy"))
            assertFalse(payload.contains("old-goal"))
            assertFalse(payload.contains("future-goal"))
            assertFalse(payload.contains("draft-strategy"))
            assertNotEquals(before, prepared.inputDigest)
        }
    }

    @Test
    fun inFlightOldInputCannotOverwriteTheCorrectedReview() {
        runBlocking {
            configureSyntheticApi { request ->
                if (request.toString().contains("rested")) {
                    saveDayRecord("skipped")
                    reviews.enqueue(TEST_DATE, allowIncomplete = true)
                    assertEquals(DailyReviewProcessResult.COMPLETED, reviews.processNext())
                    successfulReview("旧的休息输入")
                } else successfulReview("新的跳过输入")
            }
            saveDayRecord()
            reviews.enqueue(TEST_DATE, allowIncomplete = true)
            assertEquals(DailyReviewProcessResult.NONE, reviews.processNext())
            assertEquals("新的跳过输入", reviews.observe(TEST_DATE).first().review?.conclusion)
            assertEquals(1, records.allRecords().count { it.entity == "daily_reviews" })
        }
    }

    @Test
    fun deletingDayDuringGenerationDiscardsItsResult() {
        runBlocking {
            configureSyntheticApi {
                CalendarRecordRepository(records).deleteTrainingLog("synthetic-day-record")
                successfulReview("过期输入")
            }
            saveDayRecord()
            reviews.enqueue(TEST_DATE, allowIncomplete = true)
            assertEquals(DailyReviewProcessResult.NONE, reviews.processNext())
            assertEquals(0, records.allRecords().count { it.entity == "daily_reviews" })
        }
    }

    @Test
    fun statusDeadlineFailsVisiblyAndRetryKeepsTheSameJobId() {
        runBlocking {
            var now = NOW
            var calls = 0
            configureSyntheticApi(now = { now }) { calls++; buildJsonObject { put("state", JsonPrimitive("RUNNING")) } }
            saveDayRecord()
            val digest = reviews.prepare(TEST_DATE).inputDigest
            reviews.enqueue(TEST_DATE, allowIncomplete = true)
            assertEquals(DailyReviewProcessResult.WAITING, reviews.processNext())
            val original = database.aiReviewJobs().find(TEST_DATE.toString(), digest)!!
            now += 300_001L
            assertEquals(DailyReviewProcessResult.FAILED, reviews.processNext())
            assertEquals("ai_status_timeout", reviews.observe(TEST_DATE).first().jobError)
            reviews.enqueue(TEST_DATE, allowIncomplete = true)
            assertEquals(original.jobId, database.aiReviewJobs().find(TEST_DATE.toString(), digest)?.jobId)
            assertEquals(DailyReviewProcessResult.WAITING, reviews.processNext())
            assertEquals(3, calls)
        }
    }

    @Test
    fun reconnectAfterDeadlineStillRetrievesCompletedServerResult() {
        runBlocking {
            var now = NOW
            configureSyntheticApi(now = { now }) {
                if (now == NOW) buildJsonObject { put("state", JsonPrimitive("RUNNING")) }
                else successfulReview("离线期间已完成")
            }
            saveDayRecord()
            reviews.enqueue(TEST_DATE, allowIncomplete = true)
            assertEquals(DailyReviewProcessResult.WAITING, reviews.processNext())
            now += 600_000L
            assertEquals(DailyReviewProcessResult.COMPLETED, reviews.processNext())
            assertEquals("离线期间已完成", reviews.observe(TEST_DATE).first().review?.conclusion)
        }
    }

    private suspend fun configureSyntheticApi(
        now: () -> Long = { NOW },
        response: suspend (JsonObject) -> JsonObject,
    ) {
        secrets.put(SecretName.AI_PROVIDER_KEY, "synthetic-key")
        secrets.put(SecretName.SHENK_TOKEN, "synthetic-token")
        preferences.setApiBase("https://example.invalid/api")
        reviews = DailyReviewRepository(database, records, preferences, secrets, nowMillis = now, apiFactory = { _, _, _ ->
            object : WorkerAiApi {
                override suspend fun connectionTest(request: JsonObject) = JsonObject(emptyMap())
                override suspend fun dailyReview(request: JsonObject) = response(request)
                override suspend fun dailyReviewJob(jobId: String) = response(JsonObject(emptyMap()))
            }
        })
    }

    private fun successfulReview(conclusion: String) = buildJsonObject {
        put("state", JsonPrimitive("SUCCEEDED"))
        put("review", buildJsonObject {
            put("conclusion", JsonPrimitive(conclusion))
            put("actions", buildJsonArray { add(JsonPrimitive("按实际状态调整")) })
        })
    }

    private suspend fun saveDayRecord(status: String = "rested") {
        CalendarRecordRepository(records).saveTrainingLog(io.s2qtech.shenk.model.TrainingLog(
            id = "synthetic-day-record", date = TEST_DATE.toString(), type = "rest",
            status = status, source = "manual",
        ))
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
