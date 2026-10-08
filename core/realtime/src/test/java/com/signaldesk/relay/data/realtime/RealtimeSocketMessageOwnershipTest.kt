package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeSocketMessageOwnershipTest {

    @Test
    fun processesMessageFromCurrentSocket() {

        val socket =
            Any()

        assertTrue(
            shouldProcessRealtimeSocketMessage(
                activeSocket =
                    socket,
                callbackSocket =
                    socket
            )
        )
    }


    @Test
    fun ignoresMessageFromSupersededSocket() {

        val currentSocket =
            Any()

        val staleSocket =
            Any()

        assertFalse(
            shouldProcessRealtimeSocketMessage(
                activeSocket =
                    currentSocket,
                callbackSocket =
                    staleSocket
            )
        )
    }


    @Test
    fun ignoresMessageWhenNoSocketIsActive() {

        val staleSocket =
            Any()

        assertFalse(
            shouldProcessRealtimeSocketMessage(
                activeSocket =
                    null,
                callbackSocket =
                    staleSocket
            )
        )
    }
}
