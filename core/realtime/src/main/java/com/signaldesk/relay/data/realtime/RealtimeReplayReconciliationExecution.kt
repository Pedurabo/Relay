package com.signaldesk.relay.data.realtime

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job

internal data class RealtimeReplayReconciliationExecution(
    val resolvedIncidentIdsStopped: Set<String>,
    val disconnectedReplayCount: Int,
    val incidentIdsToStart: Set<String>
)

internal suspend fun applyRealtimeReplayReconciliation(
    replayJobs: ConcurrentHashMap<String, Job>,
    reconciliation: RealtimeReplayReconciliation
): RealtimeReplayReconciliationExecution {

    val resolvedIncidentIdsStopped =
        linkedSetOf<String>()

    reconciliation
        .resolvedIncidentIdsToStop
        .forEach {
            incidentId ->

            replayJobs
                .remove(
                    incidentId
                )
                ?.let {
                    replayJob ->

                    stopRealtimeReplayJobs(
                        listOf(
                            replayJob
                        )
                    )

                    resolvedIncidentIdsStopped +=
                        incidentId
                }
        }

    if (
        reconciliation.stopAllForDisconnect
    ) {
        val replayJobsToStop =
            replayJobs
                .values
                .toList()

        replayJobs.clear()

        stopRealtimeReplayJobs(
            replayJobsToStop
        )

        return RealtimeReplayReconciliationExecution(
            resolvedIncidentIdsStopped =
                resolvedIncidentIdsStopped,
            disconnectedReplayCount =
                replayJobsToStop.size,
            incidentIdsToStart =
                emptySet()
        )
    }

    return RealtimeReplayReconciliationExecution(
        resolvedIncidentIdsStopped =
            resolvedIncidentIdsStopped,
        disconnectedReplayCount =
            0,
        incidentIdsToStart =
            reconciliation.incidentIdsToStart
    )
}