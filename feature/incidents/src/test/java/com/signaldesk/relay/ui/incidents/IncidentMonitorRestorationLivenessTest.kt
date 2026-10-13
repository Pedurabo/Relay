package com.signaldesk.relay.ui.incidents

import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentMonitorRestorationLivenessTest {

    private fun ownership(command: String) =
        IncidentMonitorOwnership(
            ownerPrincipal = "account-a",
            sessionGeneration = 50L,
            commandId = command
        )

    @Test
    fun unattachedStatusClaimIsNotLive() {
        val claims = IncidentMonitorClaims()
        val owner = ownership("status-a")

        claims.claimStatus(owner)!!

        assertFalse(claims.hasActiveStatusMonitor(owner))
    }

    @Test
    fun activeStatusJobIsRecognizedAndCancelledJobIsNot() {
        val claims = IncidentMonitorClaims()
        val owner = ownership("status-a")
        val claim = claims.claimStatus(owner)!!
        val job = Job()

        assertTrue(claims.attachStatus(claim, job))
        assertTrue(claims.hasActiveStatusMonitor(owner))

        job.cancel()

        assertFalse(claims.hasActiveStatusMonitor(owner))
        claims.clear()
    }

    @Test
    fun activeSeverityJobIsRecognizedAndCancelledJobIsNot() {
        val claims = IncidentMonitorClaims()
        val owner = ownership("severity-a")
        val claim = claims.claimSeverity(owner)!!
        val job = Job()

        assertTrue(claims.attachSeverity(claim, job))
        assertTrue(claims.hasActiveSeverityMonitor(owner))

        job.cancel()

        assertFalse(claims.hasActiveSeverityMonitor(owner))
        claims.clear()
    }

    @Test
    fun differentCommandCannotReuseExistingMonitorLiveness() {
        val claims = IncidentMonitorClaims()

        val first = ownership("status-a")
        val second = ownership("status-b")

        val claim = claims.claimStatus(first)!!
        val job = Job()

        assertTrue(claims.attachStatus(claim, job))
        assertTrue(claims.hasActiveStatusMonitor(first))
        assertFalse(claims.hasActiveStatusMonitor(second))

        claims.clear()

        assertFalse(claims.hasActiveStatusMonitor(first))
        assertTrue(job.isCancelled)
    }

    @Test
    fun sessionResetInvalidatesBothMonitorKinds() {
        val claims = IncidentMonitorClaims()

        val status = ownership("status-a")
        val severity = ownership("severity-a")

        val statusClaim = claims.claimStatus(status)!!
        val severityClaim = claims.claimSeverity(severity)!!

        val statusJob = Job()
        val severityJob = Job()

        assertTrue(claims.attachStatus(statusClaim, statusJob))
        assertTrue(claims.attachSeverity(severityClaim, severityJob))

        claims.clear()

        assertFalse(claims.hasActiveStatusMonitor(status))
        assertFalse(claims.hasActiveSeverityMonitor(severity))
        assertTrue(statusJob.isCancelled)
        assertTrue(severityJob.isCancelled)
    }
}