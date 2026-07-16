package io.s2qtech.shenk

import io.s2qtech.shenk.model.ContractVersion
import io.s2qtech.shenk.sync.SyncFoundationState
import io.s2qtech.shenk.timer.TimerEngineState
import org.junit.Assert.assertEquals
import org.junit.Test

class FoundationModulesTest {
    @Test
    fun appUsesPinnedContractAndModuleBoundaries() {
        assertEquals("1.0", ContractVersion.ACTIVE)
        assertEquals(SyncFoundationState.LOCAL_ONLY, SyncFoundationState.entries.first())
        assertEquals(TimerEngineState.IDLE, TimerEngineState.entries.first())
    }
}
