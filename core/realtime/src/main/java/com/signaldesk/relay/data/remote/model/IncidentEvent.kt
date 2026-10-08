package com.signaldesk.relay.data.remote.model

sealed interface IncidentEvent {
    val eventId: String
    val incidentId: String
    val occurredAt: Long
}
