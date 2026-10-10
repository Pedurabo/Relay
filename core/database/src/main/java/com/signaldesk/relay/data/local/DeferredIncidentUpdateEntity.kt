package com.signaldesk.relay.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deferred_incident_updates",
    indices = [
        Index(
            value = ["incidentId"]
        )
    ]
)
data class DeferredIncidentUpdateEntity(
    @PrimaryKey
    val eventId: String,
    val incidentId: String,
    val occurredAt: Long,
    val sequence: Long,
    val title: String?,
    val status: String?,
    val severity: String?,
    val deferredAt: Long
)
