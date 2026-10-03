package com.signaldesk.relay.ui.incidents
import com.signaldesk.relay.data.local.RoomPendingSeverityCommandStore
import com.signaldesk.relay.data.local.PendingSeverityCommand
import java.util.UUID
import kotlinx.coroutines.delay


import com.signaldesk.relay.model.IncidentSeverity
import kotlinx.coroutines.flow.MutableStateFlow
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.repository.IncidentRepository
import com.signaldesk.relay.data.realtime.WebSocketTimelineSender
import com.signaldesk.relay.data.realtime.TimelineDeliveryCredential
import com.signaldesk.relay.data.realtime.isTimelineDeliverySessionCurrent
import com.signaldesk.relay.data.realtime.timelineDeliveryCredential
import com.signaldesk.relay.data.realtime.SeverityOutboxCoordinator
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionRefreshCoordinator
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.model.Incident
import com.signaldesk.relay.model.TimelineEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class IncidentDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val pendingSeverityStore by lazy {
        RoomPendingSeverityCommandStore(
            database
                .pendingSeverityCommandDao()
        )
    }

    val severityUpdateInProgress =
        MutableStateFlow(false)

    val severityUpdateError =
        MutableStateFlow<String?>(null)

    val optimisticSeverity =
        MutableStateFlow<IncidentSeverity?>(null)


    private val incidentId: String =
        checkNotNull(
            savedStateHandle["incidentId"]
        )

    private val database =
        RelayDatabase.getInstance(application)

    private val repository =
        IncidentRepository(
            incidentDao =
                database.incidentDao(),
            timelineEntryDao =
                database.timelineEntryDao()
        )

    private val timelineSender =
        WebSocketTimelineSender(
            url =
                "ws://127.0.0.1:9000",
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

    val incident: StateFlow<Incident?> =
        repository
            .observeIncident(incidentId)
            .stateIn(
                scope = viewModelScope,
                started =
                    SharingStarted
                        .WhileSubscribed(5_000),
                initialValue = null
            )

    val timeline: StateFlow<List<TimelineEntry>> =
        repository
            .observeTimeline(incidentId)
            .stateIn(
                scope = viewModelScope,
                started =
                    SharingStarted
                        .WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    fun postUpdate(
        message: String
    ) {
        if (message.isBlank()) {
            return
        }

        viewModelScope.launch {
            val currentSession =
                SessionManager
                    .sessionState
                    .value as?
                    SessionState.SignedIn
                    ?: return@launch

            val pending =
                repository
                    .createPendingTimelineEntry(
                        incidentId =
                            incidentId,
                        message =
                            message,
                        author =
                            "You",
                        ownerPrincipal =
                            currentSession.userId
                    )

            val credential =
                timelineDeliveryCredential(
                    currentSession,
                    currentSession.userId
                )
                    ?: return@launch

            sendPendingEntry(
                pending,
                credential
            )
        }
    }

    fun retry(
        entry: TimelineEntry
    ) {
        viewModelScope.launch {

            val ownerPrincipal =
                repository
                    .getTimelineEntryOwnerPrincipal(
                        entry.id
                    )
                    ?: return@launch

            val credential =
                timelineDeliveryCredential(
                    SessionManager
                        .sessionState
                        .value,
                    ownerPrincipal
                )
                    ?: return@launch

            repository
                .markTimelineEntryPending(
                    entry.id
                )

            sendPendingEntry(
                entry,
                credential
            )
        }
    }

    private suspend fun sendPendingEntry(
        entry: TimelineEntry,
        credential:
            TimelineDeliveryCredential
    ) {

        if (
            !isTimelineDeliverySessionCurrent(
                SessionManager
                    .sessionState
                    .value,
                credential.ownerPrincipal,
                credential.accessToken
            )
        ) {
            return
        }

        try {
            val acknowledgement =
                withTimeout(10_000) {
                    timelineSender.send(
                        entry,
                        credential.accessToken
                    )
                }

            repository
                .confirmTimelineEntry(
                    acknowledgement,
                    credential.ownerPrincipal
                )
        } catch (
            cancellation:
                kotlinx.coroutines.CancellationException
        ) {
            throw cancellation
        } catch (
            error:
                Throwable
        ) {
            repository
                .markTimelineEntryFailed(
                    entry.id
                )
        }
    }
    // PERSISTENT_SEVERITY_OUTBOX

    init {

        viewModelScope.launch {

            val ownerPrincipal =
                when (
                    val session =
                        SessionManager
                            .sessionState
                            .value
                ) {

                    SessionState.SignedOut ->
                        null

                    is SessionState.SignedIn ->
                        session.userId
                }

            ownerPrincipal
                ?.let { owner ->

                    pendingSeverityStore
                        .loadForIncident(
                            incidentId,
                            owner
                        )
                }
                ?.let { pending ->

                    optimisticSeverity.value =
                        IncidentSeverity
                            .fromStoredValue(
                                pending.severity
                            )

                    severityUpdateInProgress.value =
                        true

                    severityUpdateError.value =
                        "Pending severity update restored."

                    monitorPendingSeverity(
                        pending
                    )
                }
        }
    }
    fun updateSeverity(
        targetIncidentId: String,
        severity: IncidentSeverity
    ) {

        if (
            severityUpdateInProgress.value
        ) {
            return
        }

        val displayedIncident =
            incident.value
                ?: return

        if (
            displayedIncident.id !=
            targetIncidentId
        ) {
            return
        }

        if (
            displayedIncident.severity ==
            severity
        ) {
            return
        }

        val ownerPrincipal =
            when (
                val session =
                    SessionManager
                        .sessionState
                        .value
            ) {

                SessionState.SignedOut ->
                    return

                is SessionState.SignedIn ->
                    session.userId
            }

        val pending =
            PendingSeverityCommand(
                commandId =
                    UUID
                        .randomUUID()
                        .toString(),

                incidentId =
                    targetIncidentId,

                severity =
                    severity.name,

                baseSeverity =
                    displayedIncident
                        .severity
                        .name,

                ownerPrincipal =
                    ownerPrincipal
            )

        /*
         * Persistence happens BEFORE the first network send.
         *
         * Process death after this line cannot lose the
         * operator's intent.
         */
        optimisticSeverity.value =
            severity

        severityUpdateInProgress.value =
            true

        severityUpdateError.value =
            "Severity queued for sync."

        viewModelScope.launch {

            /*
             * Room commit happens before any network send.
             */
            pendingSeverityStore.save(
                pending
            )

            /*
             * Delivery belongs to the process-scoped coordinator.
             * The ViewModel only observes presentation state.
             */
            SeverityOutboxCoordinator
                .kick()

            monitorPendingSeverity(
                pending
            )
        }
    }
    private suspend fun monitorPendingSeverity(
        pending:
            PendingSeverityCommand
    ) {

        val requestedSeverity =
            IncidentSeverity
                .fromStoredValue(
                    pending.severity
                )

        val baseSeverity =
            IncidentSeverity
                .fromStoredValue(
                    pending.baseSeverity
                )

        while (true) {

            val current =
                incident.value

            if (
                current?.id ==
                pending.incidentId
            ) {

                if (
                    current.severity ==
                    requestedSeverity
                ) {

                    optimisticSeverity.value =
                        null

                    severityUpdateInProgress.value =
                        false

                    severityUpdateError.value =
                        null

                    return
                }

                if (
                    current.severity !=
                    baseSeverity &&
                    current.severity !=
                    requestedSeverity
                ) {

                    optimisticSeverity.value =
                        null

                    severityUpdateInProgress.value =
                        false

                    severityUpdateError.value =
                        "Severity was superseded by a newer update: ${current.severity.name}."

                    return
                }
            }

            val stored =
                pendingSeverityStore
                    .loadForIncident(
                        pending.incidentId,
                        pending.ownerPrincipal
                    )

            if (
                stored?.commandId !=
                pending.commandId
            ) {

                optimisticSeverity.value =
                    null

                severityUpdateInProgress.value =
                    false

                if (
                    current?.severity !=
                    requestedSeverity
                ) {

                    severityUpdateError.value =
                        "Severity update ended without the requested authoritative state."
                } else {

                    severityUpdateError.value =
                        null
                }

                return
            }

            severityUpdateError.value =
                "Severity queued; waiting for authoritative sync."

            delay(
                200L
            )
        }
    }
}
