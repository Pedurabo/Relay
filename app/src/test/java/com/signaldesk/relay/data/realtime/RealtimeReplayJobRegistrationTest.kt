package com.signaldesk.relay.data.realtime

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
