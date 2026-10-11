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
class ProductionRoomSnapshotStoreTest {

    private lateinit var database: Version16TestDatabase
    private lateinit var dao: Version16IncidentDao
    private lateinit var store: OwnerScopedRoomSnapshotStore

    private val accountA =
        AccountScopedIncidentId("account-a", "INC-SHARED")

    private val accountB =
        AccountScopedIncidentId("account-b", "INC-SHARED")

    private fun snapshot(
        key: AccountScopedIncidentId,
        sequence: Long
    ) = OwnerScopedIncidentSnapshot(
        key = key,
        title = "Incident",
        status = "OPEN",
        severity = "HIGH",
        latestSequence = sequence
    )

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            Version16TestDatabase::class.java
        ).build()

        dao = database.version16IncidentDao()

        val persistence = object : OwnerScopedIncidentPersistence {

            override suspend fun findIncident(
                key: AccountScopedIncidentId
            ): OwnerScopedIncidentSnapshot? =
                dao.getIncident(
                    key.ownerPrincipal,
                    key.incidentId
                )?.let {
                    OwnerScopedIncidentSnapshot(
                        key = key,
                        title = it.title,
                        status = it.status,
                        severity = it.severity,
                        latestSequence = it.latestSequence
                    )
                }

            override suspend fun findGap(
                key: AccountScopedIncidentId
            ): OwnerScopedPersistedGap? =
                dao.getGap(
                    key.ownerPrincipal,
                    key.incidentId
                )?.let {
                    OwnerScopedPersistedGap(
                        state = OwnerScopedGapState(
                            key = key,
                            expectedSequence = it.expectedSequence,
                            receivedSequence = it.receivedSequence
                        ),
                        detectedAt = it.detectedAt
                    )
                }

            override suspend fun saveIncident(
                snapshot: OwnerScopedIncidentSnapshot
            ) {
                dao.upsertIncident(
                    V16Incident(
                        ownerPrincipal = snapshot.key.ownerPrincipal,
                        id = snapshot.key.incidentId,
                        title = snapshot.title,
                        status = snapshot.status,
                        severity = snapshot.severity,
                        latestSequence = snapshot.latestSequence
                    )
                )
            }

            override suspend fun saveGap(
                gap: OwnerScopedPersistedGap
            ) {
                dao.upsertGap(
                    V16IncidentSequenceGap(
                        ownerPrincipal = gap.state.key.ownerPrincipal,
                        incidentId = gap.state.key.incidentId,
                        expectedSequence = gap.state.expectedSequence,
                        receivedSequence = gap.state.receivedSequence,
                        detectedAt = gap.detectedAt
                    )
                )
            }

            override suspend fun removeGap(
                key: AccountScopedIncidentId
            ) {
                dao.deleteGap(
                    key.ownerPrincipal,
                    key.incidentId
                )
            }
        }

        store = OwnerScopedRoomSnapshotStore(database, persistence)

        for (key in listOf(accountA, accountB)) {
            persistence.saveIncident(snapshot(key, 2L))

            persistence.saveGap(
                OwnerScopedPersistedGap(
                    state = OwnerScopedGapState(
                        key = key,
                        expectedSequence = 3L,
                        receivedSequence = 8L
                    ),
                    detectedAt = 177L
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun reconcilesOneOwnerWithoutChangingAnother() = runBlocking {
        val reconciler = OwnerScopedSnapshotReconciler(store)

        assertEquals(
            1,
            reconciler.reconcile(
                "account-a",
                listOf(snapshot(accountA, 5L))
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

        assertEquals(
            177L,
            dao.getGap("account-a", "INC-SHARED")
                ?.detectedAt
        )

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
    fun transactionRollsBackWhenLaterSnapshotFails() = runBlocking {
        val reconciler = OwnerScopedSnapshotReconciler(store)

        val invalid = snapshot(
            AccountScopedIncidentId("account-a", "INC-SECOND"),
            9L
        )

        var failed = false

        try {
            store.withTransaction {
                reconciler.reconcile(
                    "account-a",
                    listOf(snapshot(accountA, 8L))
                )

                error("Abort transaction")
            }
        } catch (error: IllegalStateException) {
            failed = error.message == "Abort transaction"
        }

        assertTrue(failed)

        assertEquals(
            2L,
            dao.getIncident("account-a", "INC-SHARED")
                ?.latestSequence
        )

        assertEquals(
            3L,
            dao.getGap("account-a", "INC-SHARED")
                ?.expectedSequence
        )

        assertNull(
            dao.getIncident("account-a", invalid.key.incidentId)
        )
    }
}
