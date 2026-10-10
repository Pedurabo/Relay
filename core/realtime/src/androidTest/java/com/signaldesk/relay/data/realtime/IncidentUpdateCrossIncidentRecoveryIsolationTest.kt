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
class IncidentUpdateCrossIncidentRecoveryIsolationTest {

    private lateinit var database:
        RelayDatabase

    private lateinit var processor:
        IncidentEventProcessor

    @Before
    fun setUp() {

        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        database =
            Room
                .inMemoryDatabaseBuilder(
                    context,
                    RelayDatabase::class.java
                )
                .allowMainThreadQueries()
                .build()

        processor =
            IncidentEventProcessor(
                database
            )
    }

    @After
    fun tearDown() {

        database.close()
    }

    @Test
    fun recoveringOneIncidentCannotMutateOrConsumeAnotherIncidentDurableChain() =
        runBlocking {

            /*
             * Incident A:
             * current=2, durable=4/5, missing=3.
             *
             * Supplying seq=3 should drain A completely.
             */
            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-ISO-A",
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

            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-ISO-A-4",
                        incidentId =
                            "INC-ISO-A",
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

            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-ISO-A-5",
                        incidentId =
                            "INC-ISO-A",
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
             *
             * It must remain completely blocked and untouched while A
             * recovers.
             */
            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-ISO-B",
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

            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-ISO-B-4",
                        incidentId =
                            "INC-ISO-B",
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

            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-ISO-B-5",
                        incidentId =
                            "INC-ISO-B",
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
             * Establish durable gaps for both incidents.
             */
            processor
                .recoverDeferredIncidentUpdates()

            val gapABefore =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-ISO-A"
                        )
                )

            val gapBBefore =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-ISO-B"
                        )
                )

            assertEquals(
                3L,
                gapABefore.expectedSequence
            )

            assertEquals(
                5L,
                gapABefore.receivedSequence
            )

            assertEquals(
                3L,
                gapBBefore.expectedSequence
            )

            assertEquals(
                5L,
                gapBBefore.receivedSequence
            )

            /*
             * Only A receives its missing predecessor.
             *
             * Public processing should recover A's durable successors
             * through its per-incident drain only.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-ISO-A-3",
                        incidentId =
                            "INC-ISO-A",
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
             * A converges through 4 and 5.
             */
            val incidentA =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-ISO-A"
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

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-ISO-A"
                    )
                    .size
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-ISO-A"
                    )
            )

            /*
             * B must be byte-for-byte logically unaffected by A's recovery.
             */
            val incidentB =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-ISO-B"
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
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-ISO-B"
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

            val gapBAfter =
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-ISO-B"
                    )

            assertNotNull(
                gapBAfter
            )

            assertEquals(
                3L,
                requireNotNull(
                    gapBAfter
                ).expectedSequence
            )

            assertEquals(
                5L,
                gapBAfter.receivedSequence
            )
        }
}
