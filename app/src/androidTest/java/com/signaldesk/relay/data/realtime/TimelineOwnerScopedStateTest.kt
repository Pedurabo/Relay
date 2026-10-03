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
class TimelineOwnerScopedStateTest {

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
    fun wrongOwnerCannotChangeTimelineDeliveryState() =
        runBlocking {

            database
                .timelineEntryDao()
                .upsert(
                    TimelineEntryEntity(
                        entryId =
                            "ENTRY-OWNER-SCOPE-1",
                        incidentId =
                            "INC-OWNER-SCOPE-1",
                        message =
                            "Owner scoped",
                        author =
                            "You",
                        occurredAt =
                            1_000L,
                        deliveryState =
                            DeliveryState.FAILED.name,
                        ownerPrincipal =
                            "operator-a"
                    )
                )

            val changed =
                database
                    .timelineEntryDao()
                    .updateDeliveryState(
                        entryId =
                            "ENTRY-OWNER-SCOPE-1",
                        ownerPrincipal =
                            "operator-b",
                        deliveryState =
                            DeliveryState.PENDING.name
                    )

            val stored =
                database
                    .timelineEntryDao()
                    .getById(
                        "ENTRY-OWNER-SCOPE-1"
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
                "operator-a",
                stored?.ownerPrincipal
            )
        }


    @Test
    fun matchingOwnerCanChangeTimelineDeliveryState() =
        runBlocking {

            database
                .timelineEntryDao()
                .upsert(
                    TimelineEntryEntity(
                        entryId =
                            "ENTRY-OWNER-SCOPE-2",
                        incidentId =
                            "INC-OWNER-SCOPE-2",
                        message =
                            "Owner scoped",
                        author =
                            "You",
                        occurredAt =
                            2_000L,
                        deliveryState =
                            DeliveryState.FAILED.name,
                        ownerPrincipal =
                            "operator-a"
                    )
                )

            val changed =
                database
                    .timelineEntryDao()
                    .updateDeliveryState(
                        entryId =
                            "ENTRY-OWNER-SCOPE-2",
                        ownerPrincipal =
                            "operator-a",
                        deliveryState =
                            DeliveryState.PENDING.name
                    )

            val stored =
                database
                    .timelineEntryDao()
                    .getById(
                        "ENTRY-OWNER-SCOPE-2"
                    )

            assertEquals(
                1,
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
