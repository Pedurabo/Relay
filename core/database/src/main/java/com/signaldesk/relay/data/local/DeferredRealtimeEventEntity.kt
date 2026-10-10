package com.signaldesk.relay.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deferred_realtime_events",
    indices = [
        Index(
            value = [
                "incidentId",
                "deferredAt"
            ]
        ),
        Index(
            value = [
                "deferredAt",
                "eventId"
            ]
        )
    ]
)
data class DeferredRealtimeEventEntity(
    @PrimaryKey
    val eventId: String,
    val incidentId: String,
    val entryId: String,
    val message: String,
    val author: String,
    val occurredAt: Long,
    val deferredAt: Long
)
