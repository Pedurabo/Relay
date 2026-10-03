package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeReplayFailureIsolationTest {

    @Test
    fun nonCancellationReplayFailureDoesNotEscapeBoundary() =
        runBlocking {

            var continuedAfterFailure =
                false

            runRealtimeReplayJobSafely {

                throw IllegalStateException(
                    "Replay transport failed"
                )
            }

            continuedAfterFailure =
                true

            assertTrue(
                continuedAfterFailure
            )
        }


    @Test
    fun cancellationStillEscapesReplayBoundary() =
        runBlocking {

            var cancellationEscaped =
                false

            var continuedAfterCancellation =
                false

            try {

                runRealtimeReplayJobSafely {

                    throw CancellationException(
                        "Coordinator stopping"
                    )
                }

                continuedAfterCancellation =
                    true

            } catch (
                error: CancellationException
            ) {

                cancellationEscaped =
                    true
            }

            assertTrue(
                cancellationEscaped
            )

            assertFalse(
                continuedAfterCancellation
            )
        }
}
