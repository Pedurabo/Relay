package com.signaldesk.relay.ui.incidents

import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentMonitorDeadClaimRecoveryTest {

    private fun owner(command: String) =
        IncidentMonitorOwnership(
            ownerPrincipal = "account-a",
            sessionGeneration = 60L,
            commandId = command
        )

    @Test
    fun cancelledStatusJobAllowsSameCommandToReclaimOwnership() {
        val registry = IncidentMonitorClaims()
        val ownership = owner("status-a")

        val original = registry.claimStatus(ownership)!!
        val originalJob = Job()

        assertTrue(registry.attachStatus(original, originalJob))
        assertNull(registry.claimStatus(ownership))

        originalJob.cancel()

        assertFalse(registry.hasActiveStatusMonitor(ownership))

        val replacement = registry.claimStatus(ownership)

        assertNotNull(replacement)
        assertNotEquals(original, replacement)
        assertTrue(registry.ownsStatus(replacement!!))

        val replacementJob = Job()
        assertTrue(registry.attachStatus(replacement, replacementJob))
        assertTrue(registry.hasActiveStatusMonitor(ownership))

        // Simulates the cancelled monitor finally releasing late.
        assertFalse(registry.releaseStatus(original))
        assertTrue(registry.ownsStatus(replacement))
        assertTrue(replacementJob.isActive)

        assertNull(registry.claimStatus(ownership))

        registry.clear()

        assertTrue(replacementJob.isCancelled)
    }

    @Test
    fun cancelledSeverityJobAllowsSameCommandToReclaimOwnership() {
        val registry = IncidentMonitorClaims()
        val ownership = owner("severity-a")

        val original = registry.claimSeverity(ownership)!!
        val originalJob = Job()

        assertTrue(registry.attachSeverity(original, originalJob))
        assertNull(registry.claimSeverity(ownership))

        originalJob.cancel()

        assertFalse(registry.hasActiveSeverityMonitor(ownership))

        val replacement = registry.claimSeverity(ownership)

        assertNotNull(replacement)
        assertNotEquals(original, replacement)
        assertTrue(registry.ownsSeverity(replacement!!))

        val replacementJob = Job()
        assertTrue(registry.attachSeverity(replacement, replacementJob))
        assertTrue(registry.hasActiveSeverityMonitor(ownership))

        // Old cleanup must not remove the newer claim.
        assertFalse(registry.releaseSeverity(original))
        assertTrue(registry.ownsSeverity(replacement))
        assertTrue(replacementJob.isActive)

        assertNull(registry.claimSeverity(ownership))

        registry.clear()

        assertTrue(replacementJob.isCancelled)
    }
}