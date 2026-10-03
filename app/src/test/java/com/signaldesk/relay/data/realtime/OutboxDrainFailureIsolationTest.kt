package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxDrainFailureIsolationTest {

    @Test
    fun retriesUnexpectedDrainFailureWhileOutboxIsActive() =
        runBlocking {

            var active =
                true

            var executions =
                0

            val delayedAttempts =
                mutableListOf<Int>()

            runOutboxDrainSafely(
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
                        "Temporary Room failure"
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
    fun stopsRetryingWhenOutboxIsNoLongerActive() =
        runBlocking {

            var active =
                true

            var executions =
                0

            var delayed =
                false

            runOutboxDrainSafely(
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
                    "Drain failed during sign-out"
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
    fun cancellationEscapesDrainFailureBoundary() =
        runBlocking {

            var cancellationEscaped =
                false

            var delayed =
                false

            try {

                runOutboxDrainSafely(
                    isActive = {
                        true
                    },
                    delayAfterFailure = {
                        delayed =
                            true
                    }
                ) {

                    throw CancellationException(
                        "Outbox cancelled"
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
