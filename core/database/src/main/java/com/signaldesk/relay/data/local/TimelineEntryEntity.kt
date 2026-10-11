package com.signaldesk.relay.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "timeline_entries",
    primaryKeys = ["ownerPrincipal", "entryId"],
    indices = [
        Index(
            value = [
                "ownerPrincipal",
                "incidentId",
                "occurredAt",
                "entryId"
            ]
        ),
        Index(
            value = [
                "ownerPrincipal",
                "deliveryState",
                "occurredAt",
                "entryId"
            ]
        )
    ]
)
data class TimelineEntryEntity(
    val entryId: String,
    val incidentId: String,
    val message: String,
    val author: String,
    val occurredAt: Long,
    val deliveryState: String,
    val ownerPrincipal: String
)
