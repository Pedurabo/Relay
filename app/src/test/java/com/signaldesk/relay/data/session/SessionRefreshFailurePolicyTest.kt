package com.signaldesk.relay.data.session

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRefreshFailurePolicyTest {

    @Test
    fun invalidRefreshCredentialRequiresSignOut() {

        assertTrue(
            shouldSignOutAfterRefreshFailure(
                AuthHttpException(
                    401
                )
            )
        )
    }


    @Test
    fun serverFailurePreservesCurrentSession() {

        assertFalse(
            shouldSignOutAfterRefreshFailure(
                AuthHttpException(
                    500
                )
            )
        )
    }


    @Test
    fun transportFailurePreservesCurrentSession() {

        assertFalse(
            shouldSignOutAfterRefreshFailure(
                IOException(
                    "Network unavailable"
                )
            )
        )
    }


    @Test
    fun malformedResponseFailurePreservesCurrentSession() {

        assertFalse(
            shouldSignOutAfterRefreshFailure(
                IllegalStateException(
                    "Malformed response"
                )
            )
        )
    }
}
