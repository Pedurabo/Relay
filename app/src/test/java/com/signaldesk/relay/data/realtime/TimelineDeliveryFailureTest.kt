package com.signaldesk.relay.data.realtime

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineDeliveryFailureTest {

    @Test
    fun explicitTimelineRejectionIsPermanent() {

        val error =
            TimelineDeliveryRejectedException(
                closeCode =
                    4003,
                message =
                    "Forbidden"
            )

        assertTrue(
            isPermanentTimelineDeliveryFailure(
                error
            )
        )
    }


    @Test
    fun ordinaryTransportFailureRemainsRetryable() {

        val error =
            IOException(
                "network unavailable"
            )

        assertFalse(
            isPermanentTimelineDeliveryFailure(
                error
            )
        )
    }
}
