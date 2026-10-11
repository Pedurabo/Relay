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
class Version16DeferredIsolationDaoTest {

    private lateinit var room: Version16TestDatabase
    private lateinit var dao: Version16DeferredIsolationDao

    private fun insertIncident(owner: String, id: String) {
        room.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO incidents
                (ownerPrincipal, id, title, status, latestSequence, severity)
            VALUES (?, ?, 'Test', 'OPEN', 1, 'HIGH')
            """.trimIndent(),
            arrayOf<Any>(owner, id)
        )
    }

    private fun insertTimeline(
        owner: String,
        eventId: String,
        incidentId: String
    ) {
        room.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO deferred_realtime_events
                (ownerPrincipal, eventId, incidentId, entryId,
                 message, author, occurredAt, deferredAt)
            VALUES (?, ?, ?, 'ENTRY-1', 'Message', 'Tester', 100, 100)
            """.trimIndent(),
            arrayOf<Any>(owner, eventId, incidentId)
        )
    }

    private fun insertUpdate(
        owner: String,
        eventId: String,
        incidentId: String
    ) {
        room.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO deferred_incident_updates
                (ownerPrincipal, eventId, incidentId, occurredAt,
                 sequence, title, status, severity, deferredAt)
            VALUES (?, ?, ?, 100, 3, NULL, NULL, NULL, 100)
            """.trimIndent(),
            arrayOf<Any>(owner, eventId, incidentId)
        )
    }

    @Before
    fun setUp() {
        room = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            Version16TestDatabase::class.java
        ).build()

        dao = room.version16DeferredIsolationDao()

        insertIncident("account-a", "INC-SHARED")
        insertIncident("account-b", "INC-SHARED")

        for (owner in listOf("", "account-a", "account-b")) {
            insertTimeline(owner, "EVT-SHARED", "INC-SHARED")
            insertUpdate(owner, "UPDATE-SHARED", "INC-SHARED")
        }
    }

    @After
    fun tearDown() {
        room.close()
    }

    @Test
    fun recoveryReadsOnlyTheRequestedOwner() = runBlocking {
        assertEquals(
            listOf("EVT-SHARED"),
            dao.timelineEvents("account-a", "INC-SHARED")
        )
        assertEquals(
            listOf("UPDATE-SHARED"),
            dao.incidentUpdates("account-b", "INC-SHARED")
        )

        assertTrue(
            dao.timelineEvents("account-c", "INC-SHARED").isEmpty()
        )
        assertTrue(
            dao.incidentUpdates("account-c", "INC-SHARED").isEmpty()
        )
    }

    @Test
    fun deletingEventsCannotDeleteAnotherOwnersEvents() = runBlocking {
        assertEquals(
            1,
            dao.deleteTimelineEvent("account-a", "EVT-SHARED")
        )
        assertEquals(
            1,
            dao.deleteIncidentUpdate("account-a", "UPDATE-SHARED")
        )

        assertEquals(
            listOf("EVT-SHARED"),
            dao.timelineEvents("account-b", "INC-SHARED")
        )
        assertEquals(
            listOf("UPDATE-SHARED"),
            dao.incidentUpdates("account-b", "INC-SHARED")
        )
        assertEquals(
            listOf("EVT-SHARED"),
            dao.timelineEvents("", "INC-SHARED")
        )
    }

    @Test
    fun crossOwnerIncidentDoesNotProtectOrphan() = runBlocking {
        insertTimeline("account-a", "EVT-ORPHAN", "INC-B-ONLY")
        insertUpdate("account-a", "UPDATE-ORPHAN", "INC-B-ONLY")
        insertIncident("account-b", "INC-B-ONLY")

        assertEquals(1, dao.deleteTimelineOrphans("account-a", 200L))
        assertEquals(1, dao.deleteUpdateOrphans("account-a", 200L))

        assertTrue(
            dao.timelineEvents("account-a", "INC-B-ONLY").isEmpty()
        )
        assertTrue(
            dao.incidentUpdates("account-a", "INC-B-ONLY").isEmpty()
        )

        assertEquals(
            listOf("EVT-SHARED"),
            dao.timelineEvents("account-b", "INC-SHARED")
        )
    }

    @Test
    fun cleanupPreservesOwnedAndLegacyRows() = runBlocking {
        assertEquals(0, dao.deleteTimelineOrphans("account-b", 200L))
        assertEquals(0, dao.deleteUpdateOrphans("account-b", 200L))

        assertEquals(
            listOf("EVT-SHARED"),
            dao.timelineEvents("account-b", "INC-SHARED")
        )

        assertEquals(
            listOf("EVT-SHARED"),
            dao.timelineEvents("", "INC-SHARED")
        )
        assertEquals(
            listOf("UPDATE-SHARED"),
            dao.incidentUpdates("", "INC-SHARED")
        )
    }
}
