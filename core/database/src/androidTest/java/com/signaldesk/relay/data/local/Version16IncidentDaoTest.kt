package com.signaldesk.relay.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room-generated DAO tests against the proposed v16 model.
 * Uses only an independent in-memory database.
 */
@RunWith(AndroidJUnit4::class)
class Version16IncidentDaoTest {

    private lateinit var room: Version16TestDatabase
    private lateinit var dao: Version16IncidentDao

    @Before
    fun setUp() = runBlocking {
        room = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            Version16TestDatabase::class.java
        ).build()

        dao = room.version16IncidentDao()

        for (owner in listOf("", "account-a", "account-b")) {
            dao.upsertIncident(
                incident(owner, 2L)
            )
            dao.upsertGap(
                V16IncidentSequenceGap(
                    ownerPrincipal = owner,
                    incidentId = "INC-SHARED",
                    expectedSequence = 3L,
                    receivedSequence = 8L,
                    detectedAt = 100L
                )
            )
        }
    }

    @After
    fun tearDown() {
        room.close()
    }

    private fun incident(
        owner: String,
        sequence: Long
    ) = V16Incident(
        ownerPrincipal = owner,
        id = "INC-SHARED",
        title = "$owner incident",
        status = "OPEN",
        latestSequence = sequence,
        severity = "HIGH"
    )

    @Test
    fun duplicateIdsRemainSeparatedByOwner() = runBlocking {
        assertEquals(
            "account-a incident",
            dao.getIncident("account-a", "INC-SHARED")?.title
        )
        assertEquals(
            "account-b incident",
            dao.getIncident("account-b", "INC-SHARED")?.title
        )
        assertNull(
            dao.getIncident("account-c", "INC-SHARED")
        )
    }

    @Test
    fun snapshotUpdatesOnlyItsOwnerAndGap() = runBlocking {
        assertTrue(
            dao.reconcile(
                "account-a",
                incident("account-a", 5L)
            )
        )

        assertEquals(
            5L,
            dao.getIncident("account-a", "INC-SHARED")
                ?.latestSequence
        )
        assertEquals(
            6L,
            dao.getGap("account-a", "INC-SHARED")
                ?.expectedSequence
        )

        for (owner in listOf("", "account-b")) {
            assertEquals(
                2L,
                dao.getIncident(owner, "INC-SHARED")
                    ?.latestSequence
            )
            assertEquals(
                3L,
                dao.getGap(owner, "INC-SHARED")
                    ?.expectedSequence
            )
        }
    }

    @Test
    fun staleSnapshotAndWrongOwnerAreRejected() = runBlocking {
        assertFalse(
            dao.reconcile(
                "account-a",
                incident("account-a", 2L)
            )
        )

        var rejected = false
        try {
            dao.reconcile(
                "account-b",
                incident("account-a", 9L)
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)

        assertEquals(
            2L,
            dao.getIncident("account-b", "INC-SHARED")
                ?.latestSequence
        )
        assertEquals(
            3L,
            dao.getGap("account-b", "INC-SHARED")
                ?.expectedSequence
        )
    }

    @Test
    fun accountAtoBtoARestoresOnlyItsOwnIncident() = runBlocking {
        // The same incident ID exists for both authenticated owners.
        // Legacy unowned records must not appear under either owner.

        var activeOwner = "account-a"

        suspend fun visibleIncident(): V16Incident? =
            dao.getIncident(activeOwner, "INC-SHARED")

        // Initial Account A session.
        assertEquals(
            "account-a incident",
            visibleIncident()?.title
        )

        // Account B signs in.
        activeOwner = "account-b"

        assertEquals(
            "account-b incident",
            visibleIncident()?.title
        )

        // B updates only B's version of the incident.
        assertTrue(
            dao.reconcile(
                activeOwner,
                incident(activeOwner, 6L)
            )
        )

        assertEquals(
            6L,
            visibleIncident()?.latestSequence
        )

        // Account A signs back in.
        activeOwner = "account-a"

        assertEquals(
            "account-a incident",
            visibleIncident()?.title
        )

        assertEquals(
            2L,
            visibleIncident()?.latestSequence
        )

        // B's update remains intact but isolated.
        assertEquals(
            6L,
            dao.getIncident("account-b", "INC-SHARED")
                ?.latestSequence
        )

        // Neither authenticated owner sees the legacy record.
        assertEquals(
            " incident",
            dao.getIncident("", "INC-SHARED")?.title
        )
    }
}
