package com.signaldesk.relay.ui.incidents

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.signaldesk.relay.model.Incident
import com.signaldesk.relay.model.IncidentSeverity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IncidentDetailSeveritySelectorTest {

    @get:Rule
    val composeRule =
        createComposeRule()


    @Test
    fun currentSeverityIsSelectedAndAnotherSeverityCanBeChosen() {

        var selected:
            IncidentSeverity? =
                null

        composeRule.setContent {

            IncidentDetailScreen(
                incident =
                    Incident(
                        id =
                            "INC-UI-1",
                        title =
                            "Severity UX",
                        status =
                            "Active",
                        severity =
                            IncidentSeverity.MEDIUM
                    ),
                timeline =
                    emptyList(),
                onPostUpdate = {},
                onRetry = {},
                onSeverityChange = {
                    selected =
                        it
                }
            )
        }

        composeRule
            .onNodeWithContentDescription(
                "MEDIUM severity, current"
            )
            .assertIsSelected()

        composeRule
            .onNodeWithContentDescription(
                "Set severity to HIGH"
            )
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {

            assertEquals(
                IncidentSeverity.HIGH,
                selected
            )
        }
    }


    @Test
    fun syncingStateLocksSeverityChoices() {

        composeRule.setContent {

            IncidentDetailScreen(
                incident =
                    Incident(
                        id =
                            "INC-UI-2",
                        title =
                            "Severity syncing",
                        status =
                            "Active",
                        severity =
                            IncidentSeverity.MEDIUM
                    ),
                timeline =
                    emptyList(),
                onPostUpdate = {},
                onRetry = {},
                optimisticSeverity =
                    IncidentSeverity.CRITICAL,
                severityUpdateInProgress =
                    true,
                severityUpdateError =
                    "Severity queued; waiting for authoritative sync.",
                onSeverityChange = {}
            )
        }

        composeRule
            .onNodeWithContentDescription(
                "CRITICAL severity, syncing"
            )
            .assertIsSelected()
            .assertIsNotEnabled()

        composeRule
            .onNodeWithContentDescription(
                "Set severity to LOW"
            )
            .assertIsNotEnabled()
    }
}