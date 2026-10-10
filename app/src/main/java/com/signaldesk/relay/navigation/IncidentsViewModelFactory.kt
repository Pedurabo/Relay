package com.signaldesk.relay.navigation

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.signaldesk.relay.BuildConfig
import com.signaldesk.relay.appstate.IncidentOperationsLifecycleCoordinator
import com.signaldesk.relay.notifications.IncidentNotificationManager
import com.signaldesk.relay.ui.incidents.IncidentsViewModel

class IncidentsViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (
            !modelClass.isAssignableFrom(
                IncidentsViewModel::class.java
            )
        ) {
            throw IllegalArgumentException(
                "Unsupported ViewModel: ${modelClass.name}"
            )
        }

        val notificationManager =
            IncidentNotificationManager(
                application
            )

        @Suppress("UNCHECKED_CAST")
        return IncidentsViewModel(
            application =
                application,
            httpBaseUrl =
                BuildConfig.RELAY_HTTP_BASE_URL,
            webSocketUrl =
                BuildConfig.RELAY_WEBSOCKET_URL,
            operationsLifecycle =
                IncidentOperationsLifecycleCoordinator(
                    application
                ),
            onAppliedEvent =
                notificationManager::notifyAppliedEvent
        ) as T
    }
}