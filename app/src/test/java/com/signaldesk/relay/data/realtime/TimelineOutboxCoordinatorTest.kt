package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineOutboxCoordinatorTest {

    @Test
    fun transientDrainFailureRetriesUntilSuccess() =
        runBlocking {

            var drains = 0

            val delayedAttempts =
                mutableListOf<Int>()

            runTimelineOutboxDrainSafely(
                isActive = {
                    true
                },
                delayAfterFailure = {
                    attempt ->

                    delayedAttempts +=
                        attempt
                },
                drain = {
                    drains += 1

                    if (drains < 3) {
                        error(
                            "transient"
                        )
                    }
                }
            )

            assertEquals(
                3,
                drains
            )

            assertEquals(
                listOf(
                    1,
                    2
                ),
                delayedAttempts
            )
        }


    @Test
    fun inactiveSessionStopsRetryAfterFailure() =
        runBlocking {

            var active = true
            var drains = 0
            var delays = 0

            runTimelineOutboxDrainSafely(
                isActive = {
                    active
                },
                delayAfterFailure = {
                    delays += 1
                },
                drain = {
                    drains += 1
                    active = false

                    error(
                        "session changed"
                    )
                }
            )

            assertEquals(
                1,
                drains
            )

            assertEquals(
                0,
                delays
            )
        }


    @Test
    fun cancellationEscapesWithoutRetry() =
        runBlocking {

            var delays = 0
            var escaped = false

            try {
                runTimelineOutboxDrainSafely(
                    isActive = {
                        true
                    },
                    delayAfterFailure = {
                        delays += 1
                    },
                    drain = {
                        throw CancellationException(
                            "cancel"
                        )
                    }
                )
            } catch (
                expected:
                    CancellationException
            ) {
                escaped = true
            }

            assertTrue(
                escaped
            )

            assertEquals(
                0,
                delays
            )
        }
}
