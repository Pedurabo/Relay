package com.signaldesk.relay.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerScopedSnapshotPolicyTest {

    private val accountA =
        AccountScopedIncidentId("account-a", "INC-SHARED")

    private val accountB =
        AccountScopedIncidentId("account-b", "INC-SHARED")

    private fun gap(
        key: AccountScopedIncidentId = accountA
    ) = OwnerScopedGapState(
        key = key,
        expectedSequence = 3L,
        receivedSequence = 8L
    )

    @Test
    fun newOwnedIncidentCanAcceptInitialSnapshot() {
        val result = OwnerScopedSnapshotPolicy.decide(
            key = accountA,
            currentIncidentKey = null,
            currentLatestSequence = null,
            incomingLatestSequence = 1L,
            currentGap = null
        )

        assertTrue(result.applyIncident)
        assertEquals(accountA, result.key)
        assertNull(result.replacementGap)
        assertFalse(result.deleteExistingGap)
    }

    @Test
    fun staleSnapshotCannotRegressSequenceGap() {
        val originalGap = gap()

        val result = OwnerScopedSnapshotPolicy.decide(
            key = accountA,
            currentIncidentKey = accountA,
            currentLatestSequence = 6L,
            incomingLatestSequence = 4L,
            currentGap = originalGap
        )

        assertFalse(result.applyIncident)
        assertEquals(originalGap, result.replacementGap)
        assertFalse(result.deleteExistingGap)
    }

    @Test
    fun ownedSnapshotAdvancesItsGap() {
        val result = OwnerScopedSnapshotPolicy.decide(
            key = accountA,
            currentIncidentKey = accountA,
            currentLatestSequence = 2L,
            incomingLatestSequence = 5L,
            currentGap = gap()
        )

        assertTrue(result.applyIncident)
        assertEquals(6L, result.replacementGap?.expectedSequence)
        assertEquals(accountA, result.replacementGap?.key)
        assertFalse(result.deleteExistingGap)
    }

    @Test
    fun receivedSequenceClosesOnlyTheMatchingGap() {
        val result = OwnerScopedSnapshotPolicy.decide(
            key = accountA,
            currentIncidentKey = accountA,
            currentLatestSequence = 2L,
            incomingLatestSequence = 8L,
            currentGap = gap()
        )

        assertTrue(result.applyIncident)
        assertNull(result.replacementGap)
        assertTrue(result.deleteExistingGap)
    }

    @Test
    fun foreignOwnerIncidentAndGapAreRejected() {
        var incidentRejected = false
        var gapRejected = false

        try {
            OwnerScopedSnapshotPolicy.decide(
                key = accountA,
                currentIncidentKey = accountB,
                currentLatestSequence = 2L,
                incomingLatestSequence = 5L,
                currentGap = null
            )
        } catch (_: IllegalArgumentException) {
            incidentRejected = true
        }

        try {
            OwnerScopedSnapshotPolicy.decide(
                key = accountA,
                currentIncidentKey = accountA,
                currentLatestSequence = 2L,
                incomingLatestSequence = 5L,
                currentGap = gap(accountB)
            )
        } catch (_: IllegalArgumentException) {
            gapRejected = true
        }

        assertTrue(incidentRejected)
        assertTrue(gapRejected)
    }
}
