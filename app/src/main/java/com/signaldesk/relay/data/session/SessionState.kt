package com.signaldesk.relay.data.session

sealed interface SessionState {

    data object SignedOut :
        SessionState

    data class SignedIn(
        val userId: String,
        val userName: String,
        val accessToken: String
    ) : SessionState
}
