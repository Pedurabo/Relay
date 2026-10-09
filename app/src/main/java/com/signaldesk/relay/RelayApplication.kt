package com.signaldesk.relay

import android.app.Application
import com.signaldesk.relay.appstate.AppVisibilityTracker
import com.signaldesk.relay.data.realtime.CreateIncidentOutboxCoordinator
import com.signaldesk.relay.data.realtime.RealtimeEndpointConfig
import com.signaldesk.relay.data.realtime.SeverityOutboxCoordinator
import com.signaldesk.relay.data.realtime.StatusOutboxCoordinator
import com.signaldesk.relay.data.realtime.TimelineOutboxCoordinator
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionRefreshCoordinator
import com.signaldesk.relay.notifications.FirebasePushInitializer
import com.signaldesk.relay.notifications.PushRegistrationCoordinator
import com.signaldesk.relay.notifications.PushTokenStore

class RelayApplication :
    Application() {

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
