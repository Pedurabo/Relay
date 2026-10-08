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
                                    .signOut()
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
                    refreshed.userId !=
                    current.userId
                ) {

                    Log.i(
                        TAG,
                        "SESSION_REFRESH_FAILURE|action=sign_out|reason=principal_mismatch"
                    )

                    SessionManager
                        .signOut()

                    return@run false
                }

                SessionManager
                    .establishSession(
                        refreshed
                    )

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
