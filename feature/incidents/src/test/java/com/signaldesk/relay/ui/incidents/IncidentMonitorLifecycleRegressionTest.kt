package com.signaldesk.relay.ui.incidents

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentMonitorLifecycleRegressionTest {

    private fun ownership(
        command: String,
        generation: Long,
        account: String = "account-a"
    ) = IncidentMonitorOwnership(
        ownerPrincipal = account,
        sessionGeneration = generation,
        commandId = command
    )

    @Test
    fun accountAtoBtoAReturnInvalidatesOldMonitor() = runBlocking {
        val registry = IncidentMonitorClaims()

        val previous = ownership("command-a", 10L)
        val firstClaim = registry.claimStatus(previous)!!

        val cancelled = CompletableDeferred<Unit>()

        val firstJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                registry.releaseStatus(firstClaim)
                cancelled.complete(Unit)
            }
        }

        assertTrue(registry.attachStatus(firstClaim, firstJob))

        // Authentication moves from account A to account B.
        registry.clear()

        withTimeout(5000L) {
            cancelled.await()
            firstJob.join()
        }

        assertFalse(registry.ownsStatus(firstClaim))

        val accountB = registry.claimStatus(
            ownership("command-b", 11L, "account-b")
        )!!

        assertTrue(registry.ownsStatus(accountB))

        // Returning to A produces a new session generation.
        registry.clear()

        val returned = registry.claimStatus(
            ownership("command-a", 12L)
        )!!

        assertFalse(registry.ownsStatus(firstClaim))
        assertTrue(registry.ownsStatus(returned))
        assertFalse(registry.releaseStatus(firstClaim))
        assertTrue(registry.ownsStatus(returned))

        registry.clear()
    }

    @Test
    fun replacementCancelsOldMonitorButPreservesNewMonitor() = runBlocking {
        val registry = IncidentMonitorClaims()

        val oldOwner = ownership("old-severity", 20L)
        val newOwner = ownership("new-severity", 20L)

        val oldClaim = registry.claimSeverity(oldOwner)!!
        val oldCancelled = CompletableDeferred<Unit>()

        val oldJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                registry.releaseSeverity(oldClaim)
                oldCancelled.complete(Unit)
            }
        }

        assertTrue(registry.attachSeverity(oldClaim, oldJob))

        val newClaim = registry.claimSeverity(newOwner)!!

        val newCancelled = CompletableDeferred<Unit>()

        val newJob = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                registry.releaseSeverity(newClaim)
                newCancelled.complete(Unit)
            }
        }

        assertTrue(registry.attachSeverity(newClaim, newJob))

        withTimeout(5000L) {
            oldCancelled.await()
            oldJob.join()
        }

        assertTrue(oldJob.isCancelled)
        assertTrue(newJob.isActive)
        assertFalse(newCancelled.isCompleted)
        assertTrue(registry.ownsSeverity(newClaim))
        assertFalse(registry.releaseSeverity(oldClaim))

        registry.clear()

        withTimeout(5000L) {
            newCancelled.await()
            newJob.join()
        }

        assertTrue(newJob.isCancelled)
    }

    @Test
    fun sameCommandDuplicateDoesNotDisplaceActiveMonitor() = runBlocking {
        val registry = IncidentMonitorClaims()

        val command = ownership("status-command", 30L)
        val claim = registry.claimStatus(command)!!

        val cancelled = CompletableDeferred<Unit>()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                registry.releaseStatus(claim)
                cancelled.complete(Unit)
            }
        }

        assertTrue(registry.attachStatus(claim, job))

        assertNull(registry.claimStatus(command))
        assertTrue(job.isActive)
        assertTrue(registry.ownsStatus(claim))

        registry.clear()

        withTimeout(5000L) {
            cancelled.await()
            job.join()
        }

        assertTrue(job.isCancelled)
    }

    @Test
    fun lateReleaseCannotClearReturnedAccountSeverity() {
        val registry = IncidentMonitorClaims()

        val old = registry.claimSeverity(
            ownership("severity", 40L)
        )!!

        registry.clear()

        val returned = registry.claimSeverity(
            ownership("severity", 42L)
        )!!

        assertFalse(registry.releaseSeverity(old))
        assertTrue(registry.ownsSeverity(returned))
        assertEquals(42L, returned.ownership.sessionGeneration)

        registry.clear()
    }
}