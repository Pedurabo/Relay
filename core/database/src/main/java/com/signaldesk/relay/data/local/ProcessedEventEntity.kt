package com.signaldesk.relay.data.local

import androidx.room.Entity

@Entity(
    tableName = "processed_events",
    primaryKeys = ["ownerPrincipal", "eventId"]
)
data class ProcessedEventEntity(
    val eventId: String,
    val processedAt: Long,
    val ownerPrincipal: String
)
