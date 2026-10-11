package com.signaldesk.relay.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "deferred_incident_updates",
    primaryKeys = ["ownerPrincipal", "eventId"],
    indices = [
        Index(
            value = [
                "ownerPrincipal",
                "incidentId",
                "sequence",
                "deferredAt",
                "eventId"
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
data class DeferredIncidentUpdateEntity(
    val eventId: String,
    val incidentId: String,
    val occurredAt: Long,
    val sequence: Long,
    val title: String?,
    val status: String?,
    val severity: String?,
    val deferredAt: Long,
    val ownerPrincipal: String
)
