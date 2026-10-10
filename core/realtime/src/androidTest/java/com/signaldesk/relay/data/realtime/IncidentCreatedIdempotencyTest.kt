package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class IncidentCreatedIdempotencyTest {

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
    fun originalCreateReplayIsDuplicateButDifferentCreateEventIsStale() =
        runBlocking {

            val original =
                IncidentCreatedEvent(
                    eventId =
                        "EVENT-CREATE-ORIGINAL",
                    incidentId =
                        "INC-CREATE-IDEMPOTENCY",
                    occurredAt =
                        1_000L,
                    title =
                        "Original title",
                    status =
                        "Active",
                    severity =
                        "LOW",
                    sequence =
                        1L
                )

            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    original
                )
            )

            /*
             * Same eventId is transport replay: DUPLICATE.
             */
            assertEquals(
                EventProcessingResult.DUPLICATE,
                processor.process(
                    original.copy(
                        title =
                            "Conflicting replay payload",
                        status =
                            "Resolved",
                        severity =
                            "HIGH"
                    )
                )
            )

            var incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-CREATE-IDEMPOTENCY"
                        )
                )

            assertEquals(
                1L,
                incident.latestSequence
            )

            assertEquals(
                "Original title",
                incident.title
            )

            assertEquals(
                "Active",
                incident.status
            )

            assertEquals(
                "LOW",
                incident.severity
            )

            /*
             * Different eventId claiming another creation for the same
             * established incident is semantic stale, not DUPLICATE.
             */
            assertEquals(
                EventProcessingResult.IGNORED_STALE,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-CREATE-CONFLICT",
                        incidentId =
                            "INC-CREATE-IDEMPOTENCY",
                        occurredAt =
                            2_000L,
                        title =
                            "Second creation",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            2L
                    )
                )
            )

            incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-CREATE-IDEMPOTENCY"
                        )
                )

            assertEquals(
                1L,
                incident.latestSequence
            )

            assertEquals(
                "Original title",
                incident.title
            )

            assertEquals(
                "Active",
                incident.status
            )

            assertEquals(
                "LOW",
                incident.severity
            )

            /*
             * Both IDs are terminally accounted for:
             * original via APPLIED, conflict via IGNORED_STALE.
             */
            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-CREATE-ORIGINAL"
                    )
            )

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-CREATE-CONFLICT"
                    )
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-CREATE-IDEMPOTENCY"
                    )
            )
        }
}
