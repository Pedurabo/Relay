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
class IncidentUpdateGapEventIdentityConflictTest {

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
    fun conflictingPayloadCannotRedefineSameGapEventIdentity() =
        runBlocking {

            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-GAP-ID-CREATE",
                        incidentId =
                            "INC-GAP-ID",
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

            val originalFuture =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-GAP-ID-3",
                    incidentId =
                        "INC-GAP-ID",
                    occurredAt =
                        3_000L,
                    title =
                        "Original third",
                    status =
                        "Investigating",
                    severity =
                        "MEDIUM",
                    sequence =
                        3L
                )

            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    originalFuture
                )
            )

            assertFalse(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-GAP-ID-3"
                    )
            )

            /*
             * Same event identity, conflicting payload.
             *
             * The first observed logical payload must remain authoritative
             * for this unresolved event identity.
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                processor.process(
                    originalFuture.copy(
                        occurredAt =
                            30_000L,
                        title =
                            "Conflicting third",
                        status =
                            "Resolved",
                        severity =
                            "HIGH"
                    )
                )
            )

            /*
             * Fill the predecessor.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-GAP-ID-2",
                        incidentId =
                            "INC-GAP-ID",
                        occurredAt =
                            2_000L,
                        title =
                            "Second",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        sequence =
                            2L
                    )
                )
            )

            /*
             * Applying seq=2 wakes and applies the durable original seq=3
             * automatically. Any later transport replay of that event ID
             * is therefore duplicate.
             */
            assertEquals(
                EventProcessingResult.DUPLICATE,
                processor.process(
                    originalFuture
                )
            )

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-GAP-ID"
                        )
                )

            assertEquals(
                3L,
                incident.latestSequence
            )

            assertEquals(
                "Original third",
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
