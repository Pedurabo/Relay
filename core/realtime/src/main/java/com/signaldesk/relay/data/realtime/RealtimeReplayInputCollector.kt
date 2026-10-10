package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.data.local.IncidentSequenceGapEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal suspend fun collectRealtimeReplayInputs(
    gaps:
        Flow<List<IncidentSequenceGapEntity>>,
    connectionState:
        Flow<RealtimeConnectionState>,
    onInput:
        suspend (
            gaps: List<IncidentSequenceGapEntity>,
            state: RealtimeConnectionState
        ) -> Unit
) {
    combine(
        gaps,
        connectionState
    ) { currentGaps, currentState ->
        currentGaps to currentState
    }.collect { result ->

        onInput(
            result.first,
            result.second
        )
    }
}