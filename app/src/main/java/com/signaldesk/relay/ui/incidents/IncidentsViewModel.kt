package com.signaldesk.relay.ui.incidents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signaldesk.relay.appstate.AppVisibilityTracker
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.realtime.IncidentEventProcessor
import com.signaldesk.relay.data.realtime.RealtimeIncidentCoordinator
import com.signaldesk.relay.data.realtime.SeverityOutboxCoordinator
import com.signaldesk.relay.data.realtime.WebSocketRealtimeIncidentSource
import com.signaldesk.relay.data.repository.IncidentRepository
import com.signaldesk.relay.data.session.AuthSessionClient
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.model.Incident
import com.signaldesk.relay.notifications.IncidentNotificationManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IncidentsViewModel(
    application: Application
) : AndroidViewModel(application) {

    init {
        AppVisibilityTracker.initialize(
            application
        )

        SessionManager.initialize(
            application
        )

        SeverityOutboxCoordinator
            .initialize(
                application
            )
    }

    private val database =
        RelayDatabase.getInstance(
            application
        )

    private val repository =
        IncidentRepository(
            incidentDao =
                database.incidentDao(),
            timelineEntryDao =
                database.timelineEntryDao()
        )

    private val notificationManager =
        IncidentNotificationManager(
            application
        )

    val incidents:
        StateFlow<List<Incident>> =
        combine(
            repository.observeIncidents(),
            database
                .timelineEntryDao()
                .observeLatestForAllIncidents()
        ) { incidents, latestEntries ->

            val latestByIncident =
                latestEntries.associateBy {
                    it.incidentId
                }

            incidents.map { incident ->

                val latest =
                    latestByIncident[
                        incident.id
                    ]

                incident.copy(
                    latestMessage =
                        latest?.message,
                    latestAuthor =
                        latest?.author,
                    latestUpdateAt =
                        latest?.occurredAt
                )
            }
        }
            .stateIn(
                scope =
                    viewModelScope,
                started =
                    SharingStarted
                        .WhileSubscribed(
                            5_000
                        ),
                initialValue =
                    emptyList()
            )

    private val authSessionClient =
        AuthSessionClient(
            baseUrl =
                "http://127.0.0.1:9000"
        )

    val sessionState =
        SessionManager.sessionState

    private val realtimeSource =
        WebSocketRealtimeIncidentSource(
            url =
                "ws://127.0.0.1:9000",
            tokenProvider = {

                when (
                    val state =
                        SessionManager
                            .sessionState
                            .value
                ) {

                    SessionState.SignedOut ->
                        null

                    is SessionState.SignedIn ->
                        state.accessToken
                }
            },
            onSessionInvalidated = {
                SessionManager.signOut()
            }
        )

    private val realtimeCoordinator =
        RealtimeIncidentCoordinator(
            source =
                realtimeSource,
            processor =
                IncidentEventProcessor(
                    database
                ),
            gapDao =
                database
                    .incidentSequenceGapDao(),
            onAppliedEvent = { event ->

                notificationManager
                    .notifyAppliedEvent(
                        event
                    )
            }
        )

    val connectionState =
        realtimeCoordinator
            .connectionState

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
        }

        viewModelScope.launch {

            sessionState
                .collectLatest { state ->

                    when (state) {

                        SessionState.SignedOut -> {
                            realtimeCoordinator
                                .stop()
                        }

                        is SessionState.SignedIn -> {
                            realtimeCoordinator
                                .start(
                                    viewModelScope
                                )

                            SeverityOutboxCoordinator
                                .kick()
                        }
                    }
                }
        }
    }

    fun signIn() {

        viewModelScope.launch {

            runCatching {

                authSessionClient
                    .createDevelopmentSession()

            }
                .onSuccess { session ->

                    SessionManager
                        .establishSession(
                            session
                        )
                }
                .onFailure {

                    SessionManager
                        .signOut()
                }
        }
    }

    fun signOut() {
        SessionManager.signOut()
    }

    override fun onCleared() {
        realtimeCoordinator.stop()
        super.onCleared()
    }
}

