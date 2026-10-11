package com.signaldesk.relay.data.session

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests of the shared-gate ownership contract.
 *
 * SessionManager uses this same gate. These tests avoid
 * mutating process-global SessionManager state.
 */
class AuthorizedOwnershipGateTest {

    private fun signedIn(owner: String) =
        SessionState.SignedIn(
            userId = owner,
            userName = "Test User",
            accessToken = "access",
            refreshToken = "refresh",
            accessTokenExpiresAt = 100000L
        )

    private suspend fun authorize(
        gate: SessionOwnershipGate,
        context: AuthenticatedOwnershipContext,
        currentState: () -> SessionState,
        currentGeneration: () -> Long,
        operation: suspend () -> String
    ): String? =
        gate.withSnapshotOwnership(
            expectedGeneration = context.sessionGeneration,
            currentGeneration = currentGeneration
        ) {
            if (
                context.matches(
                    currentState(),
                    currentGeneration()
                )
            ) {
                operation()
            } else {
                null
            }
        }

    @Test
    fun sameOwnerAndGenerationAllowOperation() = runBlocking {
        val gate = SessionOwnershipGate()
        val context =
            AuthenticatedOwnershipContext.capture(
                signedIn("account-a"),
                7L
            )!!

        var executed = false

        val result = authorize(
            gate,
            context,
            { signedIn("account-a") },
            { 7L }
        ) {
            executed = true
            "committed"
        }

        assertEquals("committed", result)
        assertTrue(executed)
    }

    @Test
    fun accountReplacementRejectsOperation() = runBlocking {
        val gate = SessionOwnershipGate()
        val context =
            AuthenticatedOwnershipContext.capture(
                signedIn("account-a"),
                7L
            )!!

        var executed = false

        val result = authorize(
            gate,
            context,
            { signedIn("account-b") },
            { 7L }
        ) {
            executed = true
            "committed"
        }

        assertNull(result)
        assertFalse(executed)
    }

    @Test
    fun newGenerationRejectsEvenSameOwner() = runBlocking {
        val gate = SessionOwnershipGate()
        val context =
            AuthenticatedOwnershipContext.capture(
                signedIn("account-a"),
                7L
            )!!

        var executed = false

        val result = authorize(
            gate,
            context,
            { signedIn("account-a") },
            { 8L }
        ) {
            executed = true
            "committed"
        }

        assertNull(result)
        assertFalse(executed)
    }

    @Test
    fun signedOutStateRejectsOperation() = runBlocking {
        val gate = SessionOwnershipGate()
        val context =
            AuthenticatedOwnershipContext.capture(
                signedIn("account-a"),
                7L
            )!!

        var executed = false

        val result = authorize(
            gate,
            context,
            { SessionState.SignedOut },
            { 7L }
        ) {
            executed = true
            "committed"
        }

        assertNull(result)
        assertFalse(executed)
    }
}
