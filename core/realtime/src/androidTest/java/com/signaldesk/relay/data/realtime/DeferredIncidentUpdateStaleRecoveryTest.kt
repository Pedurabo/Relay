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
class DeferredIncidentUpdateStaleRecoveryTest {

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
    fun staleDeferredUpdateIsTerminallyCleanedWithoutRegressingNewerIncident() =
        runBlocking {

            /*
             * Current authoritative incident has already advanced to seq=3.
             */
            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-STALE-DEFERRED",
                        title =
                            "Newest title",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        latestSequence =
                            3L
                    )
                )

            /*
             * Simulate a durable deferred update left behind from an
             * earlier ordering/process-death window.
             *
             * This event is now stale because its sequence is 2.
             */
            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-STALE-2",
                        incidentId =
                            "INC-STALE-DEFERRED",
                        occurredAt =
                            2_000L,
                        sequence =
                            2L,
                        title =
                            "Older title",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        deferredAt =
                            3_000L
                    )
                )

            assertEquals(
                1,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-STALE-DEFERRED"
                    )
                    .size
            )

            processor
                .recoverDeferredIncidentUpdates()

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-STALE-DEFERRED"
                        )
                )

            /*
             * Stale recovery must never roll state backward.
             */
            assertEquals(
                3L,
                incident.latestSequence
            )

            assertEquals(
                "Newest title",
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

            /*
             * IGNORED_STALE is terminal:
             * the durable deferred row must be removed.
             */
            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-STALE-DEFERRED"
                    )
                    .size
            )

            /*
             * Terminal stale processing must also participate in normal
             * event-id dedupe so the same old event cannot resurrect.
             */
            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-STALE-2"
                    )
            )
        }
}
