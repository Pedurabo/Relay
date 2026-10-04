package com.signaldesk.relay.notifications

import android.content.Context
import android.util.Log
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
): Boolean {
    var failedAttempts =
        0

    while (
        isCurrentRequest()
    ) {
        try {

            register()

            return true

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
                return false
            }

            delayAfterFailure(
                failedAttempts
            )
        }
    }

    return false
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

                Log.i(
                    TAG,
                    "PUSH_REGISTRATION_ABANDONED|reason=session_changed_before_start"
                )

                return@launch
            }

            val registered =
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

                    Log.i(
                        TAG,
                        "PUSH_REGISTRATION_RETRY|attempt=$attempt"
                    )

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

            Log.i(
                TAG,
                if (
                    registered
                ) {
                    "PUSH_REGISTRATION_SUCCESS"
                } else {
                    "PUSH_REGISTRATION_ABANDONED|reason=request_became_stale"
                }
            )
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

    private const val TAG =
        "RelayPushRegistration"
}
