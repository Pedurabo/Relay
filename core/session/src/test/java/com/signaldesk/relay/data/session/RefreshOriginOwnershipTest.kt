package com.signaldesk.relay.data.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshOriginOwnershipTest {

    private fun session(
        userId: String = "user-1",
        token: String = "access-1"
    ) = SessionState.SignedIn(
        userId = userId,
        userName = userId,
        accessToken = token,
        refreshToken = "refresh-$token",
        accessTokenExpiresAt = 100000L
    )

    private fun authorized(
        original: SessionState.SignedIn,
        active: SessionState,
        originalGeneration: Long = 1L,
        activeGeneration: Long = 1L
    ) = isRefreshOriginCurrent(
        originatingGeneration = originalGeneration,
        currentGeneration = activeGeneration,
        originatingSession = original,
        activeSession = active
    )

    @Test
    fun unchangedSessionRemainsAuthorized() {
        val original = session()

        assertTrue(
            authorized(original, original)
        )
    }

    @Test
    fun signOutInvalidatesPendingRefresh() {
        assertFalse(
            authorized(
                session(),
                SessionState.SignedOut,
                activeGeneration = 2L
            )
        )
    }

    @Test
    fun replacementAccountInvalidatesPendingRefresh() {
        assertFalse(
            authorized(
                session(),
                session(
                    userId = "user-2",
                    token = "access-2"
                ),
                activeGeneration = 2L
            )
        )
    }

    @Test
    fun sameCredentialsNewGenerationIsRejected() {
        val original = session()

        assertFalse(
            authorized(
                original,
                original.copy(),
                activeGeneration = 3L
            )
        )
    }

    @Test
    fun changedCredentialsWithoutGenerationChangeRejected() {
        assertFalse(
            authorized(
                session(),
                session(token = "access-2")
            )
        )
    }

    @Test
    fun delayedRefreshSuccessCannotPassAfterSignOut() =
        runBlocking {

            val origin = session()
            val networkStarted = CompletableDeferred<Unit>()
            val releaseNetwork = CompletableDeferred<Unit>()

            var generation = 1L
            var active: SessionState = origin

            val request = async(
                start = CoroutineStart.UNDISPATCHED
            ) {
                networkStarted.complete(Unit)
                releaseNetwork.await()

                authorized(
                    original = origin,
                    active = active,
                    activeGeneration = generation
                )
            }

            networkStarted.await()

            active = SessionState.SignedOut
            generation = 2L

            releaseNetwork.complete(Unit)

            assertFalse(request.await())
        }

    @Test
    fun delayedRefreshFailureCannotAffectReplacement() =
        runBlocking {

            val origin = session()
            val networkStarted = CompletableDeferred<Unit>()
            val releaseNetwork = CompletableDeferred<Unit>()

            var generation = 1L
            var active: SessionState = origin

            val request = async(
                start = CoroutineStart.UNDISPATCHED
            ) {
                networkStarted.complete(Unit)
                releaseNetwork.await()

                authorized(
                    original = origin,
                    active = active,
                    activeGeneration = generation
                )
            }

            networkStarted.await()

            active = session(
                userId = "user-2",
                token = "access-2"
            )

            generation = 2L

            releaseNetwork.complete(Unit)

            assertFalse(request.await())
        }
}