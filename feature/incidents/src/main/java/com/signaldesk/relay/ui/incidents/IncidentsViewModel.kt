package com.signaldesk.relay.ui.incidents


import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.realtime.IncidentEventProcessor
import com.signaldesk.relay.data.remote.model.IncidentEvent
import com.signaldesk.relay.data.remote.IncidentSnapshotHttpException
import com.signaldesk.relay.data.remote.IncidentSnapshotClient
import com.signaldesk.relay.data.realtime.RealtimeIncidentCoordinator
import com.signaldesk.relay.data.realtime.WebSocketRealtimeIncidentSource
import com.signaldesk.relay.data.repository.IncidentRepository
import com.signaldesk.relay.data.session.AuthLoginFailure
import com.signaldesk.relay.data.session.AuthSessionClient
import com.signaldesk.relay.data.session.classifyAuthLoginFailure
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionRefreshCoordinator
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.data.session.SessionTerminationReason
import com.signaldesk.relay.model.Incident
import com.signaldesk.relay.notifications.PushRegistrationCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal fun <T> Flow<T>.markIncidentsLoaded(
    loadedState: MutableStateFlow<Boolean>
): Flow<T> =
    onEach {
        loadedState.value = true
    }

class IncidentsViewModel(
    application: Application,
    private val httpBaseUrl: String,
    private val webSocketUrl: String,
    private val operationsLifecycle:
        IncidentOperationsLifecycle,
    private val onAppliedEvent:
        (IncidentEvent) -> Unit
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


    private val _incidentsLoaded =
        MutableStateFlow(false)

    val incidentsLoaded:
        StateFlow<Boolean> =
        _incidentsLoaded

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
            .markIncidentsLoaded(_incidentsLoaded)
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
                httpBaseUrl
        )

    private val incidentSnapshotClient =
        IncidentSnapshotClient(
            baseUrl =
                httpBaseUrl
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
                webSocketUrl,
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
            onAppliedEvent =
                onAppliedEvent
        )


    val connectionState =
        RealtimeIncidentCoordinator
            .processConnectionState

    init {
        viewModelScope.launch {

            SessionManager
                .sessionState
                .collectLatest {
                    state ->

                    if (
                        state is
                        SessionState.SignedIn
                    ) {

                        bootstrapAuthoritativeIncidents(
                            state
                        )
                    }
                }
        }

        viewModelScope.launch {
            SessionManager
                .sessionTerminationReason
                .collect { reason ->

                    when (reason) {
                        SessionTerminationReason.EXPIRED ->
                            signInError.value =
                                "Your session expired. Sign in again."

                        SessionTerminationReason.PRINCIPAL_CHANGED ->
                            signInError.value =
                                "Your session changed. Sign in again."

                        null ->
                            Unit
                    }
                }
        }

        operationsLifecycle
            .start(
                scope =
                    viewModelScope,
                realtimeCoordinator =
                    realtimeCoordinator
            )
    }

    private suspend fun bootstrapAuthoritativeIncidents(
        session: SessionState.SignedIn
    ) {

        val originatingGeneration =
            SessionManager.sessionGeneration

        val snapshots =
            try {

                incidentSnapshotClient
                    .load(
                        session.accessToken
                    )

            } catch (
                error:
                    IncidentSnapshotHttpException
            ) {

                if (
                    error.statusCode ==
                    401
                ) {

                    Log.i(
                        TAG,
                        "INCIDENT_BOOTSTRAP_AUTH_REJECTED"
                    )

                    SessionRefreshCoordinator
                        .refreshOrSignOut(
                            session.accessToken
                        )

                } else {

                    Log.i(
                        TAG,
                        "INCIDENT_BOOTSTRAP_FAILED|http=${error.statusCode}"
                    )
                }

                return

            } catch (cancelled: CancellationException) {
     throw cancelled
 }
 catch (
                error: Throwable
            ) {

                Log.i(
                    TAG,
                    "INCIDENT_BOOTSTRAP_FAILED|error=${error.javaClass.simpleName}"
                )

                return
            }

        currentCoroutineContext().ensureActive()

        if (
            SessionManager.sessionGeneration !=
                originatingGeneration ||
            SessionManager.sessionState.value !=
                session
        ) {
            Log.i(
                TAG,
                "INCIDENT_BOOTSTRAP_SUPERSEDED"
            )
            return
        }

        val applied =
            SessionManager
                .withAuthorizedSnapshot(
                    expectedGeneration =
                        originatingGeneration
                ) {
                    currentCoroutineContext().ensureActive()

                    repository
                        .reconcileAuthoritativeSnapshot(
                            snapshots
                        )
                }

        if (applied == null) {
            Log.i(
                TAG,
                "INCIDENT_BOOTSTRAP_SUPERSEDED|phase=commit"
            )
            return
        }

        Log.i(
            TAG,
            "INCIDENT_BOOTSTRAP_APPLIED|received=${snapshots.size}|applied=$applied"
        )
    }


    fun signIn(
        username: String,
        password: String
    ) {

        if (signInInProgress.value) {
            return
        }

        if (username.isBlank() || password.isBlank()) {
            signInError.value =
                "Username and password are required."
            return
        }

        signInInProgress.value = true
        signInError.value = null

        val signInOriginGeneration =
            SessionManager.sessionGeneration

        val signInOriginRevision =
            SessionManager.captureLoginAttemptRevision()

        viewModelScope.launch {

            try {
                val session =
                    authSessionClient.login(
                        username = username.trim(),
                        password = password
                    )

                val published =
                    SessionManager
                        .establishSessionIfSignedOutCoordinated(
                            expectedGeneration =
                                signInOriginGeneration,
                            expectedLoginRevision =
                                signInOriginRevision,
                            session = session
                        )

                if (published) {
                    signInError.value = null
                } else {
                    Log.i(
                        TAG,
                        "SIGN_IN_SUPERSEDED"
                    )
                }

            } catch (cancelled: CancellationException) {
                throw cancelled

            } catch (error: Throwable) {

                if (
                    SessionManager.sessionGeneration !=
                        signInOriginGeneration ||
                    SessionManager.sessionState.value !=
                        SessionState.SignedOut
                ) {
                    Log.i(
                        TAG,
                        "SIGN_IN_FAILURE_SUPERSEDED"
                    )
                    return@launch
                }

                signInError.value =
                    when (classifyAuthLoginFailure(error)) {
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

            } finally {
                signInInProgress.value = false
            }
        }
    }
    fun signOut() {

        val current =
            SessionManager
                .sessionState
                .value

        val originatingGeneration =
            SessionManager.sessionGeneration

        val application = getApplication<Application>()
        val client = authSessionClient

        SessionManager
            .requestSignOutIfUnchanged(
                expectedGeneration =
                    originatingGeneration,
                expectedSession =
                    current
            ) { signedOut ->

                if (!signedOut) {
                    Log.i(
                        TAG,
                        "SIGN_OUT_SUPERSEDED"
                    )
                    return@requestSignOutIfUnchanged
                }

                if (
                    current is
                    SessionState.SignedIn
                ) {
                    try {
                        PushRegistrationCoordinator
                            .unregister(
                                context = application,
                                accessToken = current.accessToken
                            )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(
                            TAG,
                            "SIGN_OUT_PUSH_UNREGISTER_FAILED",
                            error
                        )
                    }

                    try {
                        client.revokeSession(
                            current.refreshToken
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(
                            TAG,
                            "SIGN_OUT_REVOCATION_FAILED",
                            error
                        )
                    }
                }
            }
    }

    companion object {

        private const val TAG =
            "RelayIncidentsViewModel"
    }


    override fun onCleared() {

        operationsLifecycle
            .stop(
                realtimeCoordinator
            )

        super.onCleared()
    }
}
