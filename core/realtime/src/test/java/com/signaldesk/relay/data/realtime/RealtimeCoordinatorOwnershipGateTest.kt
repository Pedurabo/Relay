package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeCoordinatorOwnershipGateTest {

    @Test
    fun secondOwnerCannotAcquireUntilFirstReleases() {

        val gate =
            RealtimeCoordinatorOwnershipGate<Any>()

        val first =
            Any()

        val second =
            Any()

        assertTrue(
            gate.tryAcquire(
                first
            )
        )

        assertFalse(
            gate.tryAcquire(
                second
            )
        )

        assertTrue(
            gate.release(
                first
            )
        )

        assertTrue(
            gate.tryAcquire(
                second
            )
        )
    }

    @Test
    fun nonOwnerCannotReleaseCurrentOwner() {

        val gate =
            RealtimeCoordinatorOwnershipGate<Any>()

        val owner =
            Any()

        val stranger =
            Any()

        assertTrue(
            gate.tryAcquire(
                owner
            )
        )

        assertFalse(
            gate.release(
                stranger
            )
        )

        assertFalse(
            gate.tryAcquire(
                stranger
            )
        )

        assertTrue(
            gate.release(
                owner
            )
        )

        assertTrue(
            gate.tryAcquire(
                stranger
            )
        )
    }
}