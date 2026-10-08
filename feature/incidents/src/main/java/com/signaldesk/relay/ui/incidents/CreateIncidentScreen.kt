package com.signaldesk.relay.ui.incidents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CreateIncidentScreen(
    onCreateIncident: (
        title: String,
        status: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember {
        mutableStateOf("")
    }

    var status by remember {
        mutableStateOf("")
    }

    val canCreate =
        title.isNotBlank() &&
        status.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "New incident",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedCard(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            16.dp
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {

                Text(
                    text =
                        "Incident details",
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium
                )

                OutlinedTextField(
                    value =
                        title,
                    onValueChange = {
                        title =
                            it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            "Title"
                        )
                    },
                    singleLine =
                        true
                )

                OutlinedTextField(
                    value =
                        status,
                    onValueChange = {
                        status =
                            it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            "Initial status"
                        )
                    },
                    singleLine =
                        true
                )

                Button(
                    onClick = {
                        onCreateIncident(
                            title.trim(),
                            status.trim()
                        )
                    },
                    enabled =
                        canCreate,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Create incident"
                    )
                }
            }
        }
    }
}
