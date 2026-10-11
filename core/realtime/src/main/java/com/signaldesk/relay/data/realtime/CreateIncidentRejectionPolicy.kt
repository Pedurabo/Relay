package com.signaldesk.relay.data.realtime

internal enum class CreateIncidentRejectionDecision {
    MARK_FAILED
}

internal fun decideCreateIncidentRejection(
    reason: String?
): CreateIncidentRejectionDecision {

    /*
     * A server-level rejection is not a transport failure.
     *
     * Retrying the same rejected payload forever is incorrect,
     * while deleting the durable command loses operator intent.
     *
     * Preserve the row as FAILED so it remains diagnosable and
     * recoverable without automatic redelivery.
     */
    return CreateIncidentRejectionDecision.MARK_FAILED
}

internal fun normalizeCreateIncidentRejectionReason(
    reason: String?
): String {

    return reason
        ?.trim()
        ?.takeIf {
            it.isNotEmpty()
        }
        ?: "unknown"
}
