package com.signaldesk.relay.ui.incidents

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.signaldesk.relay.model.DeliveryState
import com.signaldesk.relay.model.Incident
import com.signaldesk.relay.model.IncidentSeverity
import com.signaldesk.relay.model.TimelineEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IncidentDetailTimelineCardTest {

    @get:Rule
    val composeRule =
        createComposeRule()


    @Test
    fun failedTimelineEntryShowsRetryAndInvokesCallback() {

        val failedEntry =
            TimelineEntry(
                id =
                    "ENTRY-FAILED",
                incidentId =
                    "INC-UI-TIMELINE",
                message =
                    "Database latency increased",
                author =
                    "Operator A",
                occurredAt =
                    1_000L,
                deliveryState =
                    DeliveryState.FAILED
            )

        var retried:
            TimelineEntry? =
                null

        composeRule.setContent {

            IncidentDetailScreen(
                incident =
                    Incident(
                        id =
                            "INC-UI-TIMELINE",
                        title =
                            "Timeline UX",
                        status =
                            "Active",
                        severity =
                            IncidentSeverity.HIGH
                    ),
                timeline =
                    listOf(
                        failedEntry
                    ),
                onPostUpdate = {},
                onRetry = {
                    retried =
                        it
                }
            )
        }

        composeRule
            .onNodeWithContentDescription(
                "Timeline update by Operator A, delivery failed"
            )
            .assertExists()

        composeRule
            .onNodeWithText(
                "Database latency increased"
            )
            .assertExists()

        composeRule
            .onNodeWithText(
                "Delivery failed"
            )
            .assertExists()

        composeRule
            .onNodeWithText(
                "Retry"
            )
            .performClick()

        composeRule.runOnIdle {

            assertEquals(
                failedEntry,
                retried
            )
        }
    }


    @Test
    fun pendingTimelineEntryShowsSendingState() {

        composeRule.setContent {

            IncidentDetailScreen(
                incident =
                    Incident(
                        id =
                            "INC-UI-PENDING",
                        title =
                            "Pending timeline UX",
                        status =
                            "Active",
                        severity =
                            IncidentSeverity.MEDIUM
                    ),
                timeline =
                    listOf(
                        TimelineEntry(
                            id =
                                "ENTRY-PENDING",
                            incidentId =
                                "INC-UI-PENDING",
                            message =
                                "Investigating upstream timeout",
                            author =
                                "You",
                            occurredAt =
                                2_000L,
                            deliveryState =
                                DeliveryState.PENDING
                        )
                    ),
                onPostUpdate = {},
                onRetry = {}
            )
        }

        composeRule
            .onNodeWithContentDescription(
                "Timeline update by You, waiting to sync"
            )
            .assertExists()

        composeRule
            .onNodeWithText(
                "Investigating upstream timeout"
            )
            .assertExists()

        composeRule
            .onNodeWithText(
                "Waiting to sync"
            )
            .assertExists()
    }
}