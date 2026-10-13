package com.signaldesk.relay.ui.incidents

/**
 * Bounds automatic presentation-monitor recovery attempts.
 *
 * Each mutation type receives one automatic retry per command
 * ownership (account, session generation and command ID).
 *
 * This class does not launch coroutines, read pending commands
 * or retry delivery. It only grants a retry permit.
 *
 * Access is expected from the ViewModel's serialized dispatcher.
 */
internal class IncidentMonitorRecoveryBudget {

    private val retriedStatusCommands =
        mutableSetOf<IncidentMonitorOwnership>()

    private val retriedSeverityCommands =
        mutableSetOf<IncidentMonitorOwnership>()

    fun requestStatusRetry(
        ownership: IncidentMonitorOwnership
    ): Boolean {
        if (!valid(ownership)) {
            return false
        }

        return retriedStatusCommands.add(ownership)
    }

    fun requestSeverityRetry(
        ownership: IncidentMonitorOwnership
    ): Boolean {
        if (!valid(ownership)) {
            return false
        }

        return retriedSeverityCommands.add(ownership)
    }

    fun statusRetryRequested(
        ownership: IncidentMonitorOwnership
    ): Boolean = ownership in retriedStatusCommands

    fun severityRetryRequested(
        ownership: IncidentMonitorOwnership
    ): Boolean = ownership in retriedSeverityCommands

    /**
     * Forget retry history when a session is invalidated.
     *
     * Production must still check the current session identity
     * before attaching any replacement monitor.
     */
    fun clear() {
        retriedStatusCommands.clear()
        retriedSeverityCommands.clear()
    }

    private fun valid(
        ownership: IncidentMonitorOwnership
    ): Boolean =
        ownership.ownerPrincipal.isNotBlank() &&
            ownership.commandId.isNotBlank()
}