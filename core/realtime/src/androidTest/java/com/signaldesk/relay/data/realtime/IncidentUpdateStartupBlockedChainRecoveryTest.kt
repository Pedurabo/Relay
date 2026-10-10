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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentUpdateStartupBlockedChainRecoveryTest {

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
    fun startupRecoveryStopsAtFirstMissingSequenceAndPreservesLaterDurableWork() =
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
             * Persisted state before process death:
             *
             * current = seq3
             * durable = seq5, seq6
             * missing = seq4
             */
            firstDatabase
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-STARTUP-BLOCKED",
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
                            "EVENT-STARTUP-BLOCKED-5",
                        incidentId =
                            "INC-STARTUP-BLOCKED",
                        occurredAt =
                            5_000L,
                        sequence =
                            5L,
                        title =
                            "Fifth",
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
                            "EVENT-STARTUP-BLOCKED-6",
                        incidentId =
                            "INC-STARTUP-BLOCKED",
                        occurredAt =
                            6_000L,
                        sequence =
                            6L,
                        title =
                            "Sixth",
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

            recreatedProcessor
                .recoverDeferredIncidentUpdates()

            /*
             * Startup must not leapfrog the missing seq4.
             */
            val incident =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-STARTUP-BLOCKED"
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

            /*
             * Both future events remain durable.
             */
            val deferred =
                recreatedDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-STARTUP-BLOCKED"
                    )

            assertEquals(
                2,
                deferred.size
            )

            assertEquals(
                5L,
                deferred[0].sequence
            )

            assertEquals(
                6L,
                deferred[1].sequence
            )

            /*
             * Recovery must retain the missing range through the furthest
             * known successor.
             */
            val gap =
                requireNotNull(
                    recreatedDatabase
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-STARTUP-BLOCKED"
                        )
                )

            assertEquals(
                4L,
                gap.expectedSequence
            )

            assertEquals(
                6L,
                gap.receivedSequence
            )
        }

    companion object {

        private const val DATABASE_NAME =
            "incident-startup-blocked-chain.db"
    }
}
