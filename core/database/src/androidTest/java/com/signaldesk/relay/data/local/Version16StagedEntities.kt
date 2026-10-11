package com.signaldesk.relay.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Test-only V16 entity specifications.
 *
 * These classes are NOT registered with production RelayDatabase.
 * Remove or replace them after production entities and Room's
 * exported version-16 schema are validated.
 */

@Entity(
    tableName = "incidents",
    primaryKeys = ["ownerPrincipal", "id"]
)
internal data class V16Incident(
    val ownerPrincipal: String,
    val id: String,
    val title: String,
    val status: String,
    val latestSequence: Long,
    val severity: String
)

@Entity(
    tableName = "timeline_entries",
    primaryKeys = ["ownerPrincipal", "entryId"],
    indices = [
        Index(
            value = [
                "ownerPrincipal", "incidentId",
                "occurredAt", "entryId"
            ]
        ),
        Index(
            value = [
                "ownerPrincipal", "deliveryState",
                "occurredAt", "entryId"
            ]
        )
    ]
)
internal data class V16TimelineEntry(
    val ownerPrincipal: String,
    val entryId: String,
    val incidentId: String,
    val message: String,
    val author: String,
    val occurredAt: Long,
    val deliveryState: String
)

@Entity(
    tableName = "incident_sequence_gaps",
    primaryKeys = ["ownerPrincipal", "incidentId"]
)
internal data class V16IncidentSequenceGap(
    val ownerPrincipal: String,
    val incidentId: String,
    val expectedSequence: Long,
    val receivedSequence: Long,
    val detectedAt: Long
)

@Entity(
    tableName = "processed_events",
    primaryKeys = ["ownerPrincipal", "eventId"]
)
internal data class V16ProcessedEvent(
    val ownerPrincipal: String,
    val eventId: String,
    val processedAt: Long
)

@Entity(
    tableName = "deferred_realtime_events",
    primaryKeys = ["ownerPrincipal", "eventId"],
    indices = [
        Index(
            value = [
                "ownerPrincipal", "incidentId", "deferredAt"
            ]
        ),
        Index(
            value = [
                "ownerPrincipal", "deferredAt", "eventId"
            ]
        )
    ]
)
internal data class V16DeferredRealtimeEvent(
    val ownerPrincipal: String,
    val eventId: String,
    val incidentId: String,
    val entryId: String,
    val message: String,
    val author: String,
    val occurredAt: Long,
    val deferredAt: Long
)

@Entity(
    tableName = "deferred_incident_updates",
    primaryKeys = ["ownerPrincipal", "eventId"],
    indices = [
        Index(
            value = [
                "ownerPrincipal", "incidentId",
                "sequence", "deferredAt", "eventId"
            ]
        ),
        Index(
            value = [
                "ownerPrincipal", "deferredAt", "eventId"
            ]
        )
    ]
)
internal data class V16DeferredIncidentUpdate(
    val ownerPrincipal: String,
    val eventId: String,
    val incidentId: String,
    val occurredAt: Long,
    val sequence: Long,
    val title: String?,
    val status: String?,
    val severity: String?,
    val deferredAt: Long
)
