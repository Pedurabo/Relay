package com.signaldesk.relay.data.realtime

import android.app.Application
import com.signaldesk.relay.data.local.PendingCreateIncidentCommand
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.local.RoomPendingCreateIncidentCommandStore
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionRefreshCoordinator
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.model.IncidentSeverity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object CreateIncidentOutboxCoordinator {
    private enum class DeliveryOutcome {
        COMPLETE,
        RETRY_TRANSPORT,
        RETRY_CONFIRMATION,
        SESSION_CHANGED
    }

    private data class RetryState(
        val attempt: Int,
        val notBeforeMillis: Long
    )

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    private val initialized =
        AtomicBoolean(false)

    private val ready =
        AtomicBoolean(false)

    private val draining =
        AtomicBoolean(false)

    private val retryStates =
        ConcurrentHashMap<
            String,
            RetryState
        >()

    private lateinit var database:
        RelayDatabase

    private lateinit var store:
        RoomPendingCreateIncidentCommandStore

    fun initialize(
        application: Application
    ) {

        if (
            !initialized.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        database =
            RelayDatabase.getInstance(
                application
            )

        store =
            RoomPendingCreateIncidentCommandStore(
                database
                    .pendingCreateIncidentCommandDao()
            )

        scope.launch {

            val resetCount =
                runOutboxStartupResetSafely(
                    delayAfterFailure = {
                        attempt ->

                        delay(
                            transportBackoffMillis(
                                attempt
                            )
                        )
                    },
                    resetInFlight = {
                        store.resetInFlight()
                    }
                )

            reportOutboxEvent(
"CREATE_OUTBOX_STARTUP|resetInFlight=$resetCount"
)

            ready.set(
                true
            )

            kick()
        }
    }

    fun kick() {

        if (
            !initialized.get() ||
            !ready.get()
        ) {
            return
        }

        if (
            !draining.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        scope.launch {

            try {

                runOutboxDrainSafely(
                    isActive = {
                        SessionManager
                            .sessionState
                            .value is
                            SessionState.SignedIn
                    },
                    delayAfterFailure = {
                        attempt ->

                        delay(
                            transportBackoffMillis(
                                attempt
                            )
                        )
                    },
                    drain = {
                        drainFairly()
                    }
                )

            } finally {

                draining.set(
                    false
                )

                val hasPendingWork =
                    runOutboxPostDrainCheckSafely(
                        isActive = {
                            SessionManager
                                .sessionState
                                .value is
                                SessionState.SignedIn
                        },
                        delayAfterFailure = {
                            attempt ->

                            delay(
                                transportBackoffMillis(
                                    attempt
                                )
                            )
                        },
                        hasPendingWork = {

                            val current =
                                SessionManager
                                    .sessionState
                                    .value

                            current is
                                SessionState.SignedIn &&
                                store.load(
                                    current.userId
                                ) != null
                        }
                    )

                if (
                    hasPendingWork
                ) {
                    kick()
                }
            }
        }
    }

    private suspend fun drainFairly() {

        while (true) {

            val session =
                SessionManager
                    .sessionState
                    .value

            if (
                session !is
                SessionState.SignedIn
            ) {
                return
            }

            val ownerPrincipal =
                session.userId

            val pending =
                store.loadAll(
                    ownerPrincipal
                )

            if (
                pending.isEmpty()
            ) {
                return
            }

            var attemptedAny =
                false

            val now =
                System.currentTimeMillis()

            val retryNotBeforeMillis =
                pending
                    .mapNotNull {
                        command ->

                        retryStates[
                            command.commandId
                        ]
                            ?.notBeforeMillis
                            ?.let {
                                notBefore ->

                                command.commandId to
                                    notBefore
                            }
                    }
                    .toMap()

            val eligibleCommandIds =
                OutboxDrainPlanner
                    .eligibleCommandIds(
                        commandIds =
                            pending.map {
                                it.commandId
                            },
                        retryNotBeforeMillis =
                            retryNotBeforeMillis,
                        nowMillis =
                            now
                    )
                    .toSet()

            for (
                command in pending
            ) {

                if (
                    command.commandId !in
                    eligibleCommandIds
                ) {
                    continue
                }

                if (
                    !isCurrentOwner(
                        command.ownerPrincipal
                    )
                ) {
                    return
                }

                val claimed =
                    store.claim(
                        command.commandId,
                        ownerPrincipal
                    )

                if (!claimed) {
                    continue
                }

                if (
                    !isCurrentOwner(
                        command.ownerPrincipal
                    )
                ) {

                    store.release(
                        command.commandId,
                        command.ownerPrincipal
                    )

                    return
                }

                attemptedAny =
                    true

                val outcome =
                    try {

                        deliverOneTurn(
                            command
                        )

                    } catch (
                        error: Throwable
                    ) {

                        reportOutboxEvent(
"CREATE_OUTBOX_EXCEPTION|${command.commandId}|${error::class.simpleName}"
)

                        DeliveryOutcome
                            .RETRY_TRANSPORT
                    }

                when (
                    outcome
                ) {

                    DeliveryOutcome.COMPLETE -> {

                        retryStates.remove(
                            command.commandId
                        )

                        store.release(
                            command.commandId,
                            command.ownerPrincipal
                        )
                    }

                    DeliveryOutcome.RETRY_TRANSPORT -> {

                        store.release(
                            command.commandId,
                            command.ownerPrincipal
                        )

                        scheduleTransportRetry(
                            command.commandId
                        )
                    }

                    DeliveryOutcome.RETRY_CONFIRMATION -> {

                        store.release(
                            command.commandId,
                            command.ownerPrincipal
                        )

                        scheduleConfirmationRetry(
                            command.commandId
                        )
                    }

                    DeliveryOutcome.SESSION_CHANGED -> {

                        store.release(
                            command.commandId,
                            command.ownerPrincipal
                        )

                        return
                    }
                }
            }

            val currentSession =
                SessionManager
                    .sessionState
                    .value

            if (
                currentSession !is
                    SessionState.SignedIn ||
                currentSession.userId !=
                    ownerPrincipal
            ) {
                return
            }

            val remaining =
                store.loadAll(
                    ownerPrincipal
                )

            if (
                remaining.isEmpty()
            ) {
                return
            }

            if (
                attemptedAny
            ) {
                continue
            }

            val currentTime =
                System.currentTimeMillis()

            val retryTimes =
                remaining
                    .mapNotNull {
                        command ->

                        retryStates[
                            command.commandId
                        ]
                            ?.notBeforeMillis
                            ?.let {
                                notBefore ->

                                command.commandId to
                                    notBefore
                            }
                    }
                    .toMap()

            val nearestRetry =
                OutboxDrainPlanner
                    .nearestRetryAtMillis(
                        commandIds =
                            remaining.map {
                                it.commandId
                            },
                        retryNotBeforeMillis =
                            retryTimes,
                        nowMillis =
                            currentTime
                    )

            if (
                nearestRetry != null
            ) {

                delay(
                    (
                        nearestRetry -
                            currentTime
                    )
                        .coerceIn(
                            100L,
                            30_000L
                        )
                )

                continue
            }

            delay(
                250L
            )
        }
    }

    private suspend fun deliverOneTurn(
        pending:
            PendingCreateIncidentCommand
    ): DeliveryOutcome {

        if (
            !isCurrentOwner(
                pending.ownerPrincipal
            )
        ) {
            return DeliveryOutcome
                .SESSION_CHANGED
        }

        val beforeSend =
            database
                .incidentDao()
                .getById(
                    pending.incidentId
                )

        if (
            beforeSend != null
        ) {

            val resolution =
                decideCreateIncidentResolution(
                    authoritativeTitle =
                        beforeSend.title,
                    authoritativeStatus =
                        beforeSend.status,
                    authoritativeSeverity =
                        beforeSend.severity,
                    requestedTitle =
                        pending.title,
                    requestedStatus =
                        pending.status,
                    requestedSeverity =
                        pending.severity
                )

            store.clearIf(
                pending.commandId,
                pending.ownerPrincipal
            )

            reportOutboxEvent(
when (
                    resolution
                ) {
                    CreateIncidentResolutionDecision.CONVERGED ->
                        "CREATE_OUTBOX_RESOLVED|${pending.commandId}|reason=already_converged"

                    CreateIncidentResolutionDecision.SUPERSEDED ->
                        "CREATE_OUTBOX_RESOLVED|${pending.commandId}|reason=incident_id_superseded"

                    CreateIncidentResolutionDecision.KEEP_PENDING ->
                        error(
                            "Existing incident cannot remain pending."
                        )
                }
)

            return DeliveryOutcome
                .COMPLETE
        }

        val session =
            SessionManager
                .sessionState
                .value

        if (
            session !is
                SessionState.SignedIn ||
            session.userId !=
                pending.ownerPrincipal
        ) {
            return DeliveryOutcome
                .SESSION_CHANGED
        }

        val deliveryToken =
            session.accessToken

        val sender =
            WebSocketIncidentCommandSender(
                url =
                    RealtimeEndpointConfig.webSocketUrl,

                tokenProvider = {
                    deliveryToken
                },

                onSessionInvalidated = {
                    rejectedAccessToken ->

                    scope.launch {

                        val refreshed =
                            SessionRefreshCoordinator
                                .refreshOrSignOut(
                                    rejectedAccessToken
                                )

                        if (
                            refreshed
                        ) {
                            kick()
                        }
                    }
                }
            )

        if (
            !isCurrentOwner(
                pending.ownerPrincipal
            )
        ) {
            return DeliveryOutcome
                .SESSION_CHANGED
        }

        val requestedSeverity =
            IncidentSeverity
                .fromStoredValue(
                    pending.severity
                )

        val result =
            runCatching {

                sender.createIncident(
                    incidentId =
                        pending.incidentId,

                    title =
                        pending.title,

                    status =
                        pending.status,

                    severity =
                        requestedSeverity,

                    commandId =
                        pending.commandId
                )
            }
                .getOrElse {

                    IncidentCommandResult
                        .TRANSPORT_FAILURE
                }

        reportOutboxEvent(
"CREATE_OUTBOX_RESULT|${pending.commandId}|result=${result.name}"
)

        if (
            result ==
            IncidentCommandResult.REJECTED
        ) {

            store.clearIf(
                pending.commandId,
                pending.ownerPrincipal
            )

            return DeliveryOutcome
                .COMPLETE
        }

        if (
            result ==
            IncidentCommandResult
                .TRANSPORT_FAILURE
        ) {

            val afterFailure =
                database
                    .incidentDao()
                    .getById(
                        pending.incidentId
                    )

            if (
                afterFailure != null
            ) {

                val resolution =
                    decideCreateIncidentResolution(
                        authoritativeTitle =
                            afterFailure.title,
                        authoritativeStatus =
                            afterFailure.status,
                        authoritativeSeverity =
                            afterFailure.severity,
                        requestedTitle =
                            pending.title,
                        requestedStatus =
                            pending.status,
                        requestedSeverity =
                            pending.severity
                    )

                store.clearIf(
                    pending.commandId,
                    pending.ownerPrincipal
                )

                reportOutboxEvent(
when (
                        resolution
                    ) {
                        CreateIncidentResolutionDecision.CONVERGED ->
                            "CREATE_OUTBOX_RESOLVED|${pending.commandId}|reason=converged_after_transport_failure"

                        CreateIncidentResolutionDecision.SUPERSEDED ->
                            "CREATE_OUTBOX_RESOLVED|${pending.commandId}|reason=superseded_after_transport_failure"

                        CreateIncidentResolutionDecision.KEEP_PENDING ->
                            error(
                                "Existing incident cannot remain pending."
                            )
                    }
)

                return DeliveryOutcome
                    .COMPLETE
            }

            return DeliveryOutcome
                .RETRY_TRANSPORT
        }

        repeat(
            20
        ) {

            val current =
                database
                    .incidentDao()
                    .getById(
                        pending.incidentId
                    )

            if (
                current != null
            ) {

                val resolution =
                    decideCreateIncidentResolution(
                        authoritativeTitle =
                            current.title,
                        authoritativeStatus =
                            current.status,
                        authoritativeSeverity =
                            current.severity,
                        requestedTitle =
                            pending.title,
                        requestedStatus =
                            pending.status,
                        requestedSeverity =
                            pending.severity
                    )

                store.clearIf(
                    pending.commandId,
                    pending.ownerPrincipal
                )

                reportOutboxEvent(
when (
                        resolution
                    ) {
                        CreateIncidentResolutionDecision.CONVERGED ->
                            "CREATE_OUTBOX_RESOLVED|${pending.commandId}|reason=authoritative_convergence"

                        CreateIncidentResolutionDecision.SUPERSEDED ->
                            "CREATE_OUTBOX_RESOLVED|${pending.commandId}|reason=superseded_after_accept"

                        CreateIncidentResolutionDecision.KEEP_PENDING ->
                            error(
                                "Existing incident cannot remain pending."
                            )
                    }
)

                return DeliveryOutcome
                    .COMPLETE
            }

            if (
                !isCurrentOwner(
                    pending.ownerPrincipal
                )
            ) {
                return DeliveryOutcome
                    .SESSION_CHANGED
            }

            delay(
                100L
            )
        }

        return DeliveryOutcome
            .RETRY_CONFIRMATION
    }


    private fun isCurrentOwner(
        ownerPrincipal: String
    ): Boolean {

        val session =
            SessionManager
                .sessionState
                .value

        return (
            session is
                SessionState.SignedIn &&
            session.userId ==
                ownerPrincipal
        )
    }

    private fun scheduleTransportRetry(
        commandId: String
    ) {

        val nextAttempt =
            (
                retryStates[
                    commandId
                ]
                    ?.attempt
                    ?: 0
            ) + 1

        retryStates[
            commandId
        ] =
            RetryState(
                attempt =
                    nextAttempt,

                notBeforeMillis =
                    System.currentTimeMillis() +
                        transportBackoffMillis(
                            nextAttempt
                        )
            )
    }

    private fun scheduleConfirmationRetry(
        commandId: String
    ) {

        val currentAttempt =
            retryStates[
                commandId
            ]
                ?.attempt
                ?: 0

        retryStates[
            commandId
        ] =
            RetryState(
                attempt =
                    currentAttempt,

                notBeforeMillis =
                    System.currentTimeMillis() +
                        1_000L
            )
    }

    private fun transportBackoffMillis(
        attempt: Int
    ): Long {

        return when (attempt) {

            1 ->
                1_000L

            2 ->
                2_000L

            3 ->
                4_000L

            4 ->
                8_000L

            5 ->
                16_000L

            else ->
                30_000L
        }
    }
}