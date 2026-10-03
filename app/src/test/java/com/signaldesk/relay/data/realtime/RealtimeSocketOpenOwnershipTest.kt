package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeSocketOpenOwnershipTest {

    @Test
    fun acceptsOpenFromCurrentAttempt() {

        assertTrue(
            shouldAcceptRealtimeSocketOpen(
                activeAttemptId =
                    7L,
                callbackAttemptId =
                    7L
            )
        )
    }


    @Test
    fun ignoresOpenFromSupersededAttempt() {

        assertFalse(
            shouldAcceptRealtimeSocketOpen(
                activeAttemptId =
                    8L,
                callbackAttemptId =
                    7L
            )
        )
    }


    @Test
    fun ignoresOpenAfterAttemptOwnershipIsReleased() {

        assertFalse(
            shouldAcceptRealtimeSocketOpen(
                activeAttemptId =
                    0L,
                callbackAttemptId =
                    7L
            )
        )
    }
}
