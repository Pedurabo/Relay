package com.signaldesk.relay.ui.incidents

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingSeverityProductionDecisionTest {

    private fun decide(
        activeOwner: String? = "account-a",
        activeGeneration: Long = 10L,
        observedIncidentId: String? = "INC-SHARED",
        observedSeverity: String? = "LOW",
        storedCommandId: String? = "cmd-a"
    ): SeverityMonitorDecision =
        decidePendingSeverityMonitor(
            pendingOwner = "account-a",
            pendingGeneration = 10L,
            pendingIncidentId = "INC-SHARED",
            pendingCommandId = "cmd-a",
            requestedSeverity = "HIGH",
            baseSeverity = "LOW",
            activeOwner = activeOwner,
            activeGeneration = activeGeneration,
            observedIncidentId = observedIncidentId,
            observedSeverity = observedSeverity,
            storedCommandId = storedCommandId
        )

    @Test
    fun unchangedIncidentKeepsWaiting() {
        assertEquals(
            SeverityMonitorDecision.WAIT,
            decide()
        )
    }

    @Test
    fun originalAccountCanConfirmSeverity() {
        assertEquals(
            SeverityMonitorDecision.CONFIRMED,
            decide(observedSeverity = "HIGH")
        )
    }

    @Test
    fun differentAuthoritativeSeveritySupersedesCommand() {
        assertEquals(
            SeverityMonitorDecision.SUPERSEDED,
            decide(observedSeverity = "CRITICAL")
        )
    }

    @Test
    fun removedCommandTerminatesMonitoring() {
        assertEquals(
            SeverityMonitorDecision.COMMAND_REMOVED,
            decide(storedCommandId = null)
        )
    }

    @Test
    fun accountBCannotConfirmSharedIncident() {
        assertEquals(
            SeverityMonitorDecision.SESSION_OBSOLETE,
            decide(
                activeOwner = "account-b",
                activeGeneration = 11L,
                observedSeverity = "HIGH"
            )
        )
    }

    @Test
    fun returningAccountACannotConfirmOldGeneration() {
        assertEquals(
            SeverityMonitorDecision.SESSION_OBSOLETE,
            decide(
                activeOwner = "account-a",
                activeGeneration = 12L,
                observedSeverity = "HIGH"
            )
        )
    }

    @Test
    fun signedOutSessionCannotConfirmSeverity() {
        assertEquals(
            SeverityMonitorDecision.SESSION_OBSOLETE,
            decide(
                activeOwner = null,
                activeGeneration = 11L,
                observedSeverity = "HIGH"
            )
        )
    }

    @Test
    fun differentIncidentCannotConfirmSeverity() {
        assertEquals(
            SeverityMonitorDecision.WAIT,
            decide(
                observedIncidentId = "INC-OTHER",
                observedSeverity = "HIGH"
            )
        )
    }

    @Test
    fun obsoleteSessionTakesPriorityOverCommandRemoval() {
        assertEquals(
            SeverityMonitorDecision.SESSION_OBSOLETE,
            decide(
                activeOwner = "account-b",
                activeGeneration = 11L,
                storedCommandId = null
            )
        )
    }
}