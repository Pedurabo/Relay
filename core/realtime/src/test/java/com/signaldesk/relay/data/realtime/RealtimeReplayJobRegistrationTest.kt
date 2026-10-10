package com.signaldesk.relay.data.realtime

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

class RealtimeReplayJobRegistrationTest {

    @Test
    fun replayJobIsRegisteredBeforeItsBodyRuns() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            var bodyObservedRegistration =
                false

            val replayJob =
                launchRegisteredRealtimeReplayJob(
                    replayJobs =
                        replayJobs,
                    incidentId =
                        "INC-1"
                ) {

                    val currentJob =
                        checkNotNull(
                            currentCoroutineContext()[
                                Job
                            ]
                        )

                    bodyObservedRegistration =
                        replayJobs[
                            "INC-1"
                        ] === currentJob
                }

            replayJob.join()

            assertTrue(
                bodyObservedRegistration
            )
        }


    @Test
    fun completedReplayRemovesItsOwnRegisteredEntry() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val replayJob =
                launchRegisteredRealtimeReplayJob(
                    replayJobs =
                        replayJobs,
                    incidentId =
                        "INC-2"
                ) {
                    // Complete immediately.
                }

            replayJob.join()

            assertTrue(
                replayJob.isCompleted
            )

            assertFalse(
                replayJobs.containsKey(
                    "INC-2"
                )
            )
        }


    @Test
    fun returnedJobIsTheRegisteredJobWhileActive() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val release =
                kotlinx.coroutines.CompletableDeferred<Unit>()

            val replayJob =
                launchRegisteredRealtimeReplayJob(
                    replayJobs =
                        replayJobs,
                    incidentId =
                        "INC-3"
                ) {
                    release.await()
                }

            assertSame(
                replayJob,
                replayJobs[
                    "INC-3"
                ]
            )

            release.complete(
                Unit
            )

            replayJob.join()
        }

    @Test
    fun reappearingGapReplacementSurvivesStaleReplayCleanup() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val incidentId =
                "incident-A"

            val firstBodyStarted =
                CompletableDeferred<Unit>()

            val allowFirstBodyToFinish =
                CompletableDeferred<Unit>()

            /*
             * First lifecycle owns A through the real registered-job helper.
             *
             * Its body stays alive until after a replacement has taken the
             * registry entry, forcing its finally cleanup to become stale.
             */
            val firstOwner =
                launchRegisteredRealtimeReplayJob(
                    replayJobs =
                        replayJobs,
                    incidentId =
                        incidentId
                ) {
                    firstBodyStarted.complete(Unit)

                    allowFirstBodyToFinish.await()
                }

            firstBodyStarted.await()

            assertTrue(
                replayJobs[incidentId] ===
                    firstOwner
            )

            /*
             * A resolves and then immediately reappears as fresh work.
             *
             * The replacement registers before the original job's finally
             * block is allowed to run.
             */
            val replacementBodyStarted =
                CompletableDeferred<Unit>()

            val replacementOwner =
                launchRegisteredRealtimeReplayJob(
                    replayJobs =
                        replayJobs,
                    incidentId =
                        incidentId
                ) {
                    replacementBodyStarted.complete(Unit)

                    awaitCancellation()
                }

            replacementBodyStarted.await()

            assertTrue(
                replacementOwner !==
                    firstOwner
            )

            assertTrue(
                replayJobs[incidentId] ===
                    replacementOwner
            )

            /*
             * Now release the first lifecycle.
             *
             * Its real finally block calls removeReplayJobIfCurrent with
             * firstOwner. Because the registry belongs to replacementOwner,
             * stale cleanup must not remove the replacement.
             */
            allowFirstBodyToFinish.complete(Unit)

            firstOwner.join()

            assertFalse(
                firstOwner.isActive
            )

            assertTrue(
                replacementOwner.isActive
            )

            assertTrue(
                replayJobs[incidentId] ===
                    replacementOwner
            )

            /*
             * The replacement still owns A until its own lifecycle ends.
             */
            replacementOwner.cancel()
            replacementOwner.join()

            assertFalse(
                replayJobs.containsKey(
                    incidentId
                )
            )
        }

    @Test
    fun disconnectCancelsReplacementWhileStaleCleanupFinishes() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val incidentId =
                "incident-A"

            val firstBodyStarted =
                CompletableDeferred<Unit>()

            val finishFirstBody =
                CompletableDeferred<Unit>()

            val firstBodyFinallyEntered =
                CompletableDeferred<Unit>()

            val allowFirstBodyFinallyToExit =
                CompletableDeferred<Unit>()

            /*
             * First lifecycle owns A through the real registration helper.
             *
             * Its body can finish normally, but its own finally is held open
             * so the helper's outer stale cleanup has not executed yet.
             */
            val firstOwner =
                launchRegisteredRealtimeReplayJob(
                    replayJobs =
                        replayJobs,
                    incidentId =
                        incidentId
                ) {
                    firstBodyStarted.complete(Unit)

                    try {
                        finishFirstBody.await()
                    }
                    finally {
                        firstBodyFinallyEntered.complete(Unit)

                        allowFirstBodyFinallyToExit.await()
                    }
                }

            firstBodyStarted.await()

            assertTrue(
                replayJobs[incidentId] ===
                    firstOwner
            )

            /*
             * A reappears/restarts before the original lifecycle finishes.
             * Registration immediately transfers ownership to replacement.
             */
            val replacementBodyStarted =
                CompletableDeferred<Unit>()

            val replacementOwner =
                launchRegisteredRealtimeReplayJob(
                    replayJobs =
                        replayJobs,
                    incidentId =
                        incidentId
                ) {
                    replacementBodyStarted.complete(Unit)

                    awaitCancellation()
                }

            replacementBodyStarted.await()

            assertTrue(
                replacementOwner !==
                    firstOwner
            )

            assertTrue(
                replayJobs[incidentId] ===
                    replacementOwner
            )

            /*
             * Let the original lifecycle begin finishing, but stop it before
             * the registration helper's outer finally cleanup can run.
             */
            finishFirstBody.complete(Unit)

            firstBodyFinallyEntered.await()

            assertTrue(
                firstOwner.isActive
            )

            assertTrue(
                replayJobs[incidentId] ===
                    replacementOwner
            )

            /*
             * Disconnect owns the current replacement:
             *
             * - registry is cleared,
             * - replacement is cancelled,
             * - cancellation is joined before execution returns.
             */
            val disconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            incidentId
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val disconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        disconnectReconciliation
                )

            assertTrue(
                disconnectExecution
                    .disconnectedReplayCount ==
                    1
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                replacementOwner.isCancelled
            )

            assertFalse(
                replacementOwner.isActive
            )

            /*
             * Now allow the stale first owner to leave its body.
             *
             * launchRegisteredRealtimeReplayJob reaches its real outer
             * finally and calls removeReplayJobIfCurrent(firstOwner).
             * The registry was already cleared by disconnect, so stale
             * cleanup must remain harmless.
             */
            allowFirstBodyFinallyToExit.complete(Unit)

            firstOwner.join()

            assertFalse(
                firstOwner.isActive
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                replacementOwner.isCancelled
            )
        }
}
