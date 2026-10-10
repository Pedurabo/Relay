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
class DeferredIncidentUpdateGapRecoveryTest {

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
    fun deferredUpdateIsAutomaticallyAppliedAfterMissingSequenceClosesGap() =
        runBlocking {

            /*
             * seq=3 arrives before the parent exists.
             * It must become durable deferred work.
             */
            assertEquals(
                EventProcessingResult.DEFERRED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-SEQ-3",
                        incidentId =
                            "INC-GAP-DEFERRED",
                        occurredAt =
                            3_000L,
                        title =
                            "Sequence three title",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            3L
                    )
                )
            )

            assertEquals(
                1,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-GAP-DEFERRED"
                    )
                    .size
            )

            /*
             * Parent seq=1 arrives.
             *
             * Parent-triggered recovery tries seq=3, correctly detects
             * the missing seq=2 gap, and leaves seq=3 durable.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-SEQ-1",
                        incidentId =
                            "INC-GAP-DEFERRED",
                        occurredAt =
                            1_000L,
                        title =
                            "Original title",
                        status =
                            "Active",
                        severity =
                            "MEDIUM",
                        sequence =
                            1L
                    )
                )
            )

            val afterParent =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-GAP-DEFERRED"
                        )
                )

            assertEquals(
                1L,
                afterParent.latestSequence
            )

            assertEquals(
                "Active",
                afterParent.status
            )

            assertEquals(
                1,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-GAP-DEFERRED"
                    )
                    .size
            )

            /*
             * Replay supplies the missing seq=2.
             *
             * Applying seq=2 must automatically wake the durable seq=3
             * update. No restart, reconnect, or second create event is
             * allowed to be necessary.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-SEQ-2",
                        incidentId =
                            "INC-GAP-DEFERRED",
                        occurredAt =
                            2_000L,
                        title =
                            "Sequence two title",
                        status =
                            "Investigating",
                        severity =
                            null,
                        sequence =
                            2L
                    )
                )
            )

            val converged =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-GAP-DEFERRED"
                        )
                )

            assertEquals(
                3L,
                converged.latestSequence
            )

            assertEquals(
                "Sequence three title",
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
                        "INC-GAP-DEFERRED"
                    )
                    .size
            )
        }
}
