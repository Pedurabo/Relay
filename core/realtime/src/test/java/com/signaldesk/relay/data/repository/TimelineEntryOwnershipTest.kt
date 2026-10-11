package com.signaldesk.relay.data.repository

import com.signaldesk.relay.data.local.TimelineEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineEntryOwnershipTest {

    @Test
    fun timelineEntityRequiresExplicitOwner() {

        val entry =
            TimelineEntryEntity(
                ownerPrincipal = "operator-a",
                entryId =
                    "ENTRY-OLD",
                incidentId =
                    "INC-1",
                message =
                    "Legacy",
                author =
                    "Operator",
                occurredAt =
                    1L,
                deliveryState =
                    "SENT"
            )

        assertEquals("operator-a", entry.ownerPrincipal)
    }


    @Test
    fun pendingTimelineEntityRetainsExplicitOwner() {

        val entry =
            TimelineEntryEntity(
                entryId =
                    "ENTRY-NEW",
                incidentId =
                    "INC-1",
                message =
                    "Update",
                author =
                    "You",
                occurredAt =
                    2L,
                deliveryState =
                    "PENDING",
                ownerPrincipal =
                    "operator-a"
            )

        assertEquals(
            "operator-a",
            entry.ownerPrincipal
        )
    }
}
