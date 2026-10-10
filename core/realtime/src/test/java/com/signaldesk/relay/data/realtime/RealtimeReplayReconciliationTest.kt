package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeReplayReconciliationTest {

    @Test
    fun connectedStartsOnlyGapsWithoutActiveOwners() {

        val result =
            calculateRealtimeReplayReconciliation(
                gapIncidentIds =
                    setOf(
                        "A",
                        "B",
                        "C"
                    ),
                registeredReplayIncidentIds =
                    setOf(
                        "A",
                        "B"
                    ),
                activeReplayIncidentIds =
                    setOf(
                        "A"
                    ),
                state =
                    RealtimeConnectionState.Connected
            )

        assertEquals(
            emptySet<String>(),
            result.resolvedIncidentIdsToStop
        )

        assertFalse(
            result.stopAllForDisconnect
        )

        assertEquals(
            setOf(
                "B",
                "C"
            ),
            result.incidentIdsToStart
        )
    }

    @Test
    fun connectedStopsOwnersWhoseDurableGapsResolved() {

        val result =
            calculateRealtimeReplayReconciliation(
                gapIncidentIds =
                    setOf(
                        "B"
                    ),
                registeredReplayIncidentIds =
                    setOf(
                        "A",
                        "B"
                    ),
                activeReplayIncidentIds =
                    setOf(
                        "A",
                        "B"
                    ),
                state =
                    RealtimeConnectionState.Connected
            )

        assertEquals(
            setOf(
                "A"
            ),
            result.resolvedIncidentIdsToStop
        )

        assertEquals(
            emptySet<String>(),
            result.incidentIdsToStart
        )
    }

    @Test
    fun completedRegisteredOwnerIsEligibleForReplacement() {

        val result =
            calculateRealtimeReplayReconciliation(
                gapIncidentIds =
                    setOf(
                        "A"
                    ),
                registeredReplayIncidentIds =
                    setOf(
                        "A"
                    ),
                activeReplayIncidentIds =
                    emptySet(),
                state =
                    RealtimeConnectionState.Connected
            )

        assertEquals(
            emptySet<String>(),
            result.resolvedIncidentIdsToStop
        )

        assertEquals(
            setOf(
                "A"
            ),
            result.incidentIdsToStart
        )
    }

    @Test
    fun disconnectedStartsNothingAndRequestsEpochShutdown() {

        val result =
            calculateRealtimeReplayReconciliation(
                gapIncidentIds =
                    setOf(
                        "A",
                        "B"
                    ),
                registeredReplayIncidentIds =
                    setOf(
                        "A",
                        "B"
                    ),
                activeReplayIncidentIds =
                    setOf(
                        "A",
                        "B"
                    ),
                state =
                    RealtimeConnectionState.Disconnected
            )

        assertEquals(
            emptySet<String>(),
            result.resolvedIncidentIdsToStop
        )

        assertTrue(
            result.stopAllForDisconnect
        )

        assertEquals(
            emptySet<String>(),
            result.incidentIdsToStart
        )
    }

    @Test
    fun disconnectedStillIdentifiesResolvedOwnersBeforeEpochShutdown() {

        val result =
            calculateRealtimeReplayReconciliation(
                gapIncidentIds =
                    setOf(
                        "B",
                        "C"
                    ),
                registeredReplayIncidentIds =
                    setOf(
                        "A",
                        "B"
                    ),
                activeReplayIncidentIds =
                    setOf(
                        "A",
                        "B"
                    ),
                state =
                    RealtimeConnectionState.Disconnected
            )

        assertEquals(
            setOf(
                "A"
            ),
            result.resolvedIncidentIdsToStop
        )

        assertTrue(
            result.stopAllForDisconnect
        )

        assertEquals(
            emptySet<String>(),
            result.incidentIdsToStart
        )
    }

    @Test
    fun mixedEvolutionProducesExactStopAndStartSets() {

        val result =
            calculateRealtimeReplayReconciliation(
                gapIncidentIds =
                    setOf(
                        "B",
                        "C",
                        "D"
                    ),
                registeredReplayIncidentIds =
                    setOf(
                        "A",
                        "B",
                        "C"
                    ),
                activeReplayIncidentIds =
                    setOf(
                        "A",
                        "B"
                    ),
                state =
                    RealtimeConnectionState.Connected
            )

        assertEquals(
            setOf(
                "A"
            ),
            result.resolvedIncidentIdsToStop
        )

        assertFalse(
            result.stopAllForDisconnect
        )

        assertEquals(
            setOf(
                "C",
                "D"
            ),
            result.incidentIdsToStart
        )
    }

    @Test
    fun coordinatorParityResolvedOwnerStopsWhileSurvivingOwnerRemains() {

        val result =
            calculateRealtimeReplayReconciliation(
                gapIncidentIds =
                    setOf(
                        "B"
                    ),
                registeredReplayIncidentIds =
                    setOf(
                        "A",
                        "B"
                    ),
                activeReplayIncidentIds =
                    setOf(
                        "A",
                        "B"
                    ),
                state =
                    RealtimeConnectionState.Connected
            )

        assertEquals(
            setOf(
                "A"
            ),
            result.resolvedIncidentIdsToStop
        )

        assertFalse(
            result.stopAllForDisconnect
        )

        assertEquals(
            emptySet<String>(),
            result.incidentIdsToStart
        )
    }

    @Test
    fun coordinatorParityInactiveRegisteredOwnerRestartsWhenGapStillExists() {

        val result =
            calculateRealtimeReplayReconciliation(
                gapIncidentIds =
                    setOf(
                        "A"
                    ),
                registeredReplayIncidentIds =
                    setOf(
                        "A"
                    ),
                activeReplayIncidentIds =
                    emptySet(),
                state =
                    RealtimeConnectionState.Connected
            )

        assertEquals(
            emptySet<String>(),
            result.resolvedIncidentIdsToStop
        )

        assertFalse(
            result.stopAllForDisconnect
        )

        assertEquals(
            setOf(
                "A"
            ),
            result.incidentIdsToStart
        )
    }

    @Test
    fun coordinatorParityDisconnectSuppressesEveryStartEvenForUnownedGaps() {

        val result =
            calculateRealtimeReplayReconciliation(
                gapIncidentIds =
                    setOf(
                        "A",
                        "B",
                        "C"
                    ),
                registeredReplayIncidentIds =
                    setOf(
                        "A"
                    ),
                activeReplayIncidentIds =
                    emptySet(),
                state =
                    RealtimeConnectionState.Disconnected
            )

        assertEquals(
            emptySet<String>(),
            result.resolvedIncidentIdsToStop
        )

        assertTrue(
            result.stopAllForDisconnect
        )

        assertEquals(
            emptySet<String>(),
            result.incidentIdsToStart
        )
    }

    @Test
    fun coordinatorParityReconnectRebuildsAllCurrentlyUnownedDurableGaps() {

        val result =
            calculateRealtimeReplayReconciliation(
                gapIncidentIds =
                    setOf(
                        "A",
                        "C"
                    ),
                registeredReplayIncidentIds =
                    emptySet(),
                activeReplayIncidentIds =
                    emptySet(),
                state =
                    RealtimeConnectionState.Connected
            )

        assertEquals(
            emptySet<String>(),
            result.resolvedIncidentIdsToStop
        )

        assertFalse(
            result.stopAllForDisconnect
        )

        assertEquals(
            setOf(
                "A",
                "C"
            ),
            result.incidentIdsToStart
        )
    }

    @Test
    fun coordinatorParityMixedSetEvolutionStopsResolvedAndStartsOnlyUnowned() {

        val result =
            calculateRealtimeReplayReconciliation(
                gapIncidentIds =
                    setOf(
                        "B",
                        "C",
                        "D"
                    ),
                registeredReplayIncidentIds =
                    setOf(
                        "A",
                        "B",
                        "C"
                    ),
                activeReplayIncidentIds =
                    setOf(
                        "A",
                        "B"
                    ),
                state =
                    RealtimeConnectionState.Connected
            )

        assertEquals(
            setOf(
                "A"
            ),
            result.resolvedIncidentIdsToStop
        )

        assertFalse(
            result.stopAllForDisconnect
        )

        assertEquals(
            setOf(
                "C",
                "D"
            ),
            result.incidentIdsToStart
        )
    }
}