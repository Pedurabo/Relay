package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.remote.model.IncidentCreatedEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentCreatedExistingIncidentSequenceTest {

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
    fun sequencedCreateCannotAdvanceAlreadyExistingIncident() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-EXISTING-CREATE",
                        title =
                            "Current title",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        latestSequence =
                            5L
                    )
                )

            assertEquals(
                EventProcessingResult.IGNORED_STALE,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-LATE-CREATE-6",
                        incidentId =
                            "INC-EXISTING-CREATE",
                        occurredAt =
                            6_000L,
                        title =
                            "Replacement title",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            6L
                    )
                )
            )

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-EXISTING-CREATE"
                        )
                )

            assertEquals(
                5L,
                incident.latestSequence
            )

            assertEquals(
                "Current title",
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

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-LATE-CREATE-6"
                    )
            )

            /*
             * A late create must not manufacture a sequence gap either.
             */
            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-EXISTING-CREATE"
                    )
            )
        }

    @Test
    fun sequencedCreateMayCanonicalizeExistingPreSequenceIncident() =
        runBlocking {

            /*
             * latestSequence=0 is still pre-authoritative-sequence state.
             * A real create at sequence=1 must remain allowed.
             */
            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-PRE-SEQUENCE-CREATE",
                        title =
                            "Optimistic title",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        latestSequence =
                            0L
                    )
                )

            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-CREATE-1",
                        incidentId =
                            "INC-PRE-SEQUENCE-CREATE",
                        occurredAt =
                            1_000L,
                        title =
                            "Canonical title",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            1L
                    )
                )
            )

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-PRE-SEQUENCE-CREATE"
                        )
                )

            assertEquals(
                1L,
                incident.latestSequence
            )

            assertEquals(
                "Canonical title",
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
        }
}
