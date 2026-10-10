package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateSameSequenceProcessDeathTest {

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
    fun conflictingSameSequenceAfterProcessRecreationIsTerminallyStale() =
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
                            "INC-PROCESS-DEATH",
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

            val firstProcessor =
                IncidentEventProcessor(
                    firstDatabase
                )

            val winner =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-WINNER",
                    incidentId =
                        "INC-PROCESS-DEATH",
                    occurredAt =
                        2_000L,
                    title =
                        "Winner title",
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
                    winner
                )
            )

            val beforeRestart =
                requireNotNull(
                    firstDatabase
                        .incidentDao()
                        .getById(
                            "INC-PROCESS-DEATH"
                        )
                )

            assertEquals(
                2L,
                beforeRestart.latestSequence
            )

            assertEquals(
                "Winner title",
                beforeRestart.title
            )

            assertTrue(
                firstDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-WINNER"
                    )
            )

            /*
             * Simulate process death / recreation.
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

            val conflicting =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-CONFLICT-AFTER-RESTART",
                    incidentId =
                        "INC-PROCESS-DEATH",
                    occurredAt =
                        2_100L,
                    title =
                        "Conflicting title",
                    status =
                        "Resolved",
                    severity =
                        "HIGH",
                    sequence =
                        2L
                )

            assertEquals(
                EventProcessingResult.IGNORED_STALE,
                recreatedProcessor.process(
                    conflicting
                )
            )

            val afterRestart =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-PROCESS-DEATH"
                        )
                )

            /*
             * Persisted sequence/state from the original process wins.
             */
            assertEquals(
                2L,
                afterRestart.latestSequence
            )

            assertEquals(
                "Winner title",
                afterRestart.title
            )

            assertEquals(
                "Investigating",
                afterRestart.status
            )

            assertEquals(
                "MEDIUM",
                afterRestart.severity
            )

            /*
             * The conflicting event is terminally accounted for,
             * but cannot create durable deferred work or a gap.
             */
            assertTrue(
                recreatedDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-CONFLICT-AFTER-RESTART"
                    )
            )

            assertEquals(
                0,
                recreatedDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-PROCESS-DEATH"
                    )
                    .size
            )

            assertEquals(
                null,
                recreatedDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-PROCESS-DEATH"
                    )
            )
        }

    companion object {

        private const val DATABASE_NAME =
            "incident-same-sequence-process-death.db"
    }
}
