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
class IncidentUpdateStartupBlockedGapIdempotencyTest {

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
    fun repeatedStartupRecoveryDoesNotDistortUnchangedBlockedGap() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-STARTUP-IDEMPOTENT",
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

            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-STARTUP-IDEMPOTENT-5",
                        incidentId =
                            "INC-STARTUP-IDEMPOTENT",
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

            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-STARTUP-IDEMPOTENT-6",
                        incidentId =
                            "INC-STARTUP-IDEMPOTENT",
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

            /*
             * First startup recovery establishes gap 4..6.
             */
            processor
                .recoverDeferredIncidentUpdates()

            val firstGap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-STARTUP-IDEMPOTENT"
                        )
                )

            assertEquals(
                4L,
                firstGap.expectedSequence
            )

            assertEquals(
                6L,
                firstGap.receivedSequence
            )

            /*
             * Run startup recovery repeatedly with no state change.
             */
            repeat(3) {
                processor
                    .recoverDeferredIncidentUpdates()
            }

            val finalIncident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-STARTUP-IDEMPOTENT"
                        )
                )

            assertEquals(
                3L,
                finalIncident.latestSequence
            )

            assertEquals(
                "Third",
                finalIncident.title
            )

            val finalGap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-STARTUP-IDEMPOTENT"
                        )
                )

            assertEquals(
                4L,
                finalGap.expectedSequence
            )

            assertEquals(
                6L,
                finalGap.receivedSequence
            )

            val deferred =
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-STARTUP-IDEMPOTENT"
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
        }
}
