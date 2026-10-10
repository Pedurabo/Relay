package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.DeferredIncidentUpdateEntity
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdatePartialGapRecoveryTest {

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
    fun partialReplayShrinksPersistedGapWithoutClearingItPrematurely() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-PARTIAL-GAP",
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

            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-PARTIAL-4",
                        incidentId =
                            "INC-PARTIAL-GAP",
                        occurredAt =
                            4_000L,
                        sequence =
                            4L,
                        title =
                            "Fourth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            100L
                    )
                )

            /*
             * Recovery sees seq=4 while current=1 and records 2..4.
             */
            processor
                .recoverDeferredIncidentUpdates()

            val initialGap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-PARTIAL-GAP"
                        )
                )

            assertEquals(
                2L,
                initialGap.expectedSequence
            )

            assertEquals(
                4L,
                initialGap.receivedSequence
            )

            /*
             * Only seq=2 arrives.
             *
             * The gap must shrink to 3..4, not disappear.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-PARTIAL-2",
                        incidentId =
                            "INC-PARTIAL-GAP",
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

            val afterSecond =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-PARTIAL-GAP"
                        )
                )

            assertEquals(
                2L,
                afterSecond.latestSequence
            )

            val remainingGap =
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-PARTIAL-GAP"
                    )

            assertNotNull(
                remainingGap
            )

            assertEquals(
                3L,
                requireNotNull(
                    remainingGap
                ).expectedSequence
            )

            assertEquals(
                4L,
                remainingGap.receivedSequence
            )

            /*
             * seq=4 remains durable because seq=3 is still missing.
             */
            assertEquals(
                1,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-PARTIAL-GAP"
                    )
                    .size
            )
        }
}
