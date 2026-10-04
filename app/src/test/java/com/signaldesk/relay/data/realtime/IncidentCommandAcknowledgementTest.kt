package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncidentCommandAcknowledgementTest {

    @Test
    fun matchingAcceptedAcknowledgement_isAccepted() {

        assertEquals(
            IncidentCommandResult.ACCEPTED,
            correlateIncidentCommandAcknowledgement(
                type =
                    "command.accepted",
                command =
                    "incident.status.update",
                incidentId =
                    "INC-001",
                commandId =
                    "CMD-001",
                expectedCommand =
                    "incident.status.update",
                expectedIncidentId =
                    "INC-001",
                expectedCommandId =
                    "CMD-001"
            )
        )
    }

    @Test
    fun matchingRejectedAcknowledgement_isRejected() {

        assertEquals(
            IncidentCommandResult.REJECTED,
            correlateIncidentCommandAcknowledgement(
                type =
                    "command.rejected",
                command =
                    "incident.create",
                incidentId =
                    "INC-002",
                commandId =
                    "CMD-002",
                expectedCommand =
                    "incident.create",
                expectedIncidentId =
                    "INC-002",
                expectedCommandId =
                    "CMD-002"
            )
        )
    }

    @Test
    fun unrelatedMessageType_isIgnored() {

        assertNull(
            correlateIncidentCommandAcknowledgement(
                type =
                    "incident.updated",
                command =
                    "incident.status.update",
                incidentId =
                    "INC-001",
                commandId =
                    "CMD-001",
                expectedCommand =
                    "incident.status.update",
                expectedIncidentId =
                    "INC-001",
                expectedCommandId =
                    "CMD-001"
            )
        )
    }

    @Test
    fun wrongCommandType_isIgnored() {

        assertNull(
            correlateIncidentCommandAcknowledgement(
                type =
                    "command.accepted",
                command =
                    "incident.severity.update",
                incidentId =
                    "INC-001",
                commandId =
                    "CMD-001",
                expectedCommand =
                    "incident.status.update",
                expectedIncidentId =
                    "INC-001",
                expectedCommandId =
                    "CMD-001"
            )
        )
    }

    @Test
    fun wrongIncidentId_isIgnored() {

        assertNull(
            correlateIncidentCommandAcknowledgement(
                type =
                    "command.accepted",
                command =
                    "incident.status.update",
                incidentId =
                    "INC-OTHER",
                commandId =
                    "CMD-001",
                expectedCommand =
                    "incident.status.update",
                expectedIncidentId =
                    "INC-001",
                expectedCommandId =
                    "CMD-001"
            )
        )
    }

    @Test
    fun wrongCommandId_isIgnored() {

        assertNull(
            correlateIncidentCommandAcknowledgement(
                type =
                    "command.accepted",
                command =
                    "incident.status.update",
                incidentId =
                    "INC-001",
                commandId =
                    "CMD-OTHER",
                expectedCommand =
                    "incident.status.update",
                expectedIncidentId =
                    "INC-001",
                expectedCommandId =
                    "CMD-001"
            )
        )
    }
}