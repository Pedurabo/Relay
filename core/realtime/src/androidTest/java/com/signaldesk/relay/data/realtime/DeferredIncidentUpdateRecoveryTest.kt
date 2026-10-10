package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.remote.model.IncidentCreatedEvent
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeferredIncidentUpdateRecoveryTest {

    private lateinit var database:
        RelayDatabase

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
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deferredUpdateIsAppliedWhenParentArrives() =
        runBlocking {

            val processor =
                IncidentEventProcessor(
                    database
                )

            val deferredResult =
                processor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-UPDATE-2",
                        incidentId =
                            "INC-DEFERRED",
                        occurredAt =
                            2_000L,
                        title =
                            "Updated title",
                        status =
                            "Investigating",
                        severity =
                            "HIGH",
                        sequence =
                            2L
                    )
                )

            assertEquals(
                EventProcessingResult.DEFERRED,
                deferredResult
            )

            assertEquals(
                1,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-DEFERRED"
                    )
                    .size
            )

            processor.process(
                IncidentCreatedEvent(
                    eventId =
                        "EVENT-CREATE-1",
                    incidentId =
                        "INC-DEFERRED",
                    occurredAt =
                        1_000L,
                    title =
                        "Original title",
                    status =
                        "Active",
                    severity =
                        "MEDIUM",
                    sequence =
                        1L
                )
            )

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-DEFERRED"
                        )
                )

            assertEquals(
                "Updated title",
                incident.title
            )

            assertEquals(
                "Investigating",
                incident.status
            )

            assertEquals(
                "HIGH",
                incident.severity
            )

            assertEquals(
                2L,
                incident.latestSequence
            )

            assertTrue(
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-DEFERRED"
                    )
                    .isEmpty()
            )
        }

    @Test
    fun startupRecoveryAppliesPersistedUpdateAfterProcessRecreation() =
        runBlocking {

            val firstProcessor =
                IncidentEventProcessor(
                    database
                )

            firstProcessor.process(
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-RECOVERY-2",
                    incidentId =
                        "INC-RECOVERY",
                    occurredAt =
                        2_000L,
                    status =
                        "Resolved",
                    sequence =
                        2L
                )
            )

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-RECOVERY",
                        title =
                            "Existing incident",
                        status =
                            "Active",
                        latestSequence =
                            1L,
                        severity =
                            "MEDIUM"
                    )
                )

            /*
             * Represents a new processor instance after process death.
             */
            val recoveredProcessor =
                IncidentEventProcessor(
                    database
                )

            recoveredProcessor
                .recoverDeferredIncidentUpdates()

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-RECOVERY"
                        )
                )

            assertEquals(
                "Resolved",
                incident.status
            )

            assertEquals(
                2L,
                incident.latestSequence
            )

            assertTrue(
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-RECOVERY"
                    )
                    .isEmpty()
            )
        }
}
