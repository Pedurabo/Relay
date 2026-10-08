package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeSocketTerminalStateTest {

    @Test
    fun appliesTerminalStateBeforeAnySocketBecomesActive() {

        val callbackSocket =
            Any()

        assertTrue(
            shouldApplyRealtimeSocketTerminalState(
                activeSocket =
                    null,
                callbackSocket =
                    callbackSocket
            )
        )
    }


    @Test
    fun appliesTerminalStateForCurrentSocket() {

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
    fun ignoresTerminalStateFromStaleSocket() {

        val activeSocket =
            Any()

        val staleSocket =
            Any()

        assertFalse(
            shouldApplyRealtimeSocketTerminalState(
                activeSocket =
                    activeSocket,
                callbackSocket =
                    staleSocket
            )
        )
    }
}
