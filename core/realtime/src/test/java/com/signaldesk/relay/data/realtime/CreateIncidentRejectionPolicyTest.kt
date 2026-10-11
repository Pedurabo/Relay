package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

class CreateIncidentRejectionPolicyTest {

    @Test
    fun invalidPayload_isDurablyFailed() {

        assertEquals(
            CreateIncidentRejectionDecision.MARK_FAILED,
            decideCreateIncidentRejection(
                "invalid_payload"
            )
        )
    }

    @Test
    fun incidentAlreadyExists_isDurablyFailed() {

        assertEquals(
            CreateIncidentRejectionDecision.MARK_FAILED,
            decideCreateIncidentRejection(
                "incident_already_exists"
            )
        )
    }

    @Test
    fun unsupportedCommand_isDurablyFailed() {

        assertEquals(
            CreateIncidentRejectionDecision.MARK_FAILED,
            decideCreateIncidentRejection(
                "unsupported_command"
            )
        )
    }

    @Test
    fun unknownReason_isAlsoPreservedRatherThanDropped() {

        assertEquals(
            CreateIncidentRejectionDecision.MARK_FAILED,
            decideCreateIncidentRejection(
                "future_server_reason"
            )
        )
    }

    @Test
    fun blankReason_normalizesToUnknown() {

        assertEquals(
            "unknown",
            normalizeCreateIncidentRejectionReason(
                "   "
            )
        )
    }

    @Test
    fun rejectionReason_isTrimmed() {

        assertEquals(
            "invalid_payload",
            normalizeCreateIncidentRejectionReason(
                "  invalid_payload  "
            )
        )
    }
}
