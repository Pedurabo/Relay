package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class IncidentUpdateSameSequenceConcurrentTest {

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
    fun concurrentSameSequenceUpdatesConvergeToSingleWinner() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-CONCURRENT-SAME-SEQUENCE",
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

            val eventA =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-CONCURRENT-A",
                    incidentId =
                        "INC-CONCURRENT-SAME-SEQUENCE",
                    occurredAt =
                        2_000L,
                    title =
                        "Payload A",
                    status =
                        "Investigating",
                    severity =
                        "MEDIUM",
                    sequence =
                        2L
                )

            val eventB =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-CONCURRENT-B",
                    incidentId =
                        "INC-CONCURRENT-SAME-SEQUENCE",
                    occurredAt =
                        2_100L,
                    title =
                        "Payload B",
                    status =
                        "Resolved",
                    severity =
                        "HIGH",
                    sequence =
                        2L
                )

            val results =
                coroutineScope {

                    listOf(
                        async {
                            processor.process(
                                eventA
                            )
                        },
                        async {
                            processor.process(
                                eventB
                            )
                        }
                    ).awaitAll()
                }

            /*
             * Exactly one event should advance seq=2.
             * The other must observe the already-advanced state and
             * terminate stale.
             */
            assertEquals(
                1,
                results.count {
                    it == EventProcessingResult.APPLIED
                }
            )

            assertEquals(
                1,
                results.count {
                    it == EventProcessingResult.IGNORED_STALE
                }
            )

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-CONCURRENT-SAME-SEQUENCE"
                        )
                )

            assertEquals(
                2L,
                incident.latestSequence
            )

            /*
             * Either payload may win because this is a true race.
             * The invariant is that the persisted payload is internally
             * consistent with one complete event, never a mixture.
             */
            val isPayloadA =
                incident.title == "Payload A" &&
                    incident.status == "Investigating" &&
                    incident.severity == "MEDIUM"

            val isPayloadB =
                incident.title == "Payload B" &&
                    incident.status == "Resolved" &&
                    incident.severity == "HIGH"

            assertTrue(
                isPayloadA || isPayloadB
            )

            /*
             * Both event IDs are terminally accounted for:
             * one APPLIED, one IGNORED_STALE.
             */
            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-CONCURRENT-A"
                    )
            )

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-CONCURRENT-B"
                    )
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-CONCURRENT-SAME-SEQUENCE"
                    )
            )

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-CONCURRENT-SAME-SEQUENCE"
                    )
                    .size
            )
        }
}
