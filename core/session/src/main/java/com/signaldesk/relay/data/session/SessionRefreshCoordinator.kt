package com.signaldesk.relay.data.session

import android.util.Log

internal enum class SessionRefreshFailureDisposition {
    SIGN_OUT_INVALID_REFRESH,
    PRESERVE_SESSION
}

internal fun classifySessionRefreshFailure(
    error: Throwable
): SessionRefreshFailureDisposition {

    return if (
        error is AuthHttpException &&
        error.statusCode ==
            401
    ) {
        SessionRefreshFailureDisposition
            .SIGN_OUT_INVALID_REFRESH
    } else {
        SessionRefreshFailureDisposition
            .PRESERVE_SESSION
    }
}

internal fun shouldSignOutAfterRefreshFailure(
    error: Throwable
): Boolean =
    classifySessionRefreshFailure(
        error
    ) ==
        SessionRefreshFailureDisposition
            .SIGN_OUT_INVALID_REFRESH

internal fun isRefreshOriginCurrent(
    originatingGeneration: Long,
    currentGeneration: Long,
    originatingSession: SessionState.SignedIn,
    activeSession: SessionState
): Boolean =
    originatingGeneration == currentGeneration &&
        originatingSession == activeSession

object SessionRefreshCoordinator {

    private var client:
        AuthSessionClient? =
        null

    fun configure(
        baseUrl: String
    ) {

        require(
            baseUrl.isNotBlank()
        )

        client =
            AuthSessionClient(
                baseUrl =
                    baseUrl
            )
    }

    private val gate =
        SingleFlightRefreshGate()

    suspend fun refreshOrSignOut(
        rejectedAccessToken: String
    ): Boolean {

        return gate.run(
            observedAccessToken =
                rejectedAccessToken,
            currentAccessToken = {

                (
                    SessionManager
                        .sessionState
                        .value as?
                        SessionState.SignedIn
                )
                    ?.accessToken
            },
            refresh = {

                val current =
                    SessionManager
                        .sessionState
                        .value as?
                        SessionState.SignedIn

                if (
                    current ==
                    null
                ) {

                    Log.i(
                        TAG,
                        "SESSION_REFRESH_SKIPPED|reason=no_active_session"
                    )

                    return@run false
                }

                val refreshOriginGeneration =
                    SessionManager.sessionGeneration

                Log.i(
                    TAG,
                    "SESSION_REFRESH_ATTEMPT"
                )

                val refreshed =
                    try {

                        checkNotNull(client) {
                            "SessionRefreshCoordinator must be configured before use."
                        }
                            .refreshSession(
                                current.refreshToken
                            )

                    } catch (
                        error: kotlinx.coroutines.CancellationException
                    ) {
                        throw error

                    } catch (
                        error: Throwable
                    ) {

                        if (
                            isRefreshOriginCurrent(
                                originatingGeneration =
                                    refreshOriginGeneration,
                                currentGeneration =
                                    SessionManager.sessionGeneration,
                                originatingSession =
                                    current,
                                activeSession =
                                    SessionManager.sessionState.value
                            ) == false
                        ) {
                            Log.i(
                                TAG,
                                "SESSION_REFRESH_SUPERSEDED|phase=failure"
                            )
                            return@run false
                        }

                        when (
                            classifySessionRefreshFailure(
                                error
                            )
                        ) {

                            SessionRefreshFailureDisposition
                                .SIGN_OUT_INVALID_REFRESH -> {

                                Log.i(
                                    TAG,
                                    "SESSION_REFRESH_FAILURE|action=sign_out|reason=invalid_refresh"
                                )

                                SessionManager
                                    .signOutIfCurrentCoordinated(
                                        expectedGeneration =
                                            refreshOriginGeneration,
                                        expectedSession =
                                            current,
                                        reason =
                                            SessionTerminationReason.EXPIRED
                                    )
                            }

                            SessionRefreshFailureDisposition
                                .PRESERVE_SESSION -> {

                                Log.i(
                                    TAG,
                                    "SESSION_REFRESH_FAILURE|action=preserve_session|reason=transient_or_server"
                                )
                            }
                        }

                        return@run false
                    }

                if (
                    isRefreshOriginCurrent(
                                originatingGeneration =
                                    refreshOriginGeneration,
                                currentGeneration =
                                    SessionManager.sessionGeneration,
                                originatingSession =
                                    current,
                                activeSession =
                                    SessionManager.sessionState.value
                            ) == false
                ) {
                    Log.i(
                        TAG,
                        "SESSION_REFRESH_SUPERSEDED|phase=success"
                    )
                    return@run false
                }

                if (
                    refreshed.userId !=
                    current.userId
                ) {

                    Log.i(
                        TAG,
                        "SESSION_REFRESH_FAILURE|action=sign_out|reason=principal_mismatch"
                    )

                    SessionManager
                        .signOutIfCurrentCoordinated(
                            expectedGeneration =
                                refreshOriginGeneration,
                            expectedSession =
                                current,
                            reason =
                                SessionTerminationReason.PRINCIPAL_CHANGED
                        )

                    return@run false
                }

                val published =
                    SessionManager
                        .establishSessionIfCurrentCoordinated(
                            expectedGeneration =
                                refreshOriginGeneration,
                            expectedSession =
                                current,
                            session =
                                refreshed
                        )

                if (!published) {
                    Log.i(
                        TAG,
                        "SESSION_REFRESH_SUPERSEDED|phase=publication"
                    )
                    return@run false
                }

                Log.i(
                    TAG,
                    "SESSION_REFRESH_SUCCESS"
                )

                true
            }
        )
    }

    private const val TAG =
        "RelaySessionRefresh"
}
