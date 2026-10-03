package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.data.local.IncidentSequenceGapEntity

internal object SequenceGapReplayWorker {

    suspend fun run(
        incidentId: String,
        isConnected: () -> Boolean,
        loadGap:
            suspend (String) ->
                IncidentSequenceGapEntity?,
        requestReplay:
            suspend (
                incidentId: String,
                fromSequence: Long,
                throughSequence: Long
            ) -> Unit,
        delayForAttempt:
            suspend (Int) -> Unit
    ) {

        var attempt =
            0

        while (
            isConnected()
        ) {

            val currentGap =
                loadGap(
                    incidentId
                )
                    ?: return

            attempt +=
                1

            requestReplay(
                currentGap.incidentId,
                currentGap.expectedSequence,
                currentGap.receivedSequence
            )

            delayForAttempt(
                attempt
            )
        }
    }
}
