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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateGapProcessDeathRecoveryTest {

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
    fun persistedGapAndDeferredSuccessorConvergeAfterProcessRecreation() =
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

            firstDatabase
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-GAP-RESTART",
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

            /*
             * Persist seq=3 as durable deferred work.
             */
            firstDatabase
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-GAP-3",
                        incidentId =
                            "INC-GAP-RESTART",
                        occurredAt =
                            3_000L,
                        sequence =
                            3L,
                        title =
                            "Third",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            100L
                    )
                )

            val firstProcessor =
                IncidentEventProcessor(
                    firstDatabase
                )

            /*
             * Startup-style recovery sees seq=3 while current=1.
             * It must persist the seq=2..3 gap and leave seq=3 durable.
             */
            firstProcessor
                .recoverDeferredIncidentUpdates()

            val persistedGap =
                firstDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-GAP-RESTART"
                    )

            assertNotNull(
                persistedGap
            )

            assertEquals(
                2L,
                requireNotNull(
                    persistedGap
                ).expectedSequence
            )

            assertEquals(
                3L,
                persistedGap.receivedSequence
            )

            assertEquals(
                1,
                firstDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-GAP-RESTART"
                    )
                    .size
            )

            /*
             * Simulate process death after the gap and deferred successor
             * are both durably stored.
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
             * Replay supplies the missing seq=2 after process recreation.
             *
             * Applying seq=2 must:
             *   1. advance the incident,
             *   2. update/clear the persisted gap,
             *   3. wake durable seq=3,
             *   4. converge to latestSequence=3,
             *   5. leave no residual deferred row or gap.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                recreatedProcessor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-GAP-2",
                        incidentId =
                            "INC-GAP-RESTART",
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

            val converged =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-GAP-RESTART"
                        )
                )

            assertEquals(
                3L,
                converged.latestSequence
            )

            assertEquals(
                "Third",
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
                        "INC-GAP-RESTART"
                    )
                    .size
            )

            assertNull(
                recreatedDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-GAP-RESTART"
                    )
            )
        }

    companion object {

        private const val DATABASE_NAME =
            "incident-gap-process-death-recovery.db"
    }
}
