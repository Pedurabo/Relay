package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.remote.model.IncidentCreatedEvent
import com.signaldesk.relay.data.remote.model.TimelineEntryAddedEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineEventProcessorTest {

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
    fun timelineEvent_isPersistedOnce() =
        runBlocking {

            processor.process(
                IncidentCreatedEvent(
                    eventId = "EVT-CREATE-500",
                    incidentId = "INC-500",
                    occurredAt = 1L,
                    title = "Timeline test",
                    status = "Active"
                )
            )

            val timelineEvent =
                TimelineEntryAddedEvent(
                    eventId = "EVT-TIMELINE-001",
                    incidentId = "INC-500",
                    occurredAt = 2L,
                    entryId = "ENTRY-001",
                    message =
                        "Engineer acknowledged incident",
                    author = "Alex"
                )

            processor.process(
                timelineEvent
            )

            // Replay the exact same event.
            processor.process(
                timelineEvent
            )

            val stored =
                database
                    .timelineEntryDao()
                    .getById("ENTRY-001")

            assertEquals(
                "Engineer acknowledged incident",
                stored?.message
            )

            assertEquals(
                "Alex",
                stored?.author
            )
        }
}
