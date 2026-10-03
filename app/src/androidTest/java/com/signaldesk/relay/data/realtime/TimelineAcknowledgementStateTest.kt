package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.local.TimelineEntryEntity
import com.signaldesk.relay.model.DeliveryState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineAcknowledgementStateTest {

    private lateinit var database:
        RelayDatabase


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
    }


    @After
    fun tearDown() {

        database.close()
    }


    @Test
    fun pendingOwnedEntryCanBeAcknowledged() =
        runBlocking {

            database
                .timelineEntryDao()
                .upsert(
                    TimelineEntryEntity(
                        entryId =
                            "ENTRY-ACK-1",
                        incidentId =
                            "INC-ACK-1",
                        message =
                            "Pending update",
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

            val changed =
                database
                    .timelineEntryDao()
                    .acknowledgePending(
                        entryId =
                            "ENTRY-ACK-1",
                        ownerPrincipal =
                            "operator-a",
                        incidentId =
                            "INC-ACK-1",
                        message =
                            "Pending update",
                        author =
                            "Operator A",
                        occurredAt =
                            2_000L
                    )

            val stored =
                database
                    .timelineEntryDao()
                    .getById(
                        "ENTRY-ACK-1"
                    )

            assertEquals(
                1,
                changed
            )

            assertEquals(
                DeliveryState.SENT.name,
                stored?.deliveryState
            )

            assertEquals(
                "operator-a",
                stored?.ownerPrincipal
            )

            assertEquals(
                "Operator A",
                stored?.author
            )

            assertEquals(
                2_000L,
                stored?.occurredAt
            )
        }


    @Test
    fun lateAcknowledgementCannotReviveFailedEntry() =
        runBlocking {

            database
                .timelineEntryDao()
                .upsert(
                    TimelineEntryEntity(
                        entryId =
                            "ENTRY-ACK-2",
                        incidentId =
                            "INC-ACK-2",
                        message =
                            "Failed update",
                        author =
                            "You",
                        occurredAt =
                            3_000L,
                        deliveryState =
                            DeliveryState.FAILED.name,
                        ownerPrincipal =
                            "operator-a"
                    )
                )

            val changed =
                database
                    .timelineEntryDao()
                    .acknowledgePending(
                        entryId =
                            "ENTRY-ACK-2",
                        ownerPrincipal =
                            "operator-a",
                        incidentId =
                            "INC-ACK-2",
                        message =
                            "Failed update",
                        author =
                            "Operator A",
                        occurredAt =
                            4_000L
                    )

            val stored =
                database
                    .timelineEntryDao()
                    .getById(
                        "ENTRY-ACK-2"
                    )

            assertEquals(
                0,
                changed
            )

            assertEquals(
                DeliveryState.FAILED.name,
                stored?.deliveryState
            )

            assertEquals(
                "You",
                stored?.author
            )

            assertEquals(
                3_000L,
                stored?.occurredAt
            )
        }


    @Test
    fun acknowledgementCannotCrossOwnerBoundary() =
        runBlocking {

            database
                .timelineEntryDao()
                .upsert(
                    TimelineEntryEntity(
                        entryId =
                            "ENTRY-ACK-3",
                        incidentId =
                            "INC-ACK-3",
                        message =
                            "Pending update",
                        author =
                            "You",
                        occurredAt =
                            5_000L,
                        deliveryState =
                            DeliveryState.PENDING.name,
                        ownerPrincipal =
                            "operator-a"
                    )
                )

            val changed =
                database
                    .timelineEntryDao()
                    .acknowledgePending(
                        entryId =
                            "ENTRY-ACK-3",
                        ownerPrincipal =
                            "operator-b",
                        incidentId =
                            "INC-ACK-3",
                        message =
                            "Pending update",
                        author =
                            "Operator B",
                        occurredAt =
                            6_000L
                    )

            val stored =
                database
                    .timelineEntryDao()
                    .getById(
                        "ENTRY-ACK-3"
                    )

            assertEquals(
                0,
                changed
            )

            assertEquals(
                DeliveryState.PENDING.name,
                stored?.deliveryState
            )

            assertEquals(
                "operator-a",
                stored?.ownerPrincipal
            )
        }
}