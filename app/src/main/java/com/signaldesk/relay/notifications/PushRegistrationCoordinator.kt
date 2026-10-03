package com.signaldesk.relay.notifications

import android.content.Context
import com.signaldesk.relay.data.session.SessionManager
import com.signaldesk.relay.data.session.SessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal suspend fun attemptPushUnregistration(
    unregister: suspend () -> Unit
) {
    runCatching {
        unregister()
    }
}

internal fun isCurrentPushRegistrationSession(
    current: SessionState,
    expectedUserId: String,
    expectedAccessToken: String
): Boolean =
    current is SessionState.SignedIn &&
        current.userId ==
            expectedUserId &&
        current.accessToken ==
            expectedAccessToken

internal fun isCurrentPushRegistrationRequest(
    currentSession: SessionState,
    expectedUserId: String,
    expectedAccessToken: String,
    currentRegistrationToken: String?,
    expectedRegistrationToken: String
): Boolean =
    isCurrentPushRegistrationSession(
        current =
            currentSession,
        expectedUserId =
            expectedUserId,
        expectedAccessToken =
            expectedAccessToken
    ) &&
        currentRegistrationToken ==
            expectedRegistrationToken

internal suspend fun runPushRegistrationSafely(
    isCurrentRequest: () -> Boolean,
    delayAfterFailure:
        suspend (Int) -> Unit,
    register: suspend () -> Unit
) {
    var failedAttempts =
        0

    while (
        isCurrentRequest()
    ) {
        try {

            register()

            return

        } catch (
            error: CancellationException
        ) {
            throw error

        } catch (
            error: Throwable
        ) {

            failedAttempts +=
                1

            if (
                !isCurrentRequest()
            ) {
                return
            }

            delayAfterFailure(
                failedAttempts
            )
        }
    }
}

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

            if (
                !isCurrentPushRegistrationSession(
                    current =
                        SessionManager
                            .sessionState
                            .value,
                    expectedUserId =
                        session.userId,
                    expectedAccessToken =
                        session.accessToken
                )
            ) {
                return@launch
            }

            runPushRegistrationSafely(
                isCurrentRequest = {

                    isCurrentPushRegistrationRequest(
                        currentSession =
                            SessionManager
                                .sessionState
                                .value,
                        expectedUserId =
                            session.userId,
                        expectedAccessToken =
                            session.accessToken,
                        currentRegistrationToken =
                            PushTokenStore(
                                appContext
                            )
                                .read(),
                        expectedRegistrationToken =
                            token
                    )
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
                }
            ) {

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

            attemptPushUnregistration {

                client.unregister(
                    accessToken =
                        accessToken,
                    registrationToken =
                        token
                )
            }

        }
    }
}
