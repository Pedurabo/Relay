package com.signaldesk.relay.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "deferred_realtime_events",
    primaryKeys = ["ownerPrincipal", "eventId"],
    indices = [
        Index(
            value = [
                "ownerPrincipal",
                "incidentId",
                "deferredAt"
            ]
        ),
        Index(
            value = [
                "ownerPrincipal",
                "deferredAt",
                "eventId"
            ]
        )
    ]
)
data class DeferredRealtimeEventEntity(
    val eventId: String,
    val incidentId: String,
    val entryId: String,
    val message: String,
    val author: String,
    val occurredAt: Long,
    val deferredAt: Long,
    val ownerPrincipal: String
)
