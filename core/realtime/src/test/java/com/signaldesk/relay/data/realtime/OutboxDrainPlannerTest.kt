package com.signaldesk.relay.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxDrainPlannerTest {

    @Test
    fun coolingDownCommand_doesNotBlockEligibleCommand() {

        val now =
            10_000L

        val eligible =
            OutboxDrainPlanner
                .eligibleCommandIds(
                    commandIds =
                        listOf(
                            "CMD-A",
                            "CMD-B"
                        ),

                    retryNotBeforeMillis =
                        mapOf(
                            "CMD-A" to
                                26_000L
                        ),

                    nowMillis =
                        now
                )

        assertEquals(
            listOf(
                "CMD-B"
            ),
            eligible
        )
    }

    @Test
    fun nearestRetry_usesEarliestCoolingDownCommand() {

        val nearest =
            OutboxDrainPlanner
                .nearestRetryAtMillis(
                    commandIds =
                        listOf(
                            "CMD-A",
                            "CMD-B",
                            "CMD-C"
                        ),

                    retryNotBeforeMillis =
                        mapOf(
                            "CMD-A" to
                                26_000L,
                            "CMD-B" to
                                12_000L,
                            "CMD-C" to
                                18_000L
                        ),

                    nowMillis =
                        10_000L
                )

        assertEquals(
            12_000L,
            nearest
        )
    }

    @Test
    fun expiredCooldown_becomesEligibleAgain() {

        val eligible =
            OutboxDrainPlanner
                .eligibleCommandIds(
                    commandIds =
                        listOf(
                            "CMD-A"
                        ),

                    retryNotBeforeMillis =
                        mapOf(
                            "CMD-A" to
                                9_000L
                        ),

                    nowMillis =
                        10_000L
                )

        assertEquals(
            listOf(
                "CMD-A"
            ),
            eligible
        )
    }
}
