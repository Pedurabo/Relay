package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.remote.model.IncidentCreatedEvent
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateMultipleDurableGapRecoveryTest {

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
    fun multipleDurableFutureUpdatesRecoverStrictlyInSequenceOrder() =
        runBlocking {

            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-MULTI-GAP-CREATE",
                        incidentId =
                            "INC-MULTI-GAP",
                        occurredAt =
                            1_000L,
                        title =
                            "Created",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        sequence =
                            1L
                    )
                )
            )

            /*
             * Two future events arrive before seq=2 and seq=3.
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-MULTI-GAP-4",
                        incidentId =
                            "INC-MULTI-GAP",
                        occurredAt =
                            4_000L,
                        title =
                            "Fourth",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            4L
                    )
                )
            )

            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-MULTI-GAP-5",
                        incidentId =
                            "INC-MULTI-GAP",
                        occurredAt =
                            5_000L,
                        title =
                            "Fifth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            5L
                    )
                )
            )

            var deferred =
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-MULTI-GAP"
                    )

            assertEquals(
                2,
                deferred.size
            )

            assertEquals(
                4L,
                deferred[0].sequence
            )

            assertEquals(
                5L,
                deferred[1].sequence
            )

            /*
             * seq=2 advances only to 2.
             * seq=4 and seq=5 must remain blocked because seq=3 is absent.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-MULTI-GAP-2",
                        incidentId =
                            "INC-MULTI-GAP",
                        occurredAt =
                            2_000L,
                        title =
                            "Second",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        sequence =
                            2L
                    )
                )
            )

            var incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-MULTI-GAP"
                        )
                )

            assertEquals(
                2L,
                incident.latestSequence
            )

            deferred =
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-MULTI-GAP"
                    )

            assertEquals(
                2,
                deferred.size
            )

            /*
             * seq=3 closes the hole.
             *
             * The per-incident ordered drain should then apply durable
             * seq=4 followed by seq=5 in the same recovery flow.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-MULTI-GAP-3",
                        incidentId =
                            "INC-MULTI-GAP",
                        occurredAt =
                            3_000L,
                        title =
                            "Third",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            3L
                    )
                )
            )

            incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-MULTI-GAP"
                        )
                )

            assertEquals(
                5L,
                incident.latestSequence
            )

            assertEquals(
                "Fifth",
                incident.title
            )

            assertEquals(
                "Resolved",
                incident.status
            )

            assertEquals(
                "HIGH",
                incident.severity
            )

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-MULTI-GAP"
                    )
                    .size
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-MULTI-GAP"
                    )
            )

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-MULTI-GAP-4"
                    )
            )

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-MULTI-GAP-5"
                    )
            )
        }
}
