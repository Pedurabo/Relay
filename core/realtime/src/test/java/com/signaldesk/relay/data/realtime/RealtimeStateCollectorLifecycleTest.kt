package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeStateCollectorLifecycleTest {

    @Test
    fun teardownDoesNotReturnUntilCollectorFinishesCancellation() =
        runBlocking {

            val cleanupStarted =
                CompletableDeferred<Unit>()

            val allowCleanupToFinish =
                CompletableDeferred<Unit>()

            val collector =
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

            val teardownReturned =
                CompletableDeferred<Unit>()

            val teardown =
                launch {
                    stopRealtimeStateCollector(
                        collector
                    )

                    teardownReturned.complete(
                        Unit
                    )
                }

            cleanupStarted.await()

            assertFalse(
                teardownReturned.isCompleted
            )

            allowCleanupToFinish.complete(
                Unit
            )

            teardown.join()

            assertTrue(
                collector.isCompleted
            )

            assertTrue(
                teardownReturned.isCompleted
            )
        }
}
