package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxStartupRecoveryTest {

    @Test
    fun retriesTransientResetFailureUntilStartupRecovers() =
        runBlocking {

            var resetAttempts =
                0

            val delayedAttempts =
                mutableListOf<Int>()

            val resetCount =
                runOutboxStartupResetSafely(
                    delayAfterFailure = {
                        attempt ->

                        delayedAttempts +=
                            attempt
                    },
                    resetInFlight = {

                        resetAttempts +=
                            1

                        if (
                            resetAttempts ==
                            1
                        ) {
                            throw IllegalStateException(
                                "Temporary database failure"
                            )
                        }

                        3
                    }
                )

            assertEquals(
                2,
                resetAttempts
            )

            assertEquals(
                listOf(
                    1
                ),
                delayedAttempts
            )

            assertEquals(
                3,
                resetCount
            )
        }


    @Test
    fun successfulStartupResetDoesNotDelay() =
        runBlocking {

            var delayed =
                false

            val resetCount =
                runOutboxStartupResetSafely(
                    delayAfterFailure = {
                        delayed =
                            true
                    },
                    resetInFlight = {
                        2
                    }
                )

            assertEquals(
                2,
                resetCount
            )

            assertFalse(
                delayed
            )
        }


    @Test
    fun cancellationEscapesStartupRecovery() =
        runBlocking {

            var cancellationEscaped =
                false

            var delayed =
                false

            try {

                runOutboxStartupResetSafely(
                    delayAfterFailure = {
                        delayed =
                            true
                    },
                    resetInFlight = {

                        throw CancellationException(
                            "Startup cancelled"
                        )
                    }
                )

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
