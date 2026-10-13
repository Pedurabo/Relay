package com.signaldesk.relay.ui.incidents

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentMonitorCancellationTest {

    private fun ownership(id: String, generation: Long = 10L) =
        IncidentMonitorOwnership("account-a", generation, id)

    @Test
    fun replacingStatusCancelsPreviousMonitor() = runBlocking {
        val claims = IncidentMonitorClaims()
        val oldClaim = claims.claimStatus(ownership("old"))!!

        val cancelled = CompletableDeferred<Unit>()

        val oldJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }

        assertTrue(claims.attachStatus(oldClaim, oldJob))

        val newClaim = claims.claimStatus(ownership("new"))!!

        withTimeout(5000L) {
            cancelled.await()
            oldJob.join()
        }

        assertFalse(claims.ownsStatus(oldClaim))
        assertTrue(claims.ownsStatus(newClaim))
        assertFalse(claims.releaseStatus(oldClaim))
        assertTrue(claims.ownsStatus(newClaim))

        claims.clear()
    }

    @Test
    fun replacingSeverityCancelsPreviousMonitor() = runBlocking {
        val claims = IncidentMonitorClaims()
        val oldClaim = claims.claimSeverity(ownership("old"))!!

        val cancelled = CompletableDeferred<Unit>()

        val oldJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }

        assertTrue(claims.attachSeverity(oldClaim, oldJob))

        val newClaim = claims.claimSeverity(ownership("new"))!!

        withTimeout(5000L) {
            cancelled.await()
            oldJob.join()
        }

        assertFalse(claims.ownsSeverity(oldClaim))
        assertTrue(claims.ownsSeverity(newClaim))
        assertFalse(claims.releaseSeverity(oldClaim))
        assertTrue(claims.ownsSeverity(newClaim))

        claims.clear()
    }

    @Test
    fun resetCancelsBothAttachedMonitors() = runBlocking {
        val claims = IncidentMonitorClaims()

        val statusClaim = claims.claimStatus(ownership("status"))!!
        val severityClaim = claims.claimSeverity(ownership("severity"))!!

        val statusCancelled = CompletableDeferred<Unit>()
        val severityCancelled = CompletableDeferred<Unit>()

        val statusJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                statusCancelled.complete(Unit)
            }
        }

        val severityJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                severityCancelled.complete(Unit)
            }
        }

        assertTrue(claims.attachStatus(statusClaim, statusJob))
        assertTrue(claims.attachSeverity(severityClaim, severityJob))

        claims.clear()

        withTimeout(5000L) {
            statusCancelled.await()
            severityCancelled.await()
            statusJob.join()
            severityJob.join()
        }

        assertFalse(claims.ownsStatus(statusClaim))
        assertFalse(claims.ownsSeverity(severityClaim))
    }

    @Test
    fun obsoleteClaimCannotAttachOverReplacement() {
        val claims = IncidentMonitorClaims()

        val oldClaim = claims.claimStatus(ownership("old"))!!
        val newClaim = claims.claimStatus(ownership("new"))!!

        val unattachedJob = Job()

        assertFalse(claims.attachStatus(oldClaim, unattachedJob))
        assertTrue(claims.ownsStatus(newClaim))

        unattachedJob.cancel()
        claims.clear()
    }
}