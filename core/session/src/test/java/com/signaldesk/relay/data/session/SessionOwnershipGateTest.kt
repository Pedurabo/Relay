package com.signaldesk.relay.data.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOwnershipGateTest {

    @Test
    fun currentGenerationMayCommit() = runBlocking {
        val gate = SessionOwnershipGate()
        var generation = 1L

        val result = gate.withSnapshotOwnership(
            expectedGeneration = 1L,
            currentGeneration = { generation }
        ) {
            "committed"
        }

        assertEquals("committed", result)
    }

    @Test
    fun supersededGenerationCannotCommit() = runBlocking {
        val gate = SessionOwnershipGate()
        var generation = 2L
        var executed = false

        val result = gate.withSnapshotOwnership(
            expectedGeneration = 1L,
            currentGeneration = { generation }
        ) {
            executed = true
            "committed"
        }

        assertNull(result)
        assertFalse(executed)
    }

    @Test
    fun transitionWaitsForActiveSnapshot() = runBlocking {
        val gate = SessionOwnershipGate()
        var generation = 1L

        val snapshotStarted = CompletableDeferred<Unit>()
        val releaseSnapshot = CompletableDeferred<Unit>()
        val transitionStarted = CompletableDeferred<Unit>()
        var transitionCompleted = false

        val snapshot = async(
            start = CoroutineStart.UNDISPATCHED
        ) {
            gate.withSnapshotOwnership(
                expectedGeneration = 1L,
                currentGeneration = { generation }
            ) {
                snapshotStarted.complete(Unit)
                releaseSnapshot.await()
                "committed"
            }
        }

        try {
            snapshotStarted.await()

            val transition = async(
                start = CoroutineStart.UNDISPATCHED
            ) {
                transitionStarted.complete(Unit)

                gate.withSessionTransition {
                    generation = 2L
                    transitionCompleted = true
                }
            }

            transitionStarted.await()
            yield()

            assertFalse(transitionCompleted)

            releaseSnapshot.complete(Unit)

            assertEquals("committed", snapshot.await())
            transition.await()

            assertTrue(transitionCompleted)
            assertEquals(2L, generation)

        } finally {
            releaseSnapshot.complete(Unit)
            snapshot.cancel()
        }
    }

    @Test
    fun snapshotWaitingBehindTransitionRejectsOldGeneration() =
        runBlocking {

            val gate = SessionOwnershipGate()
            var generation = 1L

            val transitionEntered = CompletableDeferred<Unit>()
            val releaseTransition = CompletableDeferred<Unit>()

            val transition = async(
                start = CoroutineStart.UNDISPATCHED
            ) {
                gate.withSessionTransition {
                    transitionEntered.complete(Unit)
                    releaseTransition.await()
                    generation = 2L
                }
            }

            try {
                transitionEntered.await()

                var snapshotExecuted = false

                val snapshot = async(
                    start = CoroutineStart.UNDISPATCHED
                ) {
                    gate.withSnapshotOwnership(
                        expectedGeneration = 1L,
                        currentGeneration = { generation }
                    ) {
                        snapshotExecuted = true
                        "committed"
                    }
                }

                releaseTransition.complete(Unit)

                transition.await()

                assertNull(snapshot.await())
                assertFalse(snapshotExecuted)

            } finally {
                releaseTransition.complete(Unit)
                transition.cancel()
            }
        }
}