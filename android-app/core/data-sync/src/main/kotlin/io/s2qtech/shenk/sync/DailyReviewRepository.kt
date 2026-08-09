package io.s2qtech.shenk.sync

import androidx.room.withTransaction
import io.s2qtech.shenk.model.ContractVersion
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class DailyReview(
    val id: String,
    val date: String,
    val version: Int,
    val status: String,
    val conclusion: String,
    val assessment: String,
    val actions: List<String>,
    val evidence: List<String>,
    val cautions: List<String>,
    val localSuggestion: DailyReviewSuggestion?,
    val inputDigest: String,
    val provider: String,
    val model: String,
    val generatedAt: String,
)

data class DailyReviewSuggestion(
    val date: String,
    val title: String,
    val trainingType: String,
    val estimatedMinutes: Int? = null,
    val reason: String? = null,
)

data class DailyReviewState(
    val review: DailyReview? = null,
    val jobState: String? = null,
    val jobError: String? = null,
)

data class DailyReviewPreparation(
    val inputDigest: String,
    val snapshot: JsonObject,
    val missingCriticalFields: List<String>,
)

data class DailyReviewEnqueueResult(
    val queued: Boolean,
    val missingCriticalFields: List<String>,
    val configurationMissing: Boolean = false,
)

enum class DailyReviewProcessResult { NONE, COMPLETED, RETRY, FAILED }

enum class AiProviderConnectionFailure {
    CLOUD_AUTH,
    KEY_REJECTED,
    BALANCE_OR_QUOTA,
    RATE_LIMITED,
    MODEL_UNAVAILABLE,
    PROVIDER_UNAVAILABLE,
    INVALID_RESPONSE,
    NETWORK,
    UNKNOWN,
}

class AiProviderConnectionException(
    val failure: AiProviderConnectionFailure,
    cause: Throwable? = null,
) : Exception(failure.name.lowercase(), cause)

interface WorkerAiApi {
    @POST("ai/connection-test")
    suspend fun connectionTest(@Body request: JsonObject): JsonObject

    @POST("ai/daily-review")
    suspend fun dailyReview(@Body request: JsonObject): JsonObject
}

object WorkerAiApiFactory {
    fun create(apiBase: String, token: String, json: Json): WorkerAiApi {
        require(apiBase.startsWith("https://")) { "cloud API must use HTTPS" }
        require(token.isNotBlank()) { "cloud token is required" }
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build(),
                )
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("${apiBase.trimEnd('/')}/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WorkerAiApi::class.java)
    }
}

class DailyReviewRepository(
    private val database: ShenkDatabase,
    private val records: LocalFirstRepository,
    private val preferences: DevicePreferencesStore,
    private val secrets: SecretStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val nowInstant: () -> String = { Instant.now().toString() },
    private val apiFactory: (String, String, Json) -> WorkerAiApi = WorkerAiApiFactory::create,
) {
    suspend fun providerSettings(): AiProviderSettings = preferences.aiProviderSettings()

    suspend fun hasProviderKey(): Boolean = !secrets.get(SecretName.AI_PROVIDER_KEY).isNullOrBlank()

    suspend fun configureProvider(settings: AiProviderSettings, apiKey: String?) {
        preferences.setAiProviderSettings(settings)
        apiKey?.trim()?.takeIf { it.isNotEmpty() }?.let { secrets.put(SecretName.AI_PROVIDER_KEY, it) }
    }

    suspend fun testAndConfigureProvider(settings: AiProviderSettings, candidateApiKey: String?): Boolean {
        val endpoint = preferences.syncSettings()
        require(endpoint.apiBase.startsWith("https://")) { "cloud_not_configured" }
        val token = secrets.get(SecretName.SHENK_TOKEN) ?: error("cloud_not_configured")
        val currentKey = secrets.get(SecretName.AI_PROVIDER_KEY)
        val candidate = candidateApiKey?.trim()?.takeIf { it.isNotEmpty() } ?: currentKey
            ?: error("ai_key_missing")
        val api = apiFactory(endpoint.apiBase, token, json)
        val response = try {
            api.connectionTest(providerRequest(settings, candidate))
        } catch (error: HttpException) {
            throw mapConnectionFailure(error)
        } catch (error: IOException) {
            throw AiProviderConnectionException(AiProviderConnectionFailure.NETWORK, error)
        }
        val succeeded = response["ok"]?.jsonPrimitive?.contentOrNull == "true"
        if (succeeded) {
            preferences.setAiProviderSettings(settings)
            if (candidateApiKey?.isNotBlank() == true) {
                secrets.put(SecretName.AI_PROVIDER_KEY, candidate)
            }
        }
        return succeeded
    }

    suspend fun nextRunDelayMillis(): Long? = database.aiReviewJobs().nextScheduledAt()?.let { scheduled ->
        (scheduled - nowMillis()).coerceAtLeast(0L)
    }

    fun observe(date: LocalDate): Flow<DailyReviewState> = combine(
        records.observeActive("daily_reviews"),
        database.aiReviewJobs().observeLatest(date.toString()),
    ) { reviews, job ->
        DailyReviewState(
            review = reviews.mapNotNull(::decodeReview)
                .filter { it.date == date.toString() && it.status == "generated" }
                .maxByOrNull { it.version },
            jobState = job?.state,
            jobError = job?.lastError,
        )
    }

    suspend fun prepare(date: LocalDate): DailyReviewPreparation {
        val all = records.allRecords().filter { it.deletedAt == null }
        val start = date.minusDays(13)
        val relevant = all.filter { record ->
            record.entity in REVIEW_INPUT_ENTITIES && record.data.dateOrNull()?.let { value ->
                runCatching { LocalDate.parse(value) }.getOrNull()?.let { it in start..date }
            } == true
        }.sortedWith(compareBy<SharedRecord>({ it.entity }, { it.data.dateOrNull() }, { it.id }))
        val todayCheckin = relevant.asSequence()
            .filter { it.entity == "status_checkins" && it.data.dateOrNull() == date.toString() }
            .filter { it.data.string("kind") == "morning" }
            .maxByOrNull { it.data.string("observedAt").orEmpty() }
        val missing = buildList {
            if (todayCheckin == null) {
                add("晨起状态")
            } else {
                if (todayCheckin.data.int("sleepDurationMinutes") == null) add("睡眠时长")
                if (todayCheckin.data.int("sleepQuality") == null) add("睡眠感受")
                if (todayCheckin.data.int("energy") == null) add("精力")
                if (todayCheckin.data.int("fatigue") == null) add("疲劳")
            }
        }
        val snapshot = buildJsonObject {
            put("schema", JsonPrimitive("daily_review_snapshot"))
            put("contractVersion", JsonPrimitive(ContractVersion.PLANNED))
            put("date", JsonPrimitive(date.toString()))
            put("missingCriticalFields", buildJsonArray { missing.forEach { add(JsonPrimitive(it)) } })
            put("records", buildJsonArray {
                relevant.forEach { record ->
                    add(buildJsonObject {
                        put("entity", JsonPrimitive(record.entity))
                        put("id", JsonPrimitive(record.id))
                        put("data", canonicalize(record.data))
                    })
                }
            })
        }
        val canonical = json.encodeToString(JsonObject.serializer(), canonicalize(snapshot).jsonObject)
        return DailyReviewPreparation(
            inputDigest = "sha256:${canonical.sha256()}",
            snapshot = snapshot,
            missingCriticalFields = missing,
        )
    }

    suspend fun enqueue(date: LocalDate, allowIncomplete: Boolean = false): DailyReviewEnqueueResult {
        val preparation = prepare(date)
        if (preparation.missingCriticalFields.isNotEmpty() && !allowIncomplete) {
            return DailyReviewEnqueueResult(false, preparation.missingCriticalFields)
        }
        val settings = preferences.aiProviderSettings()
        if (!settings.configured || !hasProviderKey()) {
            return DailyReviewEnqueueResult(
                queued = false,
                missingCriticalFields = preparation.missingCriticalFields,
                configurationMissing = true,
            )
        }
        val existing = database.aiReviewJobs().find(date.toString(), preparation.inputDigest)
        if (existing?.state in setOf("PENDING", "RUNNING", "RETRY", "COMPLETED")) {
            return DailyReviewEnqueueResult(existing?.state != "COMPLETED", preparation.missingCriticalFields)
        }
        val now = nowMillis()
        database.withTransaction {
            database.aiReviewJobs().supersedeOtherInputs(date.toString(), preparation.inputDigest, now)
            database.aiReviewJobs().put(
                AiReviewJobEntity(
                    jobId = "daily-review:${date}:${preparation.inputDigest.removePrefix("sha256:").take(16)}",
                    date = date.toString(),
                    inputDigest = preparation.inputDigest,
                    snapshotJson = json.encodeToString(JsonObject.serializer(), preparation.snapshot),
                    allowIncomplete = allowIncomplete,
                    state = "PENDING",
                    attempts = 0,
                    nextAttemptAt = now,
                    lastError = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        return DailyReviewEnqueueResult(true, preparation.missingCriticalFields)
    }

    suspend fun requeueIfReviewed(date: LocalDate): DailyReviewEnqueueResult? {
        val hasReview = records.allRecords().any { record ->
            record.entity == "daily_reviews" && record.deletedAt == null && record.data.dateOrNull() == date.toString()
        }
        return if (hasReview) enqueue(date, allowIncomplete = true) else null
    }

    suspend fun testConnection(): Boolean {
        val client = configuredClient()
        val response = client.api.connectionTest(providerRequest(client.settings, client.key))
        return response["ok"]?.jsonPrimitive?.contentOrNull == "true"
    }

    suspend fun processNext(): DailyReviewProcessResult {
        val job = database.aiReviewJobs().nextDue(nowMillis()) ?: return DailyReviewProcessResult.NONE
        updateJob(job, "RUNNING", job.attempts, nowMillis(), null)
        return try {
            val client = configuredClient()
            val snapshot = json.parseToJsonElement(job.snapshotJson).jsonObject
            val request = providerRequest(client.settings, client.key).toMutableMap().let { fields ->
                JsonObject(fields + ("snapshot" to snapshot))
            }
            val response = client.api.dailyReview(request)
            val review = response["review"]?.jsonObject
                ?: throw IllegalStateException("provider_response_invalid")
            persistGenerated(job, client.settings, review)
            updateJob(job, "COMPLETED", job.attempts, Long.MAX_VALUE, null)
            DailyReviewProcessResult.COMPLETED
        } catch (error: Throwable) {
            val attempts = job.attempts + 1
            val code = error.safeCode()
            if (attempts >= MAX_REVIEW_ATTEMPTS || code in PERMANENT_REVIEW_ERRORS) {
                updateJob(job, "FAILED", attempts, Long.MAX_VALUE, code)
                DailyReviewProcessResult.FAILED
            } else {
                val retry = nowMillis() + retryDelay(attempts)
                updateJob(job, "RETRY", attempts, retry, code)
                DailyReviewProcessResult.RETRY
            }
        }
    }

    private suspend fun persistGenerated(job: AiReviewJobEntity, settings: AiProviderSettings, result: JsonObject) {
        val conclusion = result.string("conclusion")?.trim().orEmpty()
        require(conclusion.isNotBlank()) { "provider_response_invalid" }
        val assessment = result.string("assessment")?.trim().orEmpty()
        val localSuggestion = result.objectOrNull("localSuggestion")
        val evidence = result.stringList("evidence")
        val actions = result.stringList("actions")
        val cautions = result.stringList("cautions")
        val existing = records.allRecords().filter { it.entity == "daily_reviews" && it.deletedAt == null }
            .mapNotNull { record -> decodeReview(record)?.let { it to record } }
            .filter { it.first.date == job.date }
        val version = (existing.maxOfOrNull { it.first.version } ?: 0) + 1
        val generatedAt = nowInstant()
        val updates = buildList {
            existing.filter { it.first.status == "generated" }.forEach { (_, record) ->
                add(record.withReviewStatus("invalidated") to SharedEntityOwner.AI_REVIEW)
            }
            val id = "daily_review:${job.date}:$version"
            val data = buildJsonObject {
                put("id", JsonPrimitive(id))
                put("date", JsonPrimitive(job.date))
                put("version", JsonPrimitive(version))
                put("status", JsonPrimitive("generated"))
                put("conclusion", JsonPrimitive(conclusion.take(120)))
                put("assessment", JsonPrimitive(assessment.take(600)))
                put("actions", buildJsonArray { actions.take(6).forEach { add(JsonPrimitive(it.take(300))) } })
                put("evidence", buildJsonArray { evidence.take(8).forEach { add(JsonPrimitive(it.take(300))) } })
                put("cautions", buildJsonArray { cautions.take(8).forEach { add(JsonPrimitive(it.take(300))) } })
                put("localSuggestion", localSuggestion ?: JsonNull)
                put("inputDigest", JsonPrimitive(job.inputDigest))
                put("provider", JsonPrimitive(settings.provider))
                put("model", JsonPrimitive(settings.model))
                put("generatedAt", JsonPrimitive(generatedAt))
            }
            add(SharedRecord.create("daily_reviews", id, data, contractVersion = ContractVersion.PLANNED) to SharedEntityOwner.AI_REVIEW)
        }
        records.persistOwnedBatchAndEnqueue(updates)
    }

    private suspend fun configuredClient(): ConfiguredClient {
        val endpoint = preferences.syncSettings()
        require(endpoint.apiBase.startsWith("https://")) { "cloud_not_configured" }
        val token = secrets.get(SecretName.SHENK_TOKEN) ?: error("cloud_not_configured")
        val key = secrets.get(SecretName.AI_PROVIDER_KEY) ?: error("ai_key_missing")
        val settings = preferences.aiProviderSettings()
        require(settings.configured) { "ai_provider_missing" }
        return ConfiguredClient(apiFactory(endpoint.apiBase, token, json), settings, key)
    }

    private suspend fun updateJob(job: AiReviewJobEntity, state: String, attempts: Int, next: Long, error: String?) {
        database.aiReviewJobs().updateState(job.jobId, state, attempts, next, error, nowMillis())
    }

    private data class ConfiguredClient(val api: WorkerAiApi, val settings: AiProviderSettings, val key: String)

    private fun mapConnectionFailure(error: HttpException): AiProviderConnectionException {
        val workerCode = runCatching {
            val raw = error.response()?.errorBody()?.string().orEmpty()
            json.parseToJsonElement(raw).jsonObject["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val failure = when {
            workerCode == "ai_provider_http_401" || workerCode == "ai_provider_http_403" ->
                AiProviderConnectionFailure.KEY_REJECTED
            workerCode == "ai_provider_http_402" -> AiProviderConnectionFailure.BALANCE_OR_QUOTA
            workerCode == "ai_provider_http_429" -> AiProviderConnectionFailure.RATE_LIMITED
            workerCode == "ai_provider_http_400" || workerCode == "ai_provider_http_404" ->
                AiProviderConnectionFailure.MODEL_UNAVAILABLE
            workerCode == "ai_provider_response_invalid" -> AiProviderConnectionFailure.INVALID_RESPONSE
            workerCode == "ai_provider_unreachable" || workerCode?.matches(Regex("ai_provider_http_5\\d\\d")) == true ->
                AiProviderConnectionFailure.PROVIDER_UNAVAILABLE
            error.code() == 401 || error.code() == 403 -> AiProviderConnectionFailure.CLOUD_AUTH
            error.code() >= 500 -> AiProviderConnectionFailure.PROVIDER_UNAVAILABLE
            else -> AiProviderConnectionFailure.UNKNOWN
        }
        return AiProviderConnectionException(failure, error)
    }
}

private const val MAX_REVIEW_ATTEMPTS = 6
private val PERMANENT_REVIEW_ERRORS = setOf(
    "cloud_not_configured",
    "ai_key_missing",
    "ai_provider_missing",
    "provider_response_invalid",
)

private fun providerRequest(settings: AiProviderSettings, key: String): JsonObject = buildJsonObject {
    put("provider", buildJsonObject {
        put("id", JsonPrimitive(settings.provider))
        put("baseUrl", JsonPrimitive(settings.baseUrl))
        put("model", JsonPrimitive(settings.model))
        put("apiKey", JsonPrimitive(key))
    })
}

private fun SharedRecord.withReviewStatus(status: String): SharedRecord = SharedRecord(buildJsonObject {
    envelope.forEach { (key, value) -> put(key, value) }
    put("data", buildJsonObject {
        data.forEach { (key, value) -> put(key, value) }
        put("status", JsonPrimitive(status))
    })
})

private fun decodeReview(record: SharedRecord): DailyReview? = runCatching {
    val data = record.data
    DailyReview(
        id = data.string("id") ?: record.id,
        date = requireNotNull(data.string("date")),
        version = requireNotNull(data.int("version")),
        status = requireNotNull(data.string("status")),
        conclusion = requireNotNull(data.string("conclusion")),
        assessment = data.string("assessment").orEmpty(),
        actions = data.stringList("actions"),
        evidence = data.stringList("evidence"),
        cautions = data.stringList("cautions"),
        localSuggestion = data.objectOrNull("localSuggestion")?.let { suggestion ->
            DailyReviewSuggestion(
                date = requireNotNull(suggestion.string("date")),
                title = requireNotNull(suggestion.string("title")),
                trainingType = requireNotNull(suggestion.string("trainingType")),
                estimatedMinutes = suggestion.int("estimatedMinutes"),
                reason = suggestion.string("reason"),
            )
        },
        inputDigest = requireNotNull(data.string("inputDigest")),
        provider = requireNotNull(data.string("provider")),
        model = requireNotNull(data.string("model")),
        generatedAt = requireNotNull(data.string("generatedAt")),
    )
}.getOrNull()

private fun JsonObject.dateOrNull(): String? = string("date")
private fun JsonObject.string(key: String): String? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(key: String): Int? = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull
private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key]
    ?.takeUnless { it is JsonNull }
    ?.let { runCatching { it.jsonObject }.getOrNull() }
private fun JsonObject.stringList(key: String): List<String> = this[key]?.let { value ->
    runCatching { value.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrDefault(emptyList())
}.orEmpty()

private fun canonicalize(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { it.key to canonicalize(it.value) })
    is JsonArray -> JsonArray(element.map(::canonicalize))
    else -> element
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

private fun retryDelay(attempts: Int): Long = (30_000L * (1L shl attempts.coerceIn(0, 8))).coerceAtMost(6 * 60 * 60 * 1000L)
private fun Throwable.safeCode(): String = (message ?: javaClass.simpleName)
    .lowercase().replace(Regex("[^a-z0-9_ -]"), "_").take(80)

private val REVIEW_INPUT_ENTITIES = setOf(
    "status_checkins", "body_metrics", "training_logs", "daily_plan_items", "plan_adjustments", "goal_sets", "coach_strategies",
)
