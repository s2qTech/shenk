package io.s2qtech.shenk.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreInstrumentedTest {
    @Test
    fun keystoreBackedSecretSurvivesStoreRecreationAndCanBeRemoved() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val testId = UUID.randomUUID().toString()
            val alias = "shenk_device_secrets_test_$testId"
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val file = context.preferencesDataStoreFile("shenk_device_preferences_test_$testId")
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
            val preferences = DevicePreferencesStore(dataStore)
            val cipher = AndroidKeystoreCipher(alias)
            val secret = UUID.randomUUID().toString()
            try {
                KeystoreSecretStore(preferences, cipher).put(SecretName.SHENK_TOKEN, secret)

                assertEquals(secret, KeystoreSecretStore(DevicePreferencesStore(dataStore), cipher).get(SecretName.SHENK_TOKEN))
                KeystoreSecretStore(preferences, cipher).remove(SecretName.SHENK_TOKEN)
                assertNull(KeystoreSecretStore(preferences, cipher).get(SecretName.SHENK_TOKEN))
            } finally {
                scope.cancel()
                file.delete()
                KeyStore.getInstance("AndroidKeyStore").apply {
                    load(null)
                    if (containsAlias(alias)) deleteEntry(alias)
                }
            }
        }
    }
}
