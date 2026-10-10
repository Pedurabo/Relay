package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.DeferredIncidentUpdateEntity
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateCrossIncidentLateUnblockProcessDeathIsolationTest {

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
    fun lateUnblockAfterRestartCannotDisturbAlreadyConvergedNeighbor() =
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
             * Incident A is already fully converged.
             */
            firstDatabase
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-RESTART-LATE-A",
                        title =
                            "A fifth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        latestSequence =
                            5L
                    )
                )

            /*
             * Incident B is blocked at seq=2 with durable 4/5.
             */
            firstDatabase
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-RESTART-LATE-B",
                        title =
                            "B second",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        latestSequence =
                            2L
                    )
                )

            firstDatabase
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-RESTART-LATE-B-4",
                        incidentId =
                            "INC-RESTART-LATE-B",
                        occurredAt =
                            4_000L,
                        sequence =
                            4L,
                        title =
                            "B fourth",
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
                            "EVENT-RESTART-LATE-B-5",
                        incidentId =
                            "INC-RESTART-LATE-B",
                        occurredAt =
                            5_000L,
                        sequence =
                            5L,
                        title =
                            "B fifth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            200L
                    )
                )

            /*
             * Establish durable B gap before process death.
             */
            val firstProcessor =
                IncidentEventProcessor(
                    firstDatabase
                )

            firstProcessor
                .recoverDeferredIncidentUpdates()

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

            /*
             * Only B receives its missing predecessor in the fresh process.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                recreatedProcessor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-RESTART-LATE-B-3",
                        incidentId =
                            "INC-RESTART-LATE-B",
                        occurredAt =
                            3_000L,
                        title =
                            "B third",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            3L
                    )
                )
            )

            val incidentB =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-RESTART-LATE-B"
                        )
                )

            assertEquals(
                5L,
                incidentB.latestSequence
            )

            assertEquals(
                "B fifth",
                incidentB.title
            )

            assertEquals(
                "Resolved",
                incidentB.status
            )

            assertEquals(
                "HIGH",
                incidentB.severity
            )

            assertEquals(
                0,
                recreatedDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-RESTART-LATE-B"
                    )
                    .size
            )

            assertEquals(
                null,
                recreatedDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-RESTART-LATE-B"
                    )
            )

            /*
             * A remains exactly as persisted before process death.
             */
            val incidentA =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-RESTART-LATE-A"
                        )
                )

            assertEquals(
                5L,
                incidentA.latestSequence
            )

            assertEquals(
                "A fifth",
                incidentA.title
            )

            assertEquals(
                "Resolved",
                incidentA.status
            )

            assertEquals(
                "HIGH",
                incidentA.severity
            )

            assertTrue(
                recreatedDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-RESTART-LATE-A"
                    )
                    .isEmpty()
            )

            assertEquals(
                null,
                recreatedDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-RESTART-LATE-A"
                    )
            )
        }

    companion object {

        private const val DATABASE_NAME =
            "incident-cross-incident-late-unblock-process-death-isolation.db"
    }
}
