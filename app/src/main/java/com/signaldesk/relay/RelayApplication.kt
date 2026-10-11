package com.signaldesk.relay

import android.app.Application
import com.signaldesk.relay.appstate.AppVisibilityTracker
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.realtime.CreateIncidentOutboxCoordinator
import com.signaldesk.relay.data.realtime.IncidentEventProcessor
import com.signaldesk.relay.data.realtime.RealtimeEndpointConfig
import com.signaldesk.relay.data.realtime.SeverityOutboxCoordinator
import com.signaldesk.relay.data.realtime.StatusOutboxCoordinator
import com.signaldesk.relay.data.realtime.TimelineOutboxCoordinator
import com.signaldesk.relay.data.session.SessionManager
import kotlinx.coroutines.flow.collectLatest
import com.signaldesk.relay.data.session.SessionState
import com.signaldesk.relay.data.session.SessionRefreshCoordinator
import com.signaldesk.relay.notifications.FirebasePushInitializer
import com.signaldesk.relay.notifications.PushRegistrationCoordinator
import com.signaldesk.relay.notifications.PushTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RelayApplication :
    Application() {

    private val applicationScope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    override fun onCreate() {
        super.onCreate()

        AppVisibilityTracker
            .initialize(
                this
            )

        SessionManager
            .initialize(
                this
            )

        // APP_AUTHORIZED_RECOVERY_246
        applicationScope.launch {
            val processor =
                IncidentEventProcessor(
                    RelayDatabase.getInstance(
                        this@RelayApplication
                    )
                )

            SessionManager.sessionState
                .collectLatest { state ->
                    if (state !is SessionState.SignedIn) {
                        return@collectLatest
                    }

                    val ownership =
                        SessionManager.captureAuthenticatedOwnership()
                            ?: return@collectLatest

                    if (ownership.ownerPrincipal != state.userId) {
                        return@collectLatest
                    }

                    SessionManager.withAuthorizedOwnership(
                        ownership
                    ) {
                        processor.recoverDeferredTimelineEvents(
                            ownership.ownerPrincipal
                        )

                        processor.recoverDeferredIncidentUpdates(
                            ownership.ownerPrincipal
                        )
                    }
                }
        }

        SessionRefreshCoordinator
            .configure(
                BuildConfig.RELAY_HTTP_BASE_URL
            )


        RealtimeEndpointConfig
            .configure(
                BuildConfig.RELAY_WEBSOCKET_URL
            )
        SeverityOutboxCoordinator
            .initialize(
                this
            )

        CreateIncidentOutboxCoordinator
            .initialize(
                this
            )

        StatusOutboxCoordinator
            .initialize(
                this
            )

        TimelineOutboxCoordinator
            .initialize(
                this
            )

        FirebasePushInitializer
            .initialize(
                this
            )

        FirebasePushInitializer
            .fetchToken(
                this
            ) { token ->

                PushTokenStore(
                    this
                )
                    .save(
                        token
                    )

                PushRegistrationCoordinator
                    .kick(
                        this
                    )
            }

        PushRegistrationCoordinator
            .initialize(
                this
            )
    }
}
