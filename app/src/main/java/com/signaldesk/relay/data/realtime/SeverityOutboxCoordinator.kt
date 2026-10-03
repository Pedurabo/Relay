package com.signaldesk.relay.data.realtime

import android.app.Application
import android.util.Log
import com.signaldesk.relay.data.local.PendingSeverityCommand
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.local.RoomPendingSeverityCommandStore
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionRefreshCoordinator
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.model.IncidentSeverity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal suspend fun runOutboxStartupResetSafely(
    delayAfterFailure:
        suspend (Int) -> Unit,
    resetInFlight:
        suspend () -> Int
): Int {

    var failedAttempts =
        0

    while (true) {

        try {

            return resetInFlight()

        } catch (
            error: CancellationException
        ) {
            throw error

        } catch (
            error: Throwable
        ) {

            failedAttempts +=
                1

            delayAfterFailure(
                failedAttempts
            )
        }
    }
}

internal suspend fun runOutboxDrainSafely(
    isActive: () -> Boolean,
    delayAfterFailure:
        suspend (Int) -> Unit,
    drain:
        suspend () -> Unit
) {

    var failedAttempts =
        0

    while (
        isActive()
    ) {

        try {

            drain()

            return

        } catch (
            error: CancellationException
        ) {
            throw error

        } catch (
            error: Throwable
        ) {

            failedAttempts +=
                1

            if (
                !isActive()
            ) {
                return
            }

            delayAfterFailure(
                failedAttempts
            )
        }
    }
}

object SeverityOutboxCoordinator {

    private const val TAG =
        "RelayOutbox"

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
        RoomPendingSeverityCommandStore

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
            RoomPendingSeverityCommandStore(
                database
                    .pendingSeverityCommandDao()
            )

        scope.launch {

            val resetCount =
                runOutboxStartupResetSafely(
                    delayAfterFailure = {
                        attempt ->

                        delay(
                            when (attempt) {
                                1 -> 1_000L
                                2 -> 2_000L
                                3 -> 4_000L
                                4 -> 8_000L
                                else -> 30_000L
                            }
                        )
                    },
                    resetInFlight = {
                        store.resetInFlight()
                    }
                )

            Log.i(
                TAG,
                "OUTBOX_STARTUP|resetInFlight=$resetCount"
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

            Log.i(
                TAG,
                "OUTBOX_KICK_SKIPPED|reason=already_draining"
            )

            return
        }

        Log.i(
            TAG,
            "OUTBOX_DRAIN_START"
        )

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
                            when (attempt) {
                                1 -> 1_000L
                                2 -> 2_000L
                                3 -> 4_000L
                                4 -> 8_000L
                                else -> 30_000L
                            }
                        )
                    }
                ) {

                    drainFairly()
                }

            } finally {

                draining.set(
                    false
                )

                Log.i(
                    TAG,
                    "OUTBOX_DRAIN_STOP"
                )

                val current =
                    SessionManager
                        .sessionState
                        .value

                if (
                    current is
                    SessionState.SignedIn &&
                    store.load(
                        current.userId
                    ) != null
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

                Log.i(
                    TAG,
                    "OUTBOX_DRAIN_EXIT|reason=signed_out"
                )

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

                Log.i(
                    TAG,
                    "OUTBOX_DRAIN_EXIT|reason=no_rows_for_owner|owner=$ownerPrincipal"
                )

                return
            }

            var attemptedAny =
                false

            val now =
                System.currentTimeMillis()

            val retryNotBeforeMillis =
                pending
                    .mapNotNull { command ->

                        retryStates[
                            command.commandId
                        ]
                            ?.notBeforeMillis
                            ?.let { notBefore ->
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
                command in
                pending
            ) {

                if (
                    command.commandId !in
                    eligibleCommandIds
                ) {

                    val retryState =
                        retryStates[
                            command.commandId
                        ]

                    if (
                        retryState != null
                    ) {

                        Log.i(
                            TAG,
                            "OUTBOX_COOLDOWN|${command.commandId}|remainingMs=${retryState.notBeforeMillis - now}|attempt=${retryState.attempt}"
                        )
                    }

                    continue
                }

                if (
                    !isCurrentOwner(
                        command.ownerPrincipal
                    )
                ) {

                    Log.i(
                        TAG,
                        "OUTBOX_OWNER_BLOCK|${command.commandId}|owner=${command.ownerPrincipal}"
                    )

                    return
                }


                val claimed =
                    store.claim(
                        command.commandId,
                        ownerPrincipal
                    )

                if (
                    !claimed
                ) {

                    Log.i(
                        TAG,
                        "OUTBOX_CLAIM_SKIPPED|${command.commandId}"
                    )

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

                    Log.i(
                        TAG,
                        "OUTBOX_RELEASE|${command.commandId}|reason=session_changed_before_delivery"
                    )

                    return
                }

                attemptedAny =
                    true

                val attemptNumber =
                    (
                        retryStates[
                            command.commandId
                        ]
                            ?.attempt
                            ?: 0
                    ) + 1

                Log.i(
                    TAG,
                    "OUTBOX_ATTEMPT|${command.commandId}|incident=${command.incidentId}|severity=${command.severity}|owner=${command.ownerPrincipal}|attempt=$attemptNumber"
                )

                val outcome =
                    try {

                        deliverOneTurn(
                            command
                        )

                    } catch (
                        error: Throwable
                    ) {

                        Log.i(
                            TAG,
                            "OUTBOX_EXCEPTION|${command.commandId}|${error::class.simpleName}"
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

                        Log.i(
                            TAG,
                            "OUTBOX_COMPLETE|${command.commandId}"
                        )
                    }

                    DeliveryOutcome.RETRY_TRANSPORT -> {

                        store.release(
                            command.commandId,
                            command.ownerPrincipal
                        )

                        Log.i(
                            TAG,
                            "OUTBOX_RELEASE|${command.commandId}|reason=transport"
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

                        Log.i(
                            TAG,
                            "OUTBOX_RELEASE|${command.commandId}|reason=confirmation"
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

                        Log.i(
                            TAG,
                            "OUTBOX_RELEASE|${command.commandId}|reason=session_changed"
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

                Log.i(
                    TAG,
                    "OUTBOX_DRAIN_EXIT|reason=session_changed"
                )

                return
            }

            val remaining =
                store.loadAll(
                    ownerPrincipal
                )

            if (
                remaining.isEmpty()
            ) {

                Log.i(
                    TAG,
                    "OUTBOX_DRAIN_EXIT|reason=empty"
                )

                return
            }

            if (attemptedAny) {
                continue
            }

            val currentTime =
                System.currentTimeMillis()

            val remainingRetryNotBefore =
                remaining
                    .mapNotNull { command ->

                        retryStates[
                            command.commandId
                        ]
                            ?.notBeforeMillis
                            ?.let { notBefore ->
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
                            remainingRetryNotBefore,

                        nowMillis =
                            currentTime
                    )

            if (
                nearestRetry != null
            ) {

                val sleepMillis =
                    (
                        nearestRetry -
                            currentTime
                    )
                        .coerceIn(
                            100L,
                            30_000L
                        )

                Log.i(
                    TAG,
                    "OUTBOX_IDLE_SLEEP|delayMs=$sleepMillis"
                )

                delay(
                    sleepMillis
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
            PendingSeverityCommand
    ): DeliveryOutcome {

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

        val baseSeverity =
            IncidentSeverity
                .fromStoredValue(
                    pending.baseSeverity
                )

        val beforeSend =
            database
                .incidentDao()
                .getById(
                    pending.incidentId
                )

        if (
            beforeSend != null
        ) {

            val currentSeverity =
                IncidentSeverity
                    .fromStoredValue(
                        beforeSend.severity
                    )

            if (
                currentSeverity ==
                requestedSeverity
            ) {

                store.clearIf(
                    pending.commandId,
                    pending.ownerPrincipal
                )

                Log.i(
                    TAG,
                    "OUTBOX_RESOLVED|${pending.commandId}|reason=already_converged"
                )

                return DeliveryOutcome.COMPLETE
            }

            if (
                currentSeverity !=
                baseSeverity &&
                currentSeverity !=
                requestedSeverity
            ) {

                store.clearIf(
                    pending.commandId,
                    pending.ownerPrincipal
                )

                Log.i(
                    TAG,
                    "OUTBOX_RESOLVED|${pending.commandId}|reason=superseded|current=${currentSeverity.name}"
                )

                return DeliveryOutcome.COMPLETE
            }
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

        /*
         * Capture the token belonging to the SAME principal as
         * this durable command. The sender cannot switch to a
         * later user's token while this attempt is running.
         */
        val deliveryToken =
            session.accessToken

        val sender =
            WebSocketIncidentCommandSender(
                url =
                    "ws://127.0.0.1:9000",

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

        /*
         * Final principal check before opening the transport.
         */
        if (
            !isCurrentOwner(
                pending.ownerPrincipal
            )
        ) {

            return DeliveryOutcome
                .SESSION_CHANGED
        }

        val result =
            runCatching {

                sender.updateSeverity(
                    incidentId =
                        pending.incidentId,

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

        Log.i(
            TAG,
            "OUTBOX_RESULT|${pending.commandId}|result=${result.name}"
        )

        if (
            result ==
            IncidentCommandResult.REJECTED
        ) {

            store.clearIf(
                pending.commandId,
                pending.ownerPrincipal
            )

            Log.i(
                TAG,
                "OUTBOX_RESOLVED|${pending.commandId}|reason=rejected"
            )

            return DeliveryOutcome.COMPLETE
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

                val currentSeverity =
                    IncidentSeverity
                        .fromStoredValue(
                            afterFailure.severity
                        )

                if (
                    currentSeverity ==
                    requestedSeverity
                ) {

                    store.clearIf(
                        pending.commandId,
                        pending.ownerPrincipal
                    )

                    Log.i(
                        TAG,
                        "OUTBOX_RESOLVED|${pending.commandId}|reason=converged_after_transport_failure"
                    )

                    return DeliveryOutcome.COMPLETE
                }

                if (
                    currentSeverity !=
                    baseSeverity &&
                    currentSeverity !=
                    requestedSeverity
                ) {

                    store.clearIf(
                        pending.commandId,
                        pending.ownerPrincipal
                    )

                    Log.i(
                        TAG,
                        "OUTBOX_RESOLVED|${pending.commandId}|reason=superseded_after_transport_failure"
                    )

                    return DeliveryOutcome.COMPLETE
                }
            }

            return DeliveryOutcome
                .RETRY_TRANSPORT
        }

        repeat(20) {

            val current =
                database
                    .incidentDao()
                    .getById(
                        pending.incidentId
                    )

            if (
                current != null
            ) {

                val currentSeverity =
                    IncidentSeverity
                        .fromStoredValue(
                            current.severity
                        )

                if (
                    currentSeverity ==
                    requestedSeverity
                ) {

                    store.clearIf(
                        pending.commandId,
                        pending.ownerPrincipal
                    )

                    Log.i(
                        TAG,
                        "OUTBOX_RESOLVED|${pending.commandId}|reason=authoritative_convergence"
                    )

                    return DeliveryOutcome.COMPLETE
                }

                if (
                    currentSeverity !=
                    baseSeverity &&
                    currentSeverity !=
                    requestedSeverity
                ) {

                    store.clearIf(
                        pending.commandId,
                        pending.ownerPrincipal
                    )

                    Log.i(
                        TAG,
                        "OUTBOX_RESOLVED|${pending.commandId}|reason=superseded_after_accept"
                    )

                    return DeliveryOutcome.COMPLETE
                }
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

        val previousAttempt =
            retryStates[
                commandId
            ]
                ?.attempt
                ?: 0

        val nextAttempt =
            previousAttempt + 1

        val delayMillis =
            transportBackoffMillis(
                nextAttempt
            )

        retryStates[
            commandId
        ] =
            RetryState(
                attempt =
                    nextAttempt,

                notBeforeMillis =
                    System.currentTimeMillis() +
                        delayMillis
            )

        Log.i(
            TAG,
            "OUTBOX_BACKOFF|$commandId|attempt=$nextAttempt|delayMs=$delayMillis"
        )
    }

    private fun scheduleConfirmationRetry(
        commandId: String
    ) {

        val previousAttempt =
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
                    previousAttempt,

                notBeforeMillis =
                    System.currentTimeMillis() +
                        1_000L
            )

        Log.i(
            TAG,
            "OUTBOX_BACKOFF|$commandId|attempt=$previousAttempt|delayMs=1000|reason=confirmation"
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
