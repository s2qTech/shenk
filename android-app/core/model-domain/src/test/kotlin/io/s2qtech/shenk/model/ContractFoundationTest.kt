package io.s2qtech.shenk.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractFoundationTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun canonicalFixturePreservesCoreAuthorityAndOwnership() {
        val text = requireNotNull(javaClass.classLoader?.getResource("android-package0.json"))
            .readText()
        val fixture = json.decodeFromString<PackageZeroFixture>(text)

        assertTrue(fixture.synthetic)
        assertEquals(ContractVersion.ACTIVE, fixture.contractVersion)
        assertEquals(
            listOf("training_logs", "effective_formal_plan", "local_fallback_suggestion"),
            fixture.expectedDayPriority,
        )
        assertEquals("recovery", fixture.routine.scene)
        assertEquals("recovery", fixture.routine.role)
        assertEquals(fixture.timerSession.id, fixture.trainingLog.timerSessionId)
        assertTrue(EntityOwnership.canWrite(SharedEntityOwner.TIMER, "timer_sessions"))
        assertFalse(EntityOwnership.canWrite(SharedEntityOwner.PLANNING_RECORD, "timer_sessions"))
    }
}
