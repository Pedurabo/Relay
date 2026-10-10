package com.signaldesk.relay.data.realtime

internal data class RealtimeReplayReconciliation(
    val resolvedIncidentIdsToStop: Set<String>,
    val stopAllForDisconnect: Boolean,
    val incidentIdsToStart: Set<String>
)

internal fun calculateRealtimeReplayReconciliation(
    gapIncidentIds: Set<String>,
    registeredReplayIncidentIds: Set<String>,
    activeReplayIncidentIds: Set<String>,
    state: RealtimeConnectionState
): RealtimeReplayReconciliation {

    val resolvedIncidentIdsToStop =
        registeredReplayIncidentIds -
            gapIncidentIds

    val connected =
        state ==
            RealtimeConnectionState.Connected

    val incidentIdsToStart =
        if (connected) {
            gapIncidentIds -
                activeReplayIncidentIds
        } else {
            emptySet()
        }

    return RealtimeReplayReconciliation(
        resolvedIncidentIdsToStop =
            resolvedIncidentIdsToStop,
        stopAllForDisconnect =
            !connected,
        incidentIdsToStart =
            incidentIdsToStart
    )
}