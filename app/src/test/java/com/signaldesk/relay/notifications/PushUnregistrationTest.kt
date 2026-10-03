package com.signaldesk.relay.notifications

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushUnregistrationTest {

    @Test
    fun unregisterAttemptRunsExactlyOnce() =
        runBlocking {

            var calls =
                0

            attemptPushUnregistration {

                calls +=
                    1
            }

            assertEquals(
                1,
                calls
            )
        }


    @Test
    fun unregisterTransportFailureDoesNotEscape() =
        runBlocking {

            var continuedAfterFailure =
                false

            attemptPushUnregistration {

                throw IllegalStateException(
                    "Network unavailable"
                )
            }

            continuedAfterFailure =
                true

            assertTrue(
                continuedAfterFailure
            )
        }
}
