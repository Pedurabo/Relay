package com.signaldesk.relay.data.realtime

import java.io.IOException

class TimelineDeliveryRejectedException(
    val closeCode: Int,
    message: String
) : IOException(message)

fun isPermanentTimelineDeliveryFailure(
    error: Throwable
): Boolean {

    return error is
        TimelineDeliveryRejectedException
}
