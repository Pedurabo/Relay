package com.signaldesk.relay.data.realtime

sealed interface RealtimeConnectionState {

    data object Disconnected :
        RealtimeConnectionState

    data object Connecting :
        RealtimeConnectionState

    data object Connected :
        RealtimeConnectionState

    data class Retrying(
        val attempt: Int,
        val delayMillis: Long
    ) : RealtimeConnectionState
}
