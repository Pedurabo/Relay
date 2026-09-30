package com.signaldesk.relay.notifications

import android.content.Context
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PushRegistrationCoordinator {

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    private val client =
        PushRegistrationClient(
            baseUrl =
                "http://127.0.0.1:9000"
        )

    fun initialize(
        context: Context
    ) {

        val appContext =
            context.applicationContext

        FirebasePushInitializer
            .fetchToken(
                appContext
            ) { token ->

                PushTokenStore(
                    appContext
                )
                    .save(
                        token
                    )

                kick(
                    appContext
                )
            }

        kick(
            appContext
        )
    }

    fun kick(
        context: Context
    ) {

        val appContext =
            context.applicationContext

        val session =
            SessionManager
                .sessionState
                .value

        if (
            session !is
            SessionState.SignedIn
        ) {
            return
        }

        val token =
            PushTokenStore(
                appContext
            )
                .read()
                ?: return

        scope.launch {

            runCatching {

                client.register(
                    accessToken =
                        session.accessToken,
                    registrationToken =
                        token
                )
            }
        }
    }

    fun unregister(
        context: Context,
        accessToken: String
    ) {

        val appContext =
            context.applicationContext

        val store =
            PushTokenStore(
                appContext
            )

        val token =
            store.read()
                ?: return

        scope.launch {

            runCatching {

                client.unregister(
                    accessToken =
                        accessToken,
                    registrationToken =
                        token
                )
            }

            store.clear()
        }
    }
}
