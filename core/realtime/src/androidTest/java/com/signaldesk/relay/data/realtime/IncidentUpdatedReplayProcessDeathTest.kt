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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdatedReplayProcessDeathTest {

    private lateinit var context:
        Context

    private var database:
        RelayDatabase? =
            null

    @Before
    fun setUp() {

        context =
            ApplicationProvider
                .getApplicationContext()

        context.deleteDatabase(
            DATABASE_NAME
        )
    }

    @After
    fun tearDown() {

        database
            ?.close()

        context.deleteDatabase(
            DATABASE_NAME
        )
    }

    @Test
    fun appliedUpdateReplayRemainsDuplicateAfterRestartAndCannotMutateState() =
        runBlocking {

            database =
                Room
                    .databaseBuilder(
                        context,
                        RelayDatabase::class.java,
                        DATABASE_NAME
                    )
                    .allowMainThreadQueries()
                    .build()

            val firstDatabase =
                requireNotNull(
                    database
                )

            val firstProcessor =
                IncidentEventProcessor(
                    firstDatabase
                )

            assertEquals(
                EventProcessingResult.APPLIED,
                firstProcessor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-UPDATE-REPLAY-CREATE",
                        incidentId =
                            "INC-UPDATE-REPLAY",
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

            val update =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-UPDATE-REPLAY-2",
                    incidentId =
                        "INC-UPDATE-REPLAY",
                    occurredAt =
                        2_000L,
                    title =
                        "Canonical update",
                    status =
                        "Investigating",
                    severity =
                        "MEDIUM",
                    sequence =
                        2L
                )

            assertEquals(
                EventProcessingResult.APPLIED,
                firstProcessor.process(
                    update
                )
            )

            /*
             * Advance once more so the replay is both event-id duplicate
             * and older than current incident state.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                firstProcessor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-UPDATE-REPLAY-3",
                        incidentId =
                            "INC-UPDATE-REPLAY",
                        occurredAt =
                            3_000L,
                        title =
                            "Newest state",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            3L
                    )
                )
            )

            firstDatabase.close()

            database =
                Room
                    .databaseBuilder(
                        context,
                        RelayDatabase::class.java,
                        DATABASE_NAME
                    )
                    .allowMainThreadQueries()
                    .build()

            val recreatedDatabase =
                requireNotNull(
                    database
                )

            val recreatedProcessor =
                IncidentEventProcessor(
                    recreatedDatabase
                )

            /*
             * Same eventId with hostile/conflicting payload must stop at
             * durable processed-event dedupe before update semantics run.
             */
            assertEquals(
                EventProcessingResult.DUPLICATE,
                recreatedProcessor.process(
                    update.copy(
                        title =
                            "Conflicting replay",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        occurredAt =
                            99_000L
                    )
                )
            )

            val incident =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-UPDATE-REPLAY"
                        )
                )

            assertEquals(
                3L,
                incident.latestSequence
            )

            assertEquals(
                "Newest state",
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
                recreatedDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-UPDATE-REPLAY-2"
                    )
            )

            assertTrue(
                recreatedDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-UPDATE-REPLAY-3"
                    )
            )

            assertEquals(
                null,
                recreatedDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-UPDATE-REPLAY"
                    )
            )
        }

    companion object {

        private const val DATABASE_NAME =
            "incident-update-replay-process-death.db"
    }
}
