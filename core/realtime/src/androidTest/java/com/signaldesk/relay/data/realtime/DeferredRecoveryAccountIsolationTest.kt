package com.signaldesk.relay.data.realtime

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.DeferredRealtimeEventEntity
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeferredRecoveryAccountIsolationTest {

    private lateinit var database: RelayDatabase
    private lateinit var processor: IncidentEventProcessor

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RelayDatabase::class.java
        ).build()
        processor = IncidentEventProcessor(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deferredTimelineRecoveryNeverCrossesAccountBoundary() = runBlocking {
        val incidents = database.incidentDao()
        val deferred = database.deferredRealtimeEventDao()
        val timeline = database.timelineEntryDao()

        for (owner in listOf("account-a", "account-b")) {
            deferred.insert(
                DeferredRealtimeEventEntity(
                    eventId = "EVT-SHARED",
                    incidentId = "INC-SHARED",
                    entryId = "ENTRY-SHARED",
                    message = "$owner message",
                    author = "Tester",
                    occurredAt = 100L,
                    deferredAt = System.currentTimeMillis(),
                    ownerPrincipal = owner
                )
            )
        }

        // Only B has its parent incident.
        incidents.insert(
            IncidentEntity(
                id = "INC-SHARED",
                title = "B incident",
                status = "OPEN",
                ownerPrincipal = "account-b"
            )
        )

        processor.recoverDeferredTimelineEvents("account-b")

        assertEquals(
            "account-b message",
            timeline.getById("account-b", "ENTRY-SHARED")?.message
        )
        assertNull(timeline.getById("account-a", "ENTRY-SHARED"))
        assertEquals(0, deferred.loadAll("account-b").size)
        assertEquals(1, deferred.loadAll("account-a").size)

        // B's incident must not make A's event eligible.
        processor.recoverDeferredTimelineEvents("account-a")

        assertNull(timeline.getById("account-a", "ENTRY-SHARED"))
        assertEquals(1, deferred.loadAll("account-a").size)

        // A's own incident becomes available later.
        incidents.insert(
            IncidentEntity(
                id = "INC-SHARED",
                title = "A incident",
                status = "OPEN",
                ownerPrincipal = "account-a"
            )
        )

        processor.recoverDeferredTimelineEvents("account-a")

        assertEquals(
            "account-a message",
            timeline.getById("account-a", "ENTRY-SHARED")?.message
        )
        assertEquals(
            "account-b message",
            timeline.getById("account-b", "ENTRY-SHARED")?.message
        )
        assertEquals(0, deferred.loadAll("account-a").size)
        assertEquals(0, deferred.loadAll("account-b").size)
        assertNull(timeline.getById("", "ENTRY-SHARED"))
    }
}