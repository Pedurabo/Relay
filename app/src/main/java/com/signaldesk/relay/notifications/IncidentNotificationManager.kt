package com.signaldesk.relay.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.signaldesk.relay.MainActivity
import com.signaldesk.relay.R
import com.signaldesk.relay.appstate.AppVisibilityTracker
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.remote.model.IncidentCreatedEvent
import com.signaldesk.relay.data.remote.model.IncidentEvent
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import com.signaldesk.relay.data.remote.model.TimelineEntryAddedEvent
import com.signaldesk.relay.model.IncidentSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

class IncidentNotificationManager(
    private val context: Context
) {

    private val database =
        RelayDatabase.getInstance(
            context
        )

    private val notificationScope =
        CoroutineScope(
            Dispatchers.IO
        )

    init {
        createChannel()
    }

    fun notifyAppliedEvent(
        event: IncidentEvent
    ) {

        if (
            AppVisibilityTracker.isForeground
        ) {
            return
        }

        if (!hasPermission()) {
            return
        }

        notificationScope.launch {

            val severity =
                resolveSeverity(
                    event
                )

            if (
                severity !=
                IncidentSeverity.HIGH &&
                severity !=
                IncidentSeverity.CRITICAL
            ) {
                return@launch
            }

            val content =
                buildContent(
                    event,
                    severity
                )
                    ?: return@launch

            postNotification(
                event =
                    event,
                severity =
                    severity,
                content =
                    content
            )
        }
    }

    private suspend fun resolveSeverity(
        event: IncidentEvent
    ): IncidentSeverity {

        return when (event) {

            is IncidentCreatedEvent ->
                IncidentSeverity
                    .fromStoredValue(
                        event.severity
                    )

            is IncidentUpdatedEvent -> {

                event.severity
                    ?.let {
                        IncidentSeverity
                            .fromStoredValue(
                                it
                            )
                    }
                    ?: database
                        .incidentDao()
                        .getById(
                            event.incidentId
                        )
                        ?.severity
                        ?.let {
                            IncidentSeverity
                                .fromStoredValue(
                                    it
                                )
                        }
                    ?: IncidentSeverity.MEDIUM
            }

            is TimelineEntryAddedEvent -> {

                database
                    .incidentDao()
                    .getById(
                        event.incidentId
                    )
                    ?.severity
                    ?.let {
                        IncidentSeverity
                            .fromStoredValue(
                                it
                            )
                    }
                    ?: IncidentSeverity.MEDIUM
            }
        }
    }

    private fun buildContent(
        event: IncidentEvent,
        severity: IncidentSeverity
    ): String? {

        return when (event) {

            is IncidentCreatedEvent ->
                "${severity.name}: ${event.title}"

            is IncidentUpdatedEvent -> {

                val detail =
                    event.status
                        ?: event.severity
                        ?.let {
                            "severity changed to ${it.uppercase()}"
                        }
                        ?: "incident updated"

                "${severity.name}: ${event.incidentId} · $detail"
            }

            is TimelineEntryAddedEvent ->
                "${severity.name}: ${event.author}: ${event.message}"
        }
    }

    private fun postNotification(
        event: IncidentEvent,
        severity: IncidentSeverity,
        content: String
    ) {

        val intent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                event.incidentId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val title =
            when (severity) {

                IncidentSeverity.CRITICAL ->
                    "Critical Relay incident"

                IncidentSeverity.HIGH ->
                    "High priority Relay incident"

                IncidentSeverity.MEDIUM,
                IncidentSeverity.LOW ->
                    "Relay incident update"
            }

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_ID
            )
                .setSmallIcon(
                    R.drawable.ic_launcher_foreground
                )
                .setContentTitle(
                    title
                )
                .setContentText(
                    content
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            content
                        )
                )
                .setAutoCancel(true)
                .setContentIntent(
                    pendingIntent
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .build()

        if (!hasPermission()) {
            return
        }

        try {

            NotificationManagerCompat
                .from(context)
                .notify(
                    event.eventId
                        .hashCode()
                        .absoluteValue,
                    notification
                )

        } catch (
            error: SecurityException
        ) {

            return
        }
    }

    private fun hasPermission():
        Boolean {

        if (
            Build.VERSION.SDK_INT <
            33
        ) {
            return true
        }

        return ContextCompat
            .checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun createChannel() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Important incident updates",
                NotificationManager
                    .IMPORTANCE_HIGH
            ).apply {
                description =
                    "High and critical Relay incident updates"
            }

        context
            .getSystemService(
                NotificationManager::class.java
            )
            .createNotificationChannel(
                channel
            )
    }

    companion object {

        private const val CHANNEL_ID =
            "relay_incident_updates"
    }
}
