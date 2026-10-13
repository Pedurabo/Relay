package com.signaldesk.relay.ui.incidents

internal fun matchesIncidentObservation(
    observationOwner: String,
    observationGeneration: Long,
    expectedOwner: String,
    expectedGeneration: Long
): Boolean {
    return observationOwner.isNotBlank() &&
        expectedOwner.isNotBlank() &&
        observationOwner == expectedOwner &&
        observationGeneration == expectedGeneration
}

internal data class OwnedIncidentObservation(
    val ownerPrincipal: String,
    val sessionGeneration: Long,
    val incident: com.signaldesk.relay.model.Incident?
) {
    fun matchesSession(owner: String, generation: Long): Boolean =
        matchesIncidentObservation(
            observationOwner = ownerPrincipal,
            observationGeneration = sessionGeneration,
            expectedOwner = owner,
            expectedGeneration = generation
        )
}