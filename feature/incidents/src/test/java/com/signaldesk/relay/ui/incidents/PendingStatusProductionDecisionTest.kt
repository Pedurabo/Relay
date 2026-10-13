package com.signaldesk.relay.ui.incidents

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingStatusProductionDecisionTest {

    private fun decide(
        activeOwner: String? = "account-a",
        activeGeneration: Long = 10L,
        observedIncidentId: String? = "INC-SHARED",
        observedStatus: String? = "OPEN",
        storedCommandId: String? = "cmd-a"
    ): StatusMonitorDecision =
        decidePendingStatusMonitor(
            pendingOwner = "account-a",
            pendingGeneration = 10L,
            pendingIncidentId = "INC-SHARED",
            pendingCommandId = "cmd-a",
            requestedStatus = "RESOLVED",
            baseStatus = "OPEN",
            activeOwner = activeOwner,
            activeGeneration = activeGeneration,
            observedIncidentId = observedIncidentId,
            observedStatus = observedStatus,
            storedCommandId = storedCommandId
        )

    @Test
    fun unchangedIncidentAndPendingCommandKeepWaiting() {
        assertEquals(
            StatusMonitorDecision.WAIT,
            decide()
        )
    }

    @Test
    fun originalAccountCanConfirmStatus() {
        assertEquals(
            StatusMonitorDecision.CONFIRMED,
            decide(observedStatus = "RESOLVED")
        )
    }

    @Test
    fun newerAuthoritativeStatusSupersedesPendingCommand() {
        assertEquals(
            StatusMonitorDecision.SUPERSEDED,
            decide(observedStatus = "ACKNOWLEDGED")
        )
    }

    @Test
    fun removedPendingCommandTerminatesMonitoring() {
        assertEquals(
            StatusMonitorDecision.COMMAND_REMOVED,
            decide(storedCommandId = null)
        )
    }

    @Test
    fun accountBCannotConfirmSharedIncident() {
        assertEquals(
            StatusMonitorDecision.SESSION_OBSOLETE,
            decide(
                activeOwner = "account-b",
                activeGeneration = 11L,
                observedStatus = "RESOLVED"
            )
        )
    }

    @Test
    fun returningAccountAWithNewGenerationCannotConfirmOldCommand() {
        assertEquals(
            StatusMonitorDecision.SESSION_OBSOLETE,
            decide(
                activeOwner = "account-a",
                activeGeneration = 12L,
                observedStatus = "RESOLVED"
            )
        )
    }

    @Test
    fun signedOutSessionCannotConfirm() {
        assertEquals(
            StatusMonitorDecision.SESSION_OBSOLETE,
            decide(
                activeOwner = null,
                activeGeneration = 11L,
                observedStatus = "RESOLVED"
            )
        )
    }

    @Test
    fun differentIncidentCannotConfirm() {
        assertEquals(
            StatusMonitorDecision.WAIT,
            decide(
                observedIncidentId = "INC-OTHER",
                observedStatus = "RESOLVED"
            )
        )
    }

    @Test
    fun obsoleteSessionTakesPriorityOverCommandRemoval() {
        assertEquals(
            StatusMonitorDecision.SESSION_OBSOLETE,
            decide(
                activeOwner = "account-b",
                activeGeneration = 11L,
                storedCommandId = null
            )
        )
    }
}