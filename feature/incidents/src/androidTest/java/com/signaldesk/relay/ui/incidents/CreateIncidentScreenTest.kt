package com.signaldesk.relay.ui.incidents

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class CreateIncidentScreenTest {

    @get:Rule
    val composeRule =
        createComposeRule()

    @Test
    fun savingStateDisablesFormAndShowsProgress() {

        composeRule.setContent {
            MaterialTheme {
                CreateIncidentScreen(
                    onCreateIncident = { _, _ -> },
                    createInProgress = true
                )
            }
        }

        composeRule
            .onNodeWithText(
                "Saving..."
            )
            .assertExists()
            .assertIsNotEnabled()

        composeRule
            .onNodeWithText(
                "Create incident"
            )
            .assertDoesNotExist()
    }

    @Test
    fun persistenceFailureShowsRetryableError() {

        composeRule.setContent {
            MaterialTheme {
                CreateIncidentScreen(
                    onCreateIncident = { _, _ -> },
                    createError =
                        "Unable to save incident. Try again."
                )
            }
        }

        composeRule
            .onNodeWithText(
                "Unable to save incident. Try again."
            )
            .assertExists()

        composeRule
            .onNodeWithText(
                "Create incident"
            )
            .assertExists()
    }
}
