package com.signaldesk.relay.data.realtime

import java.io.IOException

class TimelineDeliveryRejectedException(
    val closeCode: Int? = null,
    val rejectionReason: String? = null,
    message: String
) : IOException(message)

fun isPermanentTimelineDeliveryFailure(
    error: Throwable
): Boolean {

    return error is
        TimelineDeliveryRejectedException
}
