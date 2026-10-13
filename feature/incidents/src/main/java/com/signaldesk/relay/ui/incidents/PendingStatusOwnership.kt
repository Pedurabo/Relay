package com.signaldesk.relay.ui.incidents

internal fun canConfirmPendingStatus(
    pendingOwner: String,
    pendingGeneration: Long,
    pendingIncidentId: String,
    requestedStatus: String,
    activeOwner: String?,
    activeGeneration: Long,
    observedIncidentId: String?,
    observedStatus: String?
): Boolean {
    return pendingOwner.isNotBlank() &&
        pendingOwner == activeOwner &&
        pendingGeneration == activeGeneration &&
        pendingIncidentId == observedIncidentId &&
        requestedStatus == observedStatus
}

internal enum class StatusMonitorDecision {
    WAIT,
    SESSION_OBSOLETE,
    CONFIRMED,
    SUPERSEDED,
    COMMAND_REMOVED
}

internal fun decidePendingStatusMonitor(
    pendingOwner: String,
    pendingGeneration: Long,
    pendingIncidentId: String,
    pendingCommandId: String,
    requestedStatus: String,
    baseStatus: String,
    activeOwner: String?,
    activeGeneration: Long,
    observedIncidentId: String?,
    observedStatus: String?,
    storedCommandId: String?
): StatusMonitorDecision {
    if (
        pendingOwner.isBlank() ||
        pendingOwner != activeOwner ||
        pendingGeneration != activeGeneration
    ) {
        return StatusMonitorDecision.SESSION_OBSOLETE
    }

    if (
        canConfirmPendingStatus(
            pendingOwner = pendingOwner,
            pendingGeneration = pendingGeneration,
            pendingIncidentId = pendingIncidentId,
            requestedStatus = requestedStatus,
            activeOwner = activeOwner,
            activeGeneration = activeGeneration,
            observedIncidentId = observedIncidentId,
            observedStatus = observedStatus
        )
    ) {
        return StatusMonitorDecision.CONFIRMED
    }

    if (
        pendingIncidentId == observedIncidentId &&
        observedStatus != null &&
        observedStatus != baseStatus &&
        observedStatus != requestedStatus
    ) {
        return StatusMonitorDecision.SUPERSEDED
    }

    if (storedCommandId != pendingCommandId) {
        return StatusMonitorDecision.COMMAND_REMOVED
    }

    return StatusMonitorDecision.WAIT
}