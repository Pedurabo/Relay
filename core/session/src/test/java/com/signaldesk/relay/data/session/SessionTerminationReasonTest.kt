package com.signaldesk.relay.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTerminationReasonTest {

    @Test
    fun forcedSignOutPublishesTerminationReason() {
        SessionManager.signOut(
            SessionTerminationReason.EXPIRED
        )

        assertEquals(
            SessionTerminationReason.EXPIRED,
            SessionManager
                .sessionTerminationReason
                .value
        )

        SessionManager.signOut()
    }

    @Test
    fun ordinarySignOutClearsTerminationReason() {
        SessionManager.signOut(
            SessionTerminationReason.PRINCIPAL_CHANGED
        )

        SessionManager.signOut()

        assertNull(
            SessionManager
                .sessionTerminationReason
                .value
        )
    }
}
