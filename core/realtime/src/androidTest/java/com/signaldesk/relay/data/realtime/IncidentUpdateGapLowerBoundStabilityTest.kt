package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateGapLowerBoundStabilityTest {

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
    fun staleArrivalsCannotMoveAdvancedGapLowerBoundBackward() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-GAP-LOWER-BOUND",
                        title =
                            "Initial",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        latestSequence =
                            1L
                    )
                )

            /*
             * Observe seq=7 first -> gap 2..7.
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-LOWER-7",
                        incidentId =
                            "INC-GAP-LOWER-BOUND",
                        occurredAt =
                            7_000L,
                        title =
                            "Seventh",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            7L
                    )
                )
            )

            /*
             * Apply seq=2 and seq=3.
             * Gap should advance to 4..7.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-LOWER-2",
                        incidentId =
                            "INC-GAP-LOWER-BOUND",
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

            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-LOWER-3",
                        incidentId =
                            "INC-GAP-LOWER-BOUND",
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

            var gap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-GAP-LOWER-BOUND"
                        )
                )

            assertEquals(
                4L,
                gap.expectedSequence
            )

            assertEquals(
                7L,
                gap.receivedSequence
            )

            /*
             * A different event claiming already-applied seq=2 is stale.
             * It must not recreate gap 2..7.
             */
            assertEquals(
                EventProcessingResult.IGNORED_STALE,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-LOWER-STALE-2",
                        incidentId =
                            "INC-GAP-LOWER-BOUND",
                        occurredAt =
                            20_000L,
                        title =
                            "Stale second",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            2L
                    )
                )
            )

            gap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-GAP-LOWER-BOUND"
                        )
                )

            assertEquals(
                4L,
                gap.expectedSequence
            )

            assertEquals(
                7L,
                gap.receivedSequence
            )

            /*
             * Same for stale seq=3.
             */
            assertEquals(
                EventProcessingResult.IGNORED_STALE,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-LOWER-STALE-3",
                        incidentId =
                            "INC-GAP-LOWER-BOUND",
                        occurredAt =
                            30_000L,
                        title =
                            "Stale third",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            3L
                    )
                )
            )

            gap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-GAP-LOWER-BOUND"
                        )
                )

            assertEquals(
                4L,
                gap.expectedSequence
            )

            assertEquals(
                7L,
                gap.receivedSequence
            )

            /*
             * Current incident state also remains at seq=3.
             */
            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-GAP-LOWER-BOUND"
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
        }
}
