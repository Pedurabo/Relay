package com.signaldesk.relay.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthoritativeSnapshotGapTest {

    private fun gap(
        expected: Long = 8L,
        received: Long = 12L
    ) = IncidentSequenceGapEntity(
        ownerPrincipal = "test-owner",
        incidentId = "INC-001",
        expectedSequence = expected,
        receivedSequence = received,
        detectedAt = 100L
    )

    @Test
    fun snapshotCoveringEntireGapResolvesIt() {
        assertNull(
            reconcileSnapshotSequenceGap(
                gap(),
                12L
            )
        )
    }

    @Test
    fun snapshotBeyondGapResolvesIt() {
        assertNull(
            reconcileSnapshotSequenceGap(
                gap(),
                15L
            )
        )
    }

    @Test
    fun snapshotPartiallyAdvancesGap() {
        val result = reconcileSnapshotSequenceGap(
            gap(),
            10L
        )

        assertEquals(11L, result?.expectedSequence)
        assertEquals(12L, result?.receivedSequence)
        assertEquals(100L, result?.detectedAt)
    }

    @Test
    fun snapshotBeforeGapPreservesRange() {
        val original = gap()

        assertEquals(
            original,
            reconcileSnapshotSequenceGap(
                original,
                6L
            )
        )
    }

    @Test
    fun snapshotImmediatelyBeforeGapPreservesRange() {
        val original = gap()

        assertEquals(
            original,
            reconcileSnapshotSequenceGap(
                original,
                7L
            )
        )
    }

    @Test
    fun maximumSequenceResolvesGapWithoutOverflow() {
        assertNull(
            reconcileSnapshotSequenceGap(
                gap(
                    expected = Long.MAX_VALUE - 1L,
                    received = Long.MAX_VALUE
                ),
                Long.MAX_VALUE
            )
        )
    }

    @Test
    fun partialMaximumRangeDoesNotOverflow() {
        val result = reconcileSnapshotSequenceGap(
            gap(
                expected = Long.MAX_VALUE - 2L,
                received = Long.MAX_VALUE
            ),
            Long.MAX_VALUE - 1L
        )

        assertEquals(
            Long.MAX_VALUE,
            result?.expectedSequence
        )

        assertEquals(
            Long.MAX_VALUE,
            result?.receivedSequence
        )
    }
}