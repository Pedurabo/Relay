package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeSocketAttemptTerminalOwnershipTest {

    @Test
    fun acceptsTerminalCallbackFromCurrentAttempt() {

        assertTrue(
            isCurrentRealtimeAttempt(
                activeAttemptId =
                    12L,
                callbackAttemptId =
                    12L
            )
        )
    }


    @Test
    fun rejectsTerminalCallbackFromSupersededAttempt() {

        assertFalse(
            isCurrentRealtimeAttempt(
                activeAttemptId =
                    13L,
                callbackAttemptId =
                    12L
            )
        )
    }


    @Test
    fun rejectsTerminalCallbackAfterAttemptRelease() {

        assertFalse(
            isCurrentRealtimeAttempt(
                activeAttemptId =
                    0L,
                callbackAttemptId =
                    12L
            )
        )
    }


    @Test
    fun currentPreOpenFailureRemainsEligible() {

        val callbackSocket =
            Any()

        val shouldApply =
            isCurrentRealtimeAttempt(
                activeAttemptId =
                    12L,
                callbackAttemptId =
                    12L
            ) &&
                shouldApplyRealtimeSocketTerminalState(
                    activeSocket =
                        null,
                    callbackSocket =
                        callbackSocket
                )

        assertTrue(
            shouldApply
        )
    }


    @Test
    fun stalePreOpenFailureIsRejected() {

        val callbackSocket =
            Any()

        val shouldApply =
            isCurrentRealtimeAttempt(
                activeAttemptId =
                    13L,
                callbackAttemptId =
                    12L
            ) &&
                shouldApplyRealtimeSocketTerminalState(
                    activeSocket =
                        null,
                    callbackSocket =
                        callbackSocket
                )

        assertFalse(
            shouldApply
        )
    }
}
