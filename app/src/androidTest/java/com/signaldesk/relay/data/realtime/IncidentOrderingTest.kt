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
class IncidentOrderingTest {

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
    fun staleUpdateCannotOverwriteNewerState() =
        runBlocking {

            processor.process(
                IncidentCreatedEvent(
                    eventId =
                        "EVT-ORDER-100",
                    incidentId =
                        "INC-ORDER",
                    occurredAt =
                        100L,
                    title =
                        "Ordering incident",
                    status =
                        "Active",
                    sequence =
                        100L
                )
            )

            // Correct next event.
            processor.process(
                IncidentUpdatedEvent(
                    eventId =
                        "EVT-ORDER-101",
                    incidentId =
                        "INC-ORDER",
                    occurredAt =
                        101L,
                    title = null,
                    status =
                        "Monitoring",
                    sequence =
                        101L
                )
            )

            val after101 =
                database
                    .incidentDao()
                    .getById(
                        "INC-ORDER"
                    )

            assertEquals(
                "Monitoring",
                after101?.status
            )

            assertEquals(
                101L,
                after101?.latestSequence
            )

            // Different eventId, but an older server
            // sequence arrives late.
            processor.process(
                IncidentUpdatedEvent(
                    eventId =
                        "EVT-ORDER-LATE-100",
                    incidentId =
                        "INC-ORDER",
                    occurredAt =
                        100L,
                    title = null,
                    status =
                        "Investigating",
                    sequence =
                        100L
                )
            )

            val afterStale =
                database
                    .incidentDao()
                    .getById(
                        "INC-ORDER"
                    )

            // The stale event must not overwrite #101.
            assertEquals(
                "Monitoring",
                afterStale?.status
            )

            assertEquals(
                101L,
                afterStale?.latestSequence
            )

            // Stale delivery should not create a gap.
            val gap =
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-ORDER"
                    )

            assertNull(gap)

            // Correct next event must still apply.
            processor.process(
                IncidentUpdatedEvent(
                    eventId =
                        "EVT-ORDER-102",
                    incidentId =
                        "INC-ORDER",
                    occurredAt =
                        102L,
                    title = null,
                    status =
                        "Resolved",
                    sequence =
                        102L
                )
            )

            val finalState =
                database
                    .incidentDao()
                    .getById(
                        "INC-ORDER"
                    )

            assertEquals(
                "Resolved",
                finalState?.status
            )

            assertEquals(
                102L,
                finalState?.latestSequence
            )
        }
}
