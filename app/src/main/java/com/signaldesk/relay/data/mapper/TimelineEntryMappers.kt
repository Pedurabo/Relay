package com.signaldesk.relay.data.mapper

import com.signaldesk.relay.data.local.TimelineEntryEntity
import com.signaldesk.relay.model.DeliveryState
import com.signaldesk.relay.model.TimelineEntry

fun TimelineEntryEntity.toDomain(): TimelineEntry {
    return TimelineEntry(
        id = entryId,
        incidentId = incidentId,
        message = message,
        author = author,
        occurredAt = occurredAt,
        deliveryState =
            runCatching {
                DeliveryState.valueOf(
                    deliveryState
                )
            }.getOrDefault(
                DeliveryState.SENT
            )
    )
}
