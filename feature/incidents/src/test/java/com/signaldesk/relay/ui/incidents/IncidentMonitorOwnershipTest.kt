package com.signaldesk.relay.ui.incidents

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentMonitorOwnershipTest {

    @Test
    fun matchingCommandCanUpdatePresentation() {
        val command =
            IncidentMonitorOwnership("account-a", 10L, "command-1")

        assertTrue(command.canUpdatePresentation(command))
    }

    @Test
    fun olderCommandCannotUpdateNewerCommandPresentation() {
        val older =
            IncidentMonitorOwnership("account-a", 10L, "command-1")
        val newer =
            IncidentMonitorOwnership("account-a", 10L, "command-2")

        assertFalse(older.canUpdatePresentation(newer))
    }

    @Test
    fun previousSessionCannotUpdateReturnedAccountPresentation() {
        val previous =
            IncidentMonitorOwnership("account-a", 10L, "command-1")
        val returned =
            IncidentMonitorOwnership("account-a", 12L, "command-1")

        assertFalse(previous.canUpdatePresentation(returned))
    }

    @Test
    fun differentAccountCannotUpdatePresentation() {
        val accountA =
            IncidentMonitorOwnership("account-a", 10L, "command-1")
        val accountB =
            IncidentMonitorOwnership("account-b", 10L, "command-1")

        assertFalse(accountA.canUpdatePresentation(accountB))
    }

    @Test
    fun clearedPresentationRejectsOldMonitor() {
        val command =
            IncidentMonitorOwnership("account-a", 10L, "command-1")

        assertFalse(command.canUpdatePresentation(null))
    }

    @Test
    fun blankCommandIdCannotClaimPresentation() {
        val command =
            IncidentMonitorOwnership("account-a", 10L, "")

        assertFalse(command.canUpdatePresentation(command))
    }

    @Test
    fun blankOwnerCannotClaimPresentation() {
        val command =
            IncidentMonitorOwnership("", 10L, "command-1")

        assertFalse(command.canUpdatePresentation(command))
    }

    @Test
    fun matchingCommandReleasesOwnership() {
        val command =
            IncidentMonitorOwnership("account-a", 10L, "command-1")

        org.junit.Assert.assertNull(command.releaseIfCurrent(command))
    }

    @Test
    fun olderCommandCannotReleaseNewerCommand() {
        val older =
            IncidentMonitorOwnership("account-a", 10L, "command-1")

        val newer =
            IncidentMonitorOwnership("account-a", 10L, "command-2")

        org.junit.Assert.assertEquals(
            newer,
            older.releaseIfCurrent(newer)
        )
    }

    @Test
    fun oldGenerationCannotReleaseReturnedAccount() {
        val older =
            IncidentMonitorOwnership("account-a", 10L, "command-1")

        val returned =
            IncidentMonitorOwnership("account-a", 12L, "command-1")

        org.junit.Assert.assertEquals(
            returned,
            older.releaseIfCurrent(returned)
        )
    }

    @Test
    fun clearedOwnershipRemainsCleared() {
        val command =
            IncidentMonitorOwnership("account-a", 10L, "command-1")

        org.junit.Assert.assertNull(command.releaseIfCurrent(null))
    }
}
