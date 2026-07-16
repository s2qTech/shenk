package io.s2qtech.shenk.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalFirstWritePortTest {
    @Test
    fun pendingOperationRequiresStableIdentity() {
        val operation = PendingOperation("training_logs", "fixture_log_001")
        assertEquals("training_logs", operation.entity)
        assertThrows(IllegalArgumentException::class.java) {
            PendingOperation("", "fixture_log_001")
        }
    }
}
