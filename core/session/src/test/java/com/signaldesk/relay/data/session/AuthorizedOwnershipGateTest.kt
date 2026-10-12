package com.signaldesk.relay.data.session

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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

    @Test
    fun sessionTransitionWaitsForAuthorizedOperation() = runBlocking {
        val gate = SessionOwnershipGate()

        val operationEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseOperation = kotlinx.coroutines.CompletableDeferred<Unit>()
        val transitionCompleted = kotlinx.coroutines.CompletableDeferred<Unit>()

        val authorizedOperation = async(
            context = kotlinx.coroutines.Dispatchers.Default
        ) {
            gate.withSnapshotOwnership(
                expectedGeneration = 10L,
                currentGeneration = { 10L }
            ) {
                operationEntered.complete(Unit)
                releaseOperation.await()
                "account-a-write"
            }
        }

        operationEntered.await()

        val transition = launch(
            start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED
        ) {
            gate.withSessionTransition {
                transitionCompleted.complete(Unit)
            }
        }

        try {
            // B must not transition while A holds the ownership gate.
            assertFalse(transitionCompleted.isCompleted)
        } finally {
            releaseOperation.complete(Unit)
        }

        assertEquals(
            "account-a-write",
            authorizedOperation.await()
        )

        transition.join()

        // Once A releases the gate, B may complete its transition.
        assertTrue(transitionCompleted.isCompleted)
    }

    @Test
    fun oldAccountAOperationRejectedAfterBAndReturnToA() = runBlocking {
        val gate = SessionOwnershipGate()

        var currentGeneration = 10L
        var currentSession: SessionState = signedIn("account-a")

        val originalA = AuthenticatedOwnershipContext.capture(
            currentSession,
            currentGeneration
        )!!

        // B's transition completes before A's delayed work runs.
        gate.withSessionTransition {
            currentGeneration = 11L
            currentSession = signedIn("account-b")
        }

        var staleWrites = 0

        val rejectedUnderB = authorize(
            gate = gate,
            context = originalA,
            currentState = { currentSession },
            currentGeneration = { currentGeneration }
        ) {
            staleWrites++
            "stale-a-write"
        }

        assertNull(rejectedUnderB)
        assertEquals(0, staleWrites)

        // A signs in again, but with a fresh session generation.
        gate.withSessionTransition {
            currentGeneration = 12L
            currentSession = signedIn("account-a")
        }

        val rejectedAfterReturn = authorize(
            gate = gate,
            context = originalA,
            currentState = { currentSession },
            currentGeneration = { currentGeneration }
        ) {
            staleWrites++
            "stale-a-write"
        }

        assertNull(rejectedAfterReturn)
        assertEquals(0, staleWrites)

        // The new A session has legitimate authority.
        val freshA = AuthenticatedOwnershipContext.capture(
            currentSession,
            currentGeneration
        )!!

        val accepted = authorize(
            gate = gate,
            context = freshA,
            currentState = { currentSession },
            currentGeneration = { currentGeneration }
        ) {
            "fresh-a-write"
        }

        assertEquals("fresh-a-write", accepted)
        assertEquals(0, staleWrites)
    }
}
