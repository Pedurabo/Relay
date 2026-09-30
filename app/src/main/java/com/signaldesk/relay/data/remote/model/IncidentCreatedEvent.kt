package com.signaldesk.relay.data.remote.model

data class IncidentCreatedEvent(
    override val eventId: String,
    override val incidentId: String,
    override val occurredAt: Long,
    val title: String,
    val status: String,
    val severity: String = "MEDIUM",
    val sequence: Long = 0L
) : IncidentEvent
