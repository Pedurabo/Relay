package com.signaldesk.relay.ui.incidents

import com.signaldesk.relay.data.session.SessionIdentity
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentMonitorRecoveryConcurrencyTest {

    private val original = IncidentMonitorOwnership(
        ownerPrincipal = "account-a",
        sessionGeneration = 60L,
        commandId = "command-a"
    )

    private val initialIdentity = SessionIdentity(
        ownerPrincipal = "account-a",
        generation = 60L
    )

    private fun eligible(
        ownership: IncidentMonitorOwnership,
        active: IncidentMonitorOwnership?,
        identity: SessionIdentity,
        retryRequested: Boolean = false
    ) = decideIncidentMonitorRecovery(
        pendingOwnership = ownership,
        activeOwnership = active,
        currentIdentity = identity,
        pendingStillPresent = true,
        monitorActive = false,
        unexpectedFailure = true,
        retryAlreadyRequested = retryRequested
    ) == IncidentMonitorRecoveryDecision.RETRY

    @Test
    fun newerCommandDuringReloadPreventsRetryPermit() = runBlocking {
        val newer = original.copy(commandId = "command-b")
        var active = original
        val budget = IncidentMonitorRecoveryBudget()

        var attempts = 0
        var permits = 0
        var stopped = 0

        executeMonitorWithOneRecovery(
            initial = "command-a",
            monitor = {
                attempts++
                throw IllegalStateException("monitor failure")
            },
            reload = {
                active = newer
                "command-a"
            },
            mayRetry = {
                it == original.commandId &&
                    eligible(original, active, initialIdentity)
            },
            acquireRetryPermit = {
                permits++
                budget.requestStatusRetry(original)
            },
            onStopped = {
                stopped++
            }
        )

        assertEquals(1, attempts)
        assertEquals(0, permits)
        assertEquals(1, stopped)
        assertFalse(budget.statusRetryRequested(original))
    }

    @Test
    fun accountChangeDuringReloadPreventsRetry() = runBlocking {
        var identity = initialIdentity
        val budget = IncidentMonitorRecoveryBudget()

        var attempts = 0
        var permits = 0

        executeMonitorWithOneRecovery(
            initial = "command-a",
            monitor = {
                attempts++
                throw IllegalStateException("monitor failure")
            },
            reload = {
                identity = SessionIdentity(
                    ownerPrincipal = "account-b",
                    generation = 61L
                )
                "command-a"
            },
            mayRetry = {
                eligible(original, original, identity)
            },
            acquireRetryPermit = {
                permits++
                budget.requestStatusRetry(original)
            },
            onStopped = {}
        )

        assertEquals(1, attempts)
        assertEquals(0, permits)
        assertFalse(budget.statusRetryRequested(original))
    }

    @Test
    fun resetClearsBudgetsButStaleSessionCannotRetry() {
        val budget = IncidentMonitorRecoveryBudget()

        assertTrue(budget.requestStatusRetry(original))
        assertTrue(budget.requestSeverityRetry(original))

        budget.clear()

        val newIdentity = SessionIdentity(
            ownerPrincipal = "account-b",
            generation = 61L
        )

        assertFalse(budget.statusRetryRequested(original))
        assertFalse(budget.severityRetryRequested(original))

        assertFalse(
            eligible(
                ownership = original,
                active = original,
                identity = newIdentity
            )
        )

        // A cleared budget alone is not authorization.
        // The session-identity guard must also pass.
    }

    @Test
    fun ownershipChangeAfterAuthorizationBlocksClaim() = runBlocking {
        val claims = IncidentMonitorClaims()
        val budget = IncidentMonitorRecoveryBudget()

        var identity = initialIdentity
        var active = original

        val newer = original.copy(commandId = "command-b")

        var attempts = 0
        var acquiredClaims = 0
        var stopped = 0

        executeMonitorWithOneRecovery(
            initial = "command-a",
            monitor = {
                attempts++

                if (attempts == 1) {
                    throw IllegalStateException("monitor failure")
                }

                // This represents the entry guards of the
                // production monitor, not its complete body.
                if (
                    !identity.owns(
                        original.ownerPrincipal,
                        original.sessionGeneration
                    ) ||
                    !original.canUpdatePresentation(active)
                ) {
                    return@executeMonitorWithOneRecovery
                }

                val claim = claims.claimStatus(original)
                    ?: return@executeMonitorWithOneRecovery

                try {
                    val job = currentCoroutineContext()[Job]
                        ?: error("Missing coroutine job")

                    assertTrue(claims.attachStatus(claim, job))
                    acquiredClaims++
                } finally {
                    claims.releaseStatus(claim)
                }
            },
            reload = { "command-a" },
            mayRetry = {
                eligible(original, active, identity)
            },
            acquireRetryPermit = {
                val granted = budget.requestStatusRetry(original)

                // Simulate the session and command changing
                // after retry authorization but before
                // the replacement monitor begins.
                if (granted) {
                    identity = SessionIdentity(
                        ownerPrincipal = "account-b",
                        generation = 61L
                    )
                    active = newer
                }

                granted
            },
            onStopped = { stopped++ }
        )

        assertEquals(2, attempts)
        assertEquals(0, acquiredClaims)
        assertEquals(0, stopped)
        assertFalse(claims.hasActiveStatusMonitor(original))
        assertTrue(budget.statusRetryRequested(original))
    }
}