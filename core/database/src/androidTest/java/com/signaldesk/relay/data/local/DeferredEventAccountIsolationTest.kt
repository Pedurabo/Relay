package com.signaldesk.relay.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeferredEventAccountIsolationTest {

    private lateinit var database: RelayDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RelayDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun accountAtoBtoAKeepsDeferredEventsIndependent() = runBlocking {
        val realtime = database.deferredRealtimeEventDao()
        val updates = database.deferredIncidentUpdateDao()

        for (owner in listOf("", "account-a", "account-b")) {
            realtime.insert(
                DeferredRealtimeEventEntity(
                    eventId = "EVT-SHARED",
                    incidentId = "INC-SHARED",
                    entryId = "ENTRY-SHARED",
                    message = "$owner timeline",
                    author = "Tester",
                    occurredAt = 100L,
                    deferredAt = 200L,
                    ownerPrincipal = owner
                )
            )

            updates.insert(
                DeferredIncidentUpdateEntity(
                    eventId = "EVT-SHARED",
                    incidentId = "INC-SHARED",
                    occurredAt = 100L,
                    sequence = 5L,
                    title = "$owner update",
                    status = "OPEN",
                    severity = "HIGH",
                    deferredAt = 200L,
                    ownerPrincipal = owner
                )
            )
        }

        var activeOwner = "account-a"

        assertEquals(
            "account-a timeline",
            realtime.loadForIncident(
                activeOwner, "INC-SHARED"
            ).single().message
        )

        assertEquals(
            "account-a update",
            updates.loadForIncident(
                activeOwner, "INC-SHARED"
            ).single().title
        )

        // B enters with the same incident and event IDs.
        activeOwner = "account-b"

        assertEquals(
            "account-b timeline",
            realtime.loadForIncident(
                activeOwner, "INC-SHARED"
            ).single().message
        )

        assertEquals(
            "account-b update",
            updates.loadForIncident(
                activeOwner, "INC-SHARED"
            ).single().title
        )

        // Simulate B completing deferred-event processing.
        assertEquals(
            1,
            realtime.deleteByEventId(activeOwner, "EVT-SHARED")
        )

        assertEquals(
            1,
            updates.deleteByEventId(activeOwner, "EVT-SHARED")
        )

        assertTrue(realtime.loadAll(activeOwner).isEmpty())
        assertTrue(updates.loadAll(activeOwner).isEmpty())

        // A returns. Neither of A's pending events was consumed.
        activeOwner = "account-a"

        assertEquals(
            "account-a timeline",
            realtime.loadForIncident(
                activeOwner, "INC-SHARED"
            ).single().message
        )

        assertEquals(
            "account-a update",
            updates.loadForIncident(
                activeOwner, "INC-SHARED"
            ).single().title
        )

        // Historical unowned events remain quarantined.
        assertEquals(
            " timeline",
            realtime.loadAll("").single().message
        )

        assertEquals(
            " update",
            updates.loadAll("").single().title
        )

        // An unrelated account cannot access these records.
        assertTrue(realtime.loadAll("account-c").isEmpty())
        assertTrue(updates.loadAll("account-c").isEmpty())
    }
}