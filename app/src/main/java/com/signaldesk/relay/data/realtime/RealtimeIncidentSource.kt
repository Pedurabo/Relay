package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.data.remote.model.IncidentEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface RealtimeIncidentSource {

    val events: Flow<IncidentEvent>

    val connectionState:
        StateFlow<RealtimeConnectionState>

    fun requestReplay(
        incidentId: String,
        fromSequence: Long,
        throughSequence: Long
    ): Boolean
}
