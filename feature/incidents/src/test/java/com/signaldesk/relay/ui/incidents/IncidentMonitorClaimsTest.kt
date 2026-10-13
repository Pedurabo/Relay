package com.signaldesk.relay.ui.incidents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentMonitorClaimsTest {

    private fun owner(
        commandId: String,
        generation: Long = 10L
    ) = IncidentMonitorOwnership(
        ownerPrincipal = "account-a",
        sessionGeneration = generation,
        commandId = commandId
    )

    @Test
    fun duplicateStatusMonitorIsRejected() {
        val registry = IncidentMonitorClaims()
        val command = owner("command-a")

        val first = registry.claimStatus(command)

        assertNotNull(first)
        assertNull(registry.claimStatus(command))
        assertTrue(registry.ownsStatus(first!!))
    }

    @Test
    fun duplicateSeverityMonitorIsRejected() {
        val registry = IncidentMonitorClaims()
        val command = owner("command-a")

        val first = registry.claimSeverity(command)

        assertNotNull(first)
        assertNull(registry.claimSeverity(command))
        assertTrue(registry.ownsSeverity(first!!))
    }

    @Test
    fun releaseAllowsNewCommand() {
        val registry = IncidentMonitorClaims()
        val first = registry.claimStatus(owner("command-a"))!!

        assertTrue(registry.releaseStatus(first))

        val second = registry.claimStatus(owner("command-b"))

        assertNotNull(second)
        assertEquals("command-b", second!!.ownership.commandId)
    }

    @Test
    fun staleClaimCannotReleaseNewInvocation() {
        val registry = IncidentMonitorClaims()
        val command = owner("command-a")

        val first = registry.claimStatus(command)!!

        assertTrue(registry.releaseStatus(first))

        val second = registry.claimStatus(command)!!

        assertFalse(registry.releaseStatus(first))
        assertTrue(registry.ownsStatus(second))
        assertFalse(registry.ownsStatus(first))
    }

    @Test
    fun statusAndSeverityClaimsAreIndependent() {
        val registry = IncidentMonitorClaims()
        val command = owner("command-a")

        val status = registry.claimStatus(command)
        val severity = registry.claimSeverity(command)

        assertNotNull(status)
        assertNotNull(severity)
        assertTrue(registry.ownsStatus(status!!))
        assertTrue(registry.ownsSeverity(severity!!))
    }

    @Test
    fun sessionResetInvalidatesOldClaims() {
        val registry = IncidentMonitorClaims()
        val oldStatus = registry.claimStatus(owner("status", 10L))!!
        val oldSeverity = registry.claimSeverity(owner("severity", 10L))!!

        registry.clear()

        assertFalse(registry.ownsStatus(oldStatus))
        assertFalse(registry.ownsSeverity(oldSeverity))
        assertFalse(registry.releaseStatus(oldStatus))
        assertFalse(registry.releaseSeverity(oldSeverity))

        val returned = registry.claimStatus(owner("status", 12L))

        assertNotNull(returned)
        assertEquals(12L, returned!!.ownership.sessionGeneration)
    }

    @Test
    fun blankCommandCannotClaimMonitor() {
        val registry = IncidentMonitorClaims()

        assertNull(registry.claimStatus(owner("")))
        assertNull(registry.claimSeverity(owner("")))
    }

    @Test
    fun newStatusCommandReplacesOldClaim() {
        val registry = IncidentMonitorClaims()

        val older = registry.claimStatus(owner("older"))!!
        val newer = registry.claimStatus(owner("newer"))!!

        assertFalse(registry.ownsStatus(older))
        assertTrue(registry.ownsStatus(newer))
        assertFalse(registry.releaseStatus(older))
        assertTrue(registry.ownsStatus(newer))
    }

    @Test
    fun replacementDoesNotReleaseSeverityClaim() {
        val registry = IncidentMonitorClaims()

        registry.claimStatus(owner("older"))!!
        val severity = registry.claimSeverity(owner("severity"))!!

        assertNotNull(registry.claimStatus(owner("newer")))
        assertTrue(registry.ownsSeverity(severity))
    }

    @Test
    fun newSeverityCommandReplacesOldClaim() {
        val registry = IncidentMonitorClaims()

        val older = registry.claimSeverity(owner("older-severity"))!!
        val newer = registry.claimSeverity(owner("newer-severity"))!!

        assertFalse(registry.ownsSeverity(older))
        assertTrue(registry.ownsSeverity(newer))

        assertFalse(registry.releaseSeverity(older))
        assertTrue(registry.ownsSeverity(newer))
    }

    @Test
    fun severityReplacementPreservesStatusClaim() {
        val registry = IncidentMonitorClaims()

        val status = registry.claimStatus(owner("status-command"))!!
        registry.claimSeverity(owner("older-severity"))!!

        val newer = registry.claimSeverity(owner("newer-severity"))

        assertNotNull(newer)
        assertTrue(registry.ownsStatus(status))
    }
}
