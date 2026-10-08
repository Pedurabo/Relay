package com.signaldesk.relay.notifications

import com.signaldesk.relay.data.session.SessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRegistrationSessionOwnershipTest {

    private fun signedIn(
        userId: String,
        accessToken: String
    ) =
        SessionState.SignedIn(
            userId =
                userId,
            userName =
                userId,
            accessToken =
                accessToken,
            refreshToken =
                "refresh-$userId",
            accessTokenExpiresAt =
                1L
        )


    @Test
    fun allowsCapturedSessionWhileItIsStillCurrent() {

        assertTrue(
            isCurrentPushRegistrationSession(
                current =
                    signedIn(
                        userId =
                            "user-a",
                        accessToken =
                            "token-a"
                    ),
                expectedUserId =
                    "user-a",
                expectedAccessToken =
                    "token-a"
            )
        )
    }


    @Test
    fun blocksRegistrationAfterSignOut() {

        assertFalse(
            isCurrentPushRegistrationSession(
                current =
                    SessionState.SignedOut,
                expectedUserId =
                    "user-a",
                expectedAccessToken =
                    "token-a"
            )
        )
    }


    @Test
    fun blocksRegistrationAfterUserChanges() {

        assertFalse(
            isCurrentPushRegistrationSession(
                current =
                    signedIn(
                        userId =
                            "user-b",
                        accessToken =
                            "token-b"
                    ),
                expectedUserId =
                    "user-a",
                expectedAccessToken =
                    "token-a"
            )
        )
    }


    @Test
    fun blocksRegistrationAfterAccessTokenChanges() {

        assertFalse(
            isCurrentPushRegistrationSession(
                current =
                    signedIn(
                        userId =
                            "user-a",
                        accessToken =
                            "token-new"
                    ),
                expectedUserId =
                    "user-a",
                expectedAccessToken =
                    "token-old"
            )
        )
    }
}
