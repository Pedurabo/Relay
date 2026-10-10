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
class DeferredIncidentUpdateOrderingTest {

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
    fun deferredUpdatesReplayInSequenceOrderRegardlessOfArrivalOrder() =
        runBlocking {

            /*
             * seq=3 arrives first.
             */
            assertEquals(
                EventProcessingResult.DEFERRED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-ORDER-3",
                        incidentId =
                            "INC-ORDER",
                        occurredAt =
                            3_000L,
                        title =
                            "Title from sequence three",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            3L
                    )
                )
            )

            /*
             * seq=2 arrives later.
             *
             * Durable ordering must not simply follow arrival/deferredAt.
             */
            assertEquals(
                EventProcessingResult.DEFERRED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-ORDER-2",
                        incidentId =
                            "INC-ORDER",
                        occurredAt =
                            2_000L,
                        title =
                            "Title from sequence two",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        sequence =
                            2L
                    )
                )
            )

            val durableBeforeParent =
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-ORDER"
                    )

            assertEquals(
                listOf(
                    2L,
                    3L
                ),
                durableBeforeParent.map {
                    it.sequence
                }
            )

            /*
             * Parent seq=1 should trigger the per-incident drain.
             *
             * Correct ordering:
             *
             *   seq=1 -> seq=2 -> seq=3
             *
             * If seq=3 were attempted first, it would manufacture a
             * temporary gap instead of simply converging in one drain.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-ORDER-1",
                        incidentId =
                            "INC-ORDER",
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
                )
            )

            val converged =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-ORDER"
                        )
                )

            assertEquals(
                3L,
                converged.latestSequence
            )

            assertEquals(
                "Title from sequence three",
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
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-ORDER"
                    )
                    .size
            )

            /*
             * Because all contiguous durable work was available, no
             * sequence gap should remain after the drain.
             */
            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-ORDER"
                    )
            )
        }
}
