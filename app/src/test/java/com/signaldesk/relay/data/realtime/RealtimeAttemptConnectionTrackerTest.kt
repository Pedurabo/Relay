package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeAttemptConnectionTrackerTest {

    @Test
    fun staleConnectedValueDoesNotMarkFreshAttemptSuccessful() {

        val tracker =
            RealtimeAttemptConnectionTracker()

        assertFalse(
            tracker.observesSuccessfulConnection(
                RealtimeConnectionState.Connected
            )
        )
    }


    @Test
    fun connectingThenConnectedMarksAttemptSuccessful() {

        val tracker =
            RealtimeAttemptConnectionTracker()

        assertFalse(
            tracker.observesSuccessfulConnection(
                RealtimeConnectionState.Connecting
            )
        )

        assertTrue(
            tracker.observesSuccessfulConnection(
                RealtimeConnectionState.Connected
            )
        )
    }


    @Test
    fun disconnectedBeforeConnectingDoesNotAuthorizeConnected() {

        val tracker =
            RealtimeAttemptConnectionTracker()

        assertFalse(
            tracker.observesSuccessfulConnection(
                RealtimeConnectionState.Disconnected
            )
        )

        assertFalse(
            tracker.observesSuccessfulConnection(
                RealtimeConnectionState.Connected
            )
        )
    }


    @Test
    fun retryingBeforeConnectingDoesNotAuthorizeConnected() {

        val tracker =
            RealtimeAttemptConnectionTracker()

        assertFalse(
            tracker.observesSuccessfulConnection(
                RealtimeConnectionState.Retrying(
                    attempt =
                        4,
                    delayMillis =
                        8_000L
                )
            )
        )

        assertFalse(
            tracker.observesSuccessfulConnection(
                RealtimeConnectionState.Connected
            )
        )
    }
}
