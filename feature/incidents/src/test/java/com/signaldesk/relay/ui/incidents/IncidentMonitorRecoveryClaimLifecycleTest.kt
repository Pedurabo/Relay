package com.signaldesk.relay.ui.incidents

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentMonitorRecoveryClaimLifecycleTest {

    private val ownership = IncidentMonitorOwnership(
        ownerPrincipal = "account-a",
        sessionGeneration = 60L,
        commandId = "command-a"
    )

    @Test
    fun statusRecoveryReacquiresClaimAfterFirstFailure() = runBlocking {
        val claims = IncidentMonitorClaims()
        val events = mutableListOf<String>()
        val tokens = mutableListOf<Long>()
        var attempts = 0

        executeMonitorWithOneRecovery(
            initial = "command-a",
            monitor = {
                attempts++

                val claim = claims.claimStatus(ownership)
                    ?: error("Expected status claim")

                tokens.add(claim.token)

                val job = currentCoroutineContext()[Job]
                    ?: error("Missing coroutine job")

                try {
                    assertTrue(claims.attachStatus(claim, job))
                    events.add("attempt-$attempts")

                    if (attempts == 1) {
                        throw IllegalStateException("collector failed")
                    }
                } finally {
                    assertTrue(claims.releaseStatus(claim))
                }
            },
            reload = {
                events.add("reload")
                "command-a"
            },
            mayRetry = { true },
            acquireRetryPermit = { true },
            onStopped = { events.add("stopped") }
        )

        assertEquals(2, attempts)
        assertEquals(2, tokens.size)
        assertNotEquals(tokens[0], tokens[1])
        assertEquals(
            listOf("attempt-1", "reload", "attempt-2"),
            events
        )
        assertFalse(claims.hasActiveStatusMonitor(ownership))
    }

    @Test
    fun severityRecoveryReacquiresClaimAfterFirstFailure() = runBlocking {
        val claims = IncidentMonitorClaims()
        var attempts = 0

        executeMonitorWithOneRecovery(
            initial = "command-a",
            monitor = {
                attempts++

                val claim = claims.claimSeverity(ownership)
                    ?: error("Expected severity claim")

                val job = currentCoroutineContext()[Job]
                    ?: error("Missing coroutine job")

                try {
                    assertTrue(claims.attachSeverity(claim, job))

                    if (attempts == 1) {
                        throw IllegalStateException("collector failed")
                    }
                } finally {
                    assertTrue(claims.releaseSeverity(claim))
                }
            },
            reload = { "command-a" },
            mayRetry = { true },
            acquireRetryPermit = { true },
            onStopped = { error("Unexpected stop") }
        )

        assertEquals(2, attempts)
        assertFalse(claims.hasActiveSeverityMonitor(ownership))
    }

    @Test
    fun oldStatusClaimCannotReleaseNewerClaim() {
        val claims = IncidentMonitorClaims()

        val oldOwnership = ownership
        val newerOwnership = ownership.copy(commandId = "command-b")

        val oldClaim = claims.claimStatus(oldOwnership)
            ?: error("Missing old claim")

        val oldJob = Job()
        assertTrue(claims.attachStatus(oldClaim, oldJob))

        val newerClaim = claims.claimStatus(newerOwnership)
            ?: error("Missing newer claim")

        val newerJob = Job()
        assertTrue(claims.attachStatus(newerClaim, newerJob))

        assertFalse(oldJob.isActive)
        assertFalse(claims.releaseStatus(oldClaim))
        assertTrue(claims.ownsStatus(newerClaim))
        assertTrue(claims.hasActiveStatusMonitor(newerOwnership))

        assertTrue(claims.releaseStatus(newerClaim))

        newerJob.cancel()
    }

    @Test
    fun oldSeverityClaimCannotReleaseNewerClaim() {
        val claims = IncidentMonitorClaims()

        val oldClaim = claims.claimSeverity(ownership)
            ?: error("Missing old claim")

        val oldJob = Job()
        assertTrue(claims.attachSeverity(oldClaim, oldJob))

        val newerOwnership = ownership.copy(commandId = "command-b")
        val newerClaim = claims.claimSeverity(newerOwnership)
            ?: error("Missing newer claim")

        val newerJob = Job()
        assertTrue(claims.attachSeverity(newerClaim, newerJob))

        assertFalse(oldJob.isActive)
        assertFalse(claims.releaseSeverity(oldClaim))
        assertTrue(claims.ownsSeverity(newerClaim))
        assertTrue(claims.hasActiveSeverityMonitor(newerOwnership))

        assertTrue(claims.releaseSeverity(newerClaim))

        newerJob.cancel()
    }

    @Test
    fun cancelledStatusMonitorDoesNotAttemptRecovery() = runBlocking {
        val claims = IncidentMonitorClaims()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        var reloads = 0
        var permits = 0
        var stopped = false

        val worker = launch {
            executeMonitorWithOneRecovery(
                initial = "command-a",
                monitor = {
                    val claim = claims.claimStatus(ownership)
                        ?: error("Missing status claim")

                    val job = currentCoroutineContext()[Job]
                        ?: error("Missing job")

                    try {
                        assertTrue(claims.attachStatus(claim, job))
                        entered.complete(Unit)
                        release.await()
                    } finally {
                        claims.releaseStatus(claim)
                    }
                },
                reload = {
                    reloads++
                    "command-a"
                },
                mayRetry = { true },
                acquireRetryPermit = {
                    permits++
                    true
                },
                onStopped = { stopped = true }
            )
        }

        entered.await()
        claims.clear()
        worker.join()

        assertFalse(worker.isActive)
        assertFalse(claims.hasActiveStatusMonitor(ownership))
        assertEquals(0, reloads)
        assertEquals(0, permits)
        assertFalse(stopped)
    }

    @Test
    fun failedRecoveredMonitorReleasesClaimAndStopsOnce() = runBlocking {
        val claims = IncidentMonitorClaims()
        var attempts = 0
        var stops = 0

        executeMonitorWithOneRecovery(
            initial = "command-a",
            monitor = {
                attempts++

                val claim = claims.claimStatus(ownership)
                    ?: error("Missing status claim")

                val job = currentCoroutineContext()[Job]
                    ?: error("Missing job")

                try {
                    assertTrue(claims.attachStatus(claim, job))
                    throw IllegalStateException("collector failed")
                } finally {
                    claims.releaseStatus(claim)
                }
            },
            reload = { "command-a" },
            mayRetry = { true },
            acquireRetryPermit = { true },
            onStopped = {
                stops++
                assertFalse(claims.hasActiveStatusMonitor(ownership))
            }
        )

        assertEquals(2, attempts)
        assertEquals(1, stops)
        assertFalse(claims.hasActiveStatusMonitor(ownership))
    }
}