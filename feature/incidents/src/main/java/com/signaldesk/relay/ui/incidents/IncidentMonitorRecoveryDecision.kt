package com.signaldesk.relay.ui.incidents

import com.signaldesk.relay.data.session.SessionIdentity

internal enum class IncidentMonitorRecoveryDecision {
    RETRY,
    DO_NOT_RETRY
}

/**
 * Decides whether an unexpectedly failed presentation monitor
 * is eligible for one event-driven recovery attempt.
 *
 * This is a pure policy decision. It does not restart jobs,
 * query Room, or schedule retries.
 *
 * A production caller must verify the persisted command
 * and session again before starting a replacement monitor.
 */
internal fun decideIncidentMonitorRecovery(
    pendingOwnership: IncidentMonitorOwnership?,
    activeOwnership: IncidentMonitorOwnership?,
    currentIdentity: SessionIdentity,
    pendingStillPresent: Boolean,
    monitorActive: Boolean,
    unexpectedFailure: Boolean,
    retryAlreadyRequested: Boolean
): IncidentMonitorRecoveryDecision {

    if (!unexpectedFailure || retryAlreadyRequested) {
        return IncidentMonitorRecoveryDecision.DO_NOT_RETRY
    }

    if (!pendingStillPresent || monitorActive) {
        return IncidentMonitorRecoveryDecision.DO_NOT_RETRY
    }

    val pending = pendingOwnership
        ?: return IncidentMonitorRecoveryDecision.DO_NOT_RETRY

    if (!pending.canUpdatePresentation(activeOwnership)) {
        return IncidentMonitorRecoveryDecision.DO_NOT_RETRY
    }

    if (
        !currentIdentity.owns(
            pending.ownerPrincipal,
            pending.sessionGeneration
        )
    ) {
        return IncidentMonitorRecoveryDecision.DO_NOT_RETRY
    }

    return IncidentMonitorRecoveryDecision.RETRY
}