package com.signaldesk.relay.notifications

import android.content.Context

class PushTokenStore(
    context: Context
) {

    private val preferences =
        context
            .getSharedPreferences(
                "relay_push_token",
                Context.MODE_PRIVATE
            )

    fun read(): String? =
        preferences
            .getString(
                KEY_TOKEN,
                null
            )
            ?.takeIf {
                it.isNotBlank()
            }

    fun save(
        token: String
    ) {

        preferences
            .edit()
            .putString(
                KEY_TOKEN,
                token
            )
            .apply()
    }

    fun clear() {

        preferences
            .edit()
            .clear()
            .apply()
    }

    companion object {
        private const val KEY_TOKEN =
            "token"
    }
}
