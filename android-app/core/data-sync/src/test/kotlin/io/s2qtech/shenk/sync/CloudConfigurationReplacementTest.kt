package io.s2qtech.shenk.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudConfigurationReplacementTest {
    @Test
    fun failedReplacementRestoresPreviousCompleteProfile() = runBlocking {
        val preferences = DevicePreferencesStore(InMemoryPreferencesDataStore())
        preferences.setApiBase("https://old.example.invalid/api")
        preferences.setTimerUrl("https://old.example.invalid/timer")
        val secrets = FailingSecretStore().apply {
            values[SecretName.SHENK_TOKEN] = "old-shenk"
            values[SecretName.TIMER_TOKEN] = "old-timer"
            failNextTimerWrite = true
        }

        assertThrows(IllegalStateException::class.java) {
            runBlocking { replaceSyncConfiguration(preferences, secrets, replacement()) }
        }

        assertEquals("https://old.example.invalid/api", preferences.syncSettings().apiBase)
        assertEquals("https://old.example.invalid/timer", preferences.syncSettings().timerUrl)
        assertEquals("old-shenk", secrets.get(SecretName.SHENK_TOKEN))
        assertEquals("old-timer", secrets.get(SecretName.TIMER_TOKEN))
    }

    @Test
    fun successfulReplacementPublishesApiBaseLast() = runBlocking {
        val preferences = DevicePreferencesStore(InMemoryPreferencesDataStore())
        val secrets = FailingSecretStore()

        replaceSyncConfiguration(preferences, secrets, replacement())

        assertEquals("https://new.example.invalid/api", preferences.syncSettings().apiBase)
        assertEquals("https://new.example.invalid/timer", preferences.syncSettings().timerUrl)
        assertEquals("new-shenk", secrets.get(SecretName.SHENK_TOKEN))
        assertEquals("new-timer", secrets.get(SecretName.TIMER_TOKEN))
    }

    private fun replacement() = SyncProfilePayload(
        apiBase = "https://new.example.invalid/api",
        timerUrl = "https://new.example.invalid/timer",
        token = "new-shenk",
        timerToken = "new-timer",
    )

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    private class FailingSecretStore : SecretStore {
        val values = mutableMapOf<SecretName, String>()
        var failNextTimerWrite = false

        override suspend fun put(name: SecretName, value: String) {
            if (name == SecretName.TIMER_TOKEN && failNextTimerWrite) {
                failNextTimerWrite = false
                throw IllegalStateException("synthetic secure-store failure")
            }
            values[name] = value
        }

        override suspend fun get(name: SecretName): String? = values[name]

        override suspend fun remove(name: SecretName) {
            values.remove(name)
        }
    }
}
