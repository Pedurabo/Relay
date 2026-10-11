package com.signaldesk.relay.data.local

import androidx.room.Entity

@Entity(
    tableName = "incidents",
    primaryKeys = ["ownerPrincipal", "id"]
)
data class IncidentEntity(
    val id: String,
    val title: String,
    val status: String,
    val latestSequence: Long = 0L,
    val severity: String = "MEDIUM",
    val ownerPrincipal: String
)
