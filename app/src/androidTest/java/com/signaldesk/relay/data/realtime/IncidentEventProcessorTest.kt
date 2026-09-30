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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentEventProcessorTest {

    private lateinit var database: RelayDatabase
    private lateinit var processor: IncidentEventProcessor

    @Before
    fun setUp() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        database =
            Room.inMemoryDatabaseBuilder(
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
    fun createdEvent_insertsIncident() =
        runBlocking {
            processor.process(
                IncidentCreatedEvent(
                    eventId = "EVT-001",
                    incidentId = "INC-100",
                    occurredAt = 1L,
                    title = "Database outage",
                    status = "Active"
                )
            )

            val stored =
                database
                    .incidentDao()
                    .getById("INC-100")

            assertEquals(
                "Database outage",
                stored?.title
            )

            assertEquals(
                "Active",
                stored?.status
            )
        }

    @Test
    fun updatedEvent_changesExistingIncident() =
        runBlocking {
            processor.process(
                IncidentCreatedEvent(
                    eventId = "EVT-001",
                    incidentId = "INC-200",
                    occurredAt = 1L,
                    title = "Payment delays",
                    status = "Investigating"
                )
            )

            processor.process(
                IncidentUpdatedEvent(
                    eventId = "EVT-002",
                    incidentId = "INC-200",
                    occurredAt = 2L,
                    title = "Payment processor delays",
                    status = "Monitoring"
                )
            )

            val stored =
                database
                    .incidentDao()
                    .getById("INC-200")

            assertEquals(
                "Payment processor delays",
                stored?.title
            )

            assertEquals(
                "Monitoring",
                stored?.status
            )
        }

    @Test
    fun replayedEvent_isIgnored() =
        runBlocking {
            processor.process(
                IncidentCreatedEvent(
                    eventId = "EVT-DUPLICATE",
                    incidentId = "INC-300",
                    occurredAt = 1L,
                    title = "Original title",
                    status = "Active"
                )
            )

            // Same eventId with different payload simulates
            // a replay that must NOT be applied again.
            processor.process(
                IncidentCreatedEvent(
                    eventId = "EVT-DUPLICATE",
                    incidentId = "INC-300",
                    occurredAt = 2L,
                    title = "Replay should not win",
                    status = "Monitoring"
                )
            )

            val stored =
                database
                    .incidentDao()
                    .getById("INC-300")

            assertEquals(
                "Original title",
                stored?.title
            )

            assertEquals(
                "Active",
                stored?.status
            )
        }

    @Test
    fun updateBeforeCreate_canBeReplayedLater() =
        runBlocking {
            val earlyUpdate =
                IncidentUpdatedEvent(
                    eventId = "EVT-UPDATE",
                    incidentId = "INC-400",
                    occurredAt = 1L,
                    title = null,
                    status = "Monitoring"
                )

            // Update arrives before the incident.
            processor.process(earlyUpdate)

            assertNull(
                database
                    .incidentDao()
                    .getById("INC-400")
            )

            processor.process(
                IncidentCreatedEvent(
                    eventId = "EVT-CREATE",
                    incidentId = "INC-400",
                    occurredAt = 2L,
                    title = "Out of order incident",
                    status = "Active"
                )
            )

            // Server replays the previously unapplied update.
            processor.process(earlyUpdate)

            val stored =
                database
                    .incidentDao()
                    .getById("INC-400")

            assertEquals(
                "Out of order incident",
                stored?.title
            )

            assertEquals(
                "Monitoring",
                stored?.status
            )
        }
}
