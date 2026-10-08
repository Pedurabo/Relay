package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncidentCommandSessionInvalidationTest {

    @Test
    fun unauthorizedAttempt_returnsRejectedToken() {

        assertEquals(
            "token-a",
            incidentCommandInvalidatedToken(
                httpStatusCode =
                    401,
                accessToken =
                    "token-a"
            )
        )
    }

    @Test
    fun nonUnauthorizedFailure_doesNotInvalidateSession() {

        assertNull(
            incidentCommandInvalidatedToken(
                httpStatusCode =
                    500,
                accessToken =
                    "token-a"
            )
        )
    }

    @Test
    fun missingHttpResponse_doesNotInvalidateSession() {

        assertNull(
            incidentCommandInvalidatedToken(
                httpStatusCode =
                    null,
                accessToken =
                    "token-a"
            )
        )
    }

    @Test
    fun unauthorizedAttemptWithoutToken_doesNotInvalidateSession() {

        assertNull(
            incidentCommandInvalidatedToken(
                httpStatusCode =
                    401,
                accessToken =
                    null
            )
        )
    }

    @Test
    fun unauthorizedAttemptWithBlankToken_doesNotInvalidateSession() {

        assertNull(
            incidentCommandInvalidatedToken(
                httpStatusCode =
                    401,
                accessToken =
                    "   "
            )
        )
    }
}