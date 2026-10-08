package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.model.IncidentSeverity

internal enum class CreateIncidentResolutionDecision {
    KEEP_PENDING,
    CONVERGED,
    SUPERSEDED
}

internal fun decideCreateIncidentResolution(
    authoritativeTitle: String?,
    authoritativeStatus: String?,
    authoritativeSeverity: String?,
    requestedTitle: String,
    requestedStatus: String,
    requestedSeverity: String
): CreateIncidentResolutionDecision {

    if (
        authoritativeTitle == null ||
        authoritativeStatus == null ||
        authoritativeSeverity == null
    ) {
        return CreateIncidentResolutionDecision
            .KEEP_PENDING
    }

    val severityMatches =
        IncidentSeverity
            .fromStoredValue(
                authoritativeSeverity
            )
            .name ==
        IncidentSeverity
            .fromStoredValue(
                requestedSeverity
            )
            .name

    if (
        authoritativeTitle ==
            requestedTitle &&
        authoritativeStatus ==
            requestedStatus &&
        severityMatches
    ) {
        return CreateIncidentResolutionDecision
            .CONVERGED
    }

    return CreateIncidentResolutionDecision
        .SUPERSEDED
}