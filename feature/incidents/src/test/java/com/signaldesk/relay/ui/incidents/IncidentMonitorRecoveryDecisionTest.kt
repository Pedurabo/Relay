package com.signaldesk.relay.ui.incidents

import com.signaldesk.relay.data.session.SessionIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class IncidentMonitorRecoveryDecisionTest {

    private val ownership = IncidentMonitorOwnership(
        ownerPrincipal = "account-a",
        sessionGeneration = 60L,
        commandId = "command-a"
    )

    private val identity = SessionIdentity(
        ownerPrincipal = "account-a",
        generation = 60L
    )

    private fun decision(
        pending: IncidentMonitorOwnership? = ownership,
        active: IncidentMonitorOwnership? = ownership,
        session: SessionIdentity = identity,
        pendingExists: Boolean = true,
        monitorActive: Boolean = false,
        unexpectedFailure: Boolean = true,
        retryAlreadyRequested: Boolean = false
    ): IncidentMonitorRecoveryDecision =
        decideIncidentMonitorRecovery(
            pendingOwnership = pending,
            activeOwnership = active,
            currentIdentity = session,
            pendingStillPresent = pendingExists,
            monitorActive = monitorActive,
            unexpectedFailure = unexpectedFailure,
            retryAlreadyRequested = retryAlreadyRequested
        )

    @Test
    fun unexpectedFailureOfCurrentPendingCommandAllowsRetry() {
        assertEquals(
            IncidentMonitorRecoveryDecision.RETRY,
            decision()
        )
    }

    @Test
    fun normalCompletionDoesNotRetry() {
        assertEquals(
            IncidentMonitorRecoveryDecision.DO_NOT_RETRY,
            decision(unexpectedFailure = false)
        )
    }

    @Test
    fun intentionalCancellationDoesNotRetry() {
        assertEquals(
            IncidentMonitorRecoveryDecision.DO_NOT_RETRY,
            decision(unexpectedFailure = false)
        )
    }

    @Test
    fun removedPendingCommandDoesNotRetry() {
        assertEquals(
            IncidentMonitorRecoveryDecision.DO_NOT_RETRY,
            decision(pendingExists = false)
        )
    }

    @Test
    fun alreadyActiveMonitorDoesNotRetry() {
        assertEquals(
            IncidentMonitorRecoveryDecision.DO_NOT_RETRY,
            decision(monitorActive = true)
        )
    }

    @Test
    fun differentAccountDoesNotRetry() {
        assertEquals(
            IncidentMonitorRecoveryDecision.DO_NOT_RETRY,
            decision(
                session = SessionIdentity(
                    ownerPrincipal = "account-b",
                    generation = 61L
                )
            )
        )
    }

    @Test
    fun sameAccountNewGenerationDoesNotRetryOldCommand() {
        assertEquals(
            IncidentMonitorRecoveryDecision.DO_NOT_RETRY,
            decision(
                session = SessionIdentity(
                    ownerPrincipal = "account-a",
                    generation = 62L
                )
            )
        )
    }

    @Test
    fun signedOutSessionDoesNotRetry() {
        assertEquals(
            IncidentMonitorRecoveryDecision.DO_NOT_RETRY,
            decision(
                session = SessionIdentity(
                    ownerPrincipal = null,
                    generation = 61L
                )
            )
        )
    }

    @Test
    fun newerCommandBlocksOldCommandRetry() {
        val newerCommand = ownership.copy(
            commandId = "command-b"
        )

        assertEquals(
            IncidentMonitorRecoveryDecision.DO_NOT_RETRY,
            decision(active = newerCommand)
        )
    }

    @Test
    fun previouslyRequestedRetryDoesNotRepeat() {
        assertEquals(
            IncidentMonitorRecoveryDecision.DO_NOT_RETRY,
            decision(retryAlreadyRequested = true)
        )
    }

    @Test
    fun missingOwnershipDoesNotRetry() {
        assertEquals(
            IncidentMonitorRecoveryDecision.DO_NOT_RETRY,
            decision(pending = null)
        )
    }
}