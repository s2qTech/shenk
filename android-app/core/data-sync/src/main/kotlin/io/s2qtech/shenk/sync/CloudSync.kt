package io.s2qtech.shenk.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.s2qtech.shenk.model.ContractVersion
import io.s2qtech.shenk.model.EntityOwnership
import io.s2qtech.shenk.model.SharedEntityOwner
import io.s2qtech.shenk.model.SharedRecord
import io.s2qtech.shenk.model.SharedRecordKey
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface WorkerRecordApi {
    @POST("records/query")
    suspend fun query(@Body request: JsonObject): JsonObject

    @POST("records/upsert")
    suspend fun upsert(@Body request: JsonObject): JsonObject
}

object WorkerRecordApiFactory {
    fun create(apiBase: String, token: String, json: Json): WorkerRecordApi {
        require(apiBase.startsWith("https://")) { "cloud API must use HTTPS" }
        require(token.isNotBlank()) { "cloud token is required" }
        val client = OkHttpClient.Builder()
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
            .create(WorkerRecordApi::class.java)
    }
}

data class SyncRunResult(
    val pushed: Int,
    val pulled: Int,
    val conflicts: Int,
)

class SyncEngine(
    private val database: ShenkDatabase,
    private val repository: LocalFirstRepository,
    private val api: WorkerRecordApi,
    private val timerApi: WorkerRecordApi? = api,
    private val deviceId: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val timeSource: TimeSource = SystemTimeSource,
) {
    suspend fun synchronize(): SyncRunResult {
        var pushed = 0
        var conflicts = 0
        var batches = 0
        while (batches < MAX_PUSH_BATCHES_PER_RUN) {
            val push = pushDue()
            if (push.inspected == 0) break
            pushed += push.accepted
            conflicts += push.conflicts
            batches += 1
            if (push.inspected < OUTBOX_BATCH_SIZE) break
        }
        val pull = pullAllPages()
        return SyncRunResult(pushed, pull, conflicts)
    }

    private suspend fun pushDue(): PushBatchResult {
        val operations = database.outbox().due(timeSource.epochMillis(), OUTBOX_BATCH_SIZE)
        if (operations.isEmpty()) return PushBatchResult()
        val (timerOperations, shenkOperations) = operations.partition {
            EntityOwnership.ownerOf(it.entity) == SharedEntityOwner.TIMER
        }
        var result = PushBatchResult()
        if (shenkOperations.isNotEmpty()) {
            result += pushOperations(shenkOperations, api)
        }
        if (timerOperations.isNotEmpty()) {
            val roleApi = timerApi
            if (roleApi == null) {
                timerOperations.forEach { operation ->
                    repository.markRetry(
                        operation,
                        retryAt(operation.attempts),
                        "timer_token_missing",
                    )
                }
                result += PushBatchResult(inspected = timerOperations.size)
            } else {
                result += pushOperations(timerOperations, roleApi)
            }
        }
        return result
    }

    private suspend fun pushOperations(
        operations: List<OutboxEntity>,
        roleApi: WorkerRecordApi,
    ): PushBatchResult {
        val request = buildJsonObject {
            put("contractVersion", JsonPrimitive(ContractVersion.PLANNED))
            put("deviceId", JsonPrimitive(deviceId))
            put("records", buildJsonArray {
                operations.forEach { add(json.parseToJsonElement(it.payloadJson).jsonObject) }
            })
        }
        return try {
            val response = roleApi.upsert(request)
            var acceptedCount = 0
            var conflictCount = 0
            val acknowledged = mutableSetOf<String>()
            response["accepted"]?.jsonArray.orEmpty().forEach { item ->
                val accepted = item.jsonObject
                val entity = accepted.string("entity") ?: return@forEach
                val id = accepted.string("id") ?: return@forEach
                val operation = operations.firstOrNull { it.entity == entity && it.recordId == id } ?: return@forEach
                repository.markAccepted(
                    SharedRecordKey(entity, id),
                    operation.idempotencyKey,
                    accepted.int("revision"),
                    accepted.string("updatedAt"),
                )
                acknowledged += operation.recordKey
                acceptedCount += 1
            }
            response["conflicts"]?.jsonArray.orEmpty().forEach { item ->
                val conflict = item.jsonObject
                val entity = conflict.string("entity") ?: return@forEach
                val id = conflict.string("id") ?: return@forEach
                val operation = operations.firstOrNull { it.entity == entity && it.recordId == id } ?: return@forEach
                val remote = conflict["serverRecord"]?.jsonObject
                    ?: json.parseToJsonElement(operation.payloadJson).jsonObject
                repository.markConflict(operation, conflict.string("reason") ?: "cloud_rejected", remote)
                acknowledged += operation.recordKey
                conflictCount += 1
            }
            operations.filterNot { it.recordKey in acknowledged }.forEach { operation ->
                repository.markRetry(
                    operation,
                    retryAt(operation.attempts),
                    "unacknowledged_response",
                )
            }
            PushBatchResult(acceptedCount, conflictCount, operations.size)
        } catch (error: Exception) {
            operations.forEach { operation ->
                repository.markRetry(operation, retryAt(operation.attempts), error.safeSyncMessage())
            }
            throw error
        }
    }

    private suspend fun pullAllPages(): Int {
        val since = database.metadata().get(META_LAST_PULL_AT)
        var cursor: String? = null
        var pulled = 0
        var finalServerTime: String? = null
        do {
            val request = buildJsonObject {
                put("contractVersion", JsonPrimitive(ContractVersion.PLANNED))
                put("since", since?.let(::JsonPrimitive) ?: JsonNull)
                put("limit", JsonPrimitive(PULL_PAGE_SIZE))
                cursor?.let { put("cursor", JsonPrimitive(it)) }
            }
            val response = api.query(request)
            response["records"]?.jsonArray.orEmpty().forEach { element ->
                repository.applyRemote(SharedRecord(element.jsonObject))
                pulled += 1
            }
            finalServerTime = response.string("serverTime") ?: finalServerTime
            cursor = response.string("nextCursor")
        } while (cursor != null)
        finalServerTime?.let { database.metadata().put(SyncMetadataEntity(META_LAST_PULL_AT, it)) }
        return pulled
    }

    private fun retryAt(attempts: Int): Long {
        val exponent = attempts.coerceIn(0, 8)
        val delay = (30_000L shl exponent).coerceAtMost(TimeUnit.HOURS.toMillis(6))
        return timeSource.epochMillis() + delay
    }

    companion object {
        const val META_LAST_PULL_AT = "last_pull_at"
        const val OUTBOX_BATCH_SIZE = 100
        const val PULL_PAGE_SIZE = 200
        const val MAX_PUSH_BATCHES_PER_RUN = 10
    }
}

private data class PushBatchResult(
    val accepted: Int = 0,
    val conflicts: Int = 0,
    val inspected: Int = 0,
) {
    operator fun plus(other: PushBatchResult) = PushBatchResult(
        accepted = accepted + other.accepted,
        conflicts = conflicts + other.conflicts,
        inspected = inspected + other.inspected,
    )
}

class SyncScheduler(private val context: Context) {
    fun enqueue() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "shenk-cloud-sync"
    }
}

class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val preferences = DevicePreferencesStore(applicationContext)
        val settings = preferences.syncSettings()
        val secrets = KeystoreSecretStore(preferences)
        val token = secrets.get(SecretName.SHENK_TOKEN)
        val timerToken = secrets.get(SecretName.TIMER_TOKEN)
        if (settings.apiBase.isBlank() || token.isNullOrBlank()) return Result.success()
        val json = Json { ignoreUnknownKeys = true }
        val database = ShenkDatabase.get(applicationContext)
        val engine = SyncEngine(
            database = database,
            repository = LocalFirstRepository(database, localDeviceId = settings.deviceId),
            api = WorkerRecordApiFactory.create(settings.apiBase, token, json),
            timerApi = timerToken
                ?.takeIf(String::isNotBlank)
                ?.let { WorkerRecordApiFactory.create(settings.apiBase, it, json) },
            deviceId = settings.deviceId,
            json = json,
        )
        return try {
            engine.synchronize()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

private fun JsonObject.string(key: String): String? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: 0
private fun Exception.safeSyncMessage(): String = this::class.simpleName ?: "sync_error"
