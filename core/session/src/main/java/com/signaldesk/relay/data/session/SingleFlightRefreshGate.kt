package com.signaldesk.relay.data.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SingleFlightRefreshGate {

    private val mutex =
        Mutex()

    suspend fun run(
        observedAccessToken: String,
        currentAccessToken:
            () -> String?,
        refresh:
            suspend () -> Boolean
    ): Boolean {

        return mutex.withLock {

            val current =
                currentAccessToken()
                    ?: return@withLock false

            if (
                current !=
                observedAccessToken
            ) {
                return@withLock true
            }

            refresh()
        }
    }
}
