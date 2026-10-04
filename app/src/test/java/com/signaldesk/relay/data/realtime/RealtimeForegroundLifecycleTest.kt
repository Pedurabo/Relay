package com.signaldesk.relay.data.realtime

import com.signaldesk.relay.ui.incidents.shouldRunRealtimeForLifecycle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeForegroundLifecycleTest {

    @Test
    fun signedInForeground_runsRealtime() {

        assertTrue(
            shouldRunRealtimeForLifecycle(
                isSignedIn =
                    true,
                isForeground =
                    true
            )
        )
    }

    @Test
    fun signedInBackground_stopsRealtime() {

        assertFalse(
            shouldRunRealtimeForLifecycle(
                isSignedIn =
                    true,
                isForeground =
                    false
            )
        )
    }

    @Test
    fun signedOutForeground_doesNotRunRealtime() {

        assertFalse(
            shouldRunRealtimeForLifecycle(
                isSignedIn =
                    false,
                isForeground =
                    true
            )
        )
    }

    @Test
    fun signedOutBackground_doesNotRunRealtime() {

        assertFalse(
            shouldRunRealtimeForLifecycle(
                isSignedIn =
                    false,
                isForeground =
                    false
            )
        )
    }
}