package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.data.session.AuthenticatedOwnershipContext
import com.signaldesk.relay.data.session.SessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeConnectionSessionBoundaryTest {

    private fun ownership(
        principal: String,
        generation: Long
    ): AuthenticatedOwnershipContext =
        requireNotNull(
            AuthenticatedOwnershipContext.capture(
                sessionState = SessionState.SignedIn(
                    userId = principal,
                    userName = principal,
                    accessToken = "test-token",
                    refreshToken = "test-refresh",
                    accessTokenExpiresAt = Long.MAX_VALUE
                ),
                generation = generation
            )
        )

    @Test
    fun originalAccountSessionAcceptsItsOwnEvents() {
        val connection = ownership("account-a", 10L)
        val current = ownership("account-a", 10L)

        assertTrue(
            isRealtimeConnectionOwnershipCurrent(
                connection,
                current
            )
        )
    }

    @Test
    fun oldAccountAConnectionCannotWriteUnderAccountB() {
        val connection = ownership("account-a", 10L)
        val current = ownership("account-b", 12L)

        assertFalse(
            isRealtimeConnectionOwnershipCurrent(
                connection,
                current
            )
        )
    }

    @Test
    fun oldAccountAConnectionRemainsRejectedAfterReturnToA() {
        val connection = ownership("account-a", 10L)
        val current = ownership("account-a", 14L)

        assertFalse(
            isRealtimeConnectionOwnershipCurrent(
                connection,
                current
            )
        )
    }

    @Test
    fun signedOutOrUnboundConnectionCannotProcessEvent() {
        val connection = ownership("account-a", 10L)

        assertFalse(
            isRealtimeConnectionOwnershipCurrent(
                connection,
                null
            )
        )

        assertFalse(
            isRealtimeConnectionOwnershipCurrent(
                null,
                connection
            )
        )
    }
}