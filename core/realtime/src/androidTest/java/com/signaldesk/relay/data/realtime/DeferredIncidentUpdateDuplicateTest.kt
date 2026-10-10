package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class DeferredIncidentUpdateDuplicateTest {

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
    fun duplicateDeferredEventIdCannotCreateDuplicateWorkOrResurrectAfterApply() =
        runBlocking {

            val deferredEvent =
                IncidentUpdatedEvent(
                    eventId =
                        "EVENT-DUPLICATE-2",
                    incidentId =
                        "INC-DUPLICATE",
                    occurredAt =
                        2_000L,
                    title =
                        "Updated title",
                    status =
                        "Resolved",
                    severity =
                        "HIGH",
                    sequence =
                        2L
                )

            /*
             * First delivery arrives before the parent and becomes
             * durable deferred work.
             */
            assertEquals(
                EventProcessingResult.DEFERRED,
                processor.process(
                    deferredEvent
                )
            )

            assertEquals(
                1,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-DUPLICATE"
                    )
                    .size
            )

            /*
             * Exact duplicate delivery before the parent exists must
             * remain one physical deferred row because eventId is the PK
             * and insertion uses IGNORE semantics.
             */
            assertEquals(
                EventProcessingResult.DEFERRED,
                processor.process(
                    deferredEvent
                )
            )

            assertEquals(
                1,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-DUPLICATE"
                    )
                    .size
            )

            /*
             * Parent seq=1 arrives and drains the deferred seq=2.
             */
            assertEquals(
                EventProcessingResult.APPLIED,
                processor.process(
                    IncidentCreatedEvent(
                        eventId =
                            "EVENT-DUPLICATE-1",
                        incidentId =
                            "INC-DUPLICATE",
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
            )

            val converged =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-DUPLICATE"
                        )
                )

            assertEquals(
                2L,
                converged.latestSequence
            )

            assertEquals(
                "Updated title",
                converged.title
            )

            assertEquals(
                "Resolved",
                converged.status
            )

            assertEquals(
                "HIGH",
                converged.severity
            )

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-DUPLICATE"
                    )
                    .size
            )

            assertTrue(
                database
                    .processedEventDao()
                    .exists(
                        "EVENT-DUPLICATE-2"
                    )
            )

            /*
             * The same event arriving again after terminal processing
             * must be a pure duplicate. It must not recreate the deferred
             * row or mutate the incident again.
             */
            assertEquals(
                EventProcessingResult.DUPLICATE,
                processor.process(
                    deferredEvent
                )
            )

            val afterDuplicate =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-DUPLICATE"
                        )
                )

            assertEquals(
                2L,
                afterDuplicate.latestSequence
            )

            assertEquals(
                "Updated title",
                afterDuplicate.title
            )

            assertEquals(
                "Resolved",
                afterDuplicate.status
            )

            assertEquals(
                "HIGH",
                afterDuplicate.severity
            )

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-DUPLICATE"
                    )
                    .size
            )
        }
}
