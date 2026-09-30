package com.signaldesk.relay.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incident_sequence_gaps")
data class IncidentSequenceGapEntity(
    @PrimaryKey
    val incidentId: String,
    val expectedSequence: Long,
    val receivedSequence: Long,
    val detectedAt: Long
)
