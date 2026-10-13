package com.signaldesk.relay.ui.incidents

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentMonitorRecoveryBudgetTest {

    private fun ownership(
        command: String = "command-a",
        account: String = "account-a",
        generation: Long = 60L
    ) = IncidentMonitorOwnership(
        ownerPrincipal = account,
        sessionGeneration = generation,
        commandId = command
    )

    @Test
    fun statusReceivesOneAutomaticRetry() {
        val budget = IncidentMonitorRecoveryBudget()
        val command = ownership()

        assertTrue(budget.requestStatusRetry(command))
        assertTrue(budget.statusRetryRequested(command))
        assertFalse(budget.requestStatusRetry(command))
    }

    @Test
    fun severityReceivesOneAutomaticRetry() {
        val budget = IncidentMonitorRecoveryBudget()
        val command = ownership()

        assertTrue(budget.requestSeverityRetry(command))
        assertTrue(budget.severityRetryRequested(command))
        assertFalse(budget.requestSeverityRetry(command))
    }

    @Test
    fun statusAndSeverityHaveIndependentBudgets() {
        val budget = IncidentMonitorRecoveryBudget()
        val command = ownership()

        assertTrue(budget.requestStatusRetry(command))
        assertTrue(budget.requestSeverityRetry(command))

        assertFalse(budget.requestStatusRetry(command))
        assertFalse(budget.requestSeverityRetry(command))
    }

    @Test
    fun newerCommandHasIndependentRetryBudget() {
        val budget = IncidentMonitorRecoveryBudget()

        val first = ownership(command = "command-a")
        val second = ownership(command = "command-b")

        assertTrue(budget.requestStatusRetry(first))
        assertFalse(budget.requestStatusRetry(first))

        assertTrue(budget.requestStatusRetry(second))
        assertFalse(budget.requestStatusRetry(second))
    }

    @Test
    fun newSessionGenerationIsDifferentOwnership() {
        val budget = IncidentMonitorRecoveryBudget()

        val old = ownership(generation = 60L)
        val returned = ownership(generation = 62L)

        assertTrue(budget.requestStatusRetry(old))
        assertFalse(budget.requestStatusRetry(old))

        assertTrue(budget.requestStatusRetry(returned))
    }

    @Test
    fun differentAccountsHaveIndependentOwnership() {
        val budget = IncidentMonitorRecoveryBudget()

        val accountA = ownership(account = "account-a")
        val accountB = ownership(account = "account-b")

        assertTrue(budget.requestSeverityRetry(accountA))
        assertFalse(budget.requestSeverityRetry(accountA))

        assertTrue(budget.requestSeverityRetry(accountB))
    }

    @Test
    fun sessionResetClearsRetryHistory() {
        val budget = IncidentMonitorRecoveryBudget()
        val command = ownership()

        assertTrue(budget.requestStatusRetry(command))
        assertTrue(budget.requestSeverityRetry(command))

        budget.clear()

        assertFalse(budget.statusRetryRequested(command))
        assertFalse(budget.severityRetryRequested(command))
        assertTrue(budget.requestStatusRetry(command))
        assertTrue(budget.requestSeverityRetry(command))
    }

    @Test
    fun invalidOwnershipCannotConsumeRetryPermit() {
        val budget = IncidentMonitorRecoveryBudget()

        val blankAccount = ownership(account = "")
        val blankCommand = ownership(command = "")

        assertFalse(budget.requestStatusRetry(blankAccount))
        assertFalse(budget.requestSeverityRetry(blankCommand))
        assertFalse(budget.statusRetryRequested(blankAccount))
        assertFalse(budget.severityRetryRequested(blankCommand))
    }
}