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
class IncidentUpdateDurableGapStaleCleanupTest {

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
    fun durableGapEventThatBecomesStaleIsTerminallyCleaned() =
        runBlocking {

            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-DURABLE-STALE-CREATE",
                        incidentId =
                            "INC-DURABLE-STALE",
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
             * seq=3 arrives early and is durably retained.
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-DURABLE-STALE-3",
                        incidentId =
                            "INC-DURABLE-STALE",
                        occurredAt =
                            3_000L,
                        title =
                            "Stored third",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            3L
                    )
                )
            )

            assertEquals(
                1,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-DURABLE-STALE"
                    )
                    .size
            )

            /*
             * Another authoritative path advances the incident past seq=3.
             *
             * Apply seq=2, then a distinct seq=3, then seq=4.
             * The originally stored seq=3 must become stale during recovery
             * and be terminally cleaned instead of lingering.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-DURABLE-STALE-2",
                        incidentId =
                            "INC-DURABLE-STALE",
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

            /*
             * At this point the durable original seq=3 may already have
             * applied automatically. If so, this competing seq=3 is stale.
             * Otherwise, Room serialization still permits exactly one seq=3
             * winner.
             */
            val competingThreeResult =
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-DURABLE-STALE-3-COMPETING",
                        incidentId =
                            "INC-DURABLE-STALE",
                        occurredAt =
                            3_500L,
                        title =
                            "Competing third",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            3L
                    )
                )

            assertTrue(
                competingThreeResult ==
                    EventProcessingResult.APPLIED ||
                    competingThreeResult ==
                    EventProcessingResult.IGNORED_STALE
            )

            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-DURABLE-STALE-4",
                        incidentId =
                            "INC-DURABLE-STALE",
                        occurredAt =
                            4_000L,
                        title =
                            "Fourth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            4L
                    )
                )
            )

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-DURABLE-STALE"
                        )
                )

            assertEquals(
                4L,
                incident.latestSequence
            )

            assertEquals(
                "Fourth",
                incident.title
            )

            /*
             * No unresolved durable work may remain.
             */
            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-DURABLE-STALE"
                    )
                    .size
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-DURABLE-STALE"
                    )
            )

            /*
             * The original durable gap event must now be terminally
             * accounted for, whether it applied first or became stale.
             */
            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-DURABLE-STALE-3"
                    )
            )
        }
}
