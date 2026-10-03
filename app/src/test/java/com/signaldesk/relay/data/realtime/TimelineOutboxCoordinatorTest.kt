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
        }
    @Test
    fun sessionSnapshotChangeStopsDrainRetryLoop() =
        runBlocking {

            var currentOwner =
                "operator-a"

            var currentToken =
                "token-a"

            val expectedOwner =
                "operator-a"

            val expectedToken =
                "token-a"

            var drains =
                0

            var delays =
                0

            runTimelineOutboxDrainSafely(
                isActive = {

                    currentOwner ==
                        expectedOwner &&
                    currentToken ==
                        expectedToken
                },

                delayAfterFailure = {
                    delays +=
                        1
                },

                drain = {

                    drains +=
                        1

                    /*
                     * Simulate either account switch or token refresh
                     * during a failed delivery attempt.
                     */
                    currentToken =
                        "token-b"

                    error(
                        "transport failed"
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
    fun coolingDownTimelineEntryDoesNotBlockLaterEntry() {

        val eligible =
            OutboxDrainPlanner
                .eligibleCommandIds(
                    commandIds =
                        listOf(
                            "ENTRY-A",
                            "ENTRY-B"
                        ),

                    retryNotBeforeMillis =
                        mapOf(
                            "ENTRY-A" to
                                20_000L
                        ),

                    nowMillis =
                        10_000L
                )

        assertEquals(
            listOf(
                "ENTRY-B"
            ),
            eligible
        )
    }


    @Test
    fun timelineDrainSleepsUntilNearestEntryRetry() {

        val nearestRetry =
            OutboxDrainPlanner
                .nearestRetryAtMillis(
                    commandIds =
                        listOf(
                            "ENTRY-A",
                            "ENTRY-B"
                        ),

                    retryNotBeforeMillis =
                        mapOf(
                            "ENTRY-A" to
                                30_000L,
                            "ENTRY-B" to
                                14_000L
                        ),

                    nowMillis =
                        10_000L
                )

        assertEquals(
            14_000L,
            nearestRetry
        )
    }}
