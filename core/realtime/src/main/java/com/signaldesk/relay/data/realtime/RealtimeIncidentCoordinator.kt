package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.diagnostics.RelayDiagnostics
import android.util.Log
import com.signaldesk.relay.data.local.IncidentSequenceGapDao
import com.signaldesk.relay.data.remote.model.IncidentEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

internal fun shouldStartRealtimeCoordinator(
    job: Job?
): Boolean =
    job == null ||
        job.isCompleted

internal suspend fun awaitRealtimeRestartAvailability(
    job: Job?
) {

    if (
        job != null &&
        job.isCancelled &&
        !job.isCompleted
    ) {
        job.join()
    }
}

internal suspend fun stopRealtimeStateCollector(
    job: Job
) {
    job.cancel()
    job.join()
}

internal fun removeReplayJobIfCurrent(
    replayJobs: ConcurrentHashMap<String, Job>,
    incidentId: String,
    replayJob: Job
): Boolean =
    replayJobs.remove(
        incidentId,
        replayJob
    )


internal suspend fun stopRealtimeReplayJobs(
    jobs: Collection<Job>
) {
    jobs.forEach {
        it.cancel()
    }

    jobs.forEach {
        it.join()
    }
}
internal fun CoroutineScope.launchRegisteredRealtimeReplayJob(
    replayJobs: ConcurrentHashMap<String, Job>,
    incidentId: String,
    block: suspend () -> Unit
): Job {

    lateinit var replayJob:
        Job

    replayJob =
        launch(
            start =
                CoroutineStart.LAZY
        ) {
            try {

                block()

            } finally {

                removeReplayJobIfCurrent(
                    replayJobs =
                        replayJobs,
                    incidentId =
                        incidentId,
                    replayJob =
                        replayJob
                )
            }
        }

    replayJobs[
        incidentId
    ] =
        replayJob

    replayJob.start()

    return replayJob
}

internal suspend fun runRealtimeReplayJobSafely(
    isConnected: () -> Boolean,
    delayAfterFailure:
        suspend (Int) -> Unit,
    onFailure:
        (Throwable, Int) -> Unit = {
            _,
            _ ->
        },
    block: suspend () -> Unit
) {
    var failedAttempts =
        0

    while (
        isConnected()
    ) {
        try {

            block()

            return

        } catch (
            error: CancellationException
        ) {
            throw error

        } catch (
            error: Throwable
        ) {

            failedAttempts +=
                1


            onFailure(
                error,
                failedAttempts
            )

            if (
                !isConnected()
            ) {
                return
            }

            delayAfterFailure(
                failedAttempts
            )
        }
    }
}

internal suspend fun runRealtimeGapRecoverySafely(
    isActive: () -> Boolean,
    delayAfterFailure:
        suspend (Int) -> Unit,
    onFailure:
        (Throwable, Int) -> Unit = {
            _,
            _ ->
        },
    block: suspend () -> Unit
) {
    var failedAttempts =
        0

    while (
        isActive()
    ) {
        try {

            block()

            return

        } catch (
            error: CancellationException
        ) {
            throw error

        } catch (
            error: Throwable
        ) {

            failedAttempts +=
                1


            onFailure(
                error,
                failedAttempts
            )

            if (
                !isActive()
            ) {
                return
            }

            delayAfterFailure(
                failedAttempts
            )
        }
    }
}

internal class RealtimeAttemptConnectionTracker {

    private var sawConnecting =
        false

    fun observesSuccessfulConnection(
        state: RealtimeConnectionState
    ): Boolean {

        if (
            state ==
            RealtimeConnectionState.Connecting
        ) {
            sawConnecting =
                true

            return false
        }

        return sawConnecting &&
            state ==
            RealtimeConnectionState.Connected
    }
}

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

    suspend fun startWhenAvailable(
        scope: CoroutineScope
    ) {

        val previousJob =
            job

        awaitRealtimeRestartAvailability(
            previousJob
        )

        start(
            scope
        )
    }

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

        Log.i(
            TAG,
            "REALTIME_COORDINATOR_START"
        )

        job =
            scope.launch {

                launch {

                    runRealtimeGapRecoverySafely(
                        isActive = {
                            isActive
                        },
                        delayAfterFailure = {
                            attempt ->

                            val delayMillis =
                                calculateReplayBackoffMillis(
                                    attempt
                                )

                            Log.i(
                                TAG,
                                "REALTIME_GAP_RECOVERY_RETRY|attempt=$attempt|delayMillis=$delayMillis"
                            )

                            delay(
                                delayMillis
                            )
                        },
                        onFailure = {
                            error,
                            attempt ->

                            RelayDiagnostics.warning(
                                name =
                                    "realtime.gap_recovery.failure",
                                attributes =
                                    mapOf(
                                        "attempt" to
                                            attempt.toString(),
                                        "errorType" to
                                            error.javaClass
                                                .simpleName
                                    )
                            )
                        }

                    ) {

                        recoverSequenceGaps()
                    }
                }

                var failedAttempts = 0

                while (isActive) {

                    var connectedThisAttempt =
                        false

                    val connectionTracker =
                        RealtimeAttemptConnectionTracker()

                    _connectionState.value =
                        RealtimeConnectionState.Connecting

                    val stateJob =
                        launch {
                            source
                                .connectionState
                                .collect { state ->

                                    if (
                                        connectionTracker
                                            .observesSuccessfulConnection(
                                                state
                                            )
                                    ) {
                                        connectedThisAttempt =
                                            true

                                        failedAttempts = 0

                                        Log.i(
                                            TAG,
                                            "REALTIME_CONNECTED"
                                        )
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

                        RelayDiagnostics.warning(
                            name =
                                "realtime.collector.failure",
                            attributes =
                                mapOf(
                                    "errorType" to
                                        error.javaClass
                                            .simpleName
                                )
                        )

                        // Reconnect below.
                    } finally {
                        stopRealtimeStateCollector(
                            stateJob
                        )
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

                    Log.i(
                        TAG,
                        "REALTIME_RECONNECT_SCHEDULED|attempt=$failedAttempts|delayMillis=$delayMillis"
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
                ConcurrentHashMap<
                    String,
                    Job
                >()


            collectRealtimeReplayInputs(
                gaps =
                    gapDao.observeAll(),
                connectionState =
                    source.connectionState
            ) { gaps, state ->
val replayReconciliation =
                    calculateRealtimeReplayReconciliation(
                        gapIncidentIds =
                            gaps
                                .map {
                                    it.incidentId
                                }
                                .toSet(),
                        registeredReplayIncidentIds =
                            replayJobs
                                .keys
                                .toSet(),
                        activeReplayIncidentIds =
                            replayJobs
                                .entries
                                .filter {
                                    it.value.isActive
                                }
                                .map {
                                    it.key
                                }
                                .toSet(),
                        state =
                            state
                    )

                val replayExecution =
                    applyRealtimeReplayReconciliation(
                        replayJobs =
                            replayJobs,
                        reconciliation =
                            replayReconciliation
                    )

                replayExecution
                    .resolvedIncidentIdsStopped
                    .forEach { incidentId ->

                        Log.i(
                            TAG,
                            "REALTIME_REPLAY_STOPPED|incidentId=$incidentId|reason=gap_resolved"
                        )
                    }

                if (
                    replayExecution
                        .disconnectedReplayCount >
                    0
                ) {
                    Log.i(
                        TAG,
                        "REALTIME_REPLAY_STOPPED|count=${replayExecution.disconnectedReplayCount}|reason=disconnected"
                    )
                }

                if (
                    replayReconciliation
                        .stopAllForDisconnect
                ) {
                    return@collectRealtimeReplayInputs
                }

                gaps.forEach {
                    gap ->


                    if (
                        gap.incidentId !in
                        replayExecution.incidentIdsToStart
                    ) {
                        return@forEach
                    }
launchRegisteredRealtimeReplayJob(
                        replayJobs =
                            replayJobs,
                        incidentId =
                            gap.incidentId
                    ) {

                        runRealtimeReplayJobSafely(
                                    isConnected = {
                                        source
                                            .connectionState
                                            .value ==
                                        RealtimeConnectionState
                                            .Connected
                                    },
                                    delayAfterFailure = {
                                        attempt ->

                                        val delayMillis =
                                            calculateReplayBackoffMillis(
                                                attempt
                                            )

                                        Log.i(
                                            TAG,
                                            "REALTIME_REPLAY_RETRY|incidentId=${gap.incidentId}|attempt=$attempt|delayMillis=$delayMillis"
                                        )

                                        delay(
                                            delayMillis
                                        )
                                    },
                                    onFailure = {
                                        error,
                                        attempt ->

                                        RelayDiagnostics.warning(
                                            name =
                                                "realtime.replay.failure",
                                            attributes =
                                                mapOf(
                                                    "incidentId" to
                                                        gap.incidentId,
                                                    "attempt" to
                                                        attempt.toString(),
                                                    "errorType" to
                                                        error.javaClass
                                                            .simpleName
                                                )
                                        )
                                    }

                                ) {

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

        Log.i(
            TAG,
            "REALTIME_COORDINATOR_STOP"
        )

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
        private const val TAG =
            "RelayRealtimeCoordinator"

        private const val MAX_BACKOFF_MILLIS =
            30_000L

        private const val MAX_REPLAY_BACKOFF_MILLIS =
            10_000L
    }
}
