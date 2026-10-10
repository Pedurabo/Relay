package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.DeferredIncidentUpdateEntity
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeferredIncidentUpdateSameSequenceConflictTest {

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
    fun onlyOneConflictingUpdateCanAdvanceTheSameSequence() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-SAME-SEQUENCE",
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
             * Two distinct authoritative event IDs claim seq=2 with
             * different payloads.
             *
             * Stable DAO ordering makes EVENT-A the first candidate.
             */
            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-A",
                        incidentId =
                            "INC-SAME-SEQUENCE",
                        occurredAt =
                            2_000L,
                        sequence =
                            2L,
                        title =
                            "Payload A",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        deferredAt =
                            100L
                    )
                )

            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-B",
                        incidentId =
                            "INC-SAME-SEQUENCE",
                        occurredAt =
                            2_100L,
                        sequence =
                            2L,
                        title =
                            "Payload B",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            200L
                    )
                )

            processor
                .recoverDeferredIncidentUpdates()

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-SAME-SEQUENCE"
                        )
                )

            /*
             * First seq=2 advances the incident.
             * The second seq=2 is then stale and must not overwrite it.
             */
            assertEquals(
                2L,
                incident.latestSequence
            )

            assertEquals(
                "Payload A",
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

            /*
             * Both events are terminal:
             *
             * EVENT-A was APPLIED.
             * EVENT-B became IGNORED_STALE after EVENT-A advanced seq=2.
             */
            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-A"
                    )
            )

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-B"
                    )
            )

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-SAME-SEQUENCE"
                    )
                    .size
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-SAME-SEQUENCE"
                    )
            )
        }
}
