package com.signaldesk.relay.data.local

/**
 * Authenticated logical identity of one locally cached incident.
 *
 * Owner values are kept exactly as supplied by authentication.
 * Case folding, trimming, or other transformations would risk
 * combining principals that the backend treats as distinct.
 *
 * The empty-owner legacy quarantine is deliberately excluded
 * from this type.
 */
data class AccountScopedIncidentId(
    val ownerPrincipal: String,
    val incidentId: String
) {
    init {
        require(ownerPrincipal.isNotBlank()) {
            "Authenticated owner principal is required."
        }

        require(incidentId.isNotBlank()) {
            "Incident ID is required."
        }
    }
}
