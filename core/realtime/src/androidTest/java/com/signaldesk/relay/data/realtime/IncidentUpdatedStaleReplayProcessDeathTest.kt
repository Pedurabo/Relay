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
class IncidentUpdatedStaleReplayProcessDeathTest {

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
    fun staleUpdateReplayBecomesDurableDuplicateAfterRestart() =
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
                            "EVENT-STALE-CREATE",
                        incidentId =
                            "INC-STALE-REPLAY",
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

            assertEquals(
                EventProcessingResult.APPLIED,
                firstProcessor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-STALE-3",
                        incidentId =
                            "INC-STALE-REPLAY",
                        occurredAt =
                            3_000L,
                        title =
                            "Newest",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            2L
                    )
                )
            )

            val stale =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-STALE-OLD",
                    incidentId =
                        "INC-STALE-REPLAY",
                    occurredAt =
                        9_000L,
                    title =
                        "Stale payload",
                    status =
                        "Active",
                    severity =
                        "LOW",
                    sequence =
                        1L
                )

            assertEquals(
                EventProcessingResult.IGNORED_STALE,
                firstProcessor.process(
                    stale
                )
            )

            assertTrue(
                firstDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-STALE-OLD"
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
             * Terminal stale identity is durable.
             * Replay must stop at processed-event dedupe.
             */
            assertEquals(
                EventProcessingResult.DUPLICATE,
                recreatedProcessor.process(
                    stale.copy(
                        title =
                            "Conflicting stale replay",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
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
                            "INC-STALE-REPLAY"
                        )
                )

            assertEquals(
                2L,
                incident.latestSequence
            )

            assertEquals(
                "Newest",
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

            assertEquals(
                null,
                recreatedDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-STALE-REPLAY"
                    )
            )
        }

    companion object {

        private const val DATABASE_NAME =
            "incident-stale-update-replay-process-death.db"
    }
}
