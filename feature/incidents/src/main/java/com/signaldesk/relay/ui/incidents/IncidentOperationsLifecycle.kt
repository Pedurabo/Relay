package com.signaldesk.relay.ui.incidents

import com.signaldesk.relay.data.realtime.RealtimeIncidentCoordinator
import kotlinx.coroutines.CoroutineScope

interface IncidentOperationsLifecycle {

    fun start(
        scope: CoroutineScope,
        realtimeCoordinator:
            RealtimeIncidentCoordinator
    )

    fun stop(
        realtimeCoordinator:
            RealtimeIncidentCoordinator
    )
}