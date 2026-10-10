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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateRepeatedGapEventTest {

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
    fun repeatedUnresolvedGapEventRemainsNonTerminalAndDoesNotDistortGap() =
        runBlocking {

            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-REPEAT-GAP-CREATE",
                        incidentId =
                            "INC-REPEAT-GAP",
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
                        "EVENT-REPEAT-GAP-3",
                    incidentId =
                        "INC-REPEAT-GAP",
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

            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    future
                )
            )

            var gap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-REPEAT-GAP"
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

            assertFalse(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-REPEAT-GAP-3"
                    )
            )

            /*
             * The exact same unresolved event arrives again before seq=2.
             *
             * It is still non-terminal and must not change the replay
             * boundaries.
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    future
                )
            )

            gap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-REPEAT-GAP"
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

            assertFalse(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-REPEAT-GAP-3"
                    )
            )

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-REPEAT-GAP"
                        )
                )

            assertEquals(
                1L,
                incident.latestSequence
            )

            assertEquals(
                "Created",
                incident.title
            )
        }
}
