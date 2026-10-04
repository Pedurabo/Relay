package com.signaldesk.relay.data.realtime

internal enum class MutationConvergenceDecision {
    KEEP_PENDING,
    CONVERGED,
    SUPERSEDED
}

internal fun decideMutationConvergence(
    currentValue: String?,
    baseValue: String,
    requestedValue: String
): MutationConvergenceDecision {

    if (
        currentValue ==
        null
    ) {
        return MutationConvergenceDecision
            .KEEP_PENDING
    }

    /*
     * Preserve the coordinators' existing precedence:
     * requested convergence wins even if base and requested
     * happen to be identical.
     */
    if (
        currentValue ==
        requestedValue
    ) {
        return MutationConvergenceDecision
            .CONVERGED
    }

    if (
        currentValue ==
        baseValue
    ) {
        return MutationConvergenceDecision
            .KEEP_PENDING
    }

    return MutationConvergenceDecision
        .SUPERSEDED
}