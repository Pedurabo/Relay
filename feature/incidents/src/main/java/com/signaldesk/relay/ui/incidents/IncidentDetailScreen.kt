package com.signaldesk.relay.ui.incidents
import androidx.compose.ui.text.font.FontWeight
import com.signaldesk.relay.model.IncidentSeverity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    optimisticStatus: String? = null,
    statusUpdateInProgress: Boolean = false,
    statusUpdateError: String? = null,
    onStatusChange:
        (String) -> Unit = {},
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

            IncidentSummary(
                incident =
                    incident
            )
        }

        item {

            UpdateComposer(
                message =
                    message,
                onMessageChange = {
                    message =
                        it
                },
                onPostUpdate = {
                    val update =
                        message.trim()

                    message =
                        ""

                    onPostUpdate(
                        update
                    )
                }
            )
        }

        item {

            val displayStatus =
                optimisticStatus
                    ?: incident.status

            StatusSelector(
                status =
                    displayStatus,
                syncing =
                    statusUpdateInProgress,
                supportingMessage =
                    statusUpdateError,
                onStatusChange =
                    onStatusChange
            )
        }

        item {

            val displaySeverity =
                optimisticSeverity
                    ?: incident.severity

            SeveritySelector(
                severity =
                    displaySeverity,
                syncing =
                    severityUpdateInProgress,
                supportingMessage =
                    severityUpdateError,
                onSeverityChange =
                    onSeverityChange
            )

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
private fun UpdateComposer(
    message: String,
    onMessageChange: (String) -> Unit,
    onPostUpdate: () -> Unit
) {
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
                    "Post update",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium,
                fontWeight =
                    FontWeight.SemiBold
            )

            OutlinedTextField(
                value =
                    message,
                onValueChange =
                    onMessageChange,
                modifier =
                    Modifier.fillMaxWidth(),
                label = {
                    Text(
                        "What changed?"
                    )
                }
            )

            Button(
                enabled =
                    message.isNotBlank(),
                onClick =
                    onPostUpdate,
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    "Post update"
                )
            }
        }
    }
}


@Composable
private fun IncidentSummary(
    incident: Incident
) {
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
                    8.dp
                )
        ) {

            Text(
                text =
                    incident.title,
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                text =
                    incident.status,
                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )

            Text(
                text =
                    "Incident ${incident.id}",
                style =
                    MaterialTheme
                        .typography
                        .labelMedium
            )
        }
    }
}


@Composable
private fun StatusSelector(
    status: String,
    syncing: Boolean,
    supportingMessage: String?,
    onStatusChange:
        (String) -> Unit
) {

    val lifecycleStatuses =
        listOf(
            "Investigating",
            "Active",
            "Monitoring",
            "Resolved"
        )

    val options =
        if (
            status in
            lifecycleStatuses
        ) {
            lifecycleStatuses
        } else {
            listOf(
                status
            ) +
                lifecycleStatuses
        }

    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {

        Text(
            text =
                "Incident status",
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            fontWeight =
                FontWeight.SemiBold
        )

        Text(
            text =
                if (
                    syncing
                ) {
                    "Syncing $status…"
                } else {
                    "Current status: $status"
                },
            style =
                MaterialTheme
                    .typography
                    .bodyMedium
        )

        options
            .chunked(
                2
            )
            .forEach {
                rowOptions ->

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {

                    rowOptions
                        .forEach {
                            option ->

                            val selected =
                                option ==
                                    status

                            val description =
                                when {

                                    syncing &&
                                    selected ->
                                        "$option status, syncing"

                                    selected ->
                                        "$option status, current"

                                    else ->
                                        "Set status to $option"
                                }

                            FilterChip(
                                selected =
                                    selected,
                                onClick = {
                                    onStatusChange(
                                        option
                                    )
                                },
                                enabled =
                                    !syncing,
                                modifier =
                                    Modifier
                                        .weight(
                                            1f
                                        )
                                        .semantics {
                                            contentDescription =
                                                description
                                        },
                                label = {
                                    Text(
                                        text =
                                            option,
                                        maxLines =
                                            1
                                    )
                                }
                            )
                        }

                    if (
                        rowOptions.size ==
                        1
                    ) {

                        androidx.compose.foundation.layout.Spacer(
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        )
                    }
                }
            }

        if (
            supportingMessage !=
            null
        ) {

            Text(
                text =
                    supportingMessage,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )
        }
    }
}


@Composable
private fun SeveritySelector(
    severity: IncidentSeverity,
    syncing: Boolean,
    supportingMessage: String?,
    onSeverityChange:
        (IncidentSeverity) -> Unit
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {

        Text(
            text =
                "Incident severity",
            style =
                MaterialTheme
                    .typography
                    .titleMedium,
            fontWeight =
                FontWeight.SemiBold
        )

        Text(
            text =
                if (
                    syncing
                ) {
                    "Syncing ${severity.name}…"
                } else {
                    "Current severity: ${severity.name}"
                },
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
                .forEach { option ->

                    val selected =
                        option ==
                            severity

                    val description =
                        when {

                            syncing &&
                            selected ->
                                "${option.name} severity, syncing"

                            selected ->
                                "${option.name} severity, current"

                            else ->
                                "Set severity to ${option.name}"
                        }

                    FilterChip(
                        selected =
                            selected,
                        onClick = {
                            onSeverityChange(
                                option
                            )
                        },
                        enabled =
                            !syncing,
                        modifier =
                            Modifier
                                .weight(
                                    1f
                                )
                                .semantics {
                                    contentDescription =
                                        description
                                },
                        label = {
                            Text(
                                text =
                                    option.name,
                                maxLines =
                                    1
                            )
                        }
                    )
                }
        }

        if (
            supportingMessage !=
            null
        ) {
            Text(
                text =
                    supportingMessage,
                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )
        }
    }
}


@Composable
private fun TimelineEntryItem(
    entry: TimelineEntry,
    onRetry: (TimelineEntry) -> Unit
) {

    val deliveryDescription =
        when (
            entry.deliveryState
        ) {

            DeliveryState.PENDING ->
                "sending"

            DeliveryState.SENT ->
                "delivered"

            DeliveryState.FAILED ->
                "delivery failed"
        }

    OutlinedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription =
                        "Timeline update by ${entry.author}, $deliveryDescription"
                }
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
                    8.dp
                )
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text =
                        entry.author,
                    style =
                        MaterialTheme
                            .typography
                            .titleSmall,
                    fontWeight =
                        FontWeight.SemiBold
                )

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
            }

            Text(
                text =
                    entry.message,
                style =
                    MaterialTheme
                        .typography
                        .bodyLarge
            )

            when (
                entry.deliveryState
            ) {

                DeliveryState.PENDING -> {

                    Text(
                        text =
                            "Sending...",
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium
                    )
                }

                DeliveryState.SENT -> {

                    Text(
                        text =
                            "Delivered",
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium
                    )
                }

                DeliveryState.FAILED -> {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        Text(
                            text =
                                "Delivery failed",
                            style =
                                MaterialTheme
                                    .typography
                                    .labelMedium
                        )

                        Button(
                            onClick = {
                                onRetry(
                                    entry
                                )
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







