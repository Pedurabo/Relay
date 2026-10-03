package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeSocketSessionInvalidationTest {

    @Test
    fun allowsInvalidationBeforeAnySocketBecomesActive() {

        val failingSocket =
            Any()

        assertTrue(
            shouldApplyRealtimeSocketTerminalState(
                activeSocket =
                    null,
                callbackSocket =
                    failingSocket
            )
        )
    }


    @Test
    fun allowsInvalidationFromCurrentSocket() {

        val socket =
            Any()

        assertTrue(
            shouldApplyRealtimeSocketTerminalState(
                activeSocket =
                    socket,
                callbackSocket =
                    socket
            )
        )
    }


    @Test
    fun ignoresInvalidationFromSupersededSocket() {

        val currentSocket =
            Any()

        val staleSocket =
            Any()

        assertFalse(
            shouldApplyRealtimeSocketTerminalState(
                activeSocket =
                    currentSocket,
                callbackSocket =
                    staleSocket
            )
        )
    }
}
