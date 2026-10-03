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

    @Test
    fun postDrainCheckRetriesTransientFailureUntilSuccess() =
        runBlocking {

            var checks =
                0

            val delayedAttempts =
                mutableListOf<Int>()

            val hasPending =
                runTimelineOutboxPostDrainCheckSafely(
                    isActive = {
                        true
                    },

                    delayAfterFailure = {
                        attempt ->

                        delayedAttempts +=
                            attempt
                    },

                    hasPendingWork = {

                        checks +=
                            1

                        if (
                            checks < 3
                        ) {
                            error(
                                "transient Room failure"
                            )
                        }

                        true
                    }
                )

            assertTrue(
                hasPending
            )

            assertEquals(
                3,
                checks
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
    fun postDrainCheckStopsWhenSessionBecomesInactive() =
        runBlocking {

            var active =
                true

            var checks =
                0

            var delays =
                0

            val hasPending =
                runTimelineOutboxPostDrainCheckSafely(
                    isActive = {
                        active
                    },

                    delayAfterFailure = {
                        delays +=
                            1
                    },

                    hasPendingWork = {

                        checks +=
                            1

                        active =
                            false

                        error(
                            "session ended"
                        )
                    }
                )

            assertEquals(
                false,
                hasPending
            )

            assertEquals(
                1,
                checks
            )

            assertEquals(
                0,
                delays
            )
        }


    @Test
    fun postDrainCancellationEscapesWithoutRetry() =
        runBlocking {

            var delays =
                0

            var escaped =
                false

            try {

                runTimelineOutboxPostDrainCheckSafely(
                    isActive = {
                        true
                    },

                    delayAfterFailure = {
                        delays +=
                            1
                    },

                    hasPendingWork = {

                        throw CancellationException(
                            "cancel"
                        )
                    }
                )

            } catch (
                expected:
                    CancellationException
            ) {

                escaped =
                    true
            }

            assertTrue(
                escaped
            )

            assertEquals(
                0,
                delays
            )
        }}
