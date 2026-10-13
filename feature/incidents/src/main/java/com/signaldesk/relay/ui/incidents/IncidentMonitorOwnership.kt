package com.signaldesk.relay.ui.incidents

/**
 * Ownership of a single incident mutation's presentation state.
 *
 * A monitor may update shared UI only when it is still the
 * active command of the same account and session generation.
 */
internal data class IncidentMonitorOwnership(
    val ownerPrincipal: String,
    val sessionGeneration: Long,
    val commandId: String
) {
    fun canUpdatePresentation(
        active: IncidentMonitorOwnership?
    ): Boolean =
        active != null &&
            ownerPrincipal.isNotBlank() &&
            commandId.isNotBlank() &&
            this == active

    fun releaseIfCurrent(
        active: IncidentMonitorOwnership?
    ): IncidentMonitorOwnership? =
        if (canUpdatePresentation(active)) null else active
}
