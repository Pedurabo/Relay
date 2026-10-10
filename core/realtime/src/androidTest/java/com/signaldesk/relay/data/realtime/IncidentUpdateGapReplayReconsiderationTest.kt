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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateGapReplayReconsiderationTest {

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
    fun gapDetectedEventIsNotMarkedProcessedAndCanApplyLater() =
        runBlocking {

            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-GAP-RECONSIDER-CREATE",
                        incidentId =
                            "INC-GAP-RECONSIDER",
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

            val future =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-GAP-RECONSIDER-3",
                    incidentId =
                        "INC-GAP-RECONSIDER",
                    occurredAt =
                        3_000L,
                    title =
                        "Third",
                    status =
                        "Resolved",
                    severity =
                        "HIGH",
                    sequence =
                        3L
                )

            /*
             * seq=3 arrives while seq=2 is missing.
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    future
                )
            )

            /*
             * GAP_DETECTED is non-terminal: its event ID must not be
             * inserted into processed_events yet.
             */
            assertFalse(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-GAP-RECONSIDER-3"
                    )
            )

            val gap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-GAP-RECONSIDER"
                        )
                )

            assertEquals(
                2L,
                gap.expectedSequence
            )

            assertEquals(
                3L,
                gap.receivedSequence
            )

            /*
             * Replay supplies the predecessor.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-GAP-RECONSIDER-2",
                        incidentId =
                            "INC-GAP-RECONSIDER",
                        occurredAt =
                            2_000L,
                        title =
                            "Second",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            2L
                    )
                )
            )

            /*
             * Applying seq=2 wakes the durable seq=3 automatically.
             *
             * The original gap event is therefore terminally processed
             * before any explicit replay is required.
             */
            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-GAP-RECONSIDER-3"
                    )
            )

            /*
             * A later network replay of that same event ID is now a pure
             * duplicate.
             */
            assertEquals(
                EventProcessingResult.DUPLICATE,
                processor.process(
                    future
                )
            )

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-GAP-RECONSIDER"
                        )
                )

            assertEquals(
                3L,
                incident.latestSequence
            )

            assertEquals(
                "Third",
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
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-GAP-RECONSIDER"
                    )
            )
        }
}
