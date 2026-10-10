package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeFailureObservabilityTest {

    @Test
    fun replayFailureReportsAttemptBeforeRetry() =
        runBlocking {

            var connected = true
            var executions = 0

            val failures =
                mutableListOf<Pair<String, Int>>()

            runRealtimeReplayJobSafely(
                isConnected = {
                    connected
                },
                delayAfterFailure = {
                    assertEquals(
                        1,
                        it
                    )
                },
                onFailure = {
                    error,
                    attempt ->

                    failures +=
                        error.javaClass.simpleName to
                            attempt
                }
            ) {

                executions += 1

                if (executions == 1) {
                    throw IllegalStateException(
                        "expected"
                    )
                }

                connected = false
            }

            assertEquals(
                2,
                executions
            )

            assertEquals(
                listOf(
                    "IllegalStateException" to 1
                ),
                failures
            )
        }

    @Test
    fun replayFailureReportsEvenWhenConnectionDrops() =
        runBlocking {

            var connected = true

            val attempts =
                mutableListOf<Int>()

            runRealtimeReplayJobSafely(
                isConnected = {
                    connected
                },
                delayAfterFailure = {
                    throw AssertionError(
                        "delay must not run"
                    )
                },
                onFailure = {
                    _,
                    attempt ->

                    attempts += attempt
                }
            ) {

                connected = false

                throw IllegalStateException(
                    "expected"
                )
            }

            assertEquals(
                listOf(1),
                attempts
            )
        }

    @Test
    fun replayCancellationEscapesWithoutFailureReport() =
        runBlocking {

            var reported = false
            var escaped = false

            try {

                runRealtimeReplayJobSafely(
                    isConnected = {
                        true
                    },
                    delayAfterFailure = {
                        throw AssertionError(
                            "delay must not run"
                        )
                    },
                    onFailure = {
                        _,
                        _ ->

                        reported = true
                    }
                ) {

                    throw CancellationException(
                        "cancel"
                    )
                }

            } catch (
                error: CancellationException
            ) {

                escaped = true
            }

            assertTrue(
                escaped
            )

            assertFalse(
                reported
            )
        }

    @Test
    fun gapRecoveryFailureReportsAttemptBeforeRetry() =
        runBlocking {

            var active = true
            var executions = 0

            val failures =
                mutableListOf<Pair<String, Int>>()

            runRealtimeGapRecoverySafely(
                isActive = {
                    active
                },
                delayAfterFailure = {
                    assertEquals(
                        1,
                        it
                    )
                },
                onFailure = {
                    error,
                    attempt ->

                    failures +=
                        error.javaClass.simpleName to
                            attempt
                }
            ) {

                executions += 1

                if (executions == 1) {
                    throw IllegalStateException(
                        "expected"
                    )
                }

                active = false
            }

            assertEquals(
                2,
                executions
            )

            assertEquals(
                listOf(
                    "IllegalStateException" to 1
                ),
                failures
            )
        }

    @Test
    fun gapRecoveryFailureReportsEvenWhenLifecycleStops() =
        runBlocking {

            var active = true

            val attempts =
                mutableListOf<Int>()

            runRealtimeGapRecoverySafely(
                isActive = {
                    active
                },
                delayAfterFailure = {
                    throw AssertionError(
                        "delay must not run"
                    )
                },
                onFailure = {
                    _,
                    attempt ->

                    attempts += attempt
                }
            ) {

                active = false

                throw IllegalStateException(
                    "expected"
                )
            }

            assertEquals(
                listOf(1),
                attempts
            )
        }

    @Test
    fun gapRecoveryCancellationEscapesWithoutFailureReport() =
        runBlocking {

            var reported = false
            var escaped = false

            try {

                runRealtimeGapRecoverySafely(
                    isActive = {
                        true
                    },
                    delayAfterFailure = {
                        throw AssertionError(
                            "delay must not run"
                        )
                    },
                    onFailure = {
                        _,
                        _ ->

                        reported = true
                    }
                ) {

                    throw CancellationException(
                        "cancel"
                    )
                }

            } catch (
                error: CancellationException
            ) {

                escaped = true
            }

            assertTrue(
                escaped
            )

            assertFalse(
                reported
            )
        }
}