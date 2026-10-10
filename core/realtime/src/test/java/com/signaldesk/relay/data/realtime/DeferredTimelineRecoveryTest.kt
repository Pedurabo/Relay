package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeferredTimelineRecoveryTest {

    @Test
    fun appliedIncidentCreationTriggersDeferredRecovery() {

        assertTrue(
            shouldRecoverDeferredTimelineEvents(
                EventProcessingResult.APPLIED
            )
        )
    }

    @Test
    fun duplicateIncidentCreationTriggersDeferredRecovery() {

        assertTrue(
            shouldRecoverDeferredTimelineEvents(
                EventProcessingResult.DUPLICATE
            )
        )
    }

    @Test
    fun staleIncidentCreationTriggersDeferredRecovery() {

        assertTrue(
            shouldRecoverDeferredTimelineEvents(
                EventProcessingResult.IGNORED_STALE
            )
        )
    }

    @Test
    fun gapOrDeferredIncidentDoesNotTriggerRecovery() {

        assertFalse(
            shouldRecoverDeferredTimelineEvents(
                EventProcessingResult.GAP_DETECTED
            )
        )

        assertFalse(
            shouldRecoverDeferredTimelineEvents(
                EventProcessingResult.DEFERRED
            )
        )
    }
}
