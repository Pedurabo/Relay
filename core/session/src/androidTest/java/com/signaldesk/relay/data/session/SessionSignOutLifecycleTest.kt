package com.signaldesk.relay.data.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionSignOutLifecycleTest {

    @Before
    fun setUp() {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        SessionManager.initialize(context)

        // Reset before each sequential instrumentation test.
        runBlocking { SessionManager.signOutCoordinated() }
    }

    @Test
    fun signOutSurvivesCancellationOfInitiatingScope() =
        runBlocking {

            val generation =
                SessionManager.sessionGeneration

            val initialState =
                SessionManager.sessionState.value

            val snapshotEntered =
                CompletableDeferred<Unit>()

            val releaseSnapshot =
                CompletableDeferred<Unit>()

            val completed =
                CompletableDeferred<Boolean>()

            val uiScope = CoroutineScope(
                SupervisorJob() + Dispatchers.Default
            )

            val snapshot = async(
                start = CoroutineStart.UNDISPATCHED
            ) {
                SessionManager.withAuthorizedSnapshot(
                    expectedGeneration = generation
                ) {
                    snapshotEntered.complete(Unit)
                    releaseSnapshot.await()
                }
            }

            try {
                snapshotEntered.await()

                val submitted =
                    CompletableDeferred<Job>()

                uiScope.launch {
                    val job =
                        SessionManager
                            .requestSignOutIfUnchanged(
                                expectedGeneration = generation,
                                expectedSession = initialState
                            ) { result ->
                                completed.complete(result)
                            }

                    submitted.complete(job)
                }

                val signOutJob = submitted.await()

                // Simulate the initiating ViewModel being cleared.
                uiScope.cancel()

                assertFalse(signOutJob.isCompleted)

                releaseSnapshot.complete(Unit)

                snapshot.await()
                signOutJob.join()

                assertTrue(completed.await())
                assertTrue(signOutJob.isCompleted)

                assertTrue(
                    SessionManager.sessionState.value ==
                        SessionState.SignedOut
                )

                assertEquals(
                    generation + 1L,
                    SessionManager.sessionGeneration
                )
            } finally {
                releaseSnapshot.complete(Unit)
                snapshot.cancel()
                uiScope.cancel()
            }
        }

    @Test
    fun obsoleteSignOutCannotAdvanceNewGeneration() =
        runBlocking {

            val oldGeneration =
                SessionManager.sessionGeneration

            val oldSession =
                SessionManager.sessionState.value

            // A different coordinated transition supersedes
            // the generation captured by the old request.
            SessionManager.signOutCoordinated()

            val currentGeneration =
                SessionManager.sessionGeneration

            val completed =
                CompletableDeferred<Boolean>()

            val job =
                SessionManager.requestSignOutIfUnchanged(
                    expectedGeneration = oldGeneration,
                    expectedSession = oldSession
                ) { result ->
                    completed.complete(result)
                }

            job.join()

            assertFalse(completed.await())

            assertEquals(
                currentGeneration,
                SessionManager.sessionGeneration
            )

            assertEquals(
                SessionState.SignedOut,
                SessionManager.sessionState.value
            )
        }

    @Test
    fun queuedSignOutFollowsProtectedSnapshot() =
        runBlocking {

            val origin =
                SessionManager.sessionGeneration

            val active =
                SessionManager.sessionState.value

            val entered =
                CompletableDeferred<Unit>()

            val release =
                CompletableDeferred<Unit>()

            val completed =
                CompletableDeferred<Boolean>()

            val snapshot = async(
                start = CoroutineStart.UNDISPATCHED
            ) {
                SessionManager.withAuthorizedSnapshot(
                    expectedGeneration = origin
                ) {
                    entered.complete(Unit)
                    release.await()
                }
            }

            try {
                entered.await()

                val job =
                    SessionManager.requestSignOutIfUnchanged(
                        expectedGeneration = origin,
                        expectedSession = active
                    ) { result ->
                        completed.complete(result)
                    }

                assertFalse(job.isCompleted)

                release.complete(Unit)

                snapshot.await()
                job.join()

                assertTrue(completed.await())

                assertEquals(
                    origin + 1L,
                    SessionManager.sessionGeneration
                )
            } finally {
                release.complete(Unit)
                snapshot.cancel()
            }
        }
}