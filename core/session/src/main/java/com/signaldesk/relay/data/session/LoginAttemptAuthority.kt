package com.signaldesk.relay.data.session

/**
 * Coordinates interactive login publication with an
 * explicit user request to invalidate pending logins.
 *
 * The lock is held only for synchronous operations.
 * Never perform suspending work in publishIfCurrent.
 */
internal class LoginAttemptAuthority {

    private var revision = 0L

    @Synchronized
    fun capture(): Long = revision

    @Synchronized
    fun invalidate() {
        revision += 1L
    }

    @Synchronized
    fun <T> publishIfCurrent(
        expectedRevision: Long,
        publication: () -> T
    ): T? {
        if (revision != expectedRevision) {
            return null
        }

        return publication()
    }
}