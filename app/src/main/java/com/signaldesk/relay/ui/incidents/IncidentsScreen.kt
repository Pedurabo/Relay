package com.signaldesk.relay.ui.incidents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.signaldesk.relay.data.realtime.RealtimeConnectionState
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.model.Incident
import com.signaldesk.relay.model.IncidentSeverity

@Composable
fun IncidentsScreen(
    incidents: List<Incident>,
    connectionState: RealtimeConnectionState,
    sessionState: SessionState,
    signInInProgress: Boolean,
    signInError: String?,
    onIncidentClick: (String) -> Unit,
    onCreateIncidentClick: () -> Unit,
    onSignIn: (
        String,
        String
    ) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier =
            modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {
            Text(
                text = "Relay",
                style =
                    MaterialTheme
                        .typography
                        .headlineMedium,
                fontWeight =
                    FontWeight.Bold
            )
        }

        when (sessionState) {

            SessionState.SignedOut -> {

                item {

                    var username by
                        remember {
                            mutableStateOf(
                                ""
                            )
                        }

                    var password by
                        remember {
                            mutableStateOf(
                                ""
                            )
                        }

                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(
                                12.dp
                            )
                    ) {

                        Text(
                            text = "Signed out",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium
                        )

                        Text(
                            text =
                                "Sign in to enable realtime coordination.",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium
                        )

                        OutlinedTextField(
                            value =
                                username,
                            onValueChange = {
                                username =
                                    it
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    "Username"
                                )
                            },
                            singleLine =
                                true
                        )

                        OutlinedTextField(
                            value =
                                password,
                            onValueChange = {
                                password =
                                    it
                            },
                            modifier =
                                Modifier.fillMaxWidth(),
                            label = {
                                Text(
                                    "Password"
                                )
                            },
                            singleLine =
                                true,
                            visualTransformation =
                                PasswordVisualTransformation()
                        )

                        signInError
                            ?.let { error ->

                                Text(
                                    text =
                                        error,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium
                                )
                            }

                        Button(
                            enabled =
                                !signInInProgress,
                            onClick = {
                                onSignIn(
                                    username,
                                    password
                                )
                            }
                        ) {
                            Text(
                                if (
                                    signInInProgress
                                ) {
                                    "Signing in…"
                                } else {
                                    "Sign in"
                                }
                            )
                        }
                    }
                }
            }

            is SessionState.SignedIn -> {

                item {

                    SignedInOperationsPanel(
                        userName =
                            sessionState.userName,
                        connectionState =
                            connectionState,
                        onCreateIncidentClick =
                            onCreateIncidentClick,
                        onSignOut =
                            onSignOut
                    )
                }

                if (incidents.isEmpty()) {

                    item {
                        Text(
                            text =
                                "No incidents yet."
                        )
                    }
                }

                items(
                    items =
                        incidents,
                    key = {
                        it.id
                    }
                ) { incident ->

                    IncidentCard(
                        incident =
                            incident,
                        onClick = {
                            onIncidentClick(
                                incident.id
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SignedInOperationsPanel(
    userName: String,
    connectionState: RealtimeConnectionState,
    onCreateIncidentClick: () -> Unit,
    onSignOut: () -> Unit
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

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            2.dp
                        )
                ) {

                    Text(
                        text =
                            "Signed in",
                        style =
                            MaterialTheme
                                .typography
                                .labelMedium
                    )

                    Text(
                        text =
                            userName,
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium,
                        fontWeight =
                            FontWeight.SemiBold
                    )
                }

                Button(
                    onClick =
                        onSignOut
                ) {
                    Text(
                        "Sign out"
                    )
                }
            }

            ConnectionStateRow(
                state =
                    connectionState
            )

            Button(
                onClick =
                    onCreateIncidentClick,
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


@Composable
private fun ConnectionStateRow(
    state: RealtimeConnectionState
) {
    val label =
        when (state) {

            RealtimeConnectionState.Connected ->
                "Realtime: Connected"

            RealtimeConnectionState.Connecting ->
                "Realtime: Connecting…"

            RealtimeConnectionState.Disconnected ->
                "Realtime: Disconnected"

            is RealtimeConnectionState.Retrying -> {

                val seconds =
                    (
                        state.delayMillis /
                            1_000L
                        )
                        .coerceAtLeast(1)

                "Realtime: Retrying in ${seconds}s " +
                    "(attempt ${state.attempt})"
            }
        }

    Text(
        text = label,
        style =
            MaterialTheme
                .typography
                .labelLarge
    )
}

@Composable
private fun IncidentCard(
    incident: Incident,
    onClick: () -> Unit
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick =
                        onClick
                ),
        elevation =
            CardDefaults
                .cardElevation(
                    defaultElevation =
                        2.dp
                )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                        incident.title,
                    modifier =
                        Modifier.weight(1f),
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,
                    fontWeight =
                        FontWeight.SemiBold
                )

                SeverityBadge(
                    severity =
                        incident.severity
                )
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text =
                        incident.id,
                    style =
                        MaterialTheme
                            .typography
                            .labelMedium
                )

                Text(
                    text =
                        "Status: ${incident.status}",
                    style =
                        MaterialTheme
                            .typography
                            .labelLarge,
                    fontWeight =
                        FontWeight.Medium
                )
            }

            HorizontalDivider()

            Text(
                text =
                    "Latest update",
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                fontWeight =
                    FontWeight.SemiBold
            )

            val latestMessage =
                incident.latestMessage

            if (
                latestMessage != null
            ) {

                Text(
                    text =
                        latestMessage,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis
                )

                incident
                    .latestAuthor
                    ?.let { author ->

                        Text(
                            text =
                                "By $author",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }

            } else {

                Text(
                    text =
                        "No timeline updates yet",
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    fontStyle =
                        FontStyle.Italic
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        2.dp
                    )
            )

            Text(
                text =
                    "Tap to open timeline",
                style =
                    MaterialTheme
                        .typography
                        .bodySmall
            )
        }
    }
}

@Composable
private fun SeverityBadge(
    severity: IncidentSeverity
) {
    Surface(
        shape =
            MaterialTheme
                .shapes
                .small,
        tonalElevation =
            when (severity) {

                IncidentSeverity.CRITICAL ->
                    8.dp

                IncidentSeverity.HIGH ->
                    6.dp

                IncidentSeverity.MEDIUM ->
                    3.dp

                IncidentSeverity.LOW ->
                    1.dp
            }
    ) {

        Text(
            text =
                severity.name,
            modifier =
                Modifier.padding(
                    horizontal = 8.dp,
                    vertical = 4.dp
                ),
            style =
                MaterialTheme
                    .typography
                    .labelMedium,
            fontWeight =
                FontWeight.Bold
        )
    }
}
