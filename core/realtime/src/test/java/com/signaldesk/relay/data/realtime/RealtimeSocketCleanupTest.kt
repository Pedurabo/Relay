package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeSocketCleanupTest {

    @Test
    fun currentCollectorOwnsItsActiveSocket() {

        val socket =
            Any()

        assertTrue(
            shouldClearRealtimeSocketOnCollectorClose(
                activeSocket =
                    socket,
                callbackSocket =
                    socket,
                activeAttemptId =
                    7L,
                callbackAttemptId =
                    7L
            )
        )
    }

    @Test
    fun staleCollectorCannotClearNewerSocket() {

        val oldSocket =
            Any()

        val currentSocket =
            Any()

        assertFalse(
            shouldClearRealtimeSocketOnCollectorClose(
                activeSocket =
                    currentSocket,
                callbackSocket =
                    oldSocket,
                activeAttemptId =
                    8L,
                callbackAttemptId =
                    7L
            )
        )
    }

    @Test
    fun staleAttemptCannotClearCurrentSocketEvenWithSameObject() {

        val socket =
            Any()

        assertFalse(
            shouldClearRealtimeSocketOnCollectorClose(
                activeSocket =
                    socket,
                callbackSocket =
                    socket,
                activeAttemptId =
                    8L,
                callbackAttemptId =
                    7L
            )
        )
    }
}