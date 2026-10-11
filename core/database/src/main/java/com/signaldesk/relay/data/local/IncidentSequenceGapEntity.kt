package com.signaldesk.relay.data.local

import androidx.room.Entity

@Entity(
    tableName = "incident_sequence_gaps",
    primaryKeys = ["ownerPrincipal", "incidentId"]
)
data class IncidentSequenceGapEntity(
    val incidentId: String,
    val expectedSequence: Long,
    val receivedSequence: Long,
    val detectedAt: Long,
    val ownerPrincipal: String
)
