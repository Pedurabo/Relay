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
class IncidentUpdateMultipleDurableGapProcessDeathTest {

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
    fun multipleDurableFutureUpdatesDrainInOrderAfterProcessRecreation() =
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
                            "EVENT-MULTI-RESTART-CREATE",
                        incidentId =
                            "INC-MULTI-RESTART",
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

            /*
             * seq=4 and seq=5 both arrive early and become durable.
             */
            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                firstProcessor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-MULTI-RESTART-4",
                        incidentId =
                            "INC-MULTI-RESTART",
                        occurredAt =
                            4_000L,
                        title =
                            "Fourth",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            4L
                    )
                )
            )

            assertEquals(
                EventProcessingResult.GAP_DETECTED,
                firstProcessor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-MULTI-RESTART-5",
                        incidentId =
                            "INC-MULTI-RESTART",
                        occurredAt =
                            5_000L,
                        title =
                            "Fifth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            5L
                    )
                )
            )

            /*
             * seq=2 arrives, but seq=3 is still missing.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                firstProcessor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-MULTI-RESTART-2",
                        incidentId =
                            "INC-MULTI-RESTART",
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

            val beforeRestart =
                requireNotNull(
                    firstDatabase
                        .incidentDao()
                        .getById(
                            "INC-MULTI-RESTART"
                        )
                )

            assertEquals(
                2L,
                beforeRestart.latestSequence
            )

            val deferredBeforeRestart =
                firstDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-MULTI-RESTART"
                    )

            assertEquals(
                2,
                deferredBeforeRestart.size
            )

            assertEquals(
                4L,
                deferredBeforeRestart[0].sequence
            )

            assertEquals(
                5L,
                deferredBeforeRestart[1].sequence
            )

            val gapBeforeRestart =
                requireNotNull(
                    firstDatabase
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-MULTI-RESTART"
                        )
                )

            assertEquals(
                3L,
                gapBeforeRestart.expectedSequence
            )

            assertEquals(
                5L,
                gapBeforeRestart.receivedSequence
            )

            /*
             * Simulate process death with both successors still durable.
             */
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
             * seq=3 arrives after restart.
             *
             * Fresh recovery must consume seq=4 and then seq=5 in order.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                recreatedProcessor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-MULTI-RESTART-3",
                        incidentId =
                            "INC-MULTI-RESTART",
                        occurredAt =
                            3_000L,
                        title =
                            "Third",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            3L
                    )
                )
            )

            val converged =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-MULTI-RESTART"
                        )
                )

            assertEquals(
                5L,
                converged.latestSequence
            )

            assertEquals(
                "Fifth",
                converged.title
            )

            assertEquals(
                "Resolved",
                converged.status
            )

            assertEquals(
                "HIGH",
                converged.severity
            )

            assertEquals(
                0,
                recreatedDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-MULTI-RESTART"
                    )
                    .size
            )

            assertEquals(
                null,
                recreatedDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-MULTI-RESTART"
                    )
            )

            assertTrue(
                recreatedDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-MULTI-RESTART-4"
                    )
            )

            assertTrue(
                recreatedDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-MULTI-RESTART-5"
                    )
            )
        }

    companion object {

        private const val DATABASE_NAME =
            "incident-multiple-durable-gap-process-death.db"
    }
}
