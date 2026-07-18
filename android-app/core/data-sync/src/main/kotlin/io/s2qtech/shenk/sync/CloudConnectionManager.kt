package io.s2qtech.shenk.sync

import android.content.Context
import kotlinx.serialization.json.Json

data class CloudConnectionState(
    val configured: Boolean,
    val apiBase: String,
)

data class CloudConnectionResult(
    val pulled: Int,
    val pushed: Int,
    val conflicts: Int,
)

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
        val profile = SyncProfileGateway(
            SyncProfileRemoteApiFactory.create(DEFAULT_SHENK_API_BASE, json),
            json = json,
        ).load(migrationCode)

        preferences.setApiBase(profile.apiBase)
        preferences.setTimerUrl(profile.timerUrl)
        secrets.put(SecretName.SHENK_TOKEN, profile.token)
        secrets.put(SecretName.TIMER_TOKEN, profile.timerToken)
        return synchronizeNow()
    }

    suspend fun synchronizeNow(): CloudConnectionResult {
        val settings = preferences.syncSettings()
        val token = requireNotNull(secrets.get(SecretName.SHENK_TOKEN)) {
            "尚未连接身刻云数据"
        }
        require(settings.apiBase.isNotBlank()) { "尚未配置云端地址" }
        val result = SyncEngine(
            database = database,
            repository = localRepository,
            api = WorkerRecordApiFactory.create(settings.apiBase, token, json),
            deviceId = settings.deviceId,
            json = json,
        ).synchronize()
        return CloudConnectionResult(
            pulled = result.pulled,
            pushed = result.pushed,
            conflicts = result.conflicts,
        )
    }

    companion object {
        const val DEFAULT_SHENK_API_BASE = "https://shenke-cloud-db.sq-muyi.workers.dev/api"
    }
}
