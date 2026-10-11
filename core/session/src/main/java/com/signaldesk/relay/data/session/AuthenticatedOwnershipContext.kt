package com.signaldesk.relay.data.session

/**
 * Identity captured when authenticated work begins.
 *
 * This is a value object, not an authorization lock.
 * Sensitive commits must still use SessionManager's
 * shared ownership gate and verify the captured context.
 */
data class AuthenticatedOwnershipContext private constructor(
    val ownerPrincipal: String,
    val sessionGeneration: Long
) {

    init {
        require(ownerPrincipal.isNotBlank()) {
            "Authenticated owner is required."
        }
        require(sessionGeneration >= 0L) {
            "Session generation must be nonnegative."
        }
    }

    fun matches(
        sessionState: SessionState,
        generation: Long
    ): Boolean {
        return generation == sessionGeneration &&
            sessionState is SessionState.SignedIn &&
            sessionState.userId == ownerPrincipal
    }

    companion object {

        fun capture(
            sessionState: SessionState,
            generation: Long
        ): AuthenticatedOwnershipContext? {
            val signedIn =
                sessionState as? SessionState.SignedIn
                    ?: return null

            if (
                signedIn.userId.isBlank() ||
                generation < 0L
            ) {
                return null
            }

            return AuthenticatedOwnershipContext(
                ownerPrincipal = signedIn.userId,
                sessionGeneration = generation
            )
        }
    }
}
