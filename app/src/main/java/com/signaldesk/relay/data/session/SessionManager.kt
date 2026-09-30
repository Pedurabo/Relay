package com.signaldesk.relay.data.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionManager {

    private val _sessionState =
        MutableStateFlow<SessionState>(
            SessionState.SignedOut
        )

    val sessionState:
        StateFlow<SessionState> =
        _sessionState.asStateFlow()

    private var credentialStore:
        SessionCredentialStore? =
        null

    private var initialized =
        false

    fun initialize(
        context: Context
    ) {
        if (initialized) {
            return
        }

        val store =
            SessionCredentialStore(
                context.applicationContext
            )

        credentialStore =
            store

        val session =
            store.read()

        _sessionState.value =
            if (
                session != null
            ) {
                SessionState.SignedIn(
                    userId =
                        session.userId,
                    userName =
                        session.userName,
                    accessToken =
                        session.accessToken,
                    refreshToken =
                        session.refreshToken,
                    accessTokenExpiresAt =
                        session.accessTokenExpiresAt
                )
            } else {
                SessionState.SignedOut
            }

        initialized = true
    }

    fun establishSession(
        session:
            AuthenticatedSession
    ) {

        require(
            session.userId.isNotBlank()
        )

        require(
            session.userName.isNotBlank()
        )

        require(
            session.accessToken.isNotBlank()
        )

        require(
            session.refreshToken.isNotBlank()
        )

        require(
            session.accessTokenExpiresAt >
                0L
        )

        val store =
            checkNotNull(
                credentialStore
            ) {
                "SessionManager must be initialized first."
            }

        store.save(
            session
        )

        _sessionState.value =
            SessionState.SignedIn(
                userId =
                    session.userId,
                userName =
                    session.userName,
                accessToken =
                    session.accessToken,
                refreshToken =
                    session.refreshToken,
                accessTokenExpiresAt =
                    session.accessTokenExpiresAt
            )
    }

    fun signOut() {

        credentialStore
            ?.clear()

        _sessionState.value =
            SessionState.SignedOut
    }
}
