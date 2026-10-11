package com.signaldesk.relay.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the existing production snapshot reconciliation rules.
 *
 * These tests must remain valid when incident and gap identity
 * becomes owner-scoped in Room version 16.
 */
class SnapshotRulesBeforeOwnerMigrationTest {

    private fun gap(
        expected: Long = 3L,
        received: Long = 8L
    ) = IncidentSequenceGapEntity(
        ownerPrincipal = "test-owner",
        incidentId = "INC-SHARED",
        expectedSequence = expected,
        receivedSequence = received,
        detectedAt = 100L
    )

    @Test
    fun missingOrUninitializedIncidentAcceptsSnapshot() {
        assertTrue(
            shouldApplyAuthoritativeIncidentSnapshot(
                currentLatestSequence = null,
                incomingLatestSequence = 0L
            )
        )

        assertTrue(
            shouldApplyAuthoritativeIncidentSnapshot(
                currentLatestSequence = 0L,
                incomingLatestSequence = 1L
            )
        )
    }

    @Test
    fun existingPositiveSequenceRejectsEqualOrOlderSnapshot() {
        assertFalse(
            shouldApplyAuthoritativeIncidentSnapshot(
                currentLatestSequence = 7L,
                incomingLatestSequence = 7L
            )
        )

        assertFalse(
            shouldApplyAuthoritativeIncidentSnapshot(
                currentLatestSequence = 7L,
                incomingLatestSequence = 6L
            )
        )

        assertTrue(
            shouldApplyAuthoritativeIncidentSnapshot(
                currentLatestSequence = 7L,
                incomingLatestSequence = 8L
            )
        )
    }

    @Test
    fun snapshotBelowGapExpectedSequencePreservesGap() {
        val original = gap(
            expected = 5L,
            received = 9L
        )

        val result = reconcileSnapshotSequenceGap(
            gap = original,
            snapshotSequence = 4L
        )

        assertSame(original, result)
        assertEquals(5L, result?.expectedSequence)
        assertEquals(9L, result?.receivedSequence)
    }

    @Test
    fun snapshotAdvancesOrClosesSequenceGap() {
        val original = gap()

        val advanced = reconcileSnapshotSequenceGap(
            gap = original,
            snapshotSequence = 5L
        )

        assertEquals(6L, advanced?.expectedSequence)
        assertEquals(8L, advanced?.receivedSequence)
        assertEquals("INC-SHARED", advanced?.incidentId)

        assertNull(
            reconcileSnapshotSequenceGap(
                gap = original,
                snapshotSequence = 8L
            )
        )
    }
}
