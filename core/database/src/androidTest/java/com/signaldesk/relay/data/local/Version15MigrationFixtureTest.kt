package com.signaldesk.relay.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Current-version fixture checks for the v15-to-v16 migration work.
 *
 * These tests use the v15 Room model. They do not yet execute an
 * actual historical-file migration or validate the future v16 schema.
 */
@RunWith(AndroidJUnit4::class)
class Version15MigrationFixtureTest {

    private lateinit var database: RelayDatabase

    @Before
    fun setUp() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(
            context,
            RelayDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ownerKnownPendingCommandsRemainDistinct() = runBlocking {
        val dao = database.pendingCreateIncidentCommandDao()

        dao.upsert(
            PendingCreateIncidentCommand(
                commandId = "CMD-A",
                incidentId = "INC-246",
                title = "A pending incident",
                status = "OPEN",
                severity = "HIGH",
                ownerPrincipal = "account-a",
                createdAt = 100L
            )
        )

        dao.upsert(
            PendingCreateIncidentCommand(
                commandId = "CMD-B",
                incidentId = "INC-246",
                title = "B pending incident",
                status = "OPEN",
                severity = "LOW",
                ownerPrincipal = "account-b",
                createdAt = 200L
            )
        )

        val a = dao.getOldestForOwner("account-a")
        val b = dao.getOldestForOwner("account-b")

        assertNotNull(a)
        assertNotNull(b)
        assertEquals("CMD-A", a?.commandId)
        assertEquals("CMD-B", b?.commandId)
        assertEquals("account-a", a?.ownerPrincipal)
        assertEquals("account-b", b?.ownerPrincipal)
    }

    @Test
    fun legacyIncidentHasNoProvableOwner() = runBlocking {
        val dao = database.incidentDao()

        dao.insert(
            IncidentEntity(
                ownerPrincipal = "",
                id = "INC-LEGACY",
                title = "Unowned cached incident",
                status = "OPEN",
                latestSequence = 7L,
                severity = "HIGH"
            )
        )

        val saved = dao.getById("", "INC-LEGACY")

        assertNotNull(saved)
        assertEquals("Unowned cached incident", saved?.title)

        // The v15 entity does not encode the account that owns it.
        // Its ownership cannot be reconstructed from the incident ID.
    }

    @Test
    fun unknownOwnerTimelineIsNotEligibleForPendingDelivery() =
        runBlocking {
            val dao = database.timelineEntryDao()

            dao.insert(
                TimelineEntryEntity(
                    entryId = "ENTRY-LEGACY",
                    incidentId = "INC-LEGACY",
                    message = "Legacy timeline",
                    author = "Unknown",
                    occurredAt = 100L,
                    deliveryState = "PENDING",
                    ownerPrincipal = ""
                )
            )

            assertEquals(
                0,
                dao.loadPendingForOwner("account-a").size
            )

            assertEquals(
                0,
                dao.loadPendingForOwner("account-b").size
            )

            assertNotNull(dao.getById("", "ENTRY-LEGACY"))
            assertNull(dao.getById("", "NOT-PRESENT"))
        }
}