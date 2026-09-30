package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.data.remote.model.IncidentCreatedEvent
import com.signaldesk.relay.data.remote.model.IncidentEvent
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

class FakeRealtimeIncidentSource :
    RealtimeIncidentSource {

    private val _connectionState =
        MutableStateFlow<RealtimeConnectionState>(
            RealtimeConnectionState.Connected
        )

    override val connectionState:
        StateFlow<RealtimeConnectionState> =
        _connectionState

    override val events: Flow<IncidentEvent> =
        flow {
            delay(2_000)

            emit(
                IncidentCreatedEvent(
                    eventId = "EVT-FAKE-001",
                    incidentId = "INC-REALTIME",
                    occurredAt =
                        System.currentTimeMillis(),
                    title =
                        "Realtime demo incident",
                    status = "Active"
                )
            )

            delay(3_000)

            emit(
                IncidentUpdatedEvent(
                    eventId = "EVT-FAKE-002",
                    incidentId = "INC-REALTIME",
                    occurredAt =
                        System.currentTimeMillis(),
                    title = null,
                    status = "Monitoring"
                )
            )
        }

    override fun requestReplay(
        incidentId: String,
        fromSequence: Long,
        throughSequence: Long
    ): Boolean {
        return true
    }
}
