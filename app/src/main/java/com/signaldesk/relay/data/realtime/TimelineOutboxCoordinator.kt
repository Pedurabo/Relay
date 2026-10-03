package com.signaldesk.relay.data.realtime

import android.app.Application
import android.util.Log
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.local.TimelineEntryDao
import com.signaldesk.relay.data.local.TimelineEntryEntity
import com.signaldesk.relay.data.mapper.toDomain
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionRefreshCoordinator
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.model.DeliveryState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal suspend fun runTimelineOutboxDrainSafely(
    isActive: () -> Boolean,
    delayAfterFailure: suspend (Int) -> Unit,
    drain: suspend () -> Unit
) {
    var failedAttempts = 0

    while (isActive()) {
        try {
            drain()
            return
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            failedAttempts += 1

            if (!isActive()) {
                return
            }

            delayAfterFailure(
                failedAttempts
            )
        }
    }
}

internal suspend fun runTimelineOutboxPostDrainCheckSafely(
    isActive: () -> Boolean,
    delayAfterFailure:
        suspend (Int) -> Unit,
    hasPendingWork:
        suspend () -> Boolean
): Boolean {

    var failedAttempts =
        0

    while (
        isActive()
    ) {

        try {

            return hasPendingWork()

        } catch (
            error:
                CancellationException
        ) {
            throw error

        } catch (
            error:
                Throwable
        ) {

            failedAttempts +=
                1

            if (
                !isActive()
            ) {
                return false
            }

            delayAfterFailure(
                failedAttempts
            )
        }
    }

    return false
}

object TimelineOutboxCoordinator {

    private const val TAG =
        "RelayTimelineOutbox"

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    private val initialized =
        AtomicBoolean(false)

    private val draining =
        AtomicBoolean(false)

    private lateinit var timelineDao:
        TimelineEntryDao

    private lateinit var sender:
        WebSocketTimelineSender


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

        timelineDao =
            RelayDatabase
                .getInstance(
                    application
                )
                .timelineEntryDao()

        sender =
            WebSocketTimelineSender(
                url =
                    "ws://127.0.0.1:9000",
                onSessionInvalidated = {
                    rejectedAccessToken ->

                    scope.launch {
                        SessionRefreshCoordinator
                            .refreshOrSignOut(
                                rejectedAccessToken
                            )
                    }
                }
            )

        /*
         * SessionManager has already restored persisted credentials
         * before this coordinator is initialized.
         */
        kick()
    }


    fun kick() {
        if (!initialized.get()) {
            return
        }

        if (
            SessionManager
                .sessionState
                .value !is
                SessionState.SignedIn
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
                runTimelineOutboxDrainSafely(
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
                    },
                    drain = {
                        drainPending()
                    }
                )
            } finally {
                draining.set(
                    false
                )

                closeKickRace()
            }
        }
    }


    private suspend fun closeKickRace() {

        val hasPending =
            runTimelineOutboxPostDrainCheckSafely(
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
                },

                hasPendingWork = {

                    val current =
                        SessionManager
                            .sessionState
                            .value as?
                            SessionState.SignedIn
                            ?: return@runTimelineOutboxPostDrainCheckSafely false

                    timelineDao
                        .loadPendingForOwner(
                            current.userId
                        )
                        .isNotEmpty()
                }
            )

        if (
            hasPending
        ) {
            kick()
        }
    }


    private suspend fun drainPending() {
        while (true) {
            val session =
                SessionManager
                    .sessionState
                    .value as?
                    SessionState.SignedIn
                    ?: return

            val ownerPrincipal =
                session.userId

            val pending =
                timelineDao
                    .loadPendingForOwner(
                        ownerPrincipal
                    )

            if (pending.isEmpty()) {
                return
            }

            for (entity in pending) {
                val credential =
                    timelineDeliveryCredential(
                        SessionManager
                            .sessionState
                            .value,
                        entity.ownerPrincipal
                    )
                        ?: return

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

                val acknowledgement =
                    try {
                        withTimeout(
                            10_000L
                        ) {
                            sender.send(
                                entity.toDomain(),
                                credential.accessToken
                            )
                        }
                    } catch (
                        error: CancellationException
                    ) {
                        throw error
                    } catch (
                        error: Throwable
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

                        throw error
                    }

                timelineDao
                    .upsert(
                        TimelineEntryEntity(
                            entryId =
                                acknowledgement.entryId,
                            incidentId =
                                acknowledgement.incidentId,
                            message =
                                acknowledgement.message,
                            author =
                                acknowledgement.author,
                            occurredAt =
                                acknowledgement.occurredAt,
                            deliveryState =
                                DeliveryState.SENT.name,
                            ownerPrincipal =
                                credential.ownerPrincipal
                        )
                    )
            }

            val afterPass =
                SessionManager
                    .sessionState
                    .value

            if (
                afterPass !is
                    SessionState.SignedIn ||
                afterPass.userId !=
                    ownerPrincipal
            ) {
                return
            }
        }
    }
}
