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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeferredIncidentUpdateIdentityConflictTest {

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
    fun conflictingDuplicateEventIdCannotReplaceFirstDeferredPayload() =
        runBlocking {

            val original =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-CONFLICT",
                    incidentId =
                        "INC-CONFLICT-A",
                    occurredAt =
                        2_000L,
                    title =
                        "Original deferred title",
                    status =
                        "Investigating",
                    severity =
                        "MEDIUM",
                    sequence =
                        2L
                )

            val conflicting =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-CONFLICT",
                    incidentId =
                        "INC-CONFLICT-B",
                    occurredAt =
                        9_000L,
                    title =
                        "Conflicting title",
                    status =
                        "Resolved",
                    severity =
                        "HIGH",
                    sequence =
                        7L
                )

            assertEquals(
                EventProcessingResult.DEFERRED,
                processor.process(
                    original
                )
            )

            /*
             * Same eventId, different identity/payload.
             *
             * The durable first observation must remain authoritative for
             * this eventId while it is deferred. The later conflicting
             * delivery must not overwrite it.
             */
            assertEquals(
                EventProcessingResult.DEFERRED,
                processor.process(
                    conflicting
                )
            )

            val originalRows =
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-CONFLICT-A"
                    )

            assertEquals(
                1,
                originalRows.size
            )

            val stored =
                originalRows.single()

            assertEquals(
                "EVENT-CONFLICT",
                stored.eventId
            )

            assertEquals(
                "INC-CONFLICT-A",
                stored.incidentId
            )

            assertEquals(
                2L,
                stored.sequence
            )

            assertEquals(
                2_000L,
                stored.occurredAt
            )

            assertEquals(
                "Original deferred title",
                stored.title
            )

            assertEquals(
                "Investigating",
                stored.status
            )

            assertEquals(
                "MEDIUM",
                stored.severity
            )

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-CONFLICT-B"
                    )
                    .size
            )

            /*
             * Materialize the original parent. Recovery must apply the
             * first persisted payload, not the conflicting redelivery.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-CREATE-A",
                        incidentId =
                            "INC-CONFLICT-A",
                        occurredAt =
                            1_000L,
                        title =
                            "Initial",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        sequence =
                            1L
                    )
                )
            )

            val converged =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-CONFLICT-A"
                        )
                )

            assertEquals(
                2L,
                converged.latestSequence
            )

            assertEquals(
                "Original deferred title",
                converged.title
            )

            assertEquals(
                "Investigating",
                converged.status
            )

            assertEquals(
                "MEDIUM",
                converged.severity
            )

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-CONFLICT-A"
                    )
                    .size
            )
        }
}
