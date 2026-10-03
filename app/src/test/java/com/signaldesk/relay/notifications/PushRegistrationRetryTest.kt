package com.signaldesk.relay.notifications

import com.signaldesk.relay.data.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRegistrationRetryTest {

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
    fun requestRequiresCurrentSessionAndCurrentDeviceToken() {

        assertTrue(
            isCurrentPushRegistrationRequest(
                currentSession =
                    signedIn(
                        userId =
                            "user-a",
                        accessToken =
                            "access-a"
                    ),
                expectedUserId =
                    "user-a",
                expectedAccessToken =
                    "access-a",
                currentRegistrationToken =
                    "fcm-a",
                expectedRegistrationToken =
                    "fcm-a"
            )
        )

        assertFalse(
            isCurrentPushRegistrationRequest(
                currentSession =
                    signedIn(
                        userId =
                            "user-a",
                        accessToken =
                            "access-a"
                    ),
                expectedUserId =
                    "user-a",
                expectedAccessToken =
                    "access-a",
                currentRegistrationToken =
                    "fcm-new",
                expectedRegistrationToken =
                    "fcm-old"
            )
        )
    }


    @Test
    fun retriesTransientFailureWhileRequestRemainsCurrent() =
        runBlocking {

            var current =
                true

            var executions =
                0

            val delayedAttempts =
                mutableListOf<Int>()

            runPushRegistrationSafely(
                isCurrentRequest = {
                    current
                },
                delayAfterFailure = {
                    attempt ->

                    delayedAttempts +=
                        attempt
                }
            ) {

                executions +=
                    1

                if (
                    executions ==
                    1
                ) {
                    throw IllegalStateException(
                        "Temporary network failure"
                    )
                }
            }

            assertEquals(
                2,
                executions
            )

            assertEquals(
                listOf(
                    1
                ),
                delayedAttempts
            )
        }


    @Test
    fun stopsRetryingWhenRequestBecomesStale() =
        runBlocking {

            var current =
                true

            var executions =
                0

            var delayed =
                false

            runPushRegistrationSafely(
                isCurrentRequest = {
                    current
                },
                delayAfterFailure = {
                    delayed =
                        true
                }
            ) {

                executions +=
                    1

                current =
                    false

                throw IllegalStateException(
                    "Registration became stale"
                )
            }

            assertEquals(
                1,
                executions
            )

            assertFalse(
                delayed
            )
        }


    @Test
    fun cancellationStillEscapesRegistrationRetry() =
        runBlocking {

            var cancellationEscaped =
                false

            var delayed =
                false

            try {

                runPushRegistrationSafely(
                    isCurrentRequest = {
                        true
                    },
                    delayAfterFailure = {
                        delayed =
                            true
                    }
                ) {

                    throw CancellationException(
                        "Registration cancelled"
                    )
                }

            } catch (
                error: CancellationException
            ) {

                cancellationEscaped =
                    true
            }

            assertTrue(
                cancellationEscaped
            )

            assertFalse(
                delayed
            )
        }
}
