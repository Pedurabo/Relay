package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.data.session.SessionState

data class TimelineDeliveryCredential(
    val ownerPrincipal: String,
    val accessToken: String
)

fun timelineDeliveryCredential(
    sessionState: SessionState,
    ownerPrincipal: String
): TimelineDeliveryCredential? {

    val signedIn =
        sessionState as?
            SessionState.SignedIn
            ?: return null

    if (
        ownerPrincipal.isBlank() ||
        signedIn.userId != ownerPrincipal ||
        signedIn.accessToken.isBlank()
    ) {
        return null
    }

    return TimelineDeliveryCredential(
        ownerPrincipal =
            signedIn.userId,
        accessToken =
            signedIn.accessToken
    )
}

fun isTimelineDeliverySessionCurrent(
    sessionState: SessionState,
    expectedOwnerPrincipal: String,
    expectedAccessToken: String
): Boolean {

    val signedIn =
        sessionState as?
            SessionState.SignedIn
            ?: return false

    return (
        expectedOwnerPrincipal.isNotBlank() &&
            expectedAccessToken.isNotBlank() &&
            signedIn.userId ==
                expectedOwnerPrincipal &&
            signedIn.accessToken ==
                expectedAccessToken
    )
}
