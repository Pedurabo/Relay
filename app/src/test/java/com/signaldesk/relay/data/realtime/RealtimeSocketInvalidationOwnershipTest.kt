package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeSocketInvalidationOwnershipTest {

    @Test
    fun currentSocketAndAttemptMayRevokeOwnership() {

        val socket =
            Any()

        assertTrue(
            shouldRevokeRealtimeSocketOwnership(
                activeSocket =
                    socket,
                callbackSocket =
                    socket,
                activeAttemptId =
                    21L,
                callbackAttemptId =
                    21L
            )
        )
    }


    @Test
    fun staleSocketMayNotRevokeOwnership() {

        val currentSocket =
            Any()

        val staleSocket =
            Any()

        assertFalse(
            shouldRevokeRealtimeSocketOwnership(
                activeSocket =
                    currentSocket,
                callbackSocket =
                    staleSocket,
                activeAttemptId =
                    21L,
                callbackAttemptId =
                    21L
            )
        )
    }


    @Test
    fun staleAttemptMayNotRevokeOwnership() {

        val socket =
            Any()

        assertFalse(
            shouldRevokeRealtimeSocketOwnership(
                activeSocket =
                    socket,
                callbackSocket =
                    socket,
                activeAttemptId =
                    22L,
                callbackAttemptId =
                    21L
            )
        )
    }


    @Test
    fun releasedOwnershipCannotBeRevokedAgain() {

        val staleSocket =
            Any()

        assertFalse(
            shouldRevokeRealtimeSocketOwnership(
                activeSocket =
                    null,
                callbackSocket =
                    staleSocket,
                activeAttemptId =
                    0L,
                callbackAttemptId =
                    21L
            )
        )
    }
}
