package com.signaldesk.relay.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Version16TimelineIsolationDaoTest {

    private lateinit var room: Version16TestDatabase
    private lateinit var dao: Version16TimelineIsolationDao

    private fun insert(
        owner: String,
        entryId: String,
        incidentId: String,
        occurredAt: Long,
        state: String = "PENDING"
    ) {
        room.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO timeline_entries
                (ownerPrincipal, entryId, incidentId, message,
                 author, occurredAt, deliveryState)
            VALUES (?, ?, ?, 'Test message', 'Tester', ?, ?)
            """.trimIndent(),
            arrayOf<Any>(owner, entryId, incidentId, occurredAt, state)
        )
    }

    @Before
    fun setUp() {
        room = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            Version16TestDatabase::class.java
        ).build()

        dao = room.version16TimelineIsolationDao()

        for (owner in listOf("", "account-a", "account-b")) {
            insert(owner, "ENTRY-SHARED", "INC-SHARED", 100L)
        }
    }

    @After
    fun tearDown() {
        room.close()
    }

    @Test
    fun duplicateEntryIdsRemainOwnerIsolated() = runBlocking {
        assertEquals(
            listOf("ENTRY-SHARED"),
            dao.entriesForIncident("account-a", "INC-SHARED")
        )
        assertEquals(
            listOf("ENTRY-SHARED"),
            dao.entriesForIncident("account-b", "INC-SHARED")
        )
        assertTrue(
            dao.entriesForIncident("account-c", "INC-SHARED").isEmpty()
        )
        assertNull(
            dao.deliveryState("account-c", "ENTRY-SHARED")
        )
    }

    @Test
    fun acknowledgementCannotModifyAnotherOwner() = runBlocking {
        assertEquals(
            1,
            dao.acknowledge("account-a", "ENTRY-SHARED")
        )

        assertEquals(
            "SENT",
            dao.deliveryState("account-a", "ENTRY-SHARED")
        )
        assertEquals(
            "PENDING",
            dao.deliveryState("account-b", "ENTRY-SHARED")
        )
        assertEquals(
            "PENDING",
            dao.deliveryState("", "ENTRY-SHARED")
        )
    }

    @Test
    fun latestEntrySelectionPartitionsByOwnerAndIncident() = runBlocking {
        insert("account-a", "ENTRY-A-NEW", "INC-SHARED", 200L)
        insert("account-b", "ENTRY-B-NEW", "INC-SHARED", 300L)
        insert("account-a", "ENTRY-A-SECOND", "INC-SECOND", 250L)

        assertEquals(
            listOf("ENTRY-A-SECOND", "ENTRY-A-NEW"),
            dao.latestEntryIds("account-a")
        )
        assertEquals(
            listOf("ENTRY-B-NEW"),
            dao.latestEntryIds("account-b")
        )
        assertEquals(
            listOf("ENTRY-SHARED"),
            dao.latestEntryIds("")
        )
    }

    @Test
    fun foreignOwnerCannotAcknowledgeOrReadLegacyEntry() = runBlocking {
        assertEquals(
            0,
            dao.acknowledge("account-c", "ENTRY-SHARED")
        )
        assertNull(
            dao.deliveryState("account-c", "ENTRY-SHARED")
        )
        assertEquals(
            "PENDING",
            dao.deliveryState("", "ENTRY-SHARED")
        )
    }

    @Test
    fun accountAtoBtoAKeepsTimelineAcknowledgementsIsolated() = runBlocking {
        var activeOwner = "account-a"

        suspend fun visibleState(): String? =
            dao.deliveryState(activeOwner, "ENTRY-SHARED")

        suspend fun visibleEntries(): List<String> =
            dao.entriesForIncident(activeOwner, "INC-SHARED")

        // Account A initially sees its own pending entry.
        assertEquals(
            listOf("ENTRY-SHARED"),
            visibleEntries()
        )
        assertEquals("PENDING", visibleState())

        // Account B becomes active.
        activeOwner = "account-b"

        assertEquals(
            listOf("ENTRY-SHARED"),
            visibleEntries()
        )
        assertEquals("PENDING", visibleState())

        // B acknowledges B's record only.
        assertEquals(
            1,
            dao.acknowledge(activeOwner, "ENTRY-SHARED")
        )
        assertEquals("SENT", visibleState())

        // Account A returns.
        activeOwner = "account-a"

        assertEquals(
            listOf("ENTRY-SHARED"),
            visibleEntries()
        )
        assertEquals(
            "PENDING",
            visibleState()
        )

        // B's change persists independently.
        assertEquals(
            "SENT",
            dao.deliveryState("account-b", "ENTRY-SHARED")
        )

        // Historical unowned data remains quarantined.
        assertEquals(
            "PENDING",
            dao.deliveryState("", "ENTRY-SHARED")
        )

        // An unrelated owner cannot see the shared ID.
        assertTrue(
            dao.entriesForIncident("account-c", "INC-SHARED")
                .isEmpty()
        )
        assertNull(
            dao.deliveryState("account-c", "ENTRY-SHARED")
        )
    }
}
