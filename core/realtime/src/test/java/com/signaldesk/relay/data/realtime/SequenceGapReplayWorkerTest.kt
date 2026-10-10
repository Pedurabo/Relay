package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.data.local.IncidentSequenceGapEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SequenceGapReplayWorkerTest {

    @Test
    fun retriesReplayAndRereadsGapUntilGapCloses() =
        runBlocking {

            var gap:
                IncidentSequenceGapEntity? =
                IncidentSequenceGapEntity(
                    incidentId =
                        "INC-1",
                    expectedSequence =
                        13L,
                    receivedSequence =
                        14L,
                    detectedAt =
                        1L
                )

            val requests =
                mutableListOf<
                    Triple<
                        String,
                        Long,
                        Long
                    >
                >()

            val attempts =
                mutableListOf<Int>()

            SequenceGapReplayWorker
                .run(
                    incidentId =
                        "INC-1",

                    isConnected = {
                        true
                    },

                    loadGap = {
                        gap
                    },

                    requestReplay = {
                        incidentId,
                        fromSequence,
                        throughSequence ->

                        requests +=
                            Triple(
                                incidentId,
                                fromSequence,
                                throughSequence
                            )
                    },

                    delayForAttempt = {
                        attempt ->

                        attempts +=
                            attempt

                        if (
                            attempt ==
                            1
                        ) {

                            gap =
                                IncidentSequenceGapEntity(
                                    incidentId =
                                        "INC-1",
                                    expectedSequence =
                                        13L,
                                    receivedSequence =
                                        16L,
                                    detectedAt =
                                        1L
                                )

                        } else {

                            gap =
                                null
                        }
                    }
                )

            assertEquals(
                listOf(
                    Triple(
                        "INC-1",
                        13L,
                        14L
                    ),
                    Triple(
                        "INC-1",
                        13L,
                        16L
                    )
                ),
                requests
            )

            assertEquals(
                listOf(
                    1,
                    2
                ),
                attempts
            )
        }



    @Test
    fun rereadsAdvancedExpectedSequenceAfterPartialConvergence() =
        runBlocking {

            var gap:
                IncidentSequenceGapEntity? =
                IncidentSequenceGapEntity(
                    incidentId =
                        "INC-PARTIAL-REPLAY",
                    expectedSequence =
                        13L,
                    receivedSequence =
                        16L,
                    detectedAt =
                        1L
                )

            val requests =
                mutableListOf<
                    Triple<
                        String,
                        Long,
                        Long
                    >
                >()

            SequenceGapReplayWorker
                .run(
                    incidentId =
                        "INC-PARTIAL-REPLAY",

                    isConnected = {
                        true
                    },

                    loadGap = {
                        gap
                    },

                    requestReplay = {
                        incidentId,
                        fromSequence,
                        throughSequence ->

                        requests +=
                            Triple(
                                incidentId,
                                fromSequence,
                                throughSequence
                            )
                    },

                    delayForAttempt = {
                        attempt ->

                        if (
                            attempt ==
                            1
                        ) {

                            /*
                             * Partial convergence consumed seq=13 and 14.
                             * The persisted authoritative gap is now 15..16.
                             */
                            gap =
                                IncidentSequenceGapEntity(
                                    incidentId =
                                        "INC-PARTIAL-REPLAY",
                                    expectedSequence =
                                        15L,
                                    receivedSequence =
                                        16L,
                                    detectedAt =
                                        1L
                                )

                        } else {

                            gap =
                                null
                        }
                    }
                )

            assertEquals(
                listOf(
                    Triple(
                        "INC-PARTIAL-REPLAY",
                        13L,
                        16L
                    ),
                    Triple(
                        "INC-PARTIAL-REPLAY",
                        15L,
                        16L
                    )
                ),
                requests
            )
        }

    @Test
    fun doesNotReplayAgainWhenGapClosesDuringBackoff() =
        runBlocking {

            var gap:
                IncidentSequenceGapEntity? =
                IncidentSequenceGapEntity(
                    incidentId =
                        "INC-CLOSE-DURING-BACKOFF",
                    expectedSequence =
                        13L,
                    receivedSequence =
                        16L,
                    detectedAt =
                        1L
                )

            val requests =
                mutableListOf<
                    Triple<
                        String,
                        Long,
                        Long
                    >
                >()

            val attempts =
                mutableListOf<Int>()

            SequenceGapReplayWorker
                .run(
                    incidentId =
                        "INC-CLOSE-DURING-BACKOFF",

                    isConnected = {
                        true
                    },

                    loadGap = {
                        gap
                    },

                    requestReplay = {
                        incidentId,
                        fromSequence,
                        throughSequence ->

                        requests +=
                            Triple(
                                incidentId,
                                fromSequence,
                                throughSequence
                            )
                    },

                    delayForAttempt = {
                        attempt ->

                        attempts +=
                            attempt

                        /*
                         * Another delivery path resolves the incident while
                         * this worker is in backoff after its first request.
                         */
                        gap =
                            null
                    }
                )

            assertEquals(
                listOf(
                    Triple(
                        "INC-CLOSE-DURING-BACKOFF",
                        13L,
                        16L
                    )
                ),
                requests
            )

            assertEquals(
                listOf(
                    1
                ),
                attempts
            )
        }

    @Test
    fun disconnectDuringBackoffLeavesOpenGapForFutureRecovery() =
        runBlocking {

            var connected =
                true

            var gap:
                IncidentSequenceGapEntity? =
                IncidentSequenceGapEntity(
                    incidentId =
                        "INC-DISCONNECT-BACKOFF",
                    expectedSequence =
                        21L,
                    receivedSequence =
                        24L,
                    detectedAt =
                        1L
                )

            val requests =
                mutableListOf<
                    Triple<
                        String,
                        Long,
                        Long
                    >
                >()

            val attempts =
                mutableListOf<Int>()

            SequenceGapReplayWorker
                .run(
                    incidentId =
                        "INC-DISCONNECT-BACKOFF",

                    isConnected = {
                        connected
                    },

                    loadGap = {
                        gap
                    },

                    requestReplay = {
                        incidentId,
                        fromSequence,
                        throughSequence ->

                        requests +=
                            Triple(
                                incidentId,
                                fromSequence,
                                throughSequence
                            )
                    },

                    delayForAttempt = {
                        attempt ->

                        attempts +=
                            attempt

                        /*
                         * Connection drops while the persisted gap remains
                         * unresolved. This worker must stop and leave the gap
                         * available for a future reconnect/replay cycle.
                         */
                        connected =
                            false
                    }
                )

            assertEquals(
                listOf(
                    Triple(
                        "INC-DISCONNECT-BACKOFF",
                        21L,
                        24L
                    )
                ),
                requests
            )

            assertEquals(
                listOf(
                    1
                ),
                attempts
            )

            assertEquals(
                IncidentSequenceGapEntity(
                    incidentId =
                        "INC-DISCONNECT-BACKOFF",
                    expectedSequence =
                        21L,
                    receivedSequence =
                        24L,
                    detectedAt =
                        1L
                ),
                gap
            )
        }
    @Test
    fun stopsRetryingWhenConnectionDropsEvenIfGapRemains() =
        runBlocking {

            var connected =
                true

            val gap =
                IncidentSequenceGapEntity(
                    incidentId =
                        "INC-2",
                    expectedSequence =
                        8L,
                    receivedSequence =
                        10L,
                    detectedAt =
                        2L
                )

            var requestCount =
                0

            SequenceGapReplayWorker
                .run(
                    incidentId =
                        "INC-2",

                    isConnected = {
                        connected
                    },

                    loadGap = {
                        gap
                    },

                    requestReplay = {
                        _,
                        _,
                        _ ->

                        requestCount +=
                            1
                    },

                    delayForAttempt = {
                        connected =
                            false
                    }
                )

            assertEquals(
                1,
                requestCount
            )
        }

    @Test
    fun persistentIncidentUsesCurrentBoundsInEveryConnectionEpoch() =
        runBlocking {

            val incidentId =
                "incident-A"

            /*
             * Epoch 1 durable range.
             */
            var connectedEpoch1 =
                true

            var epoch1LoadCount =
                0

            val epoch1Requests =
                mutableListOf<Pair<Long, Long>>()

            SequenceGapReplayWorker.run(
                incidentId =
                    incidentId,
                isConnected = {
                    connectedEpoch1
                },
                loadGap = { _: String ->
                    epoch1LoadCount += 1

                    if (epoch1LoadCount == 1) {
                        IncidentSequenceGapEntity(
                            incidentId =
                                incidentId,
                            expectedSequence =
                                10L,
                            receivedSequence =
                                14L,
                            detectedAt =
                                1L
                        )
                    }
                    else {
                        null
                    }
                },
                requestReplay = { _: String, fromSequence: Long, throughSequence: Long ->
                    epoch1Requests +=
                        fromSequence to throughSequence
                },
                delayForAttempt = { _: Int ->
                    connectedEpoch1 =
                        false
                }
            )

            assertEquals(
                listOf(
                    10L to 14L
                ),
                epoch1Requests
            )

            /*
             * The incident remains unresolved across the disconnect,
             * but durable storage advances its bounds before epoch 2.
             *
             * A fresh replay worker must read 12..18. It must not reuse
             * 10..14 from the previous connection epoch.
             */
            var connectedEpoch2 =
                true

            var epoch2LoadCount =
                0

            val epoch2Requests =
                mutableListOf<Pair<Long, Long>>()

            SequenceGapReplayWorker.run(
                incidentId =
                    incidentId,
                isConnected = {
                    connectedEpoch2
                },
                loadGap = { _: String ->
                    epoch2LoadCount += 1

                    if (epoch2LoadCount == 1) {
                        IncidentSequenceGapEntity(
                            incidentId =
                                incidentId,
                            expectedSequence =
                                12L,
                            receivedSequence =
                                18L,
                            detectedAt =
                                2L
                        )
                    }
                    else {
                        null
                    }
                },
                requestReplay = { _: String, fromSequence: Long, throughSequence: Long ->
                    epoch2Requests +=
                        fromSequence to throughSequence
                },
                delayForAttempt = { _: Int ->
                    connectedEpoch2 =
                        false
                }
            )

            assertEquals(
                listOf(
                    12L to 18L
                ),
                epoch2Requests
            )

            assertFalse(
                epoch2Requests.contains(
                    10L to 14L
                )
            )

            /*
             * The same incident persists into a third epoch with yet
             * another authoritative durable range.
             */
            var connectedEpoch3 =
                true

            var epoch3LoadCount =
                0

            val epoch3Requests =
                mutableListOf<Pair<Long, Long>>()

            SequenceGapReplayWorker.run(
                incidentId =
                    incidentId,
                isConnected = {
                    connectedEpoch3
                },
                loadGap = { _: String ->
                    epoch3LoadCount += 1

                    if (epoch3LoadCount == 1) {
                        IncidentSequenceGapEntity(
                            incidentId =
                                incidentId,
                            expectedSequence =
                                17L,
                            receivedSequence =
                                21L,
                            detectedAt =
                                3L
                        )
                    }
                    else {
                        null
                    }
                },
                requestReplay = { _: String, fromSequence: Long, throughSequence: Long ->
                    epoch3Requests +=
                        fromSequence to throughSequence
                },
                delayForAttempt = { _: Int ->
                    connectedEpoch3 =
                        false
                }
            )

            assertEquals(
                listOf(
                    17L to 21L
                ),
                epoch3Requests
            )

            assertFalse(
                epoch3Requests.contains(
                    12L to 18L
                )
            )

            assertFalse(
                epoch3Requests.contains(
                    10L to 14L
                )
            )
        }
}
