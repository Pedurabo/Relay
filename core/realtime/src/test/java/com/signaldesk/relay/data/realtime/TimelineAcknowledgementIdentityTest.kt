package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineAcknowledgementIdentityTest {

    @Test
    fun matchingEntryAndIncidentIsAccepted() {

        assertTrue(
            isMatchingTimelineAcknowledgement(
                actualEntryId =
                    "ENTRY-1",
                actualIncidentId =
                    "INC-1",
                expectedEntryId =
                    "ENTRY-1",
                expectedIncidentId =
                    "INC-1"
            )
        )
    }

    @Test
    fun matchingEntryButWrongIncidentIsRejected() {

        assertFalse(
            isMatchingTimelineAcknowledgement(
                actualEntryId =
                    "ENTRY-1",
                actualIncidentId =
                    "INC-WRONG",
                expectedEntryId =
                    "ENTRY-1",
                expectedIncidentId =
                    "INC-1"
            )
        )
    }

    @Test
    fun wrongEntryButMatchingIncidentIsRejected() {

        assertFalse(
            isMatchingTimelineAcknowledgement(
                actualEntryId =
                    "ENTRY-WRONG",
                actualIncidentId =
                    "INC-1",
                expectedEntryId =
                    "ENTRY-1",
                expectedIncidentId =
                    "INC-1"
            )
        )
    }
}