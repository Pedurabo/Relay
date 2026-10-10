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
class IncidentUpdateStartupMultiChainFairnessTest {

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
    fun blockedIncidentCannotPreventAnotherDurableChainFromDraining() =
        runBlocking {

            /*
             * Incident A:
             * current=3, durable=5/6, missing=4.
             *
             * It must remain blocked.
             */
            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-FAIR-BLOCKED",
                        title =
                            "Blocked third",
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
                            "EVENT-FAIR-BLOCKED-5",
                        incidentId =
                            "INC-FAIR-BLOCKED",
                        occurredAt =
                            5_000L,
                        sequence =
                            5L,
                        title =
                            "Blocked fifth",
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
                            "EVENT-FAIR-BLOCKED-6",
                        incidentId =
                            "INC-FAIR-BLOCKED",
                        occurredAt =
                            6_000L,
                        sequence =
                            6L,
                        title =
                            "Blocked sixth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            200L
                    )
                )

            /*
             * Incident B:
             * current=3, durable=4/5.
             *
             * Its chain is fully contiguous and must drain even though
             * Incident A is blocked.
             */
            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-FAIR-READY",
                        title =
                            "Ready third",
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
                            "EVENT-FAIR-READY-4",
                        incidentId =
                            "INC-FAIR-READY",
                        occurredAt =
                            4_000L,
                        sequence =
                            4L,
                        title =
                            "Ready fourth",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        deferredAt =
                            300L
                    )
                )

            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-FAIR-READY-5",
                        incidentId =
                            "INC-FAIR-READY",
                        occurredAt =
                            5_000L,
                        sequence =
                            5L,
                        title =
                            "Ready fifth",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            400L
                    )
                )

            processor
                .recoverDeferredIncidentUpdates()

            /*
             * Blocked incident remains at seq=3 with both successors.
             */
            val blocked =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-FAIR-BLOCKED"
                        )
                )

            assertEquals(
                3L,
                blocked.latestSequence
            )

            assertEquals(
                2,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-FAIR-BLOCKED"
                    )
                    .size
            )

            val blockedGap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-FAIR-BLOCKED"
                        )
                )

            assertEquals(
                4L,
                blockedGap.expectedSequence
            )

            assertEquals(
                6L,
                blockedGap.receivedSequence
            )

            /*
             * Ready incident drains completely in the same startup pass.
             */
            val ready =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-FAIR-READY"
                        )
                )

            assertEquals(
                5L,
                ready.latestSequence
            )

            assertEquals(
                "Ready fifth",
                ready.title
            )

            assertEquals(
                "Resolved",
                ready.status
            )

            assertEquals(
                "HIGH",
                ready.severity
            )

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-FAIR-READY"
                    )
                    .size
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-FAIR-READY"
                    )
            )

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-FAIR-READY-4"
                    )
            )

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-FAIR-READY-5"
                    )
            )
        }
}
