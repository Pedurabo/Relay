package com.signaldesk.relay.ui.incidents
import java.util.UUID


import com.signaldesk.relay.model.IncidentSeverity
import kotlinx.coroutines.flow.MutableStateFlow
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.signaldesk.relay.data.repository.IncidentRepositoryFactory
import com.signaldesk.relay.data.realtime.timelineDeliveryCredential
import com.signaldesk.relay.data.realtime.TimelineOutboxCoordinator
import com.signaldesk.relay.data.realtime.PendingSeverityMutation
import com.signaldesk.relay.data.realtime.SeverityCommandGateway
import com.signaldesk.relay.data.realtime.SeverityOutboxCoordinator
import com.signaldesk.relay.data.realtime.PendingStatusMutation
import com.signaldesk.relay.data.realtime.StatusCommandGateway
import com.signaldesk.relay.data.realtime.StatusOutboxCoordinator
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.model.Incident
import com.signaldesk.relay.model.TimelineEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
class IncidentDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val pendingSeverityStore by lazy {
        SeverityCommandGateway(
            getApplication<Application>()
        )
    }
    private val pendingStatusStore by lazy {
        StatusCommandGateway(
            getApplication<Application>()
        )
    }
    val statusUpdateInProgress =
        MutableStateFlow(false)

    val statusUpdateError =
        MutableStateFlow<String?>(null)

    val optimisticStatus =
        MutableStateFlow<String?>(null)

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

    private val repository =
        IncidentRepositoryFactory.create(
            application
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
            TimelineOutboxCoordinator
                .kick()
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

            timelineDeliveryCredential(
                SessionManager
                    .sessionState
                    .value,
                ownerPrincipal
            )
                ?: return@launch

            val markedPending =
                repository
                    .markTimelineEntryPending(
                        entryId =
                            entry.id,
                        ownerPrincipal =
                            ownerPrincipal
                    )

            if (
                !markedPending
            ) {
                return@launch
            }

            TimelineOutboxCoordinator
                .kick()
        }
    }

    // PERSISTENT_STATUS_OUTBOX

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

                    pendingStatusStore
                        .loadForIncident(
                            incidentId,
                            owner
                        )
                }
                ?.let { pending ->

                    optimisticStatus.value =
                        pending.status

                    statusUpdateInProgress.value =
                        true

                    statusUpdateError.value =
                        "Pending status update restored."

                    monitorPendingStatus(
                        pending
                    )
                }
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
            PendingSeverityMutation(
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
    fun updateStatus(
        targetIncidentId: String,
        status: String
    ) {

        if (
            statusUpdateInProgress.value
        ) {
            return
        }

        val requestedStatus =
            status.trim()

        if (
            requestedStatus.isBlank()
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
            displayedIncident.status ==
            requestedStatus
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
            PendingStatusMutation(
                commandId =
                    UUID
                        .randomUUID()
                        .toString(),

                incidentId =
                    targetIncidentId,

                status =
                    requestedStatus,

                baseStatus =
                    displayedIncident.status,

                ownerPrincipal =
                    ownerPrincipal
            )

        optimisticStatus.value =
            requestedStatus

        statusUpdateInProgress.value =
            true

        statusUpdateError.value =
            "Status queued for sync."

        viewModelScope.launch {

            pendingStatusStore.save(
                pending
            )

            StatusOutboxCoordinator
                .kick()

            monitorPendingStatus(
                pending
            )
        }
    }

    private suspend fun monitorPendingStatus(
        pending:
            PendingStatusMutation
    ) {

        val requestedStatus =
            pending.status

        val baseStatus =
            pending.baseStatus

        statusUpdateError.value =
            "Status queued; waiting for authoritative sync."

        val result =
            combine(
                incident,
                pendingStatusStore
                    .observeForIncident(
                        pending.incidentId,
                        pending.ownerPrincipal
                    )
            ) {
                current,
                stored ->

                current to
                    stored
            }
                .first {
                    state ->

                    val current =
                        state.first

                    val stored =
                        state.second

                    val authoritativeTerminal =
                        current?.id ==
                            pending.incidentId &&
                            (
                                current.status ==
                                    requestedStatus ||
                                    (
                                        current.status !=
                                            baseStatus &&
                                            current.status !=
                                            requestedStatus
                                    )
                            )

                    authoritativeTerminal ||
                        stored?.commandId !=
                            pending.commandId
                }

        val current =
            result.first

        if (
            current?.id ==
                pending.incidentId &&
            current.status ==
                requestedStatus
        ) {

            optimisticStatus.value =
                null

            statusUpdateInProgress.value =
                false

            statusUpdateError.value =
                null

            return
        }

        if (
            current?.id ==
                pending.incidentId &&
            current.status !=
                baseStatus &&
            current.status !=
                requestedStatus
        ) {

            optimisticStatus.value =
                null

            statusUpdateInProgress.value =
                false

            statusUpdateError.value =
                "Status was superseded by a newer update: ${current.status}."

            return
        }

        optimisticStatus.value =
            null

        statusUpdateInProgress.value =
            false

        if (
            current?.status !=
                requestedStatus
        ) {

            statusUpdateError.value =
                "Status update ended without the requested authoritative state."
        } else {

            statusUpdateError.value =
                null
        }
    }

    private suspend fun monitorPendingSeverity(
        pending:
            PendingSeverityMutation
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

        severityUpdateError.value =
            "Severity queued; waiting for authoritative sync."

        val result =
            combine(
                incident,
                pendingSeverityStore
                    .observeForIncident(
                        pending.incidentId,
                        pending.ownerPrincipal
                    )
            ) {
                current,
                stored ->

                current to
                    stored
            }
                .first {
                    state ->

                    val current =
                        state.first

                    val stored =
                        state.second

                    val authoritativeTerminal =
                        current?.id ==
                            pending.incidentId &&
                            (
                                current.severity ==
                                    requestedSeverity ||
                                    (
                                        current.severity !=
                                            baseSeverity &&
                                            current.severity !=
                                            requestedSeverity
                                    )
                            )

                    authoritativeTerminal ||
                        stored?.commandId !=
                            pending.commandId
                }

        val current =
            result.first

        if (
            current?.id ==
                pending.incidentId &&
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
            current?.id ==
                pending.incidentId &&
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
    }
}
