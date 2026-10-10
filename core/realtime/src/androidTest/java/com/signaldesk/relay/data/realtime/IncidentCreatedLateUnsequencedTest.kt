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
class IncidentCreatedLateUnsequencedTest {

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
    fun lateUnsequencedCreateCannotOverwriteAdvancedIncident() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-LATE-CREATE",
                        title =
                            "Newest title",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        latestSequence =
                            5L
                    )
                )

            assertEquals(
                EventProcessingResult.IGNORED_STALE,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-LATE-CREATE",
                        incidentId =
                            "INC-LATE-CREATE",
                        occurredAt =
                            1_000L,
                        title =
                            "Old create title",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        sequence =
                            0L
                    )
                )
            )

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-LATE-CREATE"
                        )
                )

            assertEquals(
                5L,
                incident.latestSequence
            )

            assertEquals(
                "Newest title",
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

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-LATE-CREATE"
                    )
            )
        }

    @Test
    fun unsequencedCreateMayCanonicalizeExistingPreSequenceIncident() =
        runBlocking {

            /*
             * Existing sequence=0 represents a row that has not yet
             * entered authoritative sequenced state.
             */
            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-PRE-SEQUENCE",
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
                            "EVENT-CANONICAL-CREATE",
                        incidentId =
                            "INC-PRE-SEQUENCE",
                        occurredAt =
                            2_000L,
                        title =
                            "Canonical title",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            0L
                    )
                )
            )

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-PRE-SEQUENCE"
                        )
                )

            assertEquals(
                0L,
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
