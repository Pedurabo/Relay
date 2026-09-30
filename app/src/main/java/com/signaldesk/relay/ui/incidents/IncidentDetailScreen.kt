package com.signaldesk.relay.ui.incidents
import androidx.compose.ui.text.font.FontWeight
import com.signaldesk.relay.model.IncidentSeverity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.signaldesk.relay.model.DeliveryState
import com.signaldesk.relay.model.Incident
import com.signaldesk.relay.model.TimelineEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IncidentDetailScreen(
    incident: Incident?,
    timeline: List<TimelineEntry>,
    onPostUpdate: (String) -> Unit,
    onRetry: (TimelineEntry) -> Unit,
    optimisticSeverity: IncidentSeverity? = null,
    severityUpdateInProgress: Boolean = false,
    severityUpdateError: String? = null,
    onSeverityChange:
        (IncidentSeverity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var message by
        remember {
            mutableStateOf("")
        }

    if (incident == null) {
        Column(
            modifier =
                modifier.fillMaxSize()
        ) {
            Text(
                text = "Loading incident..."
            )
        }

        return
    }

    LazyColumn(
        modifier =
            modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = incident.id,
                style =
                    MaterialTheme
                        .typography
                        .labelLarge
            )
        }

        item {
            Text(
                text = incident.title,
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium
            )
        }

        item {
            Text(
                text =
                    "Status: ${incident.status}",
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge
            )
        }

        item {
            Text(
                text = "Post update",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )
        }

        item {
            OutlinedTextField(
                value = message,
                onValueChange = {
                    message = it
                },
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text(
                        "What changed?"
                    )
                }
            )
        }

        item {
            Button(
                enabled =
                    message.isNotBlank(),
                onClick = {
                    val update =
                        message.trim()

                    message = ""

                    onPostUpdate(
                        update
                    )
                }
            ) {
                Text(
                    "Post update"
                )
            }
        }

        item {

            val displaySeverity =
                optimisticSeverity
                    ?: incident.severity

            Text(
                text = "Incident severity",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                text =
                    "Current: ${displaySeverity.name}",
                style =
                    MaterialTheme
                        .typography
                        .bodyMedium
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {

                IncidentSeverity.entries
                    .forEach { severity ->

                        Button(
                            onClick = {
                                onSeverityChange(
                                    severity
                                )
                            },
                            enabled =
                                !severityUpdateInProgress &&
                                    severity !=
                                    displaySeverity
                        ) {

                            Text(
                                text =
                                    severity.name,
                                maxLines = 1
                            )
                        }
                    }
            }

            if (
                severityUpdateInProgress
            ) {
                Text(
                    text =
                        "${displaySeverity.name} · waiting for server confirmation…",
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }

            if (
                severityUpdateError !=
                null
            ) {
                Text(
                    text =
                        severityUpdateError,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }


            Text(
                text = "Timeline",
                style =
                    MaterialTheme
                        .typography
                        .titleLarge
            )
        }

        if (timeline.isEmpty()) {
            item {
                Text(
                    "No timeline updates yet."
                )
            }
        }

        items(
            items = timeline,
            key = {
                it.id
            }
        ) { entry ->
            TimelineEntryItem(
                entry = entry,
                onRetry = onRetry
            )
        }
    }
}

@Composable
private fun TimelineEntryItem(
    entry: TimelineEntry,
    onRetry: (TimelineEntry) -> Unit
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text =
                formatTimestamp(
                    entry.occurredAt
                ),
            style =
                MaterialTheme
                    .typography
                    .labelMedium
        )

        Text(
            text = entry.message,
            style =
                MaterialTheme
                    .typography
                    .bodyLarge
        )

        Text(
            text = "By ${entry.author}",
            style =
                MaterialTheme
                    .typography
                    .bodySmall
        )

        when (
            entry.deliveryState
        ) {
            DeliveryState.PENDING -> {
                Text(
                    text = "Sending…",
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )
            }

            DeliveryState.SENT -> {
                Text(
                    text = "Sent",
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )
            }

            DeliveryState.FAILED -> {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    Text(
                        text =
                            "Not delivered",
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium
                    )

                    Button(
                        onClick = {
                            onRetry(entry)
                        }
                    ) {
                        Text(
                            "Retry"
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(
    timestamp: Long
): String {
    return SimpleDateFormat(
        "HH:mm:ss",
        Locale.getDefault()
    ).format(
        Date(timestamp)
    )
}







