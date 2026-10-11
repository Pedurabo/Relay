
package com.signaldesk.relay.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentSnapshotPolicyTest {

    @Test
    fun missingLocalIncidentAcceptsSnapshot() {

        assertTrue(
            shouldApplyAuthoritativeIncidentSnapshot(
                currentLatestSequence =
                    null,
                incomingLatestSequence =
                    4L
            )
        )
    }

    @Test
    fun unsequencedLocalIncidentAcceptsSnapshot() {

        assertTrue(
            shouldApplyAuthoritativeIncidentSnapshot(
                currentLatestSequence =
                    0L,
                incomingLatestSequence =
                    1L
            )
        )
    }

    @Test
    fun newerServerSequenceAdvancesLocalState() {

        assertTrue(
            shouldApplyAuthoritativeIncidentSnapshot(
                currentLatestSequence =
                    4L,
                incomingLatestSequence =
                    5L
            )
        )
    }

    @Test
    fun equalSequenceDoesNotOverwriteLocalOptimisticState() {

        assertFalse(
            shouldApplyAuthoritativeIncidentSnapshot(
                currentLatestSequence =
                    5L,
                incomingLatestSequence =
                    5L
            )
        )
    }

    @Test
    fun olderServerSequenceDoesNotRegressLocalState() {

        assertFalse(
            shouldApplyAuthoritativeIncidentSnapshot(
                currentLatestSequence =
                    5L,
                incomingLatestSequence =
                    4L
            )
        )
    }
}
