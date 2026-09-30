package com.signaldesk.relay.data.remote.model

data class TimelineEntryAddedEvent(
    override val eventId: String,
    override val incidentId: String,
    override val occurredAt: Long,
    val entryId: String,
    val message: String,
    val author: String
) : IncidentEvent
