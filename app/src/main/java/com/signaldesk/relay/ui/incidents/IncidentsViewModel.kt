package com.signaldesk.relay.ui.incidents

import com.signaldesk.relay.BuildConfig

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signaldesk.relay.appstate.IncidentOperationsLifecycleCoordinator
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.realtime.IncidentEventProcessor
import com.signaldesk.relay.data.realtime.RealtimeIncidentCoordinator
import com.signaldesk.relay.data.realtime.WebSocketRealtimeIncidentSource
import com.signaldesk.relay.data.repository.IncidentRepository
import com.signaldesk.relay.data.session.AuthLoginFailure
import com.signaldesk.relay.data.session.AuthSessionClient
import com.signaldesk.relay.data.session.classifyAuthLoginFailure
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionRefreshCoordinator
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.model.Incident
import com.signaldesk.relay.notifications.IncidentNotificationManager
import com.signaldesk.relay.notifications.PushRegistrationCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IncidentsViewModel(
    application: Application
) : AndroidViewModel(application) {

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
                BuildConfig.RELAY_HTTP_BASE_URL
        )

    val sessionState =
        SessionManager.sessionState

    val signInInProgress =
        MutableStateFlow(false)

    val signInError =
        MutableStateFlow<String?>(null)


    private val realtimeSource =
        WebSocketRealtimeIncidentSource(
            url =
                BuildConfig.RELAY_WEBSOCKET_URL,
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
                rejectedAccessToken ->

                viewModelScope.launch {

                    SessionRefreshCoordinator
                        .refreshOrSignOut(
                            rejectedAccessToken
                        )
                }
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

    private val operationsLifecycleCoordinator =
        IncidentOperationsLifecycleCoordinator(
            context =
                application,
            realtimeCoordinator =
                realtimeCoordinator
        )

    val connectionState =
        realtimeCoordinator
            .connectionState

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
        }

        operationsLifecycleCoordinator
            .start(
                viewModelScope
            )
    }

    fun signIn(
        username: String,
        password: String
    ) {

        if (
            signInInProgress.value
        ) {
            return
        }

        if (
            username.isBlank() ||
            password.isBlank()
        ) {

            signInError.value =
                "Username and password are required."

            return
        }

        signInInProgress.value =
            true

        signInError.value =
            null

        viewModelScope.launch {

            runCatching {

                authSessionClient
                    .login(
                        username =
                            username.trim(),
                        password =
                            password
                    )

            }
                .onSuccess { session ->

                    SessionManager
                        .establishSession(
                            session
                        )

                    signInError.value =
                        null
                }
                .onFailure { error ->

                    SessionManager
                        .signOut()

                    signInError.value =
                        when (
                            classifyAuthLoginFailure(
                                error
                            )
                        ) {
                            AuthLoginFailure.INVALID_CREDENTIALS ->
                                "Incorrect username or password."

                            AuthLoginFailure.RATE_LIMITED ->
                                "Too many sign-in attempts. Try again later."

                            AuthLoginFailure.NETWORK_UNAVAILABLE ->
                                "Unable to reach Relay. Check your connection."

                            AuthLoginFailure.SERVER_ERROR ->
                                "Relay is temporarily unavailable. Try again later."

                            AuthLoginFailure.UNEXPECTED ->
                                "Sign-in failed. Try again."
                        }
                }

            signInInProgress.value =
                false
        }
    }

    fun signOut() {

        val current =
            SessionManager
                .sessionState
                .value

        SessionManager
            .signOut()

        if (
            current is
            SessionState.SignedIn
        ) {

            PushRegistrationCoordinator
                .unregister(
                    context =
                        getApplication(),
                    accessToken =
                        current.accessToken
                )

            viewModelScope.launch {

                runCatching {

                    authSessionClient
                        .revokeSession(
                            current.refreshToken
                        )
                }
            }
        }
    }

    override fun onCleared() {

        operationsLifecycleCoordinator
            .stop()

        super.onCleared()
    }
}
