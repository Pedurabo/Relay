package com.signaldesk.relay.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "incidents"
)
data class IncidentEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val status: String,
    val latestSequence: Long = 0L,
    val severity: String = "MEDIUM"
)
