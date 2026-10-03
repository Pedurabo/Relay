package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeCoordinatorStopLifecycleTest {

    @Test
    fun cancellingCoordinatorCannotRestartUntilCancellationCompletes() =
        runBlocking {

            val parentJob =
                Job()

            val cleanupStarted =
                CompletableDeferred<Unit>()

            val allowCleanupToFinish =
                CompletableDeferred<Unit>()

            val scope =
                CoroutineScope(
                    parentJob +
                        Dispatchers.Unconfined
                )

            scope.launch {
                try {

                    awaitCancellation()

                } finally {

                    cleanupStarted.complete(
                        Unit
                    )

                    withContext(
                        NonCancellable
                    ) {
                        allowCleanupToFinish.await()
                    }
                }
            }

            parentJob.cancel()

            cleanupStarted.await()

            assertFalse(
                parentJob.isCompleted
            )

            assertFalse(
                shouldStartRealtimeCoordinator(
                    parentJob
                )
            )

            allowCleanupToFinish.complete(
                Unit
            )

            parentJob.join()

            assertTrue(
                parentJob.isCompleted
            )

            assertTrue(
                shouldStartRealtimeCoordinator(
                    parentJob
                )
            )
        }
}
