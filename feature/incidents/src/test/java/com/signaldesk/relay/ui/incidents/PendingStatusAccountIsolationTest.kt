package com.signaldesk.relay.ui.incidents

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingStatusAccountIsolationTest {

    @Test
    fun sameIncidentIdFromDifferentAccountCannotConfirmPendingStatus() {
        assertFalse(
            canConfirmPendingStatus(
                "account-a", 10L, "INC-SHARED", "RESOLVED",
                "account-b", 11L, "INC-SHARED", "RESOLVED"
            )
        )
    }

    @Test
    fun sameAccountAndIncidentCanConfirmPendingStatus() {
        assertTrue(
            canConfirmPendingStatus(
                "account-a", 10L, "INC-SHARED", "RESOLVED",
                "account-a", 10L, "INC-SHARED", "RESOLVED"
            )
        )
    }

    @Test
    fun returningToSameAccountDoesNotAuthorizeOldSession() {
        assertFalse(
            canConfirmPendingStatus(
                "account-a", 10L, "INC-SHARED", "RESOLVED",
                "account-a", 12L, "INC-SHARED", "RESOLVED"
            )
        )
    }

    @Test
    fun differentIncidentCannotConfirmPendingStatus() {
        assertFalse(
            canConfirmPendingStatus(
                "account-a", 10L, "INC-SHARED", "RESOLVED",
                "account-a", 10L, "INC-OTHER", "RESOLVED"
            )
        )
    }

    @Test
    fun unchangedStatusCannotConfirmPendingStatus() {
        assertFalse(
            canConfirmPendingStatus(
                "account-a", 10L, "INC-SHARED", "RESOLVED",
                "account-a", 10L, "INC-SHARED", "OPEN"
            )
        )
    }
}