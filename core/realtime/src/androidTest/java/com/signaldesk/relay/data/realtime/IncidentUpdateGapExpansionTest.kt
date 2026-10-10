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
class IncidentUpdateGapExpansionTest {

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
    fun fartherAheadEventExpandsExistingGapWithoutLosingLowerBound() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-GAP-EXPANSION",
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
             * seq=5 creates the first known gap: 2..5.
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-GAP-5",
                        incidentId =
                            "INC-GAP-EXPANSION",
                        occurredAt =
                            5_000L,
                        title =
                            "Fifth",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            5L
                    )
                )
            )

            var gap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-GAP-EXPANSION"
                        )
                )

            assertEquals(
                2L,
                gap.expectedSequence
            )

            assertEquals(
                5L,
                gap.receivedSequence
            )

            /*
             * A farther-ahead seq=7 arrives while 2..5 is still missing.
             *
             * The lower bound must stay at 2.
             * The upper bound must expand to 7.
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-GAP-7",
                        incidentId =
                            "INC-GAP-EXPANSION",
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

            gap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-GAP-EXPANSION"
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
             * Neither out-of-order event may mutate the incident yet.
             */
            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-GAP-EXPANSION"
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
