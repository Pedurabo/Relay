package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeGapRecoveryFailureIsolationTest {

    @Test
    fun retriesGapRecoveryFailureWhileCoordinatorIsActive() =
        runBlocking {

            var active =
                true

            var executions =
                0

            val delayedAttempts =
                mutableListOf<Int>()

            runRealtimeGapRecoverySafely(
                isActive = {
                    active
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
                        "Gap DAO stream failed"
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
    fun stopsGapRecoveryRetryWhenCoordinatorIsNoLongerActive() =
        runBlocking {

            var active =
                true

            var executions =
                0

            var delayed =
                false

            runRealtimeGapRecoverySafely(
                isActive = {
                    active
                },
                delayAfterFailure = {
                    delayed =
                        true
                }
            ) {

                executions +=
                    1

                active =
                    false

                throw IllegalStateException(
                    "Gap recovery failed during shutdown"
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
    fun cancellationEscapesGapRecoveryBoundary() =
        runBlocking {

            var cancellationEscaped =
                false

            var delayed =
                false

            try {

                runRealtimeGapRecoverySafely(
                    isActive = {
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
