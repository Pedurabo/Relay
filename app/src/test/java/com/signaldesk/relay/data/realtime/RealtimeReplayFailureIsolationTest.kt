package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeReplayFailureIsolationTest {

    @Test
    fun retriesNonCancellationFailureWhileConnected() =
        runBlocking {

            var connected =
                true

            var executions =
                0

            val delayedAttempts =
                mutableListOf<Int>()

            runRealtimeReplayJobSafely(
                isConnected = {
                    connected
                },
                delayAfterFailure = {
                    attempt ->

                    delayedAttempts +=
                        attempt
                }
            ) {

                executions +=
                    1

                if (
                    executions ==
                    1
                ) {
                    throw IllegalStateException(
                        "Transient replay failure"
                    )
                }
            }

            assertEquals(
                2,
                executions
            )

            assertEquals(
                listOf(
                    1
                ),
                delayedAttempts
            )
        }


    @Test
    fun stopsRetryingFailureAfterDisconnect() =
        runBlocking {

            var connected =
                true

            var executions =
                0

            var delayed =
                false

            runRealtimeReplayJobSafely(
                isConnected = {
                    connected
                },
                delayAfterFailure = {
                    delayed =
                        true
                }
            ) {

                executions +=
                    1

                connected =
                    false

                throw IllegalStateException(
                    "Replay failed as connection dropped"
                )
            }

            assertEquals(
                1,
                executions
            )

            assertFalse(
                delayed
            )
        }


    @Test
    fun cancellationStillEscapesReplayBoundary() =
        runBlocking {

            var cancellationEscaped =
                false

            var delayed =
                false

            try {

                runRealtimeReplayJobSafely(
                    isConnected = {
                        true
                    },
                    delayAfterFailure = {
                        delayed =
                            true
                    }
                ) {

                    throw CancellationException(
                        "Coordinator stopping"
                    )
                }

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
                delayed
            )
        }
}
