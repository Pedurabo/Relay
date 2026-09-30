package com.signaldesk.relay.data.session

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
                    runCatching {

                        client
                            .refreshSession(
                                current.refreshToken
                            )
                    }
                        .getOrNull()

                if (
                    refreshed == null ||
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
