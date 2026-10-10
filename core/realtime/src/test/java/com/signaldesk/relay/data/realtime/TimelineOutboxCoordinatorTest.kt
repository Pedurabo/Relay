package com.signaldesk.relay.data.realtime
import com.signaldesk.relay.data.session.SessionState
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineOutboxCoordinatorTest {

    @Test
    fun transientDrainFailureRetriesUntilSuccess() =
        runBlocking {

            var drains = 0

            val delayedAttempts =
                mutableListOf<Int>()

            runTimelineOutboxDrainSafely(
                isActive = {
                    true
                },
                delayAfterFailure = {
                    attempt ->

                    delayedAttempts +=
                        attempt
                },
                drain = {
                    drains += 1

                    if (drains < 3) {
                        error(
                            "transient"
                        )
                    }
                }
            )

            assertEquals(
                3,
                drains
            )

            assertEquals(
                listOf(
                    1,
                    2
                ),
                delayedAttempts
            )
        }


    @Test
    fun inactiveSessionStopsRetryAfterFailure() =
        runBlocking {

            var active = true
            var drains = 0
            var delays = 0

            runTimelineOutboxDrainSafely(
                isActive = {
                    active
                },
                delayAfterFailure = {
                    delays += 1
                },
                drain = {
                    drains += 1
                    active = false

                    error(
                        "session changed"
                    )
                }
            )

            assertEquals(
                1,
                drains
            )

            assertEquals(
                0,
                delays
            )
        }


    @Test
    fun cancellationEscapesWithoutRetry() =
        runBlocking {

            var delays = 0
            var escaped = false

            try {
                runTimelineOutboxDrainSafely(
                    isActive = {
                        true
                    },
                    delayAfterFailure = {
                        delays += 1
                    },
                    drain = {
                        throw CancellationException(
                            "cancel"
                        )
                    }
                )
            } catch (
                expected:
                    CancellationException
            ) {
                escaped = true
            }

            assertTrue(
                escaped
            )

            assertEquals(
                0,
                delays
            )
        }

    @Test
    fun postDrainCheckRetriesTransientFailureUntilSuccess() =
        runBlocking {

            var checks =
                0

            val delayedAttempts =
                mutableListOf<Int>()

            val hasPending =
                runTimelineOutboxPostDrainCheckSafely(
                    isActive = {
                        true
                    },

                    delayAfterFailure = {
                        attempt ->

                        delayedAttempts +=
                            attempt
                    },

                    hasPendingWork = {

                        checks +=
                            1

                        if (
                            checks < 3
                        ) {
                            error(
                                "transient Room failure"
                            )
                        }

                        true
                    }
                )

            assertTrue(
                hasPending
            )

            assertEquals(
                3,
                checks
            )

            assertEquals(
                listOf(
                    1,
                    2
                ),
                delayedAttempts
            )
        }


    @Test
    fun postDrainCheckStopsWhenSessionBecomesInactive() =
        runBlocking {

            var active =
                true

            var checks =
                0

            var delays =
                0

            val hasPending =
                runTimelineOutboxPostDrainCheckSafely(
                    isActive = {
                        active
                    },

                    delayAfterFailure = {
                        delays +=
                            1
                    },

                    hasPendingWork = {

                        checks +=
                            1

                        active =
                            false

                        error(
                            "session ended"
                        )
                    }
                )

            assertEquals(
                false,
                hasPending
            )

            assertEquals(
                1,
                checks
            )

            assertEquals(
                0,
                delays
            )
        }


    @Test
    fun postDrainCancellationEscapesWithoutRetry() =
        runBlocking {

            var delays =
                0

            var escaped =
                false

            try {

                runTimelineOutboxPostDrainCheckSafely(
                    isActive = {
                        true
                    },

                    delayAfterFailure = {
                        delays +=
                            1
                    },

                    hasPendingWork = {

                        throw CancellationException(
                            "cancel"
                        )
                    }
                )

            } catch (
                expected:
                    CancellationException
            ) {

                escaped =
                    true
            }

            assertTrue(
                escaped
            )

            assertEquals(
                0,
                delays
            )
        }
    @Test
    fun sessionSnapshotChangeStopsDrainRetryLoop() =
        runBlocking {

            var currentOwner =
                "operator-a"

            var currentToken =
                "token-a"

            val expectedOwner =
                "operator-a"

            val expectedToken =
                "token-a"

            var drains =
                0

            var delays =
                0

            runTimelineOutboxDrainSafely(
                isActive = {

                    currentOwner ==
                        expectedOwner &&
                    currentToken ==
                        expectedToken
                },

                delayAfterFailure = {
                    delays +=
                        1
                },

                drain = {

                    drains +=
                        1

                    /*
                     * Simulate either account switch or token refresh
                     * during a failed delivery attempt.
                     */
                    currentToken =
                        "token-b"

                    error(
                        "transport failed"
                    )
                }
            )

            assertEquals(
                1,
                drains
            )

            assertEquals(
                0,
                delays
            )
        }
    @Test
    fun coolingDownTimelineEntryDoesNotBlockLaterEntry() {

        val eligible =
            OutboxDrainPlanner
                .eligibleCommandIds(
                    commandIds =
                        listOf(
                            "ENTRY-A",
                            "ENTRY-B"
                        ),

                    retryNotBeforeMillis =
                        mapOf(
                            "ENTRY-A" to
                                20_000L
                        ),

                    nowMillis =
                        10_000L
                )

        assertEquals(
            listOf(
                "ENTRY-B"
            ),
            eligible
        )
    }


    @Test
    fun timelineDrainSleepsUntilNearestEntryRetry() {

        val nearestRetry =
            OutboxDrainPlanner
                .nearestRetryAtMillis(
                    commandIds =
                        listOf(
                            "ENTRY-A",
                            "ENTRY-B"
                        ),

                    retryNotBeforeMillis =
                        mapOf(
                            "ENTRY-A" to
                                30_000L,
                            "ENTRY-B" to
                                14_000L
                        ),

                    nowMillis =
                        10_000L
                )

        assertEquals(
            14_000L,
            nearestRetry
        )
    }
    @Test
    fun retryStateIsKeptForCurrentSessionSnapshot() {

        val credential =
            TimelineDeliveryCredential(
                ownerPrincipal =
                    "operator-a",
                accessToken =
                    "token-a"
            )

        val session =
            com.signaldesk.relay.data.session.SessionState.SignedIn(
                userId =
                    "operator-a",
                userName =
                    "Operator A",
                accessToken =
                    "token-a",
                refreshToken =
                    "refresh-a",
                accessTokenExpiresAt =
                    10_000L
            )

        assertEquals(
            false,
            shouldClearTimelineRetryState(
                session,
                credential
            )
        )
    }


    @Test
    fun tokenRefreshClearsPreviousRetryState() {

        val credential =
            TimelineDeliveryCredential(
                ownerPrincipal =
                    "operator-a",
                accessToken =
                    "token-a"
            )

        val refreshedSession =
            com.signaldesk.relay.data.session.SessionState.SignedIn(
                userId =
                    "operator-a",
                userName =
                    "Operator A",
                accessToken =
                    "token-b",
                refreshToken =
                    "refresh-b",
                accessTokenExpiresAt =
                    20_000L
            )

        assertTrue(
            shouldClearTimelineRetryState(
                refreshedSession,
                credential
            )
        )
    }


    @Test
    fun accountSwitchClearsPreviousRetryState() {

        val credential =
            TimelineDeliveryCredential(
                ownerPrincipal =
                    "operator-a",
                accessToken =
                    "token-a"
            )

        val switchedSession =
            com.signaldesk.relay.data.session.SessionState.SignedIn(
                userId =
                    "operator-b",
                userName =
                    "Operator B",
                accessToken =
                    "token-b",
                refreshToken =
                    "refresh-b",
                accessTokenExpiresAt =
                    30_000L
            )

        assertTrue(
            shouldClearTimelineRetryState(
                switchedSession,
                credential
            )
        )
    }


    @Test
    fun signOutClearsPreviousRetryState() {

        val credential =
            TimelineDeliveryCredential(
                ownerPrincipal =
                    "operator-a",
                accessToken =
                    "token-a"
            )

        assertTrue(
            shouldClearTimelineRetryState(
                com.signaldesk.relay.data.session.SessionState.SignedOut,
                credential
            )
        )
    }
    @Test
    fun staleCredentialAfterSendCannotPersistAcknowledgement() =
        runBlocking {

            val credential =
                TimelineDeliveryCredential(
                    ownerPrincipal =
                        "operator-a",
                    accessToken =
                        "token-a"
                )

            var session:
                SessionState =
                SessionState.SignedIn(
                    userId =
                        "operator-a",
                    userName =
                        "Operator A",
                    accessToken =
                        "token-a",
                    refreshToken =
                        "refresh-a",
                    accessTokenExpiresAt =
                        10_000L
                )

            val sendStarted =
                CompletableDeferred<Unit>()

            val allowSendToFinish =
                CompletableDeferred<Unit>()

            var acknowledgementPersisted =
                false

            val sendAttempt =
                async(
                    Dispatchers.Default
                ) {

                    sendStarted.complete(
                        Unit
                    )

                    allowSendToFinish.await()

                    "ACK"
                }

            sendStarted.await()

            /*
             * Same principal, refreshed token.
             * The original credential must now be stale.
             */
            session =
                SessionState.SignedIn(
                    userId =
                        "operator-a",
                    userName =
                        "Operator A",
                    accessToken =
                        "token-b",
                    refreshToken =
                        "refresh-b",
                    accessTokenExpiresAt =
                        20_000L
                )

            allowSendToFinish.complete(
                Unit
            )

            val acknowledgement =
                sendAttempt.await()

            if (
                isTimelineDeliverySessionCurrent(
                    session,
                    credential.ownerPrincipal,
                    credential.accessToken
                )
            ) {
                acknowledgementPersisted =
                    acknowledgement ==
                        "ACK"
            }

            assertEquals(
                false,
                acknowledgementPersisted
            )
        }

    @Test
    fun transportTimeoutIsRetryableButJobCancellationEscapes() =
        runBlocking {

            var timeout:
                CancellationException? =
                null

            try {

                kotlinx.coroutines.withTimeout(
                    1L
                ) {
                    kotlinx.coroutines.delay(
                        50L
                    )
                }

            } catch (
                error:
                    CancellationException
            ) {
                timeout =
                    error
            }

            assertTrue(
                timeout != null
            )

            assertEquals(
                false,
                shouldRethrowTimelineCancellation(
                    timeout!!
                )
            )

            assertTrue(
                shouldRethrowTimelineCancellation(
                    CancellationException(
                        "job cancelled"
                    )
                )
            )
        }

    @Test
    fun nonPendingTimelineEntriesAreRemovedFromRetryState() {

        val stale =
            staleTimelineRetryEntryIds(
                retryEntryIds =
                    setOf(
                        "ENTRY-A",
                        "ENTRY-B",
                        "ENTRY-C"
                    ),
                pendingEntryIds =
                    setOf(
                        "ENTRY-B"
                    )
            )

        assertEquals(
            setOf(
                "ENTRY-A",
                "ENTRY-C"
            ),
            stale
        )
    }
}