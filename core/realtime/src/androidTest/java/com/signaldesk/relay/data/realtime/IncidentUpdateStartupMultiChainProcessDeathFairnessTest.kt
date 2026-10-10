package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.DeferredIncidentUpdateEntity
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateStartupMultiChainProcessDeathFairnessTest {

    private lateinit var context:
        Context

    private var database:
        RelayDatabase? =
            null

    @Before
    fun setUp() {

        context =
            ApplicationProvider
                .getApplicationContext()

        context.deleteDatabase(
            DATABASE_NAME
        )
    }

    @After
    fun tearDown() {

        database
            ?.close()

        context.deleteDatabase(
            DATABASE_NAME
        )
    }

    @Test
    fun restartRecoveryPreservesBlockedChainAndDrainsReadyChain() =
        runBlocking {

            database =
                Room
                    .databaseBuilder(
                        context,
                        RelayDatabase::class.java,
                        DATABASE_NAME
                    )
                    .allowMainThreadQueries()
                    .build()

            val firstDatabase =
                requireNotNull(
                    database
                )

            /*
             * Blocked incident:
             * current=3, durable=5/6, missing=4.
             */
            firstDatabase
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-RESTART-FAIR-BLOCKED",
                        title =
                            "Blocked third",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        latestSequence =
                            3L
                    )
                )

            firstDatabase
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-RESTART-FAIR-BLOCKED-5",
                        incidentId =
                            "INC-RESTART-FAIR-BLOCKED",
                        occurredAt =
                            5_000L,
                        sequence =
                            5L,
                        title =
                            "Blocked fifth",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        deferredAt =
                            100L
                    )
                )

            firstDatabase
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-RESTART-FAIR-BLOCKED-6",
                        incidentId =
                            "INC-RESTART-FAIR-BLOCKED",
                        occurredAt =
                            6_000L,
                        sequence =
                            6L,
                        title =
                            "Blocked sixth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            200L
                    )
                )

            /*
             * Ready incident:
             * current=3, durable=4/5.
             */
            firstDatabase
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-RESTART-FAIR-READY",
                        title =
                            "Ready third",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        latestSequence =
                            3L
                    )
                )

            firstDatabase
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-RESTART-FAIR-READY-4",
                        incidentId =
                            "INC-RESTART-FAIR-READY",
                        occurredAt =
                            4_000L,
                        sequence =
                            4L,
                        title =
                            "Ready fourth",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        deferredAt =
                            300L
                    )
                )

            firstDatabase
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-RESTART-FAIR-READY-5",
                        incidentId =
                            "INC-RESTART-FAIR-READY",
                        occurredAt =
                            5_000L,
                        sequence =
                            5L,
                        title =
                            "Ready fifth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            400L
                    )
                )

            /*
             * Simulate process death before startup recovery runs.
             */
            firstDatabase.close()

            database =
                Room
                    .databaseBuilder(
                        context,
                        RelayDatabase::class.java,
                        DATABASE_NAME
                    )
                    .allowMainThreadQueries()
                    .build()

            val recreatedDatabase =
                requireNotNull(
                    database
                )

            val recreatedProcessor =
                IncidentEventProcessor(
                    recreatedDatabase
                )

            recreatedProcessor
                .recoverDeferredIncidentUpdates()

            /*
             * Blocked chain remains untouched except for durable gap state.
             */
            val blocked =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-RESTART-FAIR-BLOCKED"
                        )
                )

            assertEquals(
                3L,
                blocked.latestSequence
            )

            val blockedDeferred =
                recreatedDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-RESTART-FAIR-BLOCKED"
                    )

            assertEquals(
                2,
                blockedDeferred.size
            )

            assertEquals(
                5L,
                blockedDeferred[0].sequence
            )

            assertEquals(
                6L,
                blockedDeferred[1].sequence
            )

            val blockedGap =
                requireNotNull(
                    recreatedDatabase
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-RESTART-FAIR-BLOCKED"
                        )
                )

            assertEquals(
                4L,
                blockedGap.expectedSequence
            )

            assertEquals(
                6L,
                blockedGap.receivedSequence
            )

            /*
             * Ready chain drains completely in the same startup pass.
             */
            val ready =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-RESTART-FAIR-READY"
                        )
                )

            assertEquals(
                5L,
                ready.latestSequence
            )

            assertEquals(
                "Ready fifth",
                ready.title
            )

            assertEquals(
                "Resolved",
                ready.status
            )

            assertEquals(
                "HIGH",
                ready.severity
            )

            assertEquals(
                0,
                recreatedDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-RESTART-FAIR-READY"
                    )
                    .size
            )

            assertEquals(
                null,
                recreatedDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-RESTART-FAIR-READY"
                    )
            )

            assertTrue(
                recreatedDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-RESTART-FAIR-READY-4"
                    )
            )

            assertTrue(
                recreatedDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-RESTART-FAIR-READY-5"
                    )
            )
        }

    companion object {

        private const val DATABASE_NAME =
            "incident-startup-multi-chain-process-death-fairness.db"
    }
}
