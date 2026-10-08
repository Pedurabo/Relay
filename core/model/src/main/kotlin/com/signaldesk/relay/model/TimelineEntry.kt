package com.signaldesk.relay.model

data class TimelineEntry(
    val id: String,
    val incidentId: String,
    val message: String,
    val author: String,
    val occurredAt: Long,
    val deliveryState: DeliveryState
)
