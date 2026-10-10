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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateCrossIncidentRecoveryProcessDeathIsolationTest {

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
    fun recoveringOnePersistedIncidentAfterRestartCannotTouchAnotherBlockedIncident() =
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
             * Incident A:
             * current=2, durable=4/5, missing=3.
             */
            firstDatabase
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-RESTART-ISO-A",
                        title =
                            "A second",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        latestSequence =
                            2L
                    )
                )

            firstDatabase
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-RESTART-ISO-A-4",
                        incidentId =
                            "INC-RESTART-ISO-A",
                        occurredAt =
                            4_000L,
                        sequence =
                            4L,
                        title =
                            "A fourth",
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
                            "EVENT-RESTART-ISO-A-5",
                        incidentId =
                            "INC-RESTART-ISO-A",
                        occurredAt =
                            5_000L,
                        sequence =
                            5L,
                        title =
                            "A fifth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            200L
                    )
                )

            /*
             * Incident B:
             * current=2, durable=4/5, missing=3.
             */
            firstDatabase
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-RESTART-ISO-B",
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
                            "EVENT-RESTART-ISO-B-4",
                        incidentId =
                            "INC-RESTART-ISO-B",
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
                            300L
                    )
                )

            firstDatabase
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-RESTART-ISO-B-5",
                        incidentId =
                            "INC-RESTART-ISO-B",
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
                            400L
                    )
                )

            /*
             * Establish persisted gap state before process death.
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
             * Only A receives its missing seq=3 after restart.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                recreatedProcessor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-RESTART-ISO-A-3",
                        incidentId =
                            "INC-RESTART-ISO-A",
                        occurredAt =
                            3_000L,
                        title =
                            "A third",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            3L
                    )
                )
            )

            /*
             * A drains through seq=5.
             */
            val incidentA =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-RESTART-ISO-A"
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
                0,
                recreatedDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-RESTART-ISO-A"
                    )
                    .size
            )

            assertEquals(
                null,
                recreatedDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-RESTART-ISO-A"
                    )
            )

            /*
             * B remains untouched and blocked exactly where it was.
             */
            val incidentB =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-RESTART-ISO-B"
                        )
                )

            assertEquals(
                2L,
                incidentB.latestSequence
            )

            assertEquals(
                "B second",
                incidentB.title
            )

            assertEquals(
                "Active",
                incidentB.status
            )

            assertEquals(
                "LOW",
                incidentB.severity
            )

            val deferredB =
                recreatedDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-RESTART-ISO-B"
                    )

            assertEquals(
                2,
                deferredB.size
            )

            assertEquals(
                4L,
                deferredB[0].sequence
            )

            assertEquals(
                5L,
                deferredB[1].sequence
            )

            val gapB =
                recreatedDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-RESTART-ISO-B"
                    )

            assertNotNull(
                gapB
            )

            assertEquals(
                3L,
                requireNotNull(
                    gapB
                ).expectedSequence
            )

            assertEquals(
                5L,
                gapB.receivedSequence
            )
        }

    companion object {

        private const val DATABASE_NAME =
            "incident-cross-incident-recovery-process-death-isolation.db"
    }
}
