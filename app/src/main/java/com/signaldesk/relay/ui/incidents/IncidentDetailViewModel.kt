package com.signaldesk.relay.ui.incidents
import com.signaldesk.relay.data.local.RoomPendingSeverityCommandStore
import com.signaldesk.relay.data.local.PendingSeverityCommand
import com.signaldesk.relay.data.local.RoomPendingStatusCommandStore
import com.signaldesk.relay.data.local.PendingStatusCommand
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
import com.signaldesk.relay.data.realtime.timelineDeliveryCredential
import com.signaldesk.relay.data.realtime.TimelineOutboxCoordinator
import com.signaldesk.relay.data.realtime.SeverityOutboxCoordinator
import com.signaldesk.relay.data.realtime.StatusOutboxCoordinator
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.model.Incident
import com.signaldesk.relay.model.TimelineEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    private val pendingStatusStore by lazy {
        RoomPendingStatusCommandStore(
            database
                .pendingStatusCommandDao()
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

    private val database =
        RelayDatabase.getInstance(application)

    private val repository =
        IncidentRepository(
            incidentDao =
                database.incidentDao(),
            timelineEntryDao =
                database.timelineEntryDao()
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
            PendingStatusCommand(
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
            PendingStatusCommand
    ) {

        val requestedStatus =
            pending.status

        val baseStatus =
            pending.baseStatus

        while (true) {

            val current =
                incident.value

            if (
                current?.id ==
                pending.incidentId
            ) {

                if (
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
            }

            val stored =
                pendingStatusStore
                    .loadForIncident(
                        pending.incidentId,
                        pending.ownerPrincipal
                    )

            if (
                stored?.commandId !=
                pending.commandId
            ) {

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

                return
            }

            statusUpdateError.value =
                "Status queued; waiting for authoritative sync."

            delay(
                200L
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
