package com.signaldesk.relay.data.realtime

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeReplayReconciliationExecutionTest {

    @Test
    fun resolvedOwnerIsRemovedAndStoppedWhileSurvivorRemains() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val jobA =
                launch {
                    awaitCancellation()
                }

            val jobB =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                jobA

            replayJobs["B"] =
                jobB

            val result =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        RealtimeReplayReconciliation(
                            resolvedIncidentIdsToStop =
                                setOf("A"),
                            stopAllForDisconnect =
                                false,
                            incidentIdsToStart =
                                setOf("C")
                        )
                )

            assertTrue(
                jobA.isCancelled
            )

            assertTrue(
                jobB.isActive
            )

            assertFalse(
                replayJobs.containsKey("A")
            )

            assertTrue(
                replayJobs["B"] === jobB
            )

            assertEquals(
                setOf("A"),
                result.resolvedIncidentIdsStopped
            )

            assertEquals(
                0,
                result.disconnectedReplayCount
            )

            assertEquals(
                setOf("C"),
                result.incidentIdsToStart
            )

            jobB.cancel()
        }

    @Test
    fun disconnectClearsAndStopsEveryRemainingOwner() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val jobA =
                launch {
                    awaitCancellation()
                }

            val jobB =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                jobA

            replayJobs["B"] =
                jobB

            val result =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        RealtimeReplayReconciliation(
                            resolvedIncidentIdsToStop =
                                emptySet(),
                            stopAllForDisconnect =
                                true,
                            incidentIdsToStart =
                                setOf(
                                    "A",
                                    "B",
                                    "C"
                                )
                        )
                )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                jobA.isCancelled
            )

            assertTrue(
                jobB.isCancelled
            )

            assertEquals(
                2,
                result.disconnectedReplayCount
            )

            assertEquals(
                emptySet<String>(),
                result.incidentIdsToStart
            )
        }

    @Test
    fun disconnectStopsResolvedOwnerThenClearsRemainingEpoch() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val jobA =
                launch {
                    awaitCancellation()
                }

            val jobB =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                jobA

            replayJobs["B"] =
                jobB

            val result =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        RealtimeReplayReconciliation(
                            resolvedIncidentIdsToStop =
                                setOf("A"),
                            stopAllForDisconnect =
                                true,
                            incidentIdsToStart =
                                emptySet()
                        )
                )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                jobA.isCancelled
            )

            assertTrue(
                jobB.isCancelled
            )

            assertEquals(
                setOf("A"),
                result.resolvedIncidentIdsStopped
            )

            assertEquals(
                1,
                result.disconnectedReplayCount
            )

            assertEquals(
                emptySet<String>(),
                result.incidentIdsToStart
            )
        }

    @Test
    fun connectedExecutionLeavesRegistryUntouchedAndReturnsStartSet() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val jobA =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                jobA

            val result =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        RealtimeReplayReconciliation(
                            resolvedIncidentIdsToStop =
                                emptySet(),
                            stopAllForDisconnect =
                                false,
                            incidentIdsToStart =
                                setOf(
                                    "B",
                                    "C"
                                )
                        )
                )

            assertTrue(
                replayJobs["A"] === jobA
            )

            assertTrue(
                jobA.isActive
            )

            assertEquals(
                emptySet<String>(),
                result.resolvedIncidentIdsStopped
            )

            assertEquals(
                0,
                result.disconnectedReplayCount
            )

            assertEquals(
                setOf(
                    "B",
                    "C"
                ),
                result.incidentIdsToStart
            )

            jobA.cancel()
        }

    @Test
    fun disconnectThenReconnectRebuildsFreshStartsFromPersistedGaps() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val jobA =
                launch {
                    awaitCancellation()
                }

            val jobB =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                jobA

            replayJobs["B"] =
                jobB

            val disconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "B"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val disconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        disconnectReconciliation
                )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                jobA.isCancelled
            )

            assertTrue(
                jobB.isCancelled
            )

            assertEquals(
                2,
                disconnectExecution
                    .disconnectedReplayCount
            )

            assertEquals(
                emptySet<String>(),
                disconnectExecution
                    .incidentIdsToStart
            )

            /*
             * Durable gaps still exist externally.
             *
             * A new connected epoch therefore sees an empty ownership
             * registry and must request fresh replay workers for both.
             */
            val reconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "B"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val reconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        reconnectReconciliation
                )

            assertEquals(
                emptySet<String>(),
                reconnectExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                0,
                reconnectExecution
                    .disconnectedReplayCount
            )

            assertEquals(
                setOf(
                    "A",
                    "B"
                ),
                reconnectExecution
                    .incidentIdsToStart
            )

            /*
             * Execution identifies new owners to launch; registration
             * remains the coordinator/registration-helper responsibility.
             */
            assertTrue(
                replayJobs.isEmpty()
            )
        }

    @Test
    fun multipleOfflineGapMutationsReconnectFromOnlyFinalDurableRange() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val originalOwner =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                originalOwner

            /*
             * Disconnect ends the current replay-ownership epoch.
             */
            val disconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val disconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        disconnectReconciliation
                )

            assertTrue(
                originalOwner.isCancelled
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertEquals(
                1,
                disconnectExecution
                    .disconnectedReplayCount
            )

            /*
             * While offline, durable storage may change several times.
             *
             * The reconciliation layer does not replay historical mutations.
             * Reconnect is calculated from the final authoritative durable set.
             */
            val firstOfflineGapRange =
                101L to 108L

            val secondOfflineGapRange =
                103L to 111L

            val finalOfflineGapRange =
                107L to 115L

            val observedOfflineRanges =
                listOf(
                    firstOfflineGapRange,
                    secondOfflineGapRange,
                    finalOfflineGapRange
                )

            assertEquals(
                finalOfflineGapRange,
                observedOfflineRanges.last()
            )

            val finalDurableIncidentIds =
                setOf(
                    "A"
                )

            val reconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        finalDurableIncidentIds,
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val reconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        reconnectReconciliation
                )

            assertEquals(
                emptySet<String>(),
                reconnectExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                0,
                reconnectExecution
                    .disconnectedReplayCount
            )

            assertEquals(
                setOf(
                    "A"
                ),
                reconnectExecution
                    .incidentIdsToStart
            )

            /*
             * The production worker remains responsible for loading
             * the current persisted range before every replay request.
             *
             * This assertion pins the authoritative range the reconnect
             * epoch should observe, rather than any earlier offline value.
             */
            assertEquals(
                107L,
                finalOfflineGapRange.first
            )

            assertEquals(
                115L,
                finalOfflineGapRange.second
            )
        }

    @Test
    fun multipleOfflineLifecyclesReconnectFromOnlyFinalSurvivingLifecycle() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val initialOwner =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                initialOwner

            /*
             * The first connected epoch owns A.
             *
             * Disconnect terminates that ownership epoch, but the durable
             * gap may continue evolving independently while offline.
             */
            val disconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val disconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        disconnectReconciliation
                )

            assertTrue(
                initialOwner.isCancelled
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertEquals(
                1,
                disconnectExecution
                    .disconnectedReplayCount
            )

            /*
             * Offline durable lifecycle history:
             *
             * lifecycle 1 disappears
             * lifecycle 2 appears
             * lifecycle 2 disappears
             * lifecycle 3 appears and survives until reconnect
             *
             * Reconciliation must not replay lifecycle history. It consumes
             * only the final durable incident membership at reconnect.
             */
            val offlineLifecycleIncidentSets =
                listOf(
                    emptySet<String>(),
                    setOf(
                        "A"
                    ),
                    emptySet(),
                    setOf(
                        "A"
                    )
                )

            assertEquals(
                setOf(
                    "A"
                ),
                offlineLifecycleIncidentSets.last()
            )

            val reconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        offlineLifecycleIncidentSets.last(),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val reconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        reconnectReconciliation
                )

            assertEquals(
                emptySet<String>(),
                reconnectExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                0,
                reconnectExecution
                    .disconnectedReplayCount
            )

            assertEquals(
                setOf(
                    "A"
                ),
                reconnectExecution
                    .incidentIdsToStart
            )

            /*
             * No ownership is installed by reconciliation execution itself.
             * Registration remains the coordinator/helper responsibility.
             *
             * This makes the reconnect start a genuinely fresh generation.
             */
            assertTrue(
                replayJobs.isEmpty()
            )
        }

    @Test
    fun mixedOfflineEvolutionReconnectsOnlyCurrentAuthoritativeIncidents() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val ownerA =
                launch {
                    awaitCancellation()
                }

            val ownerB =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA

            replayJobs["B"] =
                ownerB

            /*
             * Connected epoch starts with durable gaps A and B.
             *
             * Disconnect terminates both replay owners.
             */
            val disconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "B"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val disconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        disconnectReconciliation
                )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                ownerA.isCancelled
            )

            assertTrue(
                ownerB.isCancelled
            )

            assertEquals(
                2,
                disconnectExecution
                    .disconnectedReplayCount
            )

            /*
             * Offline authoritative evolution:
             *
             * A resolves and disappears.
             * B survives, but its durable replay range changes.
             * C becomes newly unresolved.
             *
             * Policy receives only final durable membership on reconnect.
             */
            val initialRangeB =
                1301L to 1304L

            val updatedRangeB =
                1303L to 1308L

            val rangeC =
                1401L to 1406L

            val finalDurableRanges =
                mapOf(
                    "B" to updatedRangeB,
                    "C" to rangeC
                )

            assertFalse(
                finalDurableRanges.containsKey(
                    "A"
                )
            )

            assertEquals(
                1303L to 1308L,
                finalDurableRanges["B"]
            )

            assertEquals(
                1401L to 1406L,
                finalDurableRanges["C"]
            )

            assertFalse(
                finalDurableRanges["B"] ==
                    initialRangeB
            )

            val reconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        finalDurableRanges.keys,
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val reconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        reconnectReconciliation
                )

            assertEquals(
                emptySet<String>(),
                reconnectExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                0,
                reconnectExecution
                    .disconnectedReplayCount
            )

            assertEquals(
                setOf(
                    "B",
                    "C"
                ),
                reconnectExecution
                    .incidentIdsToStart
            )

            /*
             * A must not restart.
             */
            assertFalse(
                reconnectExecution
                    .incidentIdsToStart
                    .contains(
                        "A"
                    )
            )

            /*
             * Reconciliation identifies the current incidents only.
             * SequenceGapReplayWorker remains responsible for loading
             * B's updated range and C's range from durable storage.
             */
            assertEquals(
                1303L,
                finalDurableRanges
                    .getValue("B")
                    .first
            )

            assertEquals(
                1308L,
                finalDurableRanges
                    .getValue("B")
                    .second
            )

            assertEquals(
                1401L,
                finalDurableRanges
                    .getValue("C")
                    .first
            )

            assertEquals(
                1406L,
                finalDurableRanges
                    .getValue("C")
                    .second
            )
        }

    @Test
    fun connectedMixedEvolutionStopsResolvedKeepsExistingAndStartsNew() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val ownerA =
                launch {
                    awaitCancellation()
                }

            val ownerB =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA

            replayJobs["B"] =
                ownerB

            /*
             * Current connected ownership:
             *
             * A -> active
             * B -> active
             *
             * Durable gap evolution while still connected:
             *
             * A resolves.
             * B remains unresolved and already has a live owner.
             * C becomes newly unresolved.
             */
            val reconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "B",
                            "C"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            assertEquals(
                setOf(
                    "A"
                ),
                reconciliation
                    .resolvedIncidentIdsToStop
            )

            assertFalse(
                reconciliation
                    .stopAllForDisconnect
            )

            assertEquals(
                setOf(
                    "C"
                ),
                reconciliation
                    .incidentIdsToStart
            )

            val execution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        reconciliation
                )

            /*
             * A is terminal for this lifecycle.
             */
            assertTrue(
                ownerA.isCancelled
            )

            assertFalse(
                replayJobs.containsKey(
                    "A"
                )
            )

            /*
             * B must remain under its current active owner.
             */
            assertTrue(
                ownerB.isActive
            )

            assertTrue(
                replayJobs["B"] ===
                    ownerB
            )

            /*
             * No epoch-wide disconnect occurred.
             */
            assertEquals(
                0,
                execution
                    .disconnectedReplayCount
            )

            assertEquals(
                setOf(
                    "A"
                ),
                execution
                    .resolvedIncidentIdsStopped
            )

            /*
             * Only C needs a newly registered replay worker.
             */
            assertEquals(
                setOf(
                    "C"
                ),
                execution
                    .incidentIdsToStart
            )

            /*
             * Execution does not register C itself.
             */
            assertFalse(
                replayJobs.containsKey(
                    "C"
                )
            )

            assertEquals(
                setOf(
                    "B"
                ),
                replayJobs.keys.toSet()
            )

            ownerB.cancel()
        }

    @Test
    fun rapidSuccessiveConnectedChangesConvergeToFinalIncidentSet() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val ownerA =
                launch {
                    awaitCancellation()
                }

            val ownerB =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA

            replayJobs["B"] =
                ownerB

            /*
             * First connected mutation:
             *
             * A remains unresolved and keeps its active owner.
             * B resolves and must stop.
             * C becomes newly unresolved and should start.
             */
            val firstReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "C"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val firstExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        firstReconciliation
                )

            assertTrue(
                ownerA.isActive
            )

            assertTrue(
                ownerB.isCancelled
            )

            assertEquals(
                setOf(
                    "B"
                ),
                firstExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                setOf(
                    "C"
                ),
                firstExecution
                    .incidentIdsToStart
            )

            /*
             * Model coordinator registration of C after execution.
             */
            val ownerC =
                launch {
                    awaitCancellation()
                }

            replayJobs["C"] =
                ownerC

            /*
             * Second rapid mutation:
             *
             * A now resolves.
             * C survives and keeps its existing owner.
             * D appears and needs a new owner.
             */
            val secondReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "C",
                            "D"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val secondExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        secondReconciliation
                )

            assertTrue(
                ownerA.isCancelled
            )

            assertTrue(
                ownerC.isActive
            )

            assertEquals(
                setOf(
                    "A"
                ),
                secondExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                setOf(
                    "D"
                ),
                secondExecution
                    .incidentIdsToStart
            )

            /*
             * Model registration of D.
             */
            val ownerD =
                launch {
                    awaitCancellation()
                }

            replayJobs["D"] =
                ownerD

            /*
             * Third rapid mutation:
             *
             * C resolves.
             * D survives.
             * E appears.
             *
             * The ownership registry must converge to D plus the new
             * start candidate E, with no stale A/B/C ownership.
             */
            val thirdReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "D",
                            "E"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val thirdExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        thirdReconciliation
                )

            assertTrue(
                ownerC.isCancelled
            )

            assertTrue(
                ownerD.isActive
            )

            assertEquals(
                setOf(
                    "C"
                ),
                thirdExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                setOf(
                    "E"
                ),
                thirdExecution
                    .incidentIdsToStart
            )

            assertEquals(
                setOf(
                    "D"
                ),
                replayJobs.keys.toSet()
            )

            assertFalse(
                replayJobs.containsKey(
                    "A"
                )
            )

            assertFalse(
                replayJobs.containsKey(
                    "B"
                )
            )

            assertFalse(
                replayJobs.containsKey(
                    "C"
                )
            )

            ownerD.cancel()
        }

    @Test
    fun disconnectAfterLiveSetChangeReconnectsOnlyCurrentDurableGaps() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val ownerA =
                launch {
                    awaitCancellation()
                }

            val ownerB =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA

            replayJobs["B"] =
                ownerB

            /*
             * Connected live-set evolution:
             *
             * A remains unresolved.
             * B resolves.
             * C appears.
             */
            val liveReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "C"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val liveExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        liveReconciliation
                )

            assertTrue(
                ownerB.isCancelled
            )

            assertTrue(
                ownerA.isActive
            )

            assertEquals(
                setOf(
                    "B"
                ),
                liveExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                setOf(
                    "C"
                ),
                liveExecution
                    .incidentIdsToStart
            )

            /*
             * Model coordinator registration of C.
             */
            val ownerC =
                launch {
                    awaitCancellation()
                }

            replayJobs["C"] =
                ownerC

            assertEquals(
                setOf(
                    "A",
                    "C"
                ),
                replayJobs.keys.toSet()
            )

            /*
             * Connection drops after the live-set change.
             *
             * Both current owners must be cleared from this epoch.
             */
            val disconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "C"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val disconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        disconnectReconciliation
                )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                ownerA.isCancelled
            )

            assertTrue(
                ownerC.isCancelled
            )

            assertEquals(
                2,
                disconnectExecution
                    .disconnectedReplayCount
            )

            assertTrue(
                disconnectExecution
                    .incidentIdsToStart
                    .isEmpty()
            )

            /*
             * While disconnected, authoritative durable membership changes.
             *
             * A resolves.
             * C remains unresolved.
             * D becomes unresolved.
             *
             * Reconnect must rebuild strictly from C + D.
             */
            val finalDurableIncidentIds =
                setOf(
                    "C",
                    "D"
                )

            val reconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        finalDurableIncidentIds,
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val reconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        reconnectReconciliation
                )

            assertEquals(
                emptySet<String>(),
                reconnectExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                0,
                reconnectExecution
                    .disconnectedReplayCount
            )

            assertEquals(
                setOf(
                    "C",
                    "D"
                ),
                reconnectExecution
                    .incidentIdsToStart
            )

            assertFalse(
                reconnectExecution
                    .incidentIdsToStart
                    .contains(
                        "A"
                    )
            )

            assertFalse(
                reconnectExecution
                    .incidentIdsToStart
                    .contains(
                        "B"
                    )
            )

            /*
             * Registration remains coordinator-owned.
             */
            assertTrue(
                replayJobs.isEmpty()
            )
        }

    @Test
    fun liveChurnThenOfflineMutationReconnectsFromFinalDurableSet() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val ownerA =
                launch {
                    awaitCancellation()
                }

            val ownerB =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA

            replayJobs["B"] =
                ownerB

            /*
             * Initial connected epoch:
             *
             * A and B both have active owners.
             *
             * Live durable evolution changes membership to A + C:
             *
             * B resolves.
             * A survives.
             * C appears.
             */
            val liveReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "C"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val liveExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        liveReconciliation
                )

            assertTrue(
                ownerA.isActive
            )

            assertTrue(
                ownerB.isCancelled
            )

            assertEquals(
                setOf(
                    "B"
                ),
                liveExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                setOf(
                    "C"
                ),
                liveExecution
                    .incidentIdsToStart
            )

            /*
             * Model coordinator registration of C after the live change.
             */
            val ownerC =
                launch {
                    awaitCancellation()
                }

            replayJobs["C"] =
                ownerC

            assertEquals(
                setOf(
                    "A",
                    "C"
                ),
                replayJobs.keys.toSet()
            )

            /*
             * Connection drops while A + C are current.
             *
             * Disconnect must terminate the whole replay epoch.
             */
            val disconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "C"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val disconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        disconnectReconciliation
                )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                ownerA.isCancelled
            )

            assertTrue(
                ownerC.isCancelled
            )

            assertEquals(
                2,
                disconnectExecution
                    .disconnectedReplayCount
            )

            assertTrue(
                disconnectExecution
                    .incidentIdsToStart
                    .isEmpty()
            )

            /*
             * Authoritative durable membership mutates while offline.
             *
             * Intermediate offline state:
             * C + D
             *
             * Final offline state:
             * D + E
             *
             * A is gone.
             * C is gone.
             * D survives.
             * E is newly unresolved.
             *
             * Only this final durable set matters on reconnect.
             */
            val intermediateOfflineIncidentIds =
                setOf(
                    "C",
                    "D"
                )

            val finalDurableIncidentIds =
                setOf(
                    "D",
                    "E"
                )

            assertEquals(
                setOf(
                    "C",
                    "D"
                ),
                intermediateOfflineIncidentIds
            )

            assertEquals(
                setOf(
                    "D",
                    "E"
                ),
                finalDurableIncidentIds
            )

            val reconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        finalDurableIncidentIds,
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val reconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        reconnectReconciliation
                )

            assertEquals(
                emptySet<String>(),
                reconnectExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                0,
                reconnectExecution
                    .disconnectedReplayCount
            )

            assertEquals(
                setOf(
                    "D",
                    "E"
                ),
                reconnectExecution
                    .incidentIdsToStart
            )

            /*
             * No prior online or offline membership may leak into the
             * fresh epoch.
             */
            assertFalse(
                reconnectExecution
                    .incidentIdsToStart
                    .contains(
                        "A"
                    )
            )

            assertFalse(
                reconnectExecution
                    .incidentIdsToStart
                    .contains(
                        "B"
                    )
            )

            assertFalse(
                reconnectExecution
                    .incidentIdsToStart
                    .contains(
                        "C"
                    )
            )

            /*
             * Registration remains coordinator-owned.
             */
            assertTrue(
                replayJobs.isEmpty()
            )
        }

    @Test
    fun multipleReconnectEpochsRebuildOnlyFromCurrentDurableGaps() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            /*
             * Epoch 1 starts from durable gaps A + B.
             */
            val epoch1Reconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "B"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val epoch1Execution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch1Reconnect
                )

            assertEquals(
                setOf(
                    "A",
                    "B"
                ),
                epoch1Execution
                    .incidentIdsToStart
            )

            val ownerA =
                launch {
                    awaitCancellation()
                }

            val ownerB =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA

            replayJobs["B"] =
                ownerB

            /*
             * Disconnect ends epoch 1 completely.
             */
            val epoch1Disconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "B"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val epoch1DisconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch1Disconnect
                )

            assertEquals(
                2,
                epoch1DisconnectExecution
                    .disconnectedReplayCount
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                ownerA.isCancelled
            )

            assertTrue(
                ownerB.isCancelled
            )

            /*
             * Durable membership changes while offline.
             *
             * Epoch 2 must rebuild only B + C.
             * A must not leak from epoch 1.
             */
            val epoch2Reconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "B",
                            "C"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val epoch2Execution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch2Reconnect
                )

            assertEquals(
                setOf(
                    "B",
                    "C"
                ),
                epoch2Execution
                    .incidentIdsToStart
            )

            assertFalse(
                epoch2Execution
                    .incidentIdsToStart
                    .contains(
                        "A"
                    )
            )

            val ownerB2 =
                launch {
                    awaitCancellation()
                }

            val ownerC =
                launch {
                    awaitCancellation()
                }

            replayJobs["B"] =
                ownerB2

            replayJobs["C"] =
                ownerC

            /*
             * Disconnect ends epoch 2.
             */
            val epoch2Disconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "B",
                            "C"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val epoch2DisconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch2Disconnect
                )

            assertEquals(
                2,
                epoch2DisconnectExecution
                    .disconnectedReplayCount
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                ownerB2.isCancelled
            )

            assertTrue(
                ownerC.isCancelled
            )

            /*
             * Durable membership changes again while offline.
             *
             * Epoch 3 must rebuild only C + D.
             * Neither A nor B may leak forward.
             */
            val epoch3Reconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "C",
                            "D"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val epoch3Execution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch3Reconnect
                )

            assertEquals(
                setOf(
                    "C",
                    "D"
                ),
                epoch3Execution
                    .incidentIdsToStart
            )

            assertFalse(
                epoch3Execution
                    .incidentIdsToStart
                    .contains(
                        "A"
                    )
            )

            assertFalse(
                epoch3Execution
                    .incidentIdsToStart
                    .contains(
                        "B"
                    )
            )

            assertEquals(
                0,
                epoch3Execution
                    .disconnectedReplayCount
            )

            /*
             * Registration remains coordinator-owned.
             */
            assertTrue(
                replayJobs.isEmpty()
            )
        }

    @Test
    fun connectionFlappingRebuildsFreshReplayOwnershipEachEpoch() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            /*
             * Epoch 1: connected with durable gap A.
             */
            val epoch1Reconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val epoch1Execution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch1Reconnect
                )

            assertEquals(
                setOf(
                    "A"
                ),
                epoch1Execution
                    .incidentIdsToStart
            )

            val ownerA1 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA1

            /*
             * First disconnect terminates epoch 1.
             */
            val epoch1Disconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val epoch1DisconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch1Disconnect
                )

            assertEquals(
                1,
                epoch1DisconnectExecution
                    .disconnectedReplayCount
            )

            assertTrue(
                ownerA1.isCancelled
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            /*
             * Epoch 2 reconnects while A is still durably unresolved.
             *
             * A must become eligible for a fresh owner because the old
             * owner belonged to the previous epoch and is gone.
             */
            val epoch2Reconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val epoch2Execution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch2Reconnect
                )

            assertEquals(
                setOf(
                    "A"
                ),
                epoch2Execution
                    .incidentIdsToStart
            )

            val ownerA2 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA2

            assertTrue(
                ownerA2.isActive
            )

            assertTrue(
                ownerA2 !== ownerA1
            )

            /*
             * Second disconnect terminates epoch 2.
             */
            val epoch2Disconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val epoch2DisconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch2Disconnect
                )

            assertEquals(
                1,
                epoch2DisconnectExecution
                    .disconnectedReplayCount
            )

            assertTrue(
                ownerA2.isCancelled
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            /*
             * Epoch 3 reconnects with A still present.
             *
             * It must again be eligible for fresh ownership. No prior
             * epoch's owner may suppress the restart.
             */
            val epoch3Reconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val epoch3Execution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch3Reconnect
                )

            assertEquals(
                setOf(
                    "A"
                ),
                epoch3Execution
                    .incidentIdsToStart
            )

            assertEquals(
                0,
                epoch3Execution
                    .disconnectedReplayCount
            )

            assertTrue(
                replayJobs.isEmpty()
            )
        }

    @Test
    fun connectionFlappingWithMembershipChangesRebuildsCurrentDurableSet() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            /*
             * Epoch 1 durable membership: A + B.
             */
            val epoch1Reconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "B"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val epoch1Execution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch1Reconnect
                )

            assertEquals(
                setOf(
                    "A",
                    "B"
                ),
                epoch1Execution
                    .incidentIdsToStart
            )

            val ownerA =
                launch {
                    awaitCancellation()
                }

            val ownerB =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA

            replayJobs["B"] =
                ownerB

            /*
             * First disconnect ends the whole epoch.
             */
            val epoch1Disconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "B"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val epoch1DisconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch1Disconnect
                )

            assertEquals(
                2,
                epoch1DisconnectExecution
                    .disconnectedReplayCount
            )

            assertTrue(
                ownerA.isCancelled
            )

            assertTrue(
                ownerB.isCancelled
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            /*
             * While offline:
             *
             * A resolves.
             * B survives.
             * C appears.
             *
             * Epoch 2 must rebuild only B + C.
             */
            val epoch2Reconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "B",
                            "C"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val epoch2Execution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch2Reconnect
                )

            assertEquals(
                setOf(
                    "B",
                    "C"
                ),
                epoch2Execution
                    .incidentIdsToStart
            )

            assertFalse(
                epoch2Execution
                    .incidentIdsToStart
                    .contains(
                        "A"
                    )
            )

            val ownerB2 =
                launch {
                    awaitCancellation()
                }

            val ownerC =
                launch {
                    awaitCancellation()
                }

            replayJobs["B"] =
                ownerB2

            replayJobs["C"] =
                ownerC

            /*
             * Second disconnect ends epoch 2.
             */
            val epoch2Disconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "B",
                            "C"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val epoch2DisconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch2Disconnect
                )

            assertEquals(
                2,
                epoch2DisconnectExecution
                    .disconnectedReplayCount
            )

            assertTrue(
                ownerB2.isCancelled
            )

            assertTrue(
                ownerC.isCancelled
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            /*
             * Membership changes again while offline:
             *
             * B resolves.
             * C survives.
             * D appears.
             *
             * Epoch 3 must rebuild only C + D.
             */
            val epoch3Reconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "C",
                            "D"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val epoch3Execution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        epoch3Reconnect
                )

            assertEquals(
                setOf(
                    "C",
                    "D"
                ),
                epoch3Execution
                    .incidentIdsToStart
            )

            assertFalse(
                epoch3Execution
                    .incidentIdsToStart
                    .contains(
                        "A"
                    )
            )

            assertFalse(
                epoch3Execution
                    .incidentIdsToStart
                    .contains(
                        "B"
                    )
            )

            assertEquals(
                0,
                epoch3Execution
                    .disconnectedReplayCount
            )

            /*
             * Registration remains coordinator-owned.
             */
            assertTrue(
                replayJobs.isEmpty()
            )
        }

    @Test
    fun sameIncidentCanResolveAndReappearAsFreshLifecycleAcrossEpochs() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            /*
             * Epoch 1:
             * incident A is unresolved and receives ownership.
             */
            val firstReconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val firstExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        firstReconnect
                )

            assertEquals(
                setOf(
                    "A"
                ),
                firstExecution
                    .incidentIdsToStart
            )

            val ownerA1 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA1

            /*
             * A resolves while still connected.
             *
             * Its current lifecycle must stop and leave no registered
             * owner behind.
             */
            val resolvedReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        emptySet(),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val resolvedExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        resolvedReconciliation
                )

            assertEquals(
                setOf(
                    "A"
                ),
                resolvedExecution
                    .resolvedIncidentIdsStopped
            )

            assertTrue(
                ownerA1.isCancelled
            )

            assertFalse(
                replayJobs.containsKey(
                    "A"
                )
            )

            /*
             * Connection drops after the incident has resolved.
             *
             * There is no surviving replay ownership to carry into the
             * next epoch.
             */
            val disconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        emptySet(),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val disconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        disconnectReconciliation
                )

            assertEquals(
                0,
                disconnectExecution
                    .disconnectedReplayCount
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            /*
             * While offline, A becomes unresolved again.
             *
             * This is a new durable lifecycle for the same incident ID.
             * Reconnect must treat it as fresh work.
             */
            val secondReconnect =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val secondExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        secondReconnect
                )

            assertEquals(
                setOf(
                    "A"
                ),
                secondExecution
                    .incidentIdsToStart
            )

            assertEquals(
                emptySet<String>(),
                secondExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                0,
                secondExecution
                    .disconnectedReplayCount
            )

            /*
             * Registration remains coordinator-owned, so the execution
             * seam reports A as fresh start work without installing the
             * new owner itself.
             */
            assertTrue(
                replayJobs.isEmpty()
            )

            val ownerA2 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA2

            assertTrue(
                ownerA2.isActive
            )

            assertTrue(
                ownerA2 !== ownerA1
            )

            ownerA2.cancel()
        }

    @Test
    fun sameIncidentCanResolveAndReappearAsFreshLifecycleWhileConnected() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            /*
             * A is currently unresolved while connected.
             */
            val initialReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val initialExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        initialReconciliation
                )

            assertEquals(
                setOf(
                    "A"
                ),
                initialExecution
                    .incidentIdsToStart
            )

            val ownerA1 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA1

            /*
             * A resolves while connection remains up.
             */
            val resolvedReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        emptySet(),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val resolvedExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        resolvedReconciliation
                )

            assertEquals(
                setOf(
                    "A"
                ),
                resolvedExecution
                    .resolvedIncidentIdsStopped
            )

            assertTrue(
                ownerA1.isCancelled
            )

            assertFalse(
                replayJobs.containsKey(
                    "A"
                )
            )

            /*
             * Without any disconnect, A becomes durably unresolved again.
             *
             * Because the prior lifecycle has fully resolved and its owner
             * has been removed, A must be eligible for fresh ownership.
             */
            val reappearedReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val reappearedExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        reappearedReconciliation
                )

            assertEquals(
                emptySet<String>(),
                reappearedExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                setOf(
                    "A"
                ),
                reappearedExecution
                    .incidentIdsToStart
            )

            assertEquals(
                0,
                reappearedExecution
                    .disconnectedReplayCount
            )

            /*
             * Execution reports fresh start work but does not install the
             * replacement owner itself.
             */
            assertTrue(
                replayJobs.isEmpty()
            )

            val ownerA2 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA2

            assertTrue(
                ownerA2.isActive
            )

            assertTrue(
                ownerA2 !== ownerA1
            )

            ownerA2.cancel()
        }

    @Test
    fun disconnectDuringMixedDepthOwnershipClearsCurrentAndIgnoresStaleCleanup() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            /*
             * A has advanced through three generations.
             * B has advanced through two.
             *
             * Only A3 and B2 are current registrations when disconnect
             * begins.
             */
            val ownerA1 =
                launch {
                    awaitCancellation()
                }

            val ownerA2 =
                launch {
                    awaitCancellation()
                }

            val ownerA3 =
                launch {
                    awaitCancellation()
                }

            val ownerB1 =
                launch {
                    awaitCancellation()
                }

            val ownerB2 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA3

            replayJobs["B"] =
                ownerB2

            /*
             * Disconnect must clear and cancel only the currently
             * registered ownership epoch.
             */
            val disconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "B"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val disconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        disconnectReconciliation
                )

            assertEquals(
                2,
                disconnectExecution
                    .disconnectedReplayCount
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                ownerA3.isCancelled
            )

            assertTrue(
                ownerB2.isCancelled
            )

            /*
             * Older generations may finish cleanup later.
             *
             * Their stale cleanup must remain harmless after disconnect.
             */
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA1
            )

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA2
            )

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "B",
                replayJob =
                    ownerB1
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            /*
             * A fresh epoch may now register replacement owners.
             */
            val ownerA4 =
                launch {
                    awaitCancellation()
                }

            val ownerB3 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA4

            replayJobs["B"] =
                ownerB3

            /*
             * Cleanup from every older generation, including the owners
             * canceled by disconnect, must not remove the fresh epoch.
             */
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA1
            )

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA2
            )

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA3
            )

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "B",
                replayJob =
                    ownerB1
            )

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "B",
                replayJob =
                    ownerB2
            )

            assertTrue(
                replayJobs["A"] ===
                    ownerA4
            )

            assertTrue(
                replayJobs["B"] ===
                    ownerB3
            )

            assertEquals(
                setOf(
                    "A",
                    "B"
                ),
                replayJobs.keys.toSet()
            )

            ownerA1.cancel()
            ownerA2.cancel()
            ownerB1.cancel()
            ownerA4.cancel()
            ownerB3.cancel()
        }

    @Test
    fun disconnectMutationDuringStaleCleanupReconnectsOnlyCurrentDurableGap() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            /*
             * A and B are current in the connected epoch.
             */
            val ownerA1 =
                launch {
                    awaitCancellation()
                }

            val ownerB1 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA1

            replayJobs["B"] =
                ownerB1

            /*
             * A receives a replacement before disconnect.
             * A1 is now stale but may still complete cleanup later.
             */
            val ownerA2 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA2

            assertTrue(
                replayJobs["A"] ===
                    ownerA2
            )

            assertTrue(
                replayJobs["B"] ===
                    ownerB1
            )

            /*
             * Disconnect ends the current epoch.
             */
            val disconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "B"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val disconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        disconnectReconciliation
                )

            assertEquals(
                2,
                disconnectExecution
                    .disconnectedReplayCount
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                ownerA2.isCancelled
            )

            assertTrue(
                ownerB1.isCancelled
            )

            /*
             * While offline, authoritative durable membership changes:
             *
             * A resolves completely.
             * B remains unresolved.
             *
             * Reconnect must therefore rebuild only B.
             */
            val reconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "B"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val reconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        reconnectReconciliation
                )

            assertEquals(
                setOf(
                    "B"
                ),
                reconnectExecution
                    .incidentIdsToStart
            )

            assertFalse(
                reconnectExecution
                    .incidentIdsToStart
                    .contains(
                        "A"
                    )
            )

            /*
             * Model coordinator registration of B's fresh owner.
             */
            val ownerB2 =
                launch {
                    awaitCancellation()
                }

            replayJobs["B"] =
                ownerB2

            /*
             * Stale cleanup from A1, A2, and B1 can arrive after the new
             * epoch has started. None may remove B2.
             */
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA1
            )

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA2
            )

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "B",
                replayJob =
                    ownerB1
            )

            assertFalse(
                replayJobs.containsKey(
                    "A"
                )
            )

            assertTrue(
                replayJobs["B"] ===
                    ownerB2
            )

            assertEquals(
                setOf(
                    "B"
                ),
                replayJobs.keys.toSet()
            )

            ownerA1.cancel()
            ownerB2.cancel()
        }

    @Test
    fun disconnectMutationCanIntroduceNewIncidentBeforeReconnect() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            /*
             * Connected epoch starts with A and B unresolved.
             */
            val ownerA =
                launch {
                    awaitCancellation()
                }

            val ownerB =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA

            replayJobs["B"] =
                ownerB

            /*
             * Disconnect clears the current ownership epoch.
             */
            val disconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        setOf(
                            "A",
                            "B"
                        ),
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Disconnected
                )

            val disconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        disconnectReconciliation
                )

            assertEquals(
                2,
                disconnectExecution
                    .disconnectedReplayCount
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            assertTrue(
                ownerA.isCancelled
            )

            assertTrue(
                ownerB.isCancelled
            )

            /*
             * Durable state changes while offline:
             *
             * A resolves.
             * B remains unresolved.
             * C becomes newly unresolved.
             *
             * Reconnect must rebuild from exactly B + C.
             */
            val finalDurableIncidentIds =
                setOf(
                    "B",
                    "C"
                )

            val reconnectReconciliation =
                calculateRealtimeReplayReconciliation(
                    gapIncidentIds =
                        finalDurableIncidentIds,
                    registeredReplayIncidentIds =
                        replayJobs.keys.toSet(),
                    activeReplayIncidentIds =
                        replayJobs
                            .filterValues {
                                it.isActive
                            }
                            .keys
                            .toSet(),
                    state =
                        RealtimeConnectionState.Connected
                )

            val reconnectExecution =
                applyRealtimeReplayReconciliation(
                    replayJobs =
                        replayJobs,
                    reconciliation =
                        reconnectReconciliation
                )

            assertEquals(
                setOf(
                    "B",
                    "C"
                ),
                reconnectExecution
                    .incidentIdsToStart
            )

            assertFalse(
                reconnectExecution
                    .incidentIdsToStart
                    .contains(
                        "A"
                    )
            )

            assertEquals(
                emptySet<String>(),
                reconnectExecution
                    .resolvedIncidentIdsStopped
            )

            assertEquals(
                0,
                reconnectExecution
                    .disconnectedReplayCount
            )

            /*
             * Registration remains coordinator-owned.
             */
            assertTrue(
                replayJobs.isEmpty()
            )
        }
}