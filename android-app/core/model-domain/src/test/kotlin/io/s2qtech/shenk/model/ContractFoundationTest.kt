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

    @Test
    fun packageOneFixtureReadsV2WithoutLosingV1Support() {
        val text = requireNotNull(javaClass.classLoader?.getResource("android-package1-v2.json"))
            .readText()
        val fixture = json.decodeFromString<PackageOneFixture>(text)

        assertTrue(fixture.synthetic)
        assertEquals(ContractVersion.PLANNED, fixture.contractVersion)
        assertEquals(listOf(ContractVersion.ACTIVE, ContractVersion.PLANNED), fixture.supportedContractVersions)
        assertEquals("recovery", fixture.routine.scene)
        assertEquals("recovery", fixture.routine.role)
        assertTrue(fixture.routine.timerVisible)
        assertEquals("status_checkins", fixture.statusCheckin.entity)
        assertEquals(4, fixture.statusCheckin.energy)
        assertEquals("timer_sessions", fixture.timerSession.entity)
        assertEquals("android", fixture.timerSession.devicePlatform)

        val migrated = LegacyMetricMigration.split(fixture.legacyBodyMetric)
        assertEquals("body_metric:${fixture.legacyBodyMetric.id}", migrated.bodyMetric.id)
        assertEquals("status_checkin:${fixture.legacyBodyMetric.id}", migrated.statusCheckin.id)
        assertEquals(fixture.legacyBodyMetric.id, migrated.bodyMetric.sourceRecordId)
        assertEquals(fixture.legacyBodyMetric.id, migrated.statusCheckin.sourceRecordId)
        assertEquals(fixture.legacyBodyMetric.weightKg, migrated.bodyMetric.weightKg)
        assertEquals(fixture.legacyBodyMetric.energy, migrated.statusCheckin.energy)
        assertEquals(fixture.legacyBodyMetric.fatigue, migrated.statusCheckin.fatigue)
    }
}
