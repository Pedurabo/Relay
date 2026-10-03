package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.data.local.IncidentSequenceGapDao
import com.signaldesk.relay.data.remote.model.IncidentEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

internal fun shouldStartRealtimeCoordinator(
    job: Job?
): Boolean =
    job == null ||
        job.isCompleted

class RealtimeIncidentCoordinator(
    private val source: RealtimeIncidentSource,
    private val processor: IncidentEventProcessor,
    private val gapDao: IncidentSequenceGapDao,
    private val onAppliedEvent:
        (IncidentEvent) -> Unit = {}
) {

    private val _connectionState =
        MutableStateFlow<RealtimeConnectionState>(
            RealtimeConnectionState.Disconnected
        )

    val connectionState:
        StateFlow<RealtimeConnectionState> =
        _connectionState

    private var job:
        Job? = null

    fun start(
        scope: CoroutineScope
    ) {
        if (
            !shouldStartRealtimeCoordinator(
                job
            )
        ) {
            return
        }

        job =
            scope.launch {

                launch {
                    recoverSequenceGaps()
                }

                var failedAttempts = 0

                while (isActive) {

                    var connectedThisAttempt =
                        false

                    _connectionState.value =
                        RealtimeConnectionState.Connecting

                    val stateJob =
                        launch {
                            source
                                .connectionState
                                .collect { state ->

                                    if (
                                        state ==
                                        RealtimeConnectionState.Connected
                                    ) {
                                        connectedThisAttempt =
                                            true

                                        failedAttempts = 0
                                    }

                                    if (
                                        _connectionState.value
                                            !is
                                            RealtimeConnectionState.Retrying
                                    ) {
                                        _connectionState.value =
                                            state
                                    }
                                }
                        }

                    try {

                        source.events.collect { event ->

                            val result =
                                processor.process(
                                    event
                                )

                            if (
                                result ==
                                EventProcessingResult.APPLIED
                            ) {
                                onAppliedEvent(
                                    event
                                )
                            }
                        }

                    } catch (
                        error: CancellationException
                    ) {
                        throw error

                    } catch (
                        error: Throwable
                    ) {
                        // Reconnect below.

                    } finally {
                        stateJob.cancel()
                    }

                    if (!isActive) {
                        break
                    }

                    if (connectedThisAttempt) {
                        failedAttempts = 0
                    }

                    failedAttempts += 1

                    val delayMillis =
                        calculateBackoffMillis(
                            failedAttempts
                        )

                    _connectionState.value =
                        RealtimeConnectionState.Retrying(
                            attempt =
                                failedAttempts,
                            delayMillis =
                                delayMillis
                        )

                    delay(
                        delayMillis
                    )
                }

                _connectionState.value =
                    RealtimeConnectionState.Disconnected
            }
    }

    private suspend fun recoverSequenceGaps() =
        kotlinx.coroutines.coroutineScope {

            val replayJobs =
                mutableMapOf<
                    String,
                    Job
                >()

            combine(
                gapDao.observeAll(),
                source.connectionState
            ) { gaps, state ->
                gaps to state
            }.collect { result ->

                val gaps =
                    result.first

                val state =
                    result.second

                val activeIncidentIds =
                    gaps
                        .map {
                            it.incidentId
                        }
                        .toSet()

                replayJobs
                    .keys
                    .toList()
                    .filter {
                        it !in activeIncidentIds
                    }
                    .forEach {
                        incidentId ->

                        replayJobs
                            .remove(
                                incidentId
                            )
                            ?.cancel()
                    }

                if (
                    state !=
                    RealtimeConnectionState.Connected
                ) {

                    replayJobs
                        .values
                        .forEach {
                            it.cancel()
                        }

                    replayJobs.clear()

                    return@collect
                }

                gaps.forEach {
                    gap ->

                    val existingJob =
                        replayJobs[
                            gap.incidentId
                        ]

                    if (
                        existingJob?.isActive ==
                        true
                    ) {
                        return@forEach
                    }

                    replayJobs[
                        gap.incidentId
                    ] =
                        launch {

                            try {

                                SequenceGapReplayWorker
                                    .run(
                                        incidentId =
                                            gap.incidentId,

                                        isConnected = {
                                            source
                                                .connectionState
                                                .value ==
                                            RealtimeConnectionState
                                                .Connected
                                        },

                                        loadGap = {
                                            incidentId ->

                                            gapDao
                                                .getByIncidentId(
                                                    incidentId
                                                )
                                        },

                                        requestReplay = {
                                            incidentId,
                                            fromSequence,
                                            throughSequence ->

                                            source
                                                .requestReplay(
                                                    incidentId =
                                                        incidentId,

                                                    fromSequence =
                                                        fromSequence,

                                                    throughSequence =
                                                        throughSequence
                                                )
                                        },

                                        delayForAttempt = {
                                            attempt ->

                                            delay(
                                                calculateReplayBackoffMillis(
                                                    attempt
                                                )
                                            )
                                        }
                                    )

                            } finally {

                                replayJobs.remove(
                                    gap.incidentId
                                )
                            }
                        }
                }
            }
        }

    private fun calculateReplayBackoffMillis(
        attempt: Int
    ): Long {

        var delayMillis =
            500L

        repeat(
            (attempt - 1)
                .coerceAtLeast(0)
        ) {

            delayMillis =
                min(
                    delayMillis * 2,
                    MAX_REPLAY_BACKOFF_MILLIS
                )
        }

        return delayMillis
            .coerceAtMost(
                MAX_REPLAY_BACKOFF_MILLIS
            )
    }

    fun stop() {
        job?.cancel()

        _connectionState.value =
            RealtimeConnectionState.Disconnected
    }

    private fun calculateBackoffMillis(
        attempt: Int
    ): Long {

        var delayMillis =
            1_000L

        repeat(
            (attempt - 1)
                .coerceAtLeast(0)
        ) {
            delayMillis =
                min(
                    delayMillis * 2,
                    MAX_BACKOFF_MILLIS
                )
        }

        return delayMillis
            .coerceAtMost(
                MAX_BACKOFF_MILLIS
            )
    }

    companion object {
        private const val MAX_BACKOFF_MILLIS =
            30_000L

        private const val MAX_REPLAY_BACKOFF_MILLIS =
            10_000L
    }
}
