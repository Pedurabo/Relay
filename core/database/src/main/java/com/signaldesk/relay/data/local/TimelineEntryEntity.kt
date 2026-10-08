package com.signaldesk.relay.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "timeline_entries",
    indices = [
        Index(value = ["incidentId"])
    ]
)
data class TimelineEntryEntity(
    @PrimaryKey
    val entryId: String,
    val incidentId: String,
    val message: String,
    val author: String,
    val occurredAt: Long,
    val deliveryState: String,

    val ownerPrincipal: String =
        ""
)
