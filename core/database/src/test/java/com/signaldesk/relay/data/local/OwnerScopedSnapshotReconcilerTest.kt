package com.signaldesk.relay.data.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerScopedSnapshotReconcilerTest {

    private class FakeStore : OwnerScopedSnapshotStore {
        val incidents =
            mutableMapOf<AccountScopedIncidentId, OwnerScopedIncidentSnapshot>()

        val gaps =
            mutableMapOf<AccountScopedIncidentId, OwnerScopedGapState>()

        var transactionCount = 0
        var insideTransaction = false

        override suspend fun <T> withTransaction(
            operation: suspend () -> T
        ): T {
            check(!insideTransaction)
            transactionCount++
            insideTransaction = true

            try {
                return operation()
            } finally {
                insideTransaction = false
            }
        }

        override suspend fun latestSequence(
            key: AccountScopedIncidentId
        ): Long? {
            check(insideTransaction)
            return incidents[key]?.latestSequence
        }

        override suspend fun sequenceGap(
            key: AccountScopedIncidentId
        ): OwnerScopedGapState? {
            check(insideTransaction)
            return gaps[key]
        }

        override suspend fun upsertIncident(
            snapshot: OwnerScopedIncidentSnapshot
        ) {
            check(insideTransaction)
            incidents[snapshot.key] = snapshot
        }

        override suspend fun upsertGap(
            gap: OwnerScopedGapState
        ) {
            check(insideTransaction)
            gaps[gap.key] = gap
        }

        override suspend fun deleteGap(
            key: AccountScopedIncidentId
        ) {
            check(insideTransaction)
            gaps.remove(key)
        }
    }

    private fun snapshot(
        owner: String,
        sequence: Long,
        id: String = "INC-SHARED"
    ) = OwnerScopedIncidentSnapshot(
        key = AccountScopedIncidentId(owner, id),
        title = "$owner incident",
        status = "OPEN",
        severity = "HIGH",
        latestSequence = sequence
    )

    private fun gap(
        key: AccountScopedIncidentId
    ) = OwnerScopedGapState(
        key = key,
        expectedSequence = 3L,
        receivedSequence = 8L
    )

    @Test
    fun sameIncidentIdIsIndependentAcrossAccounts() = runBlocking {
        val store = FakeStore()
        val reconciler = OwnerScopedSnapshotReconciler(store)

        assertEquals(
            1,
            reconciler.reconcile(
                "account-a",
                listOf(snapshot("account-a", 5L))
            )
        )

        assertEquals(
            1,
            reconciler.reconcile(
                "account-b",
                listOf(snapshot("account-b", 9L))
            )
        )

        val a = AccountScopedIncidentId("account-a", "INC-SHARED")
        val b = AccountScopedIncidentId("account-b", "INC-SHARED")

        assertEquals(5L, store.incidents[a]?.latestSequence)
        assertEquals(9L, store.incidents[b]?.latestSequence)
        assertEquals(2, store.transactionCount)
        assertFalse(store.insideTransaction)
    }

    @Test
    fun staleSnapshotPreservesSequenceAndGap() = runBlocking {
        val store = FakeStore()
        val key = AccountScopedIncidentId("account-a", "INC-SHARED")

        store.incidents[key] = snapshot("account-a", 6L)
        store.gaps[key] = gap(key)

        val applied = OwnerScopedSnapshotReconciler(store)
            .reconcile(
                "account-a",
                listOf(snapshot("account-a", 4L))
            )

        assertEquals(0, applied)
        assertEquals(6L, store.incidents[key]?.latestSequence)
        assertEquals(3L, store.gaps[key]?.expectedSequence)
        assertEquals(1, store.transactionCount)
    }

    @Test
    fun gapResolutionChangesOnlyMatchingOwner() = runBlocking {
        val store = FakeStore()

        val a = AccountScopedIncidentId("account-a", "INC-SHARED")
        val b = AccountScopedIncidentId("account-b", "INC-SHARED")

        store.incidents[a] = snapshot("account-a", 2L)
        store.incidents[b] = snapshot("account-b", 2L)
        store.gaps[a] = gap(a)
        store.gaps[b] = gap(b)

        val reconciler = OwnerScopedSnapshotReconciler(store)

        assertEquals(
            1,
            reconciler.reconcile(
                "account-a",
                listOf(snapshot("account-a", 5L))
            )
        )

        assertEquals(6L, store.gaps[a]?.expectedSequence)
        assertEquals(3L, store.gaps[b]?.expectedSequence)

        assertEquals(
            1,
            reconciler.reconcile(
                "account-a",
                listOf(snapshot("account-a", 8L))
            )
        )

        assertFalse(store.gaps.containsKey(a))
        assertTrue(store.gaps.containsKey(b))
        assertEquals(2L, store.incidents[b]?.latestSequence)
    }

    @Test
    fun mixedOwnerBatchFailsBeforeAnyTransaction() = runBlocking {
        val store = FakeStore()
        val reconciler = OwnerScopedSnapshotReconciler(store)

        var rejected = false

        try {
            reconciler.reconcile(
                "account-a",
                listOf(
                    snapshot("account-a", 5L),
                    snapshot("account-b", 7L)
                )
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
        assertEquals(0, store.transactionCount)
        assertTrue(store.incidents.isEmpty())
        assertTrue(store.gaps.isEmpty())
    }
}
