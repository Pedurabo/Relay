package com.signaldesk.relay.data.session

/**
 * Identifies a particular authentication generation.
 *
 * The same principal in a later generation is a
 * different session identity.
 */
data class SessionIdentity(
    val ownerPrincipal: String?,
    val generation: Long
) {
    val isAuthenticated: Boolean
        get() = !ownerPrincipal.isNullOrBlank()

    fun owns(
        expectedPrincipal: String,
        expectedGeneration: Long
    ): Boolean =
        isAuthenticated &&
            ownerPrincipal == expectedPrincipal &&
            generation == expectedGeneration
}