package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineAuthoritativeCompatibilityTest {

    @Test
    fun sameIncidentAndMessageIsCompatible() {

        assertTrue(
            isCompatibleTimelineAuthoritativeEvent(
                existingIncidentId =
                    "INC-1",
                existingMessage =
                    "Database restored",
                incomingIncidentId =
                    "INC-1",
                incomingMessage =
                    "Database restored"
            )
        )
    }

    @Test
    fun sameEntryCannotMoveToDifferentIncident() {

        assertFalse(
            isCompatibleTimelineAuthoritativeEvent(
                existingIncidentId =
                    "INC-1",
                existingMessage =
                    "Database restored",
                incomingIncidentId =
                    "INC-2",
                incomingMessage =
                    "Database restored"
            )
        )
    }

    @Test
    fun sameEntryCannotSilentlyChangeMessage() {

        assertFalse(
            isCompatibleTimelineAuthoritativeEvent(
                existingIncidentId =
                    "INC-1",
                existingMessage =
                    "Database restored",
                incomingIncidentId =
                    "INC-1",
                incomingMessage =
                    "Different authoritative text"
            )
        )
    }
}