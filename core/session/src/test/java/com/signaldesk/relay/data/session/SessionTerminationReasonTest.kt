package com.signaldesk.relay.data.session

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTerminationReasonTest {

    @Test
    fun forcedSignOutPublishesTerminationReason() =
        runBlocking {
            SessionManager.signOutCoordinated(
                SessionTerminationReason.EXPIRED
            )

            assertEquals(
                SessionTerminationReason.EXPIRED,
                SessionManager.sessionTerminationReason.value
            )

            SessionManager.signOutCoordinated()
        }

    @Test
    fun ordinarySignOutClearsTerminationReason() =
        runBlocking {
            SessionManager.signOutCoordinated(
                SessionTerminationReason.PRINCIPAL_CHANGED
            )

            SessionManager.signOutCoordinated()

            assertNull(
                SessionManager.sessionTerminationReason.value
            )
        }
}