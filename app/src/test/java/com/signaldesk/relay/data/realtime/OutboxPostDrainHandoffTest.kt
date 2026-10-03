package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxPostDrainHandoffTest {

    @Test
    fun retriesTransientPendingCheckFailure() =
        runBlocking {

            var active =
                true

            var checks =
                0

            val delayedAttempts =
                mutableListOf<Int>()

            val hasPending =
                runOutboxPostDrainCheckSafely(
                    isActive = {
                        active
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
                            checks ==
                            1
                        ) {
                            throw IllegalStateException(
                                "Temporary Room read failure"
                            )
                        }

                        true
                    }
                )

            assertTrue(
                hasPending
            )

            assertEquals(
                2,
                checks
            )

            assertEquals(
                listOf(
                    1
                ),
                delayedAttempts
            )
        }


    @Test
    fun stopsPendingCheckRetryAfterSignOut() =
        runBlocking {

            var active =
                true

            var checks =
                0

            var delayed =
                false

            val hasPending =
                runOutboxPostDrainCheckSafely(
                    isActive = {
                        active
                    },
                    delayAfterFailure = {
                        delayed =
                            true
                    },
                    hasPendingWork = {

                        checks +=
                            1

                        active =
                            false

                        throw IllegalStateException(
                            "Session ended during handoff"
                        )
                    }
                )

            assertFalse(
                hasPending
            )

            assertEquals(
                1,
                checks
            )

            assertFalse(
                delayed
            )
        }


    @Test
    fun cancellationEscapesPostDrainCheck() =
        runBlocking {

            var escaped =
                false

            var delayed =
                false

            try {

                runOutboxPostDrainCheckSafely(
                    isActive = {
                        true
                    },
                    delayAfterFailure = {
                        delayed =
                            true
                    },
                    hasPendingWork = {

                        throw CancellationException(
                            "Post-drain check cancelled"
                        )
                    }
                )

            } catch (
                error: CancellationException
            ) {

                escaped =
                    true
            }

            assertTrue(
                escaped
            )

            assertFalse(
                delayed
            )
        }
}
