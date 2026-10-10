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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateGapReplayProcessDeathTest {

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
    fun gapDetectedEventRemainsReconsiderableAfterProcessRecreation() =
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
                            "EVENT-GAP-RESTART-CREATE",
                        incidentId =
                            "INC-GAP-RESTART-REPLAY",
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

            val future =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-GAP-RESTART-3",
                    incidentId =
                        "INC-GAP-RESTART-REPLAY",
                    occurredAt =
                        3_000L,
                    title =
                        "Third",
                    status =
                        "Resolved",
                    severity =
                        "HIGH",
                    sequence =
                        3L
                )

            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                firstProcessor.process(
                    future
                )
            )

            /*
             * Non-terminal gap events must not enter processed_events.
             */
            assertFalse(
                firstDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-GAP-RESTART-3"
                    )
            )

            val beforeRestartGap =
                requireNotNull(
                    firstDatabase
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-GAP-RESTART-REPLAY"
                        )
                )

            assertEquals(
                2L,
                beforeRestartGap.expectedSequence
            )

            assertEquals(
                3L,
                beforeRestartGap.receivedSequence
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
             * The predecessor arrives in the new process.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                recreatedProcessor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-GAP-RESTART-2",
                        incidentId =
                            "INC-GAP-RESTART-REPLAY",
                        occurredAt =
                            2_000L,
                        title =
                            "Second",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            2L
                    )
                )
            )

            /*
             * Applying seq=2 in the recreated process automatically wakes
             * the durable seq=3 successor.
             */
            assertTrue(
                recreatedDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-GAP-RESTART-3"
                    )
            )

            /*
             * Any later transport replay is therefore duplicate.
             */
            assertEquals(
                EventProcessingResult.DUPLICATE,
                recreatedProcessor.process(
                    future
                )
            )

            val incident =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-GAP-RESTART-REPLAY"
                        )
                )

            assertEquals(
                3L,
                incident.latestSequence
            )

            assertEquals(
                "Third",
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
                        "INC-GAP-RESTART-REPLAY"
                    )
            )
        }

    companion object {

        private const val DATABASE_NAME =
            "incident-gap-replay-process-death.db"
    }
}
