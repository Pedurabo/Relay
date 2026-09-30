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
        if (job != null) {
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

    private suspend fun recoverSequenceGaps() {

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

            if (
                state !=
                RealtimeConnectionState.Connected
            ) {
                return@collect
            }

            gaps.forEach { gap ->

                source.requestReplay(
                    incidentId =
                        gap.incidentId,
                    fromSequence =
                        gap.expectedSequence,
                    throughSequence =
                        gap.receivedSequence
                )
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null

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
    }
}
