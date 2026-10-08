package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.data.session.SessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineDeliveryOwnershipTest {

    @Test
    fun credentialUsesTokenFromMatchingOwnerSession() {

        val credential =
            timelineDeliveryCredential(
                signedIn(
                    userId =
                        "operator-a",
                    accessToken =
                        "token-a"
                ),
                ownerPrincipal =
                    "operator-a"
            )

        assertEquals(
            "operator-a",
            credential?.ownerPrincipal
        )

        assertEquals(
            "token-a",
            credential?.accessToken
        )
    }

    @Test
    fun credentialRejectsDifferentPrincipal() {

        val credential =
            timelineDeliveryCredential(
                signedIn(
                    userId =
                        "operator-b",
                    accessToken =
                        "token-b"
                ),
                ownerPrincipal =
                    "operator-a"
            )

        assertNull(
            credential
        )
    }

    @Test
    fun credentialRejectsSignedOutSession() {

        assertNull(
            timelineDeliveryCredential(
                SessionState.SignedOut,
                ownerPrincipal =
                    "operator-a"
            )
        )
    }

    @Test
    fun currentSessionRequiresSameOwnerAndSameToken() {

        assertTrue(
            isTimelineDeliverySessionCurrent(
                signedIn(
                    userId =
                        "operator-a",
                    accessToken =
                        "token-a"
                ),
                expectedOwnerPrincipal =
                    "operator-a",
                expectedAccessToken =
                    "token-a"
            )
        )

        assertFalse(
            isTimelineDeliverySessionCurrent(
                signedIn(
                    userId =
                        "operator-b",
                    accessToken =
                        "token-b"
                ),
                expectedOwnerPrincipal =
                    "operator-a",
                expectedAccessToken =
                    "token-a"
            )
        )

        assertFalse(
            isTimelineDeliverySessionCurrent(
                signedIn(
                    userId =
                        "operator-a",
                    accessToken =
                        "refreshed-token-a"
                ),
                expectedOwnerPrincipal =
                    "operator-a",
                expectedAccessToken =
                    "token-a"
            )
        )
    }

    private fun signedIn(
        userId: String,
        accessToken: String
    ): SessionState.SignedIn {

        return SessionState.SignedIn(
            userId =
                userId,
            userName =
                "Operator",
            accessToken =
                accessToken,
            refreshToken =
                "refresh-$userId",
            accessTokenExpiresAt =
                9_999_999L
        )
    }
}
