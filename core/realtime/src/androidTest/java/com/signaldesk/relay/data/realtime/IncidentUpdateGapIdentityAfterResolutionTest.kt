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
class IncidentUpdateGapIdentityAfterResolutionTest {

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
    fun firstObservedGapPayloadWinsAndAppliesWhenPredecessorArrives() =
        runBlocking {

            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-GAP-FIRST-CREATE",
                        incidentId =
                            "INC-GAP-FIRST",
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

            val original =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-GAP-FIRST-3",
                    incidentId =
                        "INC-GAP-FIRST",
                    occurredAt =
                        3_000L,
                    title =
                        "Original third",
                    status =
                        "Investigating",
                    severity =
                        "MEDIUM",
                    sequence =
                        3L
                )

            val conflicting =
                original.copy(
                    occurredAt =
                        30_000L,
                    title =
                        "Conflicting third",
                    status =
                        "Resolved",
                    severity =
                        "HIGH"
                )

            /*
             * First observation establishes both:
             *   - persistent gap 2..3
             *   - durable unresolved event payload for eventId
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    original
                )
            )

            var deferred =
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-GAP-FIRST"
                    )

            assertEquals(
                1,
                deferred.size
            )

            assertEquals(
                "Original third",
                deferred.single().title
            )

            assertEquals(
                "Investigating",
                deferred.single().status
            )

            assertEquals(
                "MEDIUM",
                deferred.single().severity
            )

            /*
             * Conflicting representation using the same eventId cannot
             * replace the first durable payload because insertion is IGNORE
             * on the eventId primary key.
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    conflicting
                )
            )

            deferred =
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-GAP-FIRST"
                    )

            assertEquals(
                1,
                deferred.size
            )

            assertEquals(
                "Original third",
                deferred.single().title
            )

            assertEquals(
                "Investigating",
                deferred.single().status
            )

            assertEquals(
                "MEDIUM",
                deferred.single().severity
            )

            /*
             * Applying seq=2 triggers the normal per-incident deferred drain.
             * Durable seq=3 should therefore apply automatically using the
             * FIRST observed payload.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-GAP-FIRST-2",
                        incidentId =
                            "INC-GAP-FIRST",
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

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-GAP-FIRST"
                        )
                )

            assertEquals(
                3L,
                incident.latestSequence
            )

            assertEquals(
                "Original third",
                incident.title
            )

            assertEquals(
                "Investigating",
                incident.status
            )

            assertEquals(
                "MEDIUM",
                incident.severity
            )

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-GAP-FIRST"
                    )
                    .size
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-GAP-FIRST"
                    )
            )

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-GAP-FIRST-3"
                    )
            )

            /*
             * Once the durable original has applied, either representation
             * of the same eventId is now a pure duplicate.
             */
            assertEquals(
                EventProcessingResult.DUPLICATE,
                processor.process(
                    conflicting
                )
            )
        }
}
