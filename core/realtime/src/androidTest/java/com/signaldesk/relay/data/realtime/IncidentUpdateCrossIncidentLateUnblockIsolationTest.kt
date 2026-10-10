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
class IncidentUpdateCrossIncidentLateUnblockIsolationTest {

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
    fun unblockingOneIncidentLaterCannotDisturbAlreadyConvergedNeighbor() =
        runBlocking {

            /*
             * Incident A is already converged at seq=5.
             */
            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-LATE-ISO-A",
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
             * Incident B is blocked:
             * current=2, durable=4/5, missing=3.
             */
            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-LATE-ISO-B",
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
                            "EVENT-LATE-ISO-B-4",
                        incidentId =
                            "INC-LATE-ISO-B",
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

            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-LATE-ISO-B-5",
                        incidentId =
                            "INC-LATE-ISO-B",
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

            processor
                .recoverDeferredIncidentUpdates()

            val gapB =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-LATE-ISO-B"
                        )
                )

            assertEquals(
                3L,
                gapB.expectedSequence
            )

            assertEquals(
                5L,
                gapB.receivedSequence
            )

            /*
             * B finally receives seq=3.
             * Its own durable chain should drain to seq=5.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-LATE-ISO-B-3",
                        incidentId =
                            "INC-LATE-ISO-B",
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
                    database
                        .incidentDao()
                        .getById(
                            "INC-LATE-ISO-B"
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
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-LATE-ISO-B"
                    )
                    .size
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-LATE-ISO-B"
                    )
            )

            /*
             * A must remain exactly as it was.
             */
            val incidentA =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-LATE-ISO-A"
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
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-LATE-ISO-A"
                    )
                    .isEmpty()
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-LATE-ISO-A"
                    )
            )
        }
}
