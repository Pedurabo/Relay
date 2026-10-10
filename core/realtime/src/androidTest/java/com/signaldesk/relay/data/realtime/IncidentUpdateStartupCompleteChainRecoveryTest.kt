package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.DeferredIncidentUpdateEntity
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateStartupCompleteChainRecoveryTest {

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
    fun startupRecoveryDrainsFullyContiguousDurableChainWithoutLiveEvent() =
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

            /*
             * Simulate process state at shutdown:
             *
             * incident is already at seq=3
             * seq=4 and seq=5 are durable unresolved successors
             *
             * No additional network event should be required after restart.
             */
            firstDatabase
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-STARTUP-COMPLETE",
                        title =
                            "Third",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        latestSequence =
                            3L
                    )
                )

            firstDatabase
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-STARTUP-COMPLETE-4",
                        incidentId =
                            "INC-STARTUP-COMPLETE",
                        occurredAt =
                            4_000L,
                        sequence =
                            4L,
                        title =
                            "Fourth",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        deferredAt =
                            100L
                    )
                )

            firstDatabase
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-STARTUP-COMPLETE-5",
                        incidentId =
                            "INC-STARTUP-COMPLETE",
                        occurredAt =
                            5_000L,
                        sequence =
                            5L,
                        title =
                            "Fifth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            200L
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
             * Startup recovery itself must discover the incident and drain
             * seq=4 followed by seq=5.
             */
            recreatedProcessor
                .recoverDeferredIncidentUpdates()

            val incident =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-STARTUP-COMPLETE"
                        )
                )

            assertEquals(
                5L,
                incident.latestSequence
            )

            assertEquals(
                "Fifth",
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
                0,
                recreatedDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-STARTUP-COMPLETE"
                    )
                    .size
            )

            assertEquals(
                null,
                recreatedDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-STARTUP-COMPLETE"
                    )
            )

            assertTrue(
                recreatedDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-STARTUP-COMPLETE-4"
                    )
            )

            assertTrue(
                recreatedDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-STARTUP-COMPLETE-5"
                    )
            )
        }

    companion object {

        private const val DATABASE_NAME =
            "incident-startup-complete-chain.db"
    }
}
