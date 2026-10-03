package com.example.aicamalert

import com.example.aicamalert.util.RequiredPermissionState
import com.example.aicamalert.util.RequiredSetupStep
import org.junit.Assert.*
import org.junit.Test

class RequiredPermissionStateTest {
    @Test fun freshInstallRequestsPreciseLocationBeforeBackgroundLocation() {
        val state = RequiredPermissionState(emptySet())
        assertFalse(state.isReady)
        assertEquals(RequiredSetupStep.PRECISE_LOCATION, state.nextMissing)
    }

    @Test fun everyRevokedRequirementBlocksEntryAndPointsToTheMissingStep() {
        RequiredSetupStep.entries.forEach { revoked ->
            val state = RequiredPermissionState(RequiredSetupStep.entries.toSet() - revoked)
            assertFalse("$revoked must block incomplete setup", state.isReady)
            assertEquals(revoked, state.nextMissing)
        }
    }

    @Test fun completeSetupHasNoRemainingRequest() {
        val state = RequiredPermissionState(RequiredSetupStep.entries.toSet())
        assertTrue(state.isReady)
        assertNull(state.nextMissing)
    }
}
