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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
    val timelineRetryInProgressEntryId =
        MutableStateFlow<String?>(null)

    val timelineRetryErrorEntryId =
        MutableStateFlow<String?>(null)

    val timelineRetryError =
        MutableStateFlow<String?>(null)

    val timelinePostInProgress =
        MutableStateFlow(false)

    val timelinePostError =
        MutableStateFlow<String?>(null)

    val timelinePostSaved =
        MutableStateFlow(false)

    private val monitorClaims = IncidentMonitorClaims()
    private val monitorRecoveryBudget = IncidentMonitorRecoveryBudget()

    private var activeStatusCommand: IncidentMonitorOwnership? = null

    val statusUpdateInProgress =
        MutableStateFlow(false)

    val statusUpdateMessage =
        MutableStateFlow<String?>(null)

    val statusUpdateError =
        MutableStateFlow<String?>(null)

    val optimisticStatus =
        MutableStateFlow<String?>(null)

    private var activeSeverityCommand: IncidentMonitorOwnership? = null

    val severityUpdateInProgress =
        MutableStateFlow(false)

    val severityUpdateMessage =
        MutableStateFlow<String?>(null)

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

    // F10-M8A.2: Preserve the owner and generation of each Room emission.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val ownedIncident: StateFlow<OwnedIncidentObservation?> =
        SessionManager.sessionState
            .flatMapLatest { state ->
                val generation = SessionManager.sessionGeneration

                when (state) {
                    SessionState.SignedOut ->
                        flowOf<OwnedIncidentObservation?>(null)

                    is SessionState.SignedIn -> {
                        require(state.userId.isNotBlank())

                        repository.observeIncident(
                            ownerPrincipal = state.userId,
                            incidentId = incidentId
                        ).map { observed ->
                            OwnedIncidentObservation(
                                ownerPrincipal = state.userId,
                                sessionGeneration = generation,
                                incident = observed
                            )
                        }
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val incident: StateFlow<Incident?> =
        SessionManager.sessionState
            .flatMapLatest { state ->
                when (state) {
                    SessionState.SignedOut ->
                        flowOf(null)

                    is SessionState.SignedIn -> {
                        require(state.userId.isNotBlank())
                        repository.observeIncident(
                            ownerPrincipal = state.userId,
                            incidentId = incidentId
                        )
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val timeline: StateFlow<List<TimelineEntry>> =
        SessionManager.sessionState
            .flatMapLatest { state ->
                when (state) {
                    SessionState.SignedOut ->
                        flowOf(emptyList())

                    is SessionState.SignedIn -> {
                        require(state.userId.isNotBlank())
                        repository.observeTimeline(
                            ownerPrincipal = state.userId,
                            incidentId = incidentId
                        )
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    // F10-M8B.6C: Production uses the tested lifecycle coordinator.
    init {
        viewModelScope.launch {
            collectIncidentSessionLifecycle(
                identities = SessionManager.sessionIdentity,
                onReset = {
                    timelineRetryInProgressEntryId.value = null
                    timelineRetryErrorEntryId.value = null
                    timelineRetryError.value = null

                    timelinePostInProgress.value = false
                    timelinePostError.value = null
                    timelinePostSaved.value = false

                    monitorClaims.clear()
                    monitorRecoveryBudget.clear()
                    activeStatusCommand = null
                    statusUpdateInProgress.value = false
                    statusUpdateMessage.value = null
                    statusUpdateError.value = null
                    optimisticStatus.value = null

                    activeSeverityCommand = null
                    severityUpdateInProgress.value = false
                    severityUpdateMessage.value = null
                    severityUpdateError.value = null
                    optimisticSeverity.value = null
                },
                restoreStatus = statusRestore@{ identity ->
                    val owner = identity.ownerPrincipal
                        ?.takeIf { it.isNotBlank() }
                        ?: return@statusRestore

                    fun identityIsCurrent(): Boolean =
                        SessionManager.sessionIdentity.value == identity &&
                            SessionManager.sessionGeneration == identity.generation &&
                            (SessionManager.sessionState.value as?
                                SessionState.SignedIn)?.userId == owner

                    if (!identityIsCurrent()) {
                        return@statusRestore
                    }

                    val pending =
                        pendingStatusStore.loadForIncident(
                            incidentId,
                            owner
                        ) ?: return@statusRestore

                    if (
                        !identityIsCurrent() ||
                        pending.ownerPrincipal != owner
                    ) {
                        return@statusRestore
                    }

                    val pendingOwnership = IncidentMonitorOwnership(
                        ownerPrincipal = owner,
                        sessionGeneration = identity.generation,
                        commandId = pending.commandId
                    )

                    if (
                        statusUpdateInProgress.value &&
                        (
                            activeStatusCommand != pendingOwnership ||
                            monitorClaims.hasActiveStatusMonitor(pendingOwnership)
                        )
                    ) {
                        return@statusRestore
                    }

                    activeStatusCommand = pendingOwnership

                    optimisticStatus.value = pending.status
                    statusUpdateInProgress.value = true
                    statusUpdateError.value = null
                    statusUpdateMessage.value =
                        "Pending status update restored."

                    monitorPendingStatusRecoverably(
                        pending,
                        identity.generation
                    )
                },
                restoreSeverity = severityRestore@{ identity ->
                    val owner = identity.ownerPrincipal
                        ?.takeIf { it.isNotBlank() }
                        ?: return@severityRestore

                    fun identityIsCurrent(): Boolean =
                        SessionManager.sessionIdentity.value == identity &&
                            SessionManager.sessionGeneration == identity.generation &&
                            (SessionManager.sessionState.value as?
                                SessionState.SignedIn)?.userId == owner

                    if (!identityIsCurrent()) {
                        return@severityRestore
                    }

                    val pending =
                        pendingSeverityStore.loadForIncident(
                            incidentId,
                            owner
                        ) ?: return@severityRestore

                    if (
                        !identityIsCurrent() ||
                        pending.ownerPrincipal != owner
                    ) {
                        return@severityRestore
                    }

                    val pendingOwnership = IncidentMonitorOwnership(
                        ownerPrincipal = owner,
                        sessionGeneration = identity.generation,
                        commandId = pending.commandId
                    )

                    if (
                        severityUpdateInProgress.value &&
                        (
                            activeSeverityCommand != pendingOwnership ||
                            monitorClaims.hasActiveSeverityMonitor(pendingOwnership)
                        )
                    ) {
                        return@severityRestore
                    }

                    activeSeverityCommand = pendingOwnership

                    optimisticSeverity.value =
                        IncidentSeverity.fromStoredValue(
                            pending.severity
                        )

                    severityUpdateInProgress.value = true
                    severityUpdateError.value = null
                    severityUpdateMessage.value =
                        "Pending severity update restored."

                    monitorPendingSeverityRecoverably(
                        pending,
                        identity.generation
                    )
                },
                onRestoreFailure = { operation, exceptionType ->
                    android.util.Log.w(
                        "RelayIncidentRestore",
                        "$operation restoration failed ($exceptionType)"
                    )
                }
            )
        }
    }

    fun postUpdate(
        message: String
    ) {
        val normalizedMessage =
            message.trim()

        if (
            normalizedMessage.isBlank() ||
            timelinePostInProgress.value
        ) {
            return
        }

        val currentSession =
            SessionManager
                .sessionState
                .value as?
                SessionState.SignedIn
                ?: return

        val postingGeneration =
            SessionManager.sessionGeneration
        timelinePostError.value =
            null

        timelinePostSaved.value =
            false

        timelinePostInProgress.value =
            true

        viewModelScope.launch {

            try {

                repository
                    .createPendingTimelineEntry(
                        incidentId =
                            incidentId,
                        message =
                            normalizedMessage,
                        author =
                            "You",
                        ownerPrincipal =
                            currentSession.userId
                    )

            } catch (
                error: CancellationException
            ) {
                throw error

            } catch (
                error: Exception
            ) {

                            if (
                SessionManager.sessionGeneration != postingGeneration ||
                (SessionManager.sessionState.value as?
                    SessionState.SignedIn)?.userId != currentSession.userId
            ) {
                return@launch
            }
                timelinePostInProgress.value =
                    false

                timelinePostError.value =
                    "Unable to save timeline update. Try again."

                return@launch
            }


            if (
                SessionManager.sessionGeneration != postingGeneration ||
                (SessionManager.sessionState.value as?
                    SessionState.SignedIn)?.userId != currentSession.userId
            ) {
                return@launch
            }
            timelinePostInProgress.value =
                false

            timelinePostSaved.value =
                true

            TimelineOutboxCoordinator
                .kick()
        }
    }

    fun consumeTimelinePostSaved() {
        timelinePostSaved.value =
            false
    }

    fun retry(
        entry: TimelineEntry
    ) {
        if (timelineRetryInProgressEntryId.value == entry.id) {
            return
        }

        val retrySession =
            SessionManager.sessionState.value as?
                SessionState.SignedIn
                ?: return

        val retryGeneration =
            SessionManager.sessionGeneration

        if (
            (SessionManager.sessionState.value as?
                SessionState.SignedIn)?.userId != retrySession.userId ||
            SessionManager.sessionGeneration != retryGeneration
        ) {
            return
        }

        timelineRetryErrorEntryId.value = null
        timelineRetryError.value = null
        timelineRetryInProgressEntryId.value = entry.id

        viewModelScope.launch {
            val activeSession =
                SessionManager.sessionState.value as?
                    SessionState.SignedIn

            if (
                activeSession?.userId != retrySession.userId ||
                SessionManager.sessionGeneration != retryGeneration
            ) {
                return@launch
            }

            val ownerPrincipal =
                repository.getTimelineEntryOwnerPrincipal(
                    ownerPrincipal = retrySession.userId,
                    entryId = entry.id
                )

            // The database query may have suspended while accounts switched.
            if (
                (SessionManager.sessionState.value as?
                    SessionState.SignedIn)?.userId != retrySession.userId ||
                SessionManager.sessionGeneration != retryGeneration
            ) {
                return@launch
            }

            if (ownerPrincipal == null) {
                timelineRetryInProgressEntryId.value = null
                return@launch
            }

            val credential =
                timelineDeliveryCredential(
                    SessionManager.sessionState.value,
                    ownerPrincipal
                )

            if (
                (SessionManager.sessionState.value as?
                    SessionState.SignedIn)?.userId != retrySession.userId ||
                SessionManager.sessionGeneration != retryGeneration
            ) {
                return@launch
            }

            if (credential == null) {
                timelineRetryInProgressEntryId.value = null
                return@launch
            }

            val markedPending =
                try {
                    repository.markTimelineEntryPending(
                        entryId = entry.id,
                        ownerPrincipal = ownerPrincipal
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    false
                }

            // Reject old coroutine results before touching shared UI state.
            if (
                (SessionManager.sessionState.value as?
                    SessionState.SignedIn)?.userId != retrySession.userId ||
                SessionManager.sessionGeneration != retryGeneration
            ) {
                return@launch
            }

            if (!markedPending) {
                timelineRetryInProgressEntryId.value = null
                timelineRetryErrorEntryId.value = entry.id
                timelineRetryError.value =
                    "Unable to retry timeline update. Try again."
                return@launch
            }

            timelineRetryInProgressEntryId.value = null
            TimelineOutboxCoordinator.kick()
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

        val observedIncident =
            ownedIncident.value
                ?: return

        val displayedIncident =
            observedIncident.incident
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

        val originatingGeneration =
            SessionManager.sessionGeneration

        // A displayed incident is not mutation-authoritative
        // unless its observation belongs to this exact session.
        if (
            !observedIncident.matchesSession(
                ownerPrincipal,
                originatingGeneration
            ) ||
            !SessionManager.sessionIdentity.value.owns(
                ownerPrincipal,
                originatingGeneration
            )
        ) {
            return
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
        val commandOwnership = IncidentMonitorOwnership(
            ownerPrincipal = ownerPrincipal,
            sessionGeneration = originatingGeneration,
            commandId = pending.commandId
        )

        activeSeverityCommand = commandOwnership

        optimisticSeverity.value =
            severity

        severityUpdateInProgress.value =
            true

        severityUpdateError.value =
            null

        severityUpdateMessage.value =
            "Saving severity change..."

        viewModelScope.launch {

            try {

                pendingSeverityStore.save(
                    pending
                )

            } catch (
                error: CancellationException
            ) {
                throw error

            } catch (
                error: Exception
            ) {

                                if (
                    SessionManager.sessionGeneration != originatingGeneration ||
                    (SessionManager.sessionState.value as?
                        SessionState.SignedIn)?.userId != ownerPrincipal
                ) {
                    return@launch
                }
                if (!commandOwnership.canUpdatePresentation(activeSeverityCommand)) {
                    return@launch
                }

                optimisticSeverity.value =
                    null

                severityUpdateInProgress.value =
                    false

                severityUpdateMessage.value =
                    null

                severityUpdateError.value =
                    "Unable to save severity change. Try again."

                activeSeverityCommand =
                    commandOwnership.releaseIfCurrent(activeSeverityCommand)

                return@launch
            }

                if (
                    SessionManager.sessionGeneration != originatingGeneration ||
                    (SessionManager.sessionState.value as?
                        SessionState.SignedIn)?.userId != ownerPrincipal
                ) {
                    return@launch
                }
            if (!commandOwnership.canUpdatePresentation(activeSeverityCommand)) {
                return@launch
            }

            severityUpdateMessage.value =
                "Severity change saved. Syncing..."

            /*
             * Delivery belongs to the process-scoped coordinator.
             * The ViewModel only observes presentation state.
             */
            SeverityOutboxCoordinator
                .kick()

            monitorPendingSeverityRecoverably(pending, originatingGeneration)
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

        val observedIncident =
            ownedIncident.value
                ?: return

        val displayedIncident =
            observedIncident.incident
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

        val originatingGeneration =
            SessionManager.sessionGeneration

        // A displayed incident is not mutation-authoritative
        // unless its observation belongs to this exact session.
        if (
            !observedIncident.matchesSession(
                ownerPrincipal,
                originatingGeneration
            ) ||
            !SessionManager.sessionIdentity.value.owns(
                ownerPrincipal,
                originatingGeneration
            )
        ) {
            return
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

        val commandOwnership = IncidentMonitorOwnership(
            ownerPrincipal = ownerPrincipal,
            sessionGeneration = originatingGeneration,
            commandId = pending.commandId
        )

        activeStatusCommand = commandOwnership

        optimisticStatus.value =
            requestedStatus

        statusUpdateInProgress.value =
            true

        statusUpdateError.value =
            null

        statusUpdateMessage.value =
            "Saving status change..."

        viewModelScope.launch {

            try {

                pendingStatusStore.save(
                    pending
                )

            } catch (
                error: CancellationException
            ) {
                throw error

            } catch (
                error: Exception
            ) {

                if (
                    SessionManager.sessionGeneration != originatingGeneration ||
                    (SessionManager.sessionState.value as?
                        SessionState.SignedIn)?.userId != ownerPrincipal
                ) {
                    return@launch
                }

                if (!commandOwnership.canUpdatePresentation(activeStatusCommand)) {
                    return@launch
                }

                optimisticStatus.value =
                    null

                statusUpdateInProgress.value =
                    false

                statusUpdateMessage.value =
                    null

                statusUpdateError.value =
                    "Unable to save status change. Try again."

                activeStatusCommand =
                    commandOwnership.releaseIfCurrent(activeStatusCommand)

                return@launch
            }

            if (
                    SessionManager.sessionGeneration != originatingGeneration ||
                    (SessionManager.sessionState.value as?
                        SessionState.SignedIn)?.userId != ownerPrincipal
                ) {
                    return@launch
                }

            if (!commandOwnership.canUpdatePresentation(activeStatusCommand)) {
                return@launch
            }

            statusUpdateMessage.value =
                "Status change saved. Syncing..."

            StatusOutboxCoordinator
                .kick()

            monitorPendingStatusRecoverably(
                pending,
                originatingGeneration
            )
        }
    }

    /**
     * Runs the status presentation monitor with at most one
     * automatic recovery attempt for the current command.
     *
     * Pending mutation persistence and outbox delivery remain
     * separate from presentation recovery.
     */
    /**
     * Monitors status with at most one automatic recovery.
     * Outbox delivery remains independent of presentation monitoring.
     */
    private suspend fun monitorPendingStatusRecoverably(
        pending: PendingStatusMutation,
        originatingGeneration: Long
    ) {
        val ownership = IncidentMonitorOwnership(
            ownerPrincipal = pending.ownerPrincipal,
            sessionGeneration = originatingGeneration,
            commandId = pending.commandId
        )

        executeMonitorWithOneRecovery(
            initial = pending,
            monitor = { command ->
                monitorPendingStatus(command, originatingGeneration)
            },
            reload = {
                pendingStatusStore.loadForIncident(
                    pending.incidentId,
                    pending.ownerPrincipal
                )
            },
            mayRetry = { reloaded ->
                val sameCommand =
                    reloaded.commandId == pending.commandId &&
                        reloaded.ownerPrincipal == pending.ownerPrincipal

                decideIncidentMonitorRecovery(
                    pendingOwnership = if (sameCommand) ownership else null,
                    activeOwnership = activeStatusCommand,
                    currentIdentity = SessionManager.sessionIdentity.value,
                    pendingStillPresent = sameCommand,
                    monitorActive =
                        monitorClaims.hasActiveStatusMonitor(ownership),
                    unexpectedFailure = true,
                    retryAlreadyRequested =
                        monitorRecoveryBudget.statusRetryRequested(ownership)
                ) == IncidentMonitorRecoveryDecision.RETRY
            },
            acquireRetryPermit = {
                monitorRecoveryBudget.requestStatusRetry(ownership)
            },
            onStopped = {
                if (
                    SessionManager.sessionIdentity.value.owns(
                        ownership.ownerPrincipal,
                        ownership.sessionGeneration
                    ) &&
                    ownership.canUpdatePresentation(activeStatusCommand) &&
                    !monitorClaims.hasActiveStatusMonitor(ownership)
                ) {
                    statusUpdateInProgress.value = false
                    optimisticStatus.value = null
                    statusUpdateMessage.value = null
                    statusUpdateError.value =
                        "Status monitoring stopped. The saved update may still sync."

                    activeStatusCommand =
                        ownership.releaseIfCurrent(activeStatusCommand)
                }
            }
        )
    }
    private suspend fun monitorPendingStatus(
        pending: PendingStatusMutation,
        originatingGeneration: Long
    ) {

        val requestedStatus =
            pending.status

        val baseStatus =
            pending.baseStatus

        val entrySession =
            SessionManager.sessionState.value as?
                SessionState.SignedIn

        if (
            entrySession?.userId != pending.ownerPrincipal ||
            SessionManager.sessionGeneration != originatingGeneration
        ) {
            return
        }
        val monitorOwnership = IncidentMonitorOwnership(
            ownerPrincipal = pending.ownerPrincipal,
            sessionGeneration = originatingGeneration,
            commandId = pending.commandId
        )

        if (!monitorOwnership.canUpdatePresentation(activeStatusCommand)) {
            return
        }

        val claim = monitorClaims.claimStatus(monitorOwnership)
            ?: return

        try {
            val monitorJob = currentCoroutineContext()[Job]
                ?: return

            if (!monitorClaims.attachStatus(claim, monitorJob)) {
                return
            }

        statusUpdateMessage.value =
            "Status change saved. Waiting for confirmation."

        val result =
            combine(
                ownedIncident,
                pendingStatusStore.observeForIncident(
                    pending.incidentId,
                    pending.ownerPrincipal
                ),
                SessionManager.sessionState
            ) { current, stored, session ->
                Triple(current, stored, session)
            }
                .first {
                    state ->

                    val current =
                        state.first
                            ?.takeIf { observation ->
                                observation.matchesSession(
                                    pending.ownerPrincipal,
                                    originatingGeneration
                                )
                            }
                            ?.incident
                    val stored = state.second
                    val observedSession = state.third

                    val decision =
                        decidePendingStatusMonitor(
                            pendingOwner = pending.ownerPrincipal,
                            pendingGeneration = originatingGeneration,
                            pendingIncidentId = pending.incidentId,
                            pendingCommandId = pending.commandId,
                            requestedStatus = requestedStatus,
                            baseStatus = baseStatus,
                            activeOwner =
                                (observedSession as?
                                    SessionState.SignedIn)?.userId,
                            activeGeneration =
                                SessionManager.sessionGeneration,
                            observedIncidentId = current?.id,
                            observedStatus = current?.status,
                            storedCommandId = stored?.commandId
                        )

                    decision != StatusMonitorDecision.WAIT
                }

        if (
            !monitorClaims.ownsStatus(claim) ||
            !monitorOwnership.canUpdatePresentation(activeStatusCommand)
        ) {
            return
        }

        val current =
            result.first
                ?.takeIf { observation ->
                    observation.matchesSession(
                        pending.ownerPrincipal,
                        originatingGeneration
                    )
                }
                ?.incident
        val resultSession = result.third
        val activeSession = SessionManager.sessionState.value

        if (
            resultSession !is SessionState.SignedIn ||
            resultSession.userId != pending.ownerPrincipal ||
            activeSession !is SessionState.SignedIn ||
            activeSession.userId != pending.ownerPrincipal ||
            SessionManager.sessionGeneration != originatingGeneration
        ) {
            return
        }

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

            activeStatusCommand =
                monitorOwnership.releaseIfCurrent(activeStatusCommand)

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

            activeStatusCommand =
                monitorOwnership.releaseIfCurrent(activeStatusCommand)

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
                "Status update was not applied. Try again if you still have access."
        } else {

            statusUpdateError.value =
                null
        }

        activeStatusCommand =
            monitorOwnership.releaseIfCurrent(activeStatusCommand)
        } finally {
            monitorClaims.releaseStatus(claim)
        }
    }

    /**
     * Runs the severity presentation monitor with a single
     * bounded recovery attempt after unexpected failure.
     *
     * Persistence and outbox delivery remain independent.
     */
    /**
     * Monitors severity with at most one automatic recovery.
     * Outbox delivery remains independent of presentation monitoring.
     */
    private suspend fun monitorPendingSeverityRecoverably(
        pending: PendingSeverityMutation,
        originatingGeneration: Long
    ) {
        val ownership = IncidentMonitorOwnership(
            ownerPrincipal = pending.ownerPrincipal,
            sessionGeneration = originatingGeneration,
            commandId = pending.commandId
        )

        executeMonitorWithOneRecovery(
            initial = pending,
            monitor = { command ->
                monitorPendingSeverity(command, originatingGeneration)
            },
            reload = {
                pendingSeverityStore.loadForIncident(
                    pending.incidentId,
                    pending.ownerPrincipal
                )
            },
            mayRetry = { reloaded ->
                val sameCommand =
                    reloaded.commandId == pending.commandId &&
                        reloaded.ownerPrincipal == pending.ownerPrincipal

                decideIncidentMonitorRecovery(
                    pendingOwnership = if (sameCommand) ownership else null,
                    activeOwnership = activeSeverityCommand,
                    currentIdentity = SessionManager.sessionIdentity.value,
                    pendingStillPresent = sameCommand,
                    monitorActive =
                        monitorClaims.hasActiveSeverityMonitor(ownership),
                    unexpectedFailure = true,
                    retryAlreadyRequested =
                        monitorRecoveryBudget.severityRetryRequested(ownership)
                ) == IncidentMonitorRecoveryDecision.RETRY
            },
            acquireRetryPermit = {
                monitorRecoveryBudget.requestSeverityRetry(ownership)
            },
            onStopped = {
                if (
                    SessionManager.sessionIdentity.value.owns(
                        ownership.ownerPrincipal,
                        ownership.sessionGeneration
                    ) &&
                    ownership.canUpdatePresentation(activeSeverityCommand) &&
                    !monitorClaims.hasActiveSeverityMonitor(ownership)
                ) {
                    severityUpdateInProgress.value = false
                    optimisticSeverity.value = null
                    severityUpdateMessage.value = null
                    severityUpdateError.value =
                        "Severity monitoring stopped. The saved update may still sync."

                    activeSeverityCommand =
                        ownership.releaseIfCurrent(activeSeverityCommand)
                }
            }
        )
    }
    private suspend fun monitorPendingSeverity(
        pending: PendingSeverityMutation,
        originatingGeneration: Long
    ) {
        val requestedSeverity =
            IncidentSeverity.fromStoredValue(pending.severity)

        val baseSeverity =
            IncidentSeverity.fromStoredValue(pending.baseSeverity)

        val entrySession =
            SessionManager.sessionState.value as?
                SessionState.SignedIn

        if (
            entrySession?.userId != pending.ownerPrincipal ||
            SessionManager.sessionGeneration != originatingGeneration
        ) {
            return
        }

        val monitorOwnership = IncidentMonitorOwnership(
            ownerPrincipal = pending.ownerPrincipal,
            sessionGeneration = originatingGeneration,
            commandId = pending.commandId
        )

        if (!monitorOwnership.canUpdatePresentation(activeSeverityCommand)) {
            return
        }

        val claim = monitorClaims.claimSeverity(monitorOwnership)
            ?: return

        try {
            val monitorJob = currentCoroutineContext()[Job]
                ?: return

            if (!monitorClaims.attachSeverity(claim, monitorJob)) {
                return
            }

        severityUpdateMessage.value =
            "Severity change saved. Waiting for confirmation."

        val result =
            combine(
                ownedIncident,
                pendingSeverityStore.observeForIncident(
                    pending.incidentId,
                    pending.ownerPrincipal
                ),
                SessionManager.sessionState
            ) { current, stored, session ->
                Triple(current, stored, session)
            }.first { state ->
                val current =
                    state.first
                        ?.takeIf { observation ->
                            observation.matchesSession(
                                pending.ownerPrincipal,
                                originatingGeneration
                            )
                        }
                        ?.incident
                val stored = state.second
                val session = state.third

                decidePendingSeverityMonitor(
                    pendingOwner = pending.ownerPrincipal,
                    pendingGeneration = originatingGeneration,
                    pendingIncidentId = pending.incidentId,
                    pendingCommandId = pending.commandId,
                    requestedSeverity = requestedSeverity.name,
                    baseSeverity = baseSeverity.name,
                    activeOwner =
                        (session as? SessionState.SignedIn)?.userId,
                    activeGeneration = SessionManager.sessionGeneration,
                    observedIncidentId = current?.id,
                    observedSeverity = current?.severity?.name,
                    storedCommandId = stored?.commandId
                ) != SeverityMonitorDecision.WAIT
            }

        if (
            !monitorClaims.ownsSeverity(claim) ||
            !monitorOwnership.canUpdatePresentation(activeSeverityCommand)
        ) {
            return
        }

        val activeSession =
            SessionManager.sessionState.value as?
                SessionState.SignedIn

        if (
            activeSession?.userId != pending.ownerPrincipal ||
            (result.third as? SessionState.SignedIn)?.userId !=
                pending.ownerPrincipal ||
            SessionManager.sessionGeneration != originatingGeneration
        ) {
            return
        }

        val authorizedIncident =
            result.first
                ?.takeIf { observation ->
                    observation.matchesSession(
                        pending.ownerPrincipal,
                        originatingGeneration
                    )
                }
                ?.incident

        val decision =
            decidePendingSeverityMonitor(
                pendingOwner = pending.ownerPrincipal,
                pendingGeneration = originatingGeneration,
                pendingIncidentId = pending.incidentId,
                pendingCommandId = pending.commandId,
                requestedSeverity = requestedSeverity.name,
                baseSeverity = baseSeverity.name,
                activeOwner = activeSession.userId,
                activeGeneration = SessionManager.sessionGeneration,
                observedIncidentId = authorizedIncident?.id,
                observedSeverity = authorizedIncident?.severity?.name,
                storedCommandId = result.second?.commandId
            )

        when (decision) {
            SeverityMonitorDecision.SESSION_OBSOLETE,
            SeverityMonitorDecision.WAIT -> return

            SeverityMonitorDecision.CONFIRMED -> {
                optimisticSeverity.value = null
                severityUpdateInProgress.value = false
                severityUpdateError.value = null
            }

            SeverityMonitorDecision.SUPERSEDED -> {
                optimisticSeverity.value = null
                severityUpdateInProgress.value = false
                severityUpdateError.value =
                    "Severity was superseded by a newer update: ${authorizedIncident?.severity?.name}."
            }

            SeverityMonitorDecision.COMMAND_REMOVED -> {
                optimisticSeverity.value = null
                severityUpdateInProgress.value = false
                severityUpdateError.value =
                    "Severity update was not applied. Try again if you still have access."
            }
        }

        activeSeverityCommand =
            monitorOwnership.releaseIfCurrent(activeSeverityCommand)
        } finally {
            monitorClaims.releaseSeverity(claim)
        }
    }
}
