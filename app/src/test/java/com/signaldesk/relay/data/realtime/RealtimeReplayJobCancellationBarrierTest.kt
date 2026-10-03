package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeReplayJobCancellationBarrierTest {

    @Test
    fun barrierDoesNotReturnUntilReplayCleanupFinishes() =
        runBlocking {

            val cleanupStarted =
                CompletableDeferred<Unit>()

            val allowCleanupToFinish =
                CompletableDeferred<Unit>()

            val replayJob =
                launch(
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    try {

                        awaitCancellation()

                    } finally {

                        withContext(
                            NonCancellable
                        ) {
                            cleanupStarted.complete(
                                Unit
                            )

                            allowCleanupToFinish.await()
                        }
                    }
                }

            val barrierReturned =
                CompletableDeferred<Unit>()

            val barrier =
                launch {
                    stopRealtimeReplayJobs(
                        listOf(
                            replayJob
                        )
                    )

                    barrierReturned.complete(
                        Unit
                    )
                }

            cleanupStarted.await()

            assertFalse(
                barrierReturned.isCompleted
            )

            allowCleanupToFinish.complete(
                Unit
            )

            barrier.join()

            assertTrue(
                replayJob.isCompleted
            )

            assertTrue(
                barrierReturned.isCompleted
            )
        }


    @Test
    fun barrierCancelsEveryReplayBeforeWaitingForCleanup() =
        runBlocking {

            val firstCleanupStarted =
                CompletableDeferred<Unit>()

            val allowFirstCleanupToFinish =
                CompletableDeferred<Unit>()

            val firstJob =
                launch(
                    start =
                        CoroutineStart.UNDISPATCHED
                ) {
                    try {

                        awaitCancellation()

                    } finally {

                        withContext(
                            NonCancellable
                        ) {
                            firstCleanupStarted.complete(
                                Unit
                            )

                            allowFirstCleanupToFinish.await()
                        }
                    }
                }

            val secondJob =
                Job()

            val barrier =
                launch {
                    stopRealtimeReplayJobs(
                        listOf(
                            firstJob,
                            secondJob
                        )
                    )
                }

            firstCleanupStarted.await()

            assertTrue(
                secondJob.isCancelled
            )

            assertFalse(
                barrier.isCompleted
            )

            allowFirstCleanupToFinish.complete(
                Unit
            )

            barrier.join()

            assertTrue(
                firstJob.isCompleted
            )

            assertTrue(
                secondJob.isCompleted
            )
        }
}
