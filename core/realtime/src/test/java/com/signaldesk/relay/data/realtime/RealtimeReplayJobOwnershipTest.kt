package com.signaldesk.relay.data.realtime

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals

class RealtimeReplayJobOwnershipTest {

    @Test
    fun currentReplayJobRemovesItsOwnRegistryEntry() {

        val replayJobs =
            ConcurrentHashMap<String, Job>()

        val currentJob =
            Job()

        replayJobs[
            "incident-1"
        ] =
            currentJob

        assertTrue(
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "incident-1",
                replayJob =
                    currentJob
            )
        )

        assertFalse(
            replayJobs.containsKey(
                "incident-1"
            )
        )
    }


    @Test
    fun staleReplayJobCannotRemoveReplacementJob() {

        val replayJobs =
            ConcurrentHashMap<String, Job>()

        val staleJob =
            Job()

        val replacementJob =
            Job()

        replayJobs[
            "incident-1"
        ] =
            replacementJob

        assertFalse(
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "incident-1",
                replayJob =
                    staleJob
            )
        )

        assertSame(
            replacementJob,
            replayJobs[
                "incident-1"
            ]
        )
    }


    @Test
    fun canceledReplayCannotRemoveFutureReplacement() {

        val replayJobs =
            ConcurrentHashMap<String, Job>()

        val oldJob =
            Job()

        replayJobs[
            "incident-1"
        ] =
            oldJob

        replayJobs.clear()

        val replacementJob =
            Job()

        replayJobs[
            "incident-1"
        ] =
            replacementJob

        assertFalse(
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "incident-1",
                replayJob =
                    oldJob
            )
        )

        assertSame(
            replacementJob,
            replayJobs[
                "incident-1"
            ]
        )
    }

    @Test
    fun multipleSameIncidentReplacementsSurviveAllStaleCleanup() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            val incidentId =
                "incident-A"

            /*
             * Generation 1 owns A.
             */
            val owner1 =
                launch {
                    awaitCancellation()
                }

            replayJobs[incidentId] =
                owner1

            /*
             * Generation 2 replaces generation 1 before generation 1's
             * stale cleanup runs.
             */
            val owner2 =
                launch {
                    awaitCancellation()
                }

            replayJobs[incidentId] =
                owner2

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    incidentId,
                replayJob =
                    owner1
            )

            assertTrue(
                replayJobs[incidentId] ===
                    owner2
            )

            /*
             * Generation 3 replaces generation 2.
             *
             * Stale cleanup from both older owners must preserve the
             * newest generation.
             */
            val owner3 =
                launch {
                    awaitCancellation()
                }

            replayJobs[incidentId] =
                owner3

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    incidentId,
                replayJob =
                    owner1
            )

            assertTrue(
                replayJobs[incidentId] ===
                    owner3
            )

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    incidentId,
                replayJob =
                    owner2
            )

            assertTrue(
                replayJobs[incidentId] ===
                    owner3
            )

            /*
             * Even after cancellation/completion of stale generations,
             * their final cleanup remains generation-safe.
             */
            owner1.cancel()
            owner2.cancel()

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    incidentId,
                replayJob =
                    owner1
            )

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    incidentId,
                replayJob =
                    owner2
            )

            assertTrue(
                replayJobs[incidentId] ===
                    owner3
            )

            /*
             * Only the current generation may remove its own registration.
             */
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    incidentId,
                replayJob =
                    owner3
            )

            assertFalse(
                replayJobs.containsKey(
                    incidentId
                )
            )

            owner3.cancel()
        }

    @Test
    fun multiIncidentReplacementCleanupIsGenerationSafeAndIncidentLocal() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            /*
             * Generation 1 owners for two independent incidents.
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
             * Both incidents receive replacement owners.
             */
            val ownerA2 =
                launch {
                    awaitCancellation()
                }

            val ownerB2 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA2

            replayJobs["B"] =
                ownerB2

            /*
             * Stale cleanup for A generation 1 must preserve A generation 2
             * and must not affect B at all.
             */
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA1
            )

            assertTrue(
                replayJobs["A"] ===
                    ownerA2
            )

            assertTrue(
                replayJobs["B"] ===
                    ownerB2
            )

            /*
             * Stale cleanup for B generation 1 is equally local.
             */
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "B",
                replayJob =
                    ownerB1
            )

            assertTrue(
                replayJobs["A"] ===
                    ownerA2
            )

            assertTrue(
                replayJobs["B"] ===
                    ownerB2
            )

            /*
             * A advances again to generation 3 while B remains on
             * generation 2.
             */
            val ownerA3 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA3

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA2
            )

            assertTrue(
                replayJobs["A"] ===
                    ownerA3
            )

            assertTrue(
                replayJobs["B"] ===
                    ownerB2
            )

            /*
             * Cancellation/completion of all stale generations must still
             * preserve the current owner for each incident independently.
             */
            ownerA1.cancel()
            ownerA2.cancel()
            ownerB1.cancel()

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
                replayJobs["A"] ===
                    ownerA3
            )

            assertTrue(
                replayJobs["B"] ===
                    ownerB2
            )

            assertEquals(
                setOf(
                    "A",
                    "B"
                ),
                replayJobs.keys.toSet()
            )

            /*
             * Only each incident's current generation may remove its own
             * registration.
             */
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA3
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

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "B",
                replayJob =
                    ownerB2
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            ownerA3.cancel()
            ownerB2.cancel()
        }

    @Test
    fun mixedReplacementDepthPreservesNewestOwnerPerIncident() =
        runBlocking {

            val replayJobs =
                ConcurrentHashMap<String, Job>()

            /*
             * A will advance through three generations.
             * B will advance through two.
             * C remains on its original owner.
             */
            val ownerA1 =
                launch {
                    awaitCancellation()
                }

            val ownerB1 =
                launch {
                    awaitCancellation()
                }

            val ownerC1 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA1

            replayJobs["B"] =
                ownerB1

            replayJobs["C"] =
                ownerC1

            val ownerA2 =
                launch {
                    awaitCancellation()
                }

            val ownerB2 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA2

            replayJobs["B"] =
                ownerB2

            val ownerA3 =
                launch {
                    awaitCancellation()
                }

            replayJobs["A"] =
                ownerA3

            /*
             * Stale cleanup from A generation 1 must preserve A3,
             * B2, and C1.
             */
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA1
            )

            assertTrue(
                replayJobs["A"] ===
                    ownerA3
            )

            assertTrue(
                replayJobs["B"] ===
                    ownerB2
            )

            assertTrue(
                replayJobs["C"] ===
                    ownerC1
            )

            /*
             * Stale cleanup from A generation 2 must also preserve the
             * current owners for every incident.
             */
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA2
            )

            assertTrue(
                replayJobs["A"] ===
                    ownerA3
            )

            assertTrue(
                replayJobs["B"] ===
                    ownerB2
            )

            assertTrue(
                replayJobs["C"] ===
                    ownerC1
            )

            /*
             * B has shallower replacement depth. Its stale cleanup must
             * remove neither B2 nor any owner belonging to A or C.
             */
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "B",
                replayJob =
                    ownerB1
            )

            assertTrue(
                replayJobs["A"] ===
                    ownerA3
            )

            assertTrue(
                replayJobs["B"] ===
                    ownerB2
            )

            assertTrue(
                replayJobs["C"] ===
                    ownerC1
            )

            /*
             * Cancellation of all stale generations changes nothing.
             */
            ownerA1.cancel()
            ownerA2.cancel()
            ownerB1.cancel()

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
                replayJobs["A"] ===
                    ownerA3
            )

            assertTrue(
                replayJobs["B"] ===
                    ownerB2
            )

            assertTrue(
                replayJobs["C"] ===
                    ownerC1
            )

            assertEquals(
                setOf(
                    "A",
                    "B",
                    "C"
                ),
                replayJobs.keys.toSet()
            )

            /*
             * Only each incident's newest owner may remove itself.
             */
            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "A",
                replayJob =
                    ownerA3
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

            assertTrue(
                replayJobs["C"] ===
                    ownerC1
            )

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "B",
                replayJob =
                    ownerB2
            )

            removeReplayJobIfCurrent(
                replayJobs =
                    replayJobs,
                incidentId =
                    "C",
                replayJob =
                    ownerC1
            )

            assertTrue(
                replayJobs.isEmpty()
            )

            ownerA3.cancel()
            ownerB2.cancel()
            ownerC1.cancel()
        }
}
