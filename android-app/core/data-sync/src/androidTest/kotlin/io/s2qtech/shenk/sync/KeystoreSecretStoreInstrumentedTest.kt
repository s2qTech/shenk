package io.s2qtech.shenk.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
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
            val preferences = DevicePreferencesStore(context)
            val secret = UUID.randomUUID().toString()
            KeystoreSecretStore(preferences).put(SecretName.SHENK_TOKEN, secret)

            assertEquals(secret, KeystoreSecretStore(DevicePreferencesStore(context)).get(SecretName.SHENK_TOKEN))
            KeystoreSecretStore(preferences).remove(SecretName.SHENK_TOKEN)
            assertNull(KeystoreSecretStore(preferences).get(SecretName.SHENK_TOKEN))
        }
    }
}
