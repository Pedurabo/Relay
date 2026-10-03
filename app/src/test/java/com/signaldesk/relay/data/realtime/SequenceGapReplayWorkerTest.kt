package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.data.local.IncidentSequenceGapEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
