package com.signaldesk.relay.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

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

        val incidentId =
            data["incidentId"]
                .orEmpty()

        val severity =
            data["severity"]
                .orEmpty()

        val content =
            data["content"]
                .orEmpty()

        if (
            eventId.isBlank() ||
            incidentId.isBlank() ||
            severity.isBlank() ||
            content.isBlank()
        ) {
            return
        }

        IncidentNotificationManager(
            applicationContext
        )
            .notifyRemoteEvent(
                eventId =
                    eventId,
                incidentId =
                    incidentId,
                severityValue =
                    severity,
                content =
                    content
            )
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
