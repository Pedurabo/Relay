package com.signaldesk.relay.data.session

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRefreshFailurePolicyTest {

    @Test
    fun invalidRefreshCredential_isClassifiedForSignOut() {

        org.junit.Assert.assertEquals(
            SessionRefreshFailureDisposition
                .SIGN_OUT_INVALID_REFRESH,
            classifySessionRefreshFailure(
                AuthHttpException(
                    401
                )
            )
        )
    }


    @Test
    fun serverFailure_isClassifiedToPreserveSession() {

        org.junit.Assert.assertEquals(
            SessionRefreshFailureDisposition
                .PRESERVE_SESSION,
            classifySessionRefreshFailure(
                AuthHttpException(
                    500
                )
            )
        )
    }


    @Test
    fun transportFailure_isClassifiedToPreserveSession() {

        org.junit.Assert.assertEquals(
            SessionRefreshFailureDisposition
                .PRESERVE_SESSION,
            classifySessionRefreshFailure(
                IOException(
                    "Network unavailable"
                )
            )
        )
    }


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
