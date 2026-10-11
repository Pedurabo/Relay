package com.signaldesk.relay.data.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTransitionOwnershipTest {

    private val original = SessionState.SignedIn(
        userId = "operator-1",
        userName = "Operator One",
        accessToken = "access-1",
        refreshToken = "refresh-1",
        accessTokenExpiresAt = 100000L
    )

    private fun ownsSession(
        expectedGeneration: Long,
        currentGeneration: Long,
        expectedSession: SessionState.SignedIn,
        activeSession: SessionState
    ): Boolean =
        expectedGeneration == currentGeneration &&
            expectedSession == activeSession

    @Test
    fun matchingSessionAndGenerationCanPublish() {
        assertTrue(
            ownsSession(5L, 5L, original, original)
        )
    }

    @Test
    fun signedOutSessionCannotPublish() {
        assertFalse(
            ownsSession(
                5L, 6L,
                original, SessionState.SignedOut
            )
        )
    }

    @Test
    fun replacementSessionCannotPublishOldRefresh() {
        assertFalse(
            ownsSession(
                5L, 6L,
                original,
                original.copy(
                    userId = "operator-2",
                    accessToken = "access-2"
                )
            )
        )
    }

    @Test
    fun sameCredentialsWithNewGenerationAreRejected() {
        assertFalse(
            ownsSession(5L, 6L, original, original.copy())
        )
    }

    @Test
    fun ownershipIsRecheckedAfterWaitingForGate() =
        runBlocking {
            val gate = SessionOwnershipGate()
            var generation = 5L
            var active: SessionState = original

            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()

            val transition = async(
                start = CoroutineStart.UNDISPATCHED
            ) {
                gate.withSessionTransition {
                    entered.complete(Unit)
                    release.await()
                    active = SessionState.SignedOut
                    generation = 6L
                }
            }

            try {
                entered.await()

                val publication = async(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    gate.withSessionTransition {
                        ownsSession(
                            5L, generation, original, active
                        )
                    }
                }

                release.complete(Unit)
                transition.await()

                assertFalse(publication.await())
            } finally {
                release.complete(Unit)
                transition.cancel()
            }
        }
}