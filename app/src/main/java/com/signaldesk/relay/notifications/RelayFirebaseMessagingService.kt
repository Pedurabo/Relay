package com.signaldesk.relay.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.realtime.IncidentEventProcessor
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.remote.model.IncidentCreatedEvent
import com.signaldesk.relay.data.remote.model.IncidentEvent
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import com.signaldesk.relay.data.remote.model.TimelineEntryAddedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class RelayFirebaseMessagingService :
    FirebaseMessagingService() {

    override fun onMessageReceived(
        message: RemoteMessage
    ) {

        val data =
            message.data

        val eventId =
            data["eventId"]
                .orEmpty()

        val eventType =
            data["eventType"]
                .orEmpty()

        val incidentId =
            data["incidentId"]
                .orEmpty()

        val severity =
            data["severity"]
                .orEmpty()

        val content =
            data["content"]
                .orEmpty()

        val occurredAt =
            data["occurredAt"]
                ?.toLongOrNull()
                ?: System
                    .currentTimeMillis()

        val sequence =
            data["sequence"]
                ?.toLongOrNull()
                ?: 0L

        if (
            eventId.isBlank() ||
            incidentId.isBlank() ||
            severity.isBlank() ||
            content.isBlank()
        ) {
            return
        }

        // FIREBASE_PUSH_OWNER_GATE_246
        // Missing or mismatched ownership is rejected.
        val payloadOwner =
            data["ownerPrincipal"]
                ?.trim()
                .orEmpty()

        if (payloadOwner.isBlank()) {
            return
        }

        val ownership =
            SessionManager.captureAuthenticatedOwnership()
                ?: return

        if (ownership.ownerPrincipal != payloadOwner) {
            return
        }

        val processor =
            IncidentEventProcessor(
                RelayDatabase.getInstance(
                    applicationContext
                )
            )

        runBlocking(Dispatchers.IO) {
            SessionManager.withAuthorizedOwnership(
                ownership
            ) {
                applySnapshot(
                    processor = processor,
                    ownerPrincipal = ownership.ownerPrincipal,
                    eventId = eventId,
                    incidentId = incidentId,
                    occurredAt = occurredAt,
                    sequence = sequence,
                    severity = severity,
                    title = data["incidentTitle"].orEmpty(),
                    status = data["incidentStatus"].orEmpty()
                )

                buildEvent(
                    eventType = eventType,
                    eventId = eventId,
                    incidentId = incidentId,
                    occurredAt = occurredAt,
                    sequence = sequence,
                    severity = severity,
                    entryId = data["entryId"].orEmpty(),
                    message = data["message"].orEmpty(),
                    author = data["author"].orEmpty()
                )?.let { event ->
                    processor.process(
                        event = event,
                        ownerPrincipal = ownership.ownerPrincipal
                    )
                }

                IncidentNotificationManager(
                    applicationContext
                ).notifyRemoteEvent(
                    eventId = eventId,
                    incidentId = incidentId,
                    severityValue = severity,
                    content = content
                )
            }
        }
    }
    private suspend fun applySnapshot(
        processor: IncidentEventProcessor,
        ownerPrincipal: String,
        eventId: String,
        incidentId: String,
        occurredAt: Long,
        sequence: Long,
        severity: String,
        title: String,
        status: String
    ) {
        if (title.isBlank() || status.isBlank()) {
            return
        }

        processor.process(
            event = IncidentCreatedEvent(
                eventId = "$eventId:push-snapshot",
                incidentId = incidentId,
                occurredAt = occurredAt,
                title = title,
                status = status,
                severity = severity,
                sequence = sequence
            ),
            ownerPrincipal = ownerPrincipal
        )
    }
    private fun buildEvent(
        eventType: String,
        eventId: String,
        incidentId: String,
        occurredAt: Long,
        sequence: Long,
        severity: String,
        entryId: String,
        message: String,
        author: String
    ): IncidentEvent? {

        return when (
            eventType
        ) {

            "incident.updated" ->
                IncidentUpdatedEvent(
                    eventId =
                        eventId,
                    incidentId =
                        incidentId,
                    occurredAt =
                        occurredAt,
                    severity =
                        severity,
                    sequence =
                        sequence
                )

            "timeline.entry.added" -> {

                if (
                    entryId.isBlank() ||
                    message.isBlank() ||
                    author.isBlank()
                ) {
                    null
                } else {
                    TimelineEntryAddedEvent(
                        eventId =
                            eventId,
                        incidentId =
                            incidentId,
                        occurredAt =
                            occurredAt,
                        entryId =
                            entryId,
                        message =
                            message,
                        author =
                            author
                    )
                }
            }

            else ->
                null
        }
    }

    override fun onNewToken(
        token: String
    ) {

        PushTokenStore(
            applicationContext
        )
            .save(
                token
            )

        PushRegistrationCoordinator
            .kick(
                applicationContext
            )
    }
}
