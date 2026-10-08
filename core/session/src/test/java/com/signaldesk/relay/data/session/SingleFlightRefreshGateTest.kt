package com.signaldesk.relay.data.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleFlightRefreshGateTest {

    @Test
    fun concurrentRejectedTokenRefreshesOnlyOnce() =
        runBlocking {

            val gate =
                SingleFlightRefreshGate()

            var currentAccessToken =
                "access-old"

            var refreshCalls =
                0

            val refreshStarted =
                CompletableDeferred<Unit>()

            val allowRefreshToFinish =
                CompletableDeferred<Unit>()

            val first =
                async {

                    gate.run(
                        observedAccessToken =
                            "access-old",
                        currentAccessToken = {
                            currentAccessToken
                        },
                        refresh = {

                            refreshCalls +=
                                1

                            refreshStarted
                                .complete(
                                    Unit
                                )

                            allowRefreshToFinish
                                .await()

                            currentAccessToken =
                                "access-new"

                            true
                        }
                    )
                }

            refreshStarted
                .await()

            val second =
                async {

                    gate.run(
                        observedAccessToken =
                            "access-old",
                        currentAccessToken = {
                            currentAccessToken
                        },
                        refresh = {

                            refreshCalls +=
                                1

                            currentAccessToken =
                                "unexpected-second-refresh"

                            true
                        }
                    )
                }

            allowRefreshToFinish
                .complete(
                    Unit
                )

            assertTrue(
                first.await()
            )

            assertTrue(
                second.await()
            )

            assertEquals(
                1,
                refreshCalls
            )

            assertEquals(
                "access-new",
                currentAccessToken
            )
        }

    @Test
    fun staleRejectedTokenDoesNotRotateCurrentSession() =
        runBlocking {

            val gate =
                SingleFlightRefreshGate()

            var refreshCalls =
                0

            val result =
                gate.run(
                    observedAccessToken =
                        "access-old",
                    currentAccessToken = {
                        "access-new"
                    },
                    refresh = {

                        refreshCalls +=
                            1

                        false
                    }
                )

            assertTrue(
                result
            )

            assertEquals(
                0,
                refreshCalls
            )
        }
}
