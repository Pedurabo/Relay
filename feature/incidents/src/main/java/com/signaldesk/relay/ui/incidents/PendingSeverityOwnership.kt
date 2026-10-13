package com.signaldesk.relay.ui.incidents

internal enum class SeverityMonitorDecision {
    WAIT,
    SESSION_OBSOLETE,
    CONFIRMED,
    SUPERSEDED,
    COMMAND_REMOVED
}

internal fun decidePendingSeverityMonitor(
    pendingOwner: String,
    pendingGeneration: Long,
    pendingIncidentId: String,
    pendingCommandId: String,
    requestedSeverity: String,
    baseSeverity: String,
    activeOwner: String?,
    activeGeneration: Long,
    observedIncidentId: String?,
    observedSeverity: String?,
    storedCommandId: String?
): SeverityMonitorDecision {
    if (
        pendingOwner.isBlank() ||
        pendingOwner != activeOwner ||
        pendingGeneration != activeGeneration
    ) {
        return SeverityMonitorDecision.SESSION_OBSOLETE
    }

    if (
        pendingIncidentId == observedIncidentId &&
        requestedSeverity == observedSeverity
    ) {
        return SeverityMonitorDecision.CONFIRMED
    }

    if (
        pendingIncidentId == observedIncidentId &&
        observedSeverity != null &&
        observedSeverity != baseSeverity &&
        observedSeverity != requestedSeverity
    ) {
        return SeverityMonitorDecision.SUPERSEDED
    }

    if (storedCommandId != pendingCommandId) {
        return SeverityMonitorDecision.COMMAND_REMOVED
    }

    return SeverityMonitorDecision.WAIT
}