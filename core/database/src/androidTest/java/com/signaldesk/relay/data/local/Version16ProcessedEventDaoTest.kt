package com.signaldesk.relay.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room-generated processed-event query tests.
 *
 * Uses an independent in-memory test database.
 * The production Relay database is never opened.
 */
@RunWith(AndroidJUnit4::class)
class Version16ProcessedEventDaoTest {

    private lateinit var room: Version16TestDatabase
    private lateinit var dao: Version16ProcessedEventDao

    @Before
    fun setUp() {
        room = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            Version16TestDatabase::class.java
        ).build()

        dao = room.version16ProcessedEventDao()

        for (owner in listOf("", "account-a", "account-b")) {
            room.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO processed_events
                    (ownerPrincipal, eventId, processedAt)
                VALUES (?, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(owner, "EVT-SHARED", 100L)
            )
        }
    }

    @After
    fun tearDown() {
        room.close()
    }

    @Test
    fun identicalEventIdsAreIndependentAcrossOwners() = runBlocking {
        assertTrue(dao.exists("account-a", "EVT-SHARED"))
        assertTrue(dao.exists("account-b", "EVT-SHARED"))
        assertTrue(dao.exists("", "EVT-SHARED"))

        assertFalse(dao.exists("account-c", "EVT-SHARED"))

        assertEquals(1, dao.countForOwner("account-a"))
        assertEquals(1, dao.countForOwner("account-b"))
        assertEquals(1, dao.countForOwner(""))
    }

    @Test
    fun ownerDeletionCannotRemoveAnotherOwnersDedupRecord() = runBlocking {
        assertEquals(
            1,
            dao.deleteForOwner("account-a", "EVT-SHARED")
        )

        assertFalse(dao.exists("account-a", "EVT-SHARED"))
        assertTrue(dao.exists("account-b", "EVT-SHARED"))
        assertTrue(dao.exists("", "EVT-SHARED"))
    }

    @Test
    fun unknownOwnerCannotReadOrDeleteLegacyRecord() = runBlocking {
        assertFalse(dao.exists("account-c", "EVT-SHARED"))

        assertEquals(
            0,
            dao.deleteForOwner("account-c", "EVT-SHARED")
        )

        assertEquals(1, dao.countForOwner(""))
        assertTrue(dao.exists("", "EVT-SHARED"))
    }
}
