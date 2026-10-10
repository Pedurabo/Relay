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
class IncidentUpdateGapUpperBoundStabilityTest {

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
    fun nearerOutOfOrderEventCannotShrinkKnownGapUpperBound() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-GAP-UPPER-BOUND",
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
             * First observation establishes 2..7.
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-UPPER-7",
                        incidentId =
                            "INC-GAP-UPPER-BOUND",
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

            var gap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-GAP-UPPER-BOUND"
                        )
                )

            assertEquals(
                2L,
                gap.expectedSequence
            )

            assertEquals(
                7L,
                gap.receivedSequence
            )

            /*
             * A later arrival at seq=4 is still out of order, but it does
             * not reduce what we already know: seq=7 has been observed.
             *
             * The upper bound must therefore remain 7.
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-UPPER-4",
                        incidentId =
                            "INC-GAP-UPPER-BOUND",
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

            gap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-GAP-UPPER-BOUND"
                        )
                )

            assertEquals(
                2L,
                gap.expectedSequence
            )

            assertEquals(
                7L,
                gap.receivedSequence
            )

            /*
             * Neither out-of-order event may advance the incident.
             */
            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-GAP-UPPER-BOUND"
                        )
                )

            assertEquals(
                1L,
                incident.latestSequence
            )

            assertEquals(
                "Initial",
                incident.title
            )
        }
}
