package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

class DeferredTimelineRetentionTest {

    @Test
    fun retentionCutoffKeepsThirtyDays() {

        val nowMillis =
            50L * 24L * 60L * 60L * 1_000L

        val expected =
            20L * 24L * 60L * 60L * 1_000L

        assertEquals(
            expected,
            deferredTimelineRetentionCutoff(
                nowMillis
            )
        )
    }

    @Test
    fun retentionWindowIsThirtyDays() {

        assertEquals(
            30L * 24L * 60L * 60L * 1_000L,
            DEFERRED_TIMELINE_ORPHAN_RETENTION_MILLIS
        )
    }
}
