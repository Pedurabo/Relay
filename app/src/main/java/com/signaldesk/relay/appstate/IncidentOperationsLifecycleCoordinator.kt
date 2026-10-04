package com.signaldesk.relay.appstate

import android.content.Context
import com.signaldesk.relay.data.realtime.CreateIncidentOutboxCoordinator
import com.signaldesk.relay.data.realtime.RealtimeIncidentCoordinator
import com.signaldesk.relay.data.realtime.SeverityOutboxCoordinator
import com.signaldesk.relay.data.realtime.StatusOutboxCoordinator
import com.signaldesk.relay.data.realtime.TimelineOutboxCoordinator
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.notifications.PushRegistrationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal fun shouldRunRealtimeForLifecycle(
    isSignedIn: Boolean,
    isForeground: Boolean
): Boolean {

    return isSignedIn &&
        isForeground
}

class IncidentOperationsLifecycleCoordinator(
    context: Context,
    private val realtimeCoordinator:
        RealtimeIncidentCoordinator
) {

    private val appContext =
        context.applicationContext

    private var lifecycleJob:
        Job? =
        null

    fun start(
        scope: CoroutineScope
    ) {

        if (
            lifecycleJob?.isActive ==
            true
        ) {
            return
        }

        lifecycleJob =
            scope.launch {

                combine(
                    SessionManager
                        .sessionState,
                    AppVisibilityTracker
                        .foregroundState
                ) {
                    state,
                    isForeground ->

                    state to
                        isForeground
                }
                    .collectLatest {
                        lifecycle ->

                        val state =
                            lifecycle.first

                        val isForeground =
                            lifecycle.second

                        if (
                            !shouldRunRealtimeForLifecycle(
                                isSignedIn =
                                    state is
                                        SessionState.SignedIn,
                                isForeground =
                                    isForeground
                            )
                        ) {

                            realtimeCoordinator
                                .stop()

                            return@collectLatest
                        }

                        realtimeCoordinator
                            .startWhenAvailable(
                                scope
                            )

                        SeverityOutboxCoordinator
                            .kick()

                        StatusOutboxCoordinator
                            .kick()

                        CreateIncidentOutboxCoordinator
                            .kick()

                        TimelineOutboxCoordinator
                            .kick()

                        PushRegistrationCoordinator
                            .kick(
                                appContext
                            )
                    }
            }
    }

    fun stop() {

        lifecycleJob
            ?.cancel()

        lifecycleJob =
            null

        realtimeCoordinator
            .stop()
    }
}