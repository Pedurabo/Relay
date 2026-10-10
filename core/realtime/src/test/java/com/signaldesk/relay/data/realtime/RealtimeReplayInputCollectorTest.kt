package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.data.local.IncidentSequenceGapEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeReplayInputCollectorTest {

    @Test
    fun emitsDurableGapChangeWithoutConnectionMutation() =
        runBlocking {

            val gap =
                IncidentSequenceGapEntity(
                    incidentId =
                        "INC-INPUT-GAP",
                    expectedSequence =
                        11L,
                    receivedSequence =
                        14L,
                    detectedAt =
                        1L
                )

            val gaps =
                MutableStateFlow<
                    List<IncidentSequenceGapEntity>
                >(
                    emptyList()
                )

            val state =
                MutableStateFlow<RealtimeConnectionState>(
                    RealtimeConnectionState.Connected
                )

            val firstEmission =
                CompletableDeferred<Unit>()

            val secondEmission =
                CompletableDeferred<Unit>()

            val observed =
                mutableListOf<
                    Pair<
                        List<IncidentSequenceGapEntity>,
                        RealtimeConnectionState
                    >
                >()

            val collector =
                launch {

                    collectRealtimeReplayInputs(
                        gaps =
                            gaps,
                        connectionState =
                            state
                    ) {
                        currentGaps,
                        currentState ->

                        observed +=
                            currentGaps to
                                currentState

                        when (observed.size) {

                            1 ->
                                firstEmission.complete(
                                    Unit
                                )

                            2 ->
                                secondEmission.complete(
                                    Unit
                                )
                        }
                    }
                }

            firstEmission.await()

            gaps.value =
                listOf(
                    gap
                )

            secondEmission.await()

            assertEquals(
                2,
                observed.size
            )

            assertEquals(
                emptyList<IncidentSequenceGapEntity>(),
                observed[0].first
            )

            assertEquals(
                RealtimeConnectionState.Connected,
                observed[0].second
            )

            assertEquals(
                listOf(
                    gap
                ),
                observed[1].first
            )

            assertEquals(
                RealtimeConnectionState.Connected,
                observed[1].second
            )

            collector.cancelAndJoin()
        }

    @Test
    fun emitsConnectionChangeWithoutDurableGapMutation() =
        runBlocking {

            val gap =
                IncidentSequenceGapEntity(
                    incidentId =
                        "INC-INPUT-STATE",
                    expectedSequence =
                        21L,
                    receivedSequence =
                        24L,
                    detectedAt =
                        2L
                )

            val gaps =
                MutableStateFlow(
                    listOf(
                        gap
                    )
                )

            val state =
                MutableStateFlow<RealtimeConnectionState>(
                    RealtimeConnectionState.Disconnected
                )

            val firstEmission =
                CompletableDeferred<Unit>()

            val secondEmission =
                CompletableDeferred<Unit>()

            val observedStates =
                mutableListOf<
                    RealtimeConnectionState
                >()

            val observedGaps =
                mutableListOf<
                    List<IncidentSequenceGapEntity>
                >()

            val collector =
                launch {

                    collectRealtimeReplayInputs(
                        gaps =
                            gaps,
                        connectionState =
                            state
                    ) {
                        currentGaps,
                        currentState ->

                        observedGaps +=
                            currentGaps

                        observedStates +=
                            currentState

                        when (observedStates.size) {

                            1 ->
                                firstEmission.complete(
                                    Unit
                                )

                            2 ->
                                secondEmission.complete(
                                    Unit
                                )
                        }
                    }
                }

            firstEmission.await()

            state.value =
                RealtimeConnectionState.Connected

            secondEmission.await()

            assertEquals(
                listOf(
                    RealtimeConnectionState.Disconnected,
                    RealtimeConnectionState.Connected
                ),
                observedStates
            )

            assertEquals(
                listOf(
                    gap
                ),
                observedGaps[0]
            )

            assertEquals(
                listOf(
                    gap
                ),
                observedGaps[1]
            )

            collector.cancelAndJoin()
        }

    @Test
    fun eachEmissionCarriesLatestValuesFromBothAuthoritativeFlows() =
        runBlocking {

            val firstGap =
                IncidentSequenceGapEntity(
                    incidentId =
                        "INC-INPUT-LATEST",
                    expectedSequence =
                        31L,
                    receivedSequence =
                        35L,
                    detectedAt =
                        3L
                )

            val updatedGap =
                IncidentSequenceGapEntity(
                    incidentId =
                        "INC-INPUT-LATEST",
                    expectedSequence =
                        33L,
                    receivedSequence =
                        38L,
                    detectedAt =
                        4L
                )

            val gaps =
                MutableStateFlow(
                    listOf(
                        firstGap
                    )
                )

            val state =
                MutableStateFlow<RealtimeConnectionState>(
                    RealtimeConnectionState.Disconnected
                )

            val emissionCount =
                CompletableDeferred<Unit>()

            val observed =
                mutableListOf<
                    Pair<
                        List<IncidentSequenceGapEntity>,
                        RealtimeConnectionState
                    >
                >()

            val collector =
                launch {

                    collectRealtimeReplayInputs(
                        gaps =
                            gaps,
                        connectionState =
                            state
                    ) {
                        currentGaps,
                        currentState ->

                        observed +=
                            currentGaps to
                                currentState

                        if (
                            observed.size ==
                            3
                        ) {
                            emissionCount.complete(
                                Unit
                            )
                        }
                    }
                }

            while (observed.isEmpty()) {
                kotlinx.coroutines.yield()
            }

            gaps.value =
                listOf(
                    updatedGap
                )

            while (observed.size < 2) {
                kotlinx.coroutines.yield()
            }

            state.value =
                RealtimeConnectionState.Connected

            emissionCount.await()

            assertEquals(
                listOf(
                    updatedGap
                ),
                observed[2].first
            )

            assertEquals(
                RealtimeConnectionState.Connected,
                observed[2].second
            )

            collector.cancelAndJoin()
        }
}