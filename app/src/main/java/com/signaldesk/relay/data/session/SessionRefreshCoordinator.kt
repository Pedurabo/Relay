package com.signaldesk.relay.data.session

internal fun shouldSignOutAfterRefreshFailure(
    error: Throwable
): Boolean =
    error is AuthHttpException &&
        error.statusCode ==
            401

object SessionRefreshCoordinator {

    private val client =
        AuthSessionClient(
            baseUrl =
                "http://127.0.0.1:9000"
        )

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
                        ?: return@run false

                val refreshed =
                    try {

                        client
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
                            shouldSignOutAfterRefreshFailure(
                                error
                            )
                        ) {

                            SessionManager
                                .signOut()
                        }

                        return@run false
                    }

                if (
                    refreshed.userId !=
                    current.userId
                ) {

                    SessionManager
                        .signOut()

                    return@run false
                }

                SessionManager
                    .establishSession(
                        refreshed
                    )

                true
            }
        )
    }
}
