package com.signaldesk.relay.data.remote.model

data class IncidentUpdatedEvent(
    override val eventId: String,
    override val incidentId: String,
    override val occurredAt: Long,
    val title: String? = null,
    val status: String? = null,
    val severity: String? = null,
    val sequence: Long = 0L
) : IncidentEvent
