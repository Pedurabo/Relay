package com.signaldesk.relay.data.session

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthLoginFailureTest {

    @Test
    fun unauthorizedIsInvalidCredentials() {
        assertEquals(
            AuthLoginFailure.INVALID_CREDENTIALS,
            classifyAuthLoginFailure(
                AuthHttpException(401)
            )
        )
    }

    @Test
    fun tooManyRequestsIsRateLimited() {
        assertEquals(
            AuthLoginFailure.RATE_LIMITED,
            classifyAuthLoginFailure(
                AuthHttpException(429)
            )
        )
    }

    @Test
    fun serverFailureIsServerError() {
        assertEquals(
            AuthLoginFailure.SERVER_ERROR,
            classifyAuthLoginFailure(
                AuthHttpException(500)
            )
        )
    }

    @Test
    fun ioFailureIsNetworkUnavailable() {
        assertEquals(
            AuthLoginFailure.NETWORK_UNAVAILABLE,
            classifyAuthLoginFailure(
                IOException("Network unavailable")
            )
        )
    }

    @Test
    fun unknownFailureIsUnexpected() {
        assertEquals(
            AuthLoginFailure.UNEXPECTED,
            classifyAuthLoginFailure(
                IllegalStateException("Unexpected")
            )
        )
    }
}
