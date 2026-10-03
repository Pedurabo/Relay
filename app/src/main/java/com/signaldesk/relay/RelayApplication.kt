package com.signaldesk.relay

import android.app.Application
import com.signaldesk.relay.appstate.AppVisibilityTracker
import com.signaldesk.relay.data.realtime.SeverityOutboxCoordinator
import com.signaldesk.relay.data.realtime.TimelineOutboxCoordinator
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.notifications.FirebasePushInitializer
import com.signaldesk.relay.notifications.PushRegistrationCoordinator

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

        SeverityOutboxCoordinator
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

        PushRegistrationCoordinator
            .initialize(
                this
            )
    }
}
