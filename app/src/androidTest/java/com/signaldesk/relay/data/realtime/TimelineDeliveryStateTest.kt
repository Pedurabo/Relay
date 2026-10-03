package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.repository.IncidentRepository
import com.signaldesk.relay.data.remote.model.TimelineEntryAddedEvent
import com.signaldesk.relay.model.DeliveryState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineDeliveryStateTest {

    private lateinit var database: RelayDatabase
    private lateinit var repository: IncidentRepository

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

        repository =
            IncidentRepository(
                incidentDao =
                    database.incidentDao(),
                timelineEntryDao =
                    database.timelineEntryDao()
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pendingEntry_becomesSentAfterAck() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id = "INC-600",
                        title = "Delivery test",
                        status = "Active"
                    )
                )

            val pending =
                repository
                    .createPendingTimelineEntry(
                        incidentId =
                            "INC-600",
                        message =
                            "Investigating database latency",
                        author =
                            "You",
                        ownerPrincipal =
                            "operator-a"
                    )

            val pendingStored =
                database
                    .timelineEntryDao()
                    .getById(
                        pending.id
                    )

            assertEquals(
                DeliveryState.PENDING.name,
                pendingStored
                    ?.deliveryState
            )

            repository
                .confirmTimelineEntry(
                    TimelineEntryAddedEvent(
                        eventId =
                            "EVT-ACK-600",
                        incidentId =
                            "INC-600",
                        occurredAt =
                            12345L,
                        entryId =
                            pending.id,
                        message =
                            pending.message,
                        author =
                            pending.author
                    )
                )

            val confirmed =
                database
                    .timelineEntryDao()
                    .getById(
                        pending.id
                    )

            assertEquals(
                DeliveryState.SENT.name,
                confirmed
                    ?.deliveryState
            )
        }
}
