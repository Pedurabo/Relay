package com.signaldesk.relay.data.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionLoginIntentIntegrationTest {

    private fun credentials(
        userId: String = "login-test-1"
    ) = AuthenticatedSession(
        userId = userId,
        userName = userId,
        accessToken = "access-$userId",
        refreshToken = "refresh-$userId",
        accessTokenExpiresAt = 2000000000000L
    )

    @Before
    fun setUp() {
        SessionManager.initialize(
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        )

        runBlocking { SessionManager.signOutCoordinated() }
    }

    @After
    fun tearDown() {
        runBlocking { SessionManager.signOutCoordinated() }
    }

    @Test
    fun currentLoginPublishesCredentials() = runBlocking {

        val generation = SessionManager.sessionGeneration
        val revision =
            SessionManager.captureLoginAttemptRevision()

        val published =
            SessionManager.establishSessionIfSignedOutCoordinated(
                expectedGeneration = generation,
                expectedLoginRevision = revision,
                session = credentials()
            )

        assertTrue(published)

        val active = SessionManager.sessionState.value
        assertTrue(active is SessionState.SignedIn)

        assertEquals(
            "login-test-1",
            (active as SessionState.SignedIn).userId
        )
    }

    @Test
    fun signOutIntentRejectsOlderLoginResponse() = runBlocking {

        val originGeneration =
            SessionManager.sessionGeneration

        val originRevision =
            SessionManager.captureLoginAttemptRevision()

        val originState =
            SessionManager.sessionState.value

        val completed = CompletableDeferred<Boolean>()

        val job = SessionManager.requestSignOutIfUnchanged(
            expectedGeneration = originGeneration,
            expectedSession = originState
        ) { result ->
            completed.complete(result)
        }

        job.join()
        assertTrue(completed.await())

        val published =
            SessionManager.establishSessionIfSignedOutCoordinated(
                expectedGeneration = originGeneration,
                expectedLoginRevision = originRevision,
                session = credentials()
            )

        assertFalse(published)
        assertEquals(
            SessionState.SignedOut,
            SessionManager.sessionState.value
        )
    }

    @Test
    fun signOutInvalidatesLoginWhileSnapshotHoldsGate() =
        runBlocking {

            val generation =
                SessionManager.sessionGeneration

            val revision =
                SessionManager.captureLoginAttemptRevision()

            val originalState =
                SessionManager.sessionState.value

            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val completed = CompletableDeferred<Boolean>()

            val snapshot = async(
                start = CoroutineStart.UNDISPATCHED
            ) {
                SessionManager.withAuthorizedSnapshot(
                    expectedGeneration = generation
                ) {
                    entered.complete(Unit)
                    release.await()
                }
            }

            try {
                entered.await()

                val signOutJob =
                    SessionManager.requestSignOutIfUnchanged(
                        expectedGeneration = generation,
                        expectedSession = originalState
                    ) { result ->
                        completed.complete(result)
                    }

                assertFalse(signOutJob.isCompleted)

                release.complete(Unit)
                snapshot.await()
                signOutJob.join()

                assertTrue(completed.await())

                val published =
                    SessionManager.establishSessionIfSignedOutCoordinated(
                        expectedGeneration = generation,
                        expectedLoginRevision = revision,
                        session = credentials()
                    )

                assertFalse(published)
                assertEquals(
                    SessionState.SignedOut,
                    SessionManager.sessionState.value
                )
            } finally {
                release.complete(Unit)
                snapshot.cancel()
            }
        }

    @Test
    fun obsoleteLoginCannotReplaceNewerSession() =
        runBlocking {

            val oldGeneration =
                SessionManager.sessionGeneration

            val oldRevision =
                SessionManager.captureLoginAttemptRevision()

            SessionManager.signOutCoordinated()

            val newGeneration =
                SessionManager.sessionGeneration

            val newRevision =
                SessionManager.captureLoginAttemptRevision()

            val freshPublished =
                SessionManager.establishSessionIfSignedOutCoordinated(
                    expectedGeneration = newGeneration,
                    expectedLoginRevision = newRevision,
                    session = credentials("login-test-2")
                )

            assertTrue(freshPublished)

            val stalePublished =
                SessionManager.establishSessionIfSignedOutCoordinated(
                    expectedGeneration = oldGeneration,
                    expectedLoginRevision = oldRevision,
                    session = credentials("login-test-1")
                )

            assertFalse(stalePublished)

            val active = SessionManager.sessionState.value
            assertTrue(active is SessionState.SignedIn)

            assertEquals(
                "login-test-2",
                (active as SessionState.SignedIn).userId
            )
        }
}