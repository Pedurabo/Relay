package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxDiagnosticsTest {

    @Test
    fun namedAttributesBecomeStructuredFields() {

        val payload =
            parseOutboxDiagnostic(
                "TIMELINE_OUTBOX_RETRY|entryId=ENTRY-1|reason=timeout"
            )

        assertEquals(
            "timeline.outbox.retry",
            payload.name
        )

        assertEquals(
            mapOf(
                "entryId" to
                    "ENTRY-1",
                "reason" to
                    "timeout"
            ),
            payload.attributes
        )
    }

    @Test
    fun positionalLegacySegmentsArePreserved() {

        val payload =
            parseOutboxDiagnostic(
                "CREATE_OUTBOX_EXCEPTION|COMMAND-1|IllegalStateException"
            )

        assertEquals(
            "create.outbox.exception",
            payload.name
        )

        assertEquals(
            mapOf(
                "arg1" to
                    "COMMAND-1",
                "arg2" to
                    "IllegalStateException"
            ),
            payload.attributes
        )
    }

    @Test
    fun valuesContainingEqualsRemainIntact() {

        val payload =
            parseOutboxDiagnostic(
                "STATUS_OUTBOX_RESULT|result=value=extended"
            )

        assertEquals(
            "status.outbox.result",
            payload.name
        )

        assertEquals(
            "value=extended",
            payload.attributes[
                "result"
            ]
        )
    }

    @Test
    fun eventWithoutAttributesRemainsValid() {

        val payload =
            parseOutboxDiagnostic(
                "OUTBOX_READY"
            )

        assertEquals(
            "outbox.ready",
            payload.name
        )

        assertEquals(
            emptyMap<String, String>(),
            payload.attributes
        )
    }
}