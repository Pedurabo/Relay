package com.signaldesk.relay.ui.incidents

import kotlinx.coroutines.Job

/**
 * Tracks one active monitor per mutation type.
 *
 * Claims are identified by account, session generation
 * and command ID. A claim token additionally prevents
 * an older invocation from releasing a newer invocation
 * for the same command.
 *
 * ViewModel coroutine callbacks access this registry
 * from their serialized presentation dispatcher.
 */
internal class IncidentMonitorClaims {

    internal data class Claim(
        val ownership: IncidentMonitorOwnership,
        val token: Long
    )

    private var nextToken = 0L

    private var statusClaim: Claim? = null
    private var severityClaim: Claim? = null

    private var statusJob: Job? = null
    private var severityJob: Job? = null

    fun claimStatus(
        ownership: IncidentMonitorOwnership
    ): Claim? {
        if (!valid(ownership)) {
            return null
        }

        if (
            statusClaim?.ownership == ownership &&
            statusJob?.isActive != false
        ) {
            return null
        }

        statusJob?.cancel()
        statusJob = null

        val claim = newClaim(ownership)
        statusClaim = claim
        return claim
    }

    fun claimSeverity(
        ownership: IncidentMonitorOwnership
    ): Claim? {
        if (!valid(ownership)) {
            return null
        }

        if (
            severityClaim?.ownership == ownership &&
            severityJob?.isActive != false
        ) {
            return null
        }

        severityJob?.cancel()
        severityJob = null

        val claim = newClaim(ownership)
        severityClaim = claim
        return claim
    }

    fun releaseStatus(claim: Claim): Boolean {
        if (statusClaim != claim) {
            return false
        }

        statusClaim = null
        statusJob = null
        return true
    }

    fun releaseSeverity(claim: Claim): Boolean {
        if (severityClaim != claim) {
            return false
        }

        severityClaim = null
        severityJob = null
        return true
    }

    fun clear() {
        statusClaim = null
        severityClaim = null

        statusJob?.cancel()
        severityJob?.cancel()

        statusJob = null
        severityJob = null
    }

    fun attachStatus(claim: Claim, job: Job): Boolean {
        if (!ownsStatus(claim)) {
            return false
        }

        statusJob = job
        return true
    }

    fun attachSeverity(claim: Claim, job: Job): Boolean {
        if (!ownsSeverity(claim)) {
            return false
        }

        severityJob = job
        return true
    }

    fun hasActiveStatusMonitor(
        ownership: IncidentMonitorOwnership
    ): Boolean =
        statusClaim?.ownership == ownership &&
            statusJob?.isActive == true

    fun hasActiveSeverityMonitor(
        ownership: IncidentMonitorOwnership
    ): Boolean =
        severityClaim?.ownership == ownership &&
            severityJob?.isActive == true

    fun ownsStatus(claim: Claim): Boolean =
        statusClaim == claim

    fun ownsSeverity(claim: Claim): Boolean =
        severityClaim == claim

    private fun valid(
        ownership: IncidentMonitorOwnership
    ): Boolean =
        ownership.ownerPrincipal.isNotBlank() &&
            ownership.commandId.isNotBlank()

    private fun newClaim(
        ownership: IncidentMonitorOwnership
    ): Claim {
        nextToken += 1L
        return Claim(ownership, nextToken)
    }
}