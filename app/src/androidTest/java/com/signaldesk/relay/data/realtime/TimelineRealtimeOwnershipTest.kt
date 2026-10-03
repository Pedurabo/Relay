package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.local.TimelineEntryEntity
import com.signaldesk.relay.data.remote.model.TimelineEntryAddedEvent
import com.signaldesk.relay.model.DeliveryState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineRealtimeOwnershipTest {

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
    fun realtimeConfirmationPreservesExistingOwner() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-OWNER-1",
                        title =
                            "Ownership race",
                        status =
                            "Active"
                    )
                )

            database
                .timelineEntryDao()
                .upsert(
                    TimelineEntryEntity(
                        entryId =
                            "ENTRY-OWNER-1",
                        incidentId =
                            "INC-OWNER-1",
                        message =
                            "Local pending update",
                        author =
                            "You",
                        occurredAt =
                            1_000L,
                        deliveryState =
                            DeliveryState.PENDING.name,
                        ownerPrincipal =
                            "operator-a"
                    )
                )

            processor.process(
                TimelineEntryAddedEvent(
                    eventId =
                        "EVT-OWNER-1",
                    incidentId =
                        "INC-OWNER-1",
                    occurredAt =
                        2_000L,
                    entryId =
                        "ENTRY-OWNER-1",
                    message =
                        "Local pending update",
                    author =
                        "Operator A"
                )
            )

            val stored =
                database
                    .timelineEntryDao()
                    .getById(
                        "ENTRY-OWNER-1"
                    )

            assertEquals(
                DeliveryState.SENT.name,
                stored?.deliveryState
            )

            assertEquals(
                "operator-a",
                stored?.ownerPrincipal
            )
        }


    @Test
    fun genuinelyRemoteTimelineEntryRemainsOwnerless() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-OWNER-2",
                        title =
                            "Remote event",
                        status =
                            "Active"
                    )
                )

            processor.process(
                TimelineEntryAddedEvent(
                    eventId =
                        "EVT-OWNER-2",
                    incidentId =
                        "INC-OWNER-2",
                    occurredAt =
                        3_000L,
                    entryId =
                        "ENTRY-REMOTE-1",
                    message =
                        "Remote timeline update",
                    author =
                        "Other operator"
                )
            )

            val stored =
                database
                    .timelineEntryDao()
                    .getById(
                        "ENTRY-REMOTE-1"
                    )

            assertEquals(
                DeliveryState.SENT.name,
                stored?.deliveryState
            )

            assertEquals(
                "",
                stored?.ownerPrincipal
            )
        }
}
