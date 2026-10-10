package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.DeferredIncidentUpdateEntity
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeferredIncidentUpdateConcurrentRecoveryTest {

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
    fun concurrentStartupAndLiveRecoveryConvergeExactlyOnce() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-CONCURRENT-RECOVERY",
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
                            "EVENT-CONCURRENT-3",
                        incidentId =
                            "INC-CONCURRENT-RECOVERY",
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

            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-CONCURRENT-2",
                        incidentId =
                            "INC-CONCURRENT-RECOVERY",
                        occurredAt =
                            2_000L,
                        sequence =
                            2L,
                        title =
                            "Second",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        deferredAt =
                            200L
                    )
                )

            /*
             * Race startup recovery against a live duplicate seq=2
             * delivery. Both can cause work on the same durable chain.
             *
             * Transactional processed-event dedupe must make the final
             * result equivalent to one ordered application of seq=2
             * followed by seq=3.
             */
            coroutineScope {

                listOf(
                    async {
                        processor
                            .recoverDeferredIncidentUpdates()
                    },
                    async {
                        processor.process(
                            IncidentUpdatedEvent(
                                eventId =
                                    "EVENT-CONCURRENT-2",
                                incidentId =
                                    "INC-CONCURRENT-RECOVERY",
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
                    }
                ).awaitAll()
            }

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-CONCURRENT-RECOVERY"
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
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-CONCURRENT-RECOVERY"
                    )
                    .size
            )

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-CONCURRENT-2"
                    )
            )

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-CONCURRENT-3"
                    )
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-CONCURRENT-RECOVERY"
                    )
            )
        }
}
