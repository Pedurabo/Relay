package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineEntryDeliveryGateTest {

    @Test
    fun authoritativeSentBeforeSendBoundaryPreventsStaleSend() =
        runBlocking {

            val entryId =
                "ENTRY-RACE"

            var durableState =
                "PENDING"

            var sendCount =
                0

            val authoritativeHasGate =
                CompletableDeferred<Unit>()

            val allowAuthoritativeCommit =
                CompletableDeferred<Unit>()

            val authoritative =
                launch(
                    Dispatchers.Default
                ) {

                    TimelineEntryDeliveryGate
                        .withEntry(
                            entryId
                        ) {

                            authoritativeHasGate
                                .complete(
                                    Unit
                                )

                            allowAuthoritativeCommit
                                .await()

                            durableState =
                                "SENT"
                        }
                }

            authoritativeHasGate
                .await()

            val sendAttempt =
                async(
                    Dispatchers.Default
                ) {

                    sendTimelineEntryIfCurrent(
                        entryId =
                            entryId,

                        loadCurrent = {
                            durableState
                        },

                        isEligible = {
                            state ->

                            state ==
                                "PENDING"
                        },

                        send = {

                            sendCount +=
                                1

                            "ACK"
                        }
                    )
                }

            allowAuthoritativeCommit
                .complete(
                    Unit
                )

            authoritative
                .join()

            val result =
                sendAttempt
                    .await()

            assertNull(
                result
            )

            assertEquals(
                "SENT",
                durableState
            )

            assertEquals(
                0,
                sendCount
            )
        }
}