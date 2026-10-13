package com.signaldesk.relay.ui.incidents

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentObservationOwnershipTest {

    @Test
    fun matchingAccountAndGenerationAreAuthorized() {
        assertTrue(
            matchesIncidentObservation(
                "account-a", 10L,
                "account-a", 10L
            )
        )
    }

    @Test
    fun differentAccountIsRejected() {
        assertFalse(
            matchesIncidentObservation(
                "account-b", 11L,
                "account-a", 10L
            )
        )
    }

    @Test
    fun oldGenerationCannotAuthorizeReturnedAccount() {
        assertFalse(
            matchesIncidentObservation(
                "account-a", 10L,
                "account-a", 12L
            )
        )
    }

    @Test
    fun newGenerationCannotAuthorizeOldOperation() {
        assertFalse(
            matchesIncidentObservation(
                "account-a", 12L,
                "account-a", 10L
            )
        )
    }

    @Test
    fun blankOwnersAreRejected() {
        assertFalse(
            matchesIncidentObservation(
                "", 10L,
                "", 10L
            )
        )
    }
}