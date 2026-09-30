package com.signaldesk.relay.data.session

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionManager {

    private const val PREFS_NAME =
        "relay_session"

    private const val KEY_USER_ID =
        "user_id"

    private const val KEY_USER_NAME =
        "user_name"

    private const val KEY_ACCESS_TOKEN =
        "access_token"

    private val _sessionState =
        MutableStateFlow<SessionState>(
            SessionState.SignedOut
        )

    val sessionState:
        StateFlow<SessionState> =
        _sessionState.asStateFlow()

    private var appContext: Context? =
        null

    private var initialized =
        false

    fun initialize(
        context: Context
    ) {
        if (initialized) {
            return
        }

        appContext =
            context.applicationContext

        val preferences =
            context.applicationContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )

        val userId =
            preferences.getString(
                KEY_USER_ID,
                null
            )

        val userName =
            preferences.getString(
                KEY_USER_NAME,
                null
            )

        val accessToken =
            preferences.getString(
                KEY_ACCESS_TOKEN,
                null
            )

        _sessionState.value =
            if (
                !userId.isNullOrBlank() &&
                !userName.isNullOrBlank() &&
                !accessToken.isNullOrBlank()
            ) {
                SessionState.SignedIn(
                    userId = userId,
                    userName = userName,
                    accessToken = accessToken
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

        val signedIn =
            SessionState.SignedIn(
                userId =
                    session.userId,
                userName =
                    session.userName,
                accessToken =
                    session.accessToken
            )

        appContext
            ?.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            ?.edit()
            ?.putString(
                KEY_USER_ID,
                signedIn.userId
            )
            ?.putString(
                KEY_USER_NAME,
                signedIn.userName
            )
            ?.putString(
                KEY_ACCESS_TOKEN,
                signedIn.accessToken
            )
            ?.commit()

        _sessionState.value =
            signedIn
    }

    fun signOut() {

        appContext
            ?.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            ?.edit()
            ?.clear()
            ?.commit()

        _sessionState.value =
            SessionState.SignedOut
    }
}
