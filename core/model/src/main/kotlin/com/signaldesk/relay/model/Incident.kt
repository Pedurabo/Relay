package com.signaldesk.relay.model

data class Incident(
    val id: String,
    val title: String,
    val status: String,
    val severity: IncidentSeverity =
        IncidentSeverity.MEDIUM,
    val latestMessage: String? = null,
    val latestAuthor: String? = null,
    val latestUpdateAt: Long? = null
)
