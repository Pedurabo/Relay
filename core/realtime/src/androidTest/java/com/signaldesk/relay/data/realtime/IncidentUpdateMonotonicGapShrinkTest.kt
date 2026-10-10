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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateMonotonicGapShrinkTest {

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
    fun repeatedPartialReplayShrinksGapMonotonicallyUntilResolved() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-MONOTONIC-GAP",
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
             * Durable successor seq=5 establishes an initial gap 2..5.
             */
            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-GAP-5",
                        incidentId =
                            "INC-MONOTONIC-GAP",
                        occurredAt =
                            5_000L,
                        sequence =
                            5L,
                        title =
                            "Fifth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            100L
                    )
                )

            processor
                .recoverDeferredIncidentUpdates()

            assertGap(
                expected =
                    2L,
                received =
                    5L
            )

            applyUpdate(
                sequence =
                    2L,
                eventId =
                    "EVENT-GAP-2",
                title =
                    "Second"
            )

            assertGap(
                expected =
                    3L,
                received =
                    5L
            )

            applyUpdate(
                sequence =
                    3L,
                eventId =
                    "EVENT-GAP-3",
                title =
                    "Third"
            )

            assertGap(
                expected =
                    4L,
                received =
                    5L
            )

            /*
             * seq=4 closes the missing range.
             *
             * The public process hook should then wake durable seq=5,
             * leaving the incident fully converged and the gap removed.
             */
            applyUpdate(
                sequence =
                    4L,
                eventId =
                    "EVENT-GAP-4",
                title =
                    "Fourth"
            )

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-MONOTONIC-GAP"
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

            assertNull(
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-MONOTONIC-GAP"
                    )
            )

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-MONOTONIC-GAP"
                    )
                    .size
            )
        }

    private suspend fun applyUpdate(
        sequence: Long,
        eventId: String,
        title: String
    ) {

        assertEquals(
            EventProcessingResult.APPLIED,
            processor.process(
                IncidentUpdatedEvent(
                    eventId =
                        eventId,
                    incidentId =
                        "INC-MONOTONIC-GAP",
                    occurredAt =
                        sequence * 1_000L,
                    title =
                        title,
                    status =
                        "Investigating",
                    severity =
                        "MEDIUM",
                    sequence =
                        sequence
                )
            )
        )
    }

    private suspend fun assertGap(
        expected: Long,
        received: Long
    ) {

        val gap =
            requireNotNull(
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-MONOTONIC-GAP"
                    )
            )

        assertEquals(
            expected,
            gap.expectedSequence
        )

        assertEquals(
            received,
            gap.receivedSequence
        )
    }
}
