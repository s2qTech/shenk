package io.s2qtech.shenk.sync

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException

data class CloudConnectionState(
    val configured: Boolean,
    val apiBase: String,
)

data class CloudConnectionResult(
    val pulled: Int,
    val pushed: Int,
    val conflicts: Int,
)

enum class CloudConnectionFailure {
    INVALID_MIGRATION_CODE,
    PROFILE_UNAVAILABLE,
    NETWORK_UNAVAILABLE,
    INVALID_PROFILE,
    CLOUD_SYNC_FAILED,
    LOCAL_CONFIGURATION_FAILED,
}

class CloudConnectionException(
    val failure: CloudConnectionFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

class CloudConnectionManager(
    context: Context,
    private val preferences: DevicePreferencesStore = DevicePreferencesStore(context),
    private val secrets: SecretStore = KeystoreSecretStore(preferences),
    private val database: ShenkDatabase = ShenkDatabase.get(context),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val localRepository = LocalFirstRepository(database, localDeviceId = null)

    suspend fun state(): CloudConnectionState {
        val settings = preferences.syncSettings()
        val hasToken = !secrets.get(SecretName.SHENK_TOKEN).isNullOrBlank()
        return CloudConnectionState(
            configured = settings.apiBase.isNotBlank() && hasToken,
            apiBase = settings.apiBase,
        )
    }

    suspend fun connectWithMigrationCode(migrationCode: String): CloudConnectionResult {
        val normalizedCode = try {
            SyncProfileCrypto.validateMigrationCode(migrationCode)
        } catch (error: IllegalArgumentException) {
            throw CloudConnectionException(CloudConnectionFailure.INVALID_MIGRATION_CODE, error)
        }
        val profile = loadProfile(normalizedCode)
        val result = synchronizeWith(profile.apiBase, profile.token, profile.timerToken)
        saveConfiguration(profile)
        return result
    }

    suspend fun synchronizeNow(): CloudConnectionResult {
        val settings = preferences.syncSettings()
        val token = requireNotNull(secrets.get(SecretName.SHENK_TOKEN)) {
            "Cloud connection is not configured"
        }
        val timerToken = secrets.get(SecretName.TIMER_TOKEN)
        require(settings.apiBase.isNotBlank()) { "Cloud API base is not configured" }
        return synchronizeWith(settings.apiBase, token, timerToken)
    }

    private suspend fun loadProfile(migrationCode: String): SyncProfilePayload {
        return try {
            withContext(Dispatchers.IO) {
                SyncProfileGateway(
                    SyncProfileRemoteApiFactory.create(DEFAULT_SHENK_API_BASE, json),
                    json = json,
                ).load(migrationCode)
            }
        } catch (error: HttpException) {
            val failure = if (error.code() in listOf(401, 403, 404)) {
                CloudConnectionFailure.PROFILE_UNAVAILABLE
            } else {
                CloudConnectionFailure.NETWORK_UNAVAILABLE
            }
            throw CloudConnectionException(failure, error)
        } catch (error: IOException) {
            throw CloudConnectionException(CloudConnectionFailure.NETWORK_UNAVAILABLE, error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw CloudConnectionException(CloudConnectionFailure.INVALID_PROFILE, error)
        }
    }

    private suspend fun synchronizeWith(
        apiBase: String,
        token: String,
        timerToken: String?,
    ): CloudConnectionResult {
        val settings = preferences.syncSettings()
        val result = try {
            withContext(Dispatchers.IO) {
                SyncEngine(
                    database = database,
                    repository = localRepository,
                    api = WorkerRecordApiFactory.create(apiBase, token, json),
                    timerApi = timerToken
                        ?.takeIf(String::isNotBlank)
                        ?.let { WorkerRecordApiFactory.create(apiBase, it, json) },
                    deviceId = settings.deviceId,
                    json = json,
                ).synchronize()
            }
        } catch (error: IOException) {
            throw CloudConnectionException(CloudConnectionFailure.NETWORK_UNAVAILABLE, error)
        } catch (error: CancellationException) {
            throw error
        } catch (error: CloudConnectionException) {
            throw error
        } catch (error: Exception) {
            throw CloudConnectionException(CloudConnectionFailure.CLOUD_SYNC_FAILED, error)
        }
        return CloudConnectionResult(
            pulled = result.pulled,
            pushed = result.pushed,
            conflicts = result.conflicts,
        )
    }

    private suspend fun saveConfiguration(profile: SyncProfilePayload) {
        try {
            replaceSyncConfiguration(preferences, secrets, profile)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw CloudConnectionException(CloudConnectionFailure.LOCAL_CONFIGURATION_FAILED, error)
        }
    }

    companion object {
        const val DEFAULT_SHENK_API_BASE = "https://shenke-cloud-db.sq-muyi.workers.dev/api"
    }
}

internal suspend fun replaceSyncConfiguration(
    preferences: DevicePreferencesStore,
    secrets: SecretStore,
    profile: SyncProfilePayload,
) {
    val previousSettings = preferences.syncSettings()
    val previousShenkToken = secrets.get(SecretName.SHENK_TOKEN)
    val previousTimerToken = secrets.get(SecretName.TIMER_TOKEN)

    // Clearing the marker first prevents a partially replaced profile from appearing usable.
    preferences.setApiBase("")
    try {
        secrets.put(SecretName.SHENK_TOKEN, profile.token)
        secrets.put(SecretName.TIMER_TOKEN, profile.timerToken)
        preferences.setTimerUrl(profile.timerUrl)
        preferences.setApiBase(profile.apiBase)
    } catch (error: Exception) {
        var rollbackComplete = runCatching {
            secrets.restore(SecretName.SHENK_TOKEN, previousShenkToken)
            secrets.restore(SecretName.TIMER_TOKEN, previousTimerToken)
            preferences.setTimerUrl(previousSettings.timerUrl)
        }.isSuccess
        if (rollbackComplete) {
            rollbackComplete = runCatching { preferences.setApiBase(previousSettings.apiBase) }.isSuccess
        }
        if (!rollbackComplete) runCatching { preferences.setApiBase("") }
        throw error
    }
}

private suspend fun SecretStore.restore(name: SecretName, value: String?) {
    if (value.isNullOrBlank()) remove(name) else put(name, value)
}
