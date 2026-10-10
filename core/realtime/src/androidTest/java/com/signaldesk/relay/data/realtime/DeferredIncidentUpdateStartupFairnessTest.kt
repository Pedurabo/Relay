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
class DeferredIncidentUpdateStartupFairnessTest {

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
    fun gapInOneIncidentDoesNotBlockReadyDeferredWorkForAnotherIncident() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-BLOCKED",
                        title =
                            "Blocked initial",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        latestSequence =
                            1L
                    )
                )

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-READY",
                        title =
                            "Ready initial",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        latestSequence =
                            1L
                    )
                )

            /*
             * INC-BLOCKED has only seq=3.
             * Startup recovery must detect the missing seq=2 gap and
             * leave this row durable.
             */
            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-BLOCKED-3",
                        incidentId =
                            "INC-BLOCKED",
                        occurredAt =
                            3_000L,
                        sequence =
                            3L,
                        title =
                            "Blocked third",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            100L
                    )
                )

            /*
             * INC-READY has contiguous seq=2 and must still be applied
             * in this same startup recovery pass.
             */
            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-READY-2",
                        incidentId =
                            "INC-READY",
                        occurredAt =
                            2_000L,
                        sequence =
                            2L,
                        title =
                            "Ready second",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        deferredAt =
                            200L
                    )
                )

            processor
                .recoverDeferredIncidentUpdates()

            val blocked =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-BLOCKED"
                        )
                )

            val ready =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-READY"
                        )
                )

            /*
             * Blocked incident remains at seq=1.
             */
            assertEquals(
                1L,
                blocked.latestSequence
            )

            assertEquals(
                "Blocked initial",
                blocked.title
            )

            assertEquals(
                1,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-BLOCKED"
                    )
                    .size
            )

            val gap =
                requireNotNull(
                    database
                        .incidentSequenceGapDao()
                        .getByIncidentId(
                            "INC-BLOCKED"
                        )
                )

            assertEquals(
                2L,
                gap.expectedSequence
            )

            assertEquals(
                3L,
                gap.receivedSequence
            )

            /*
             * Ready incident must still converge independently.
             */
            assertEquals(
                2L,
                ready.latestSequence
            )

            assertEquals(
                "Ready second",
                ready.title
            )

            assertEquals(
                "Investigating",
                ready.status
            )

            assertEquals(
                "MEDIUM",
                ready.severity
            )

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-READY"
                    )
                    .size
            )

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-READY-2"
                    )
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-READY"
                    )
            )
        }
}
