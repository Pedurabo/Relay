package com.signaldesk.relay.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelinePendingRecoveryQueryTest {

    private lateinit var database:
        RelayDatabase

    private lateinit var dao:
        TimelineEntryDao


    @Before
    fun setUp() {

        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        database =
            Room
                .inMemoryDatabaseBuilder(
                    context,
                    RelayDatabase::class.java
                )
                .allowMainThreadQueries()
                .build()

        dao =
            database
                .timelineEntryDao()
    }


    @After
    fun tearDown() {

        database.close()
    }


    @Test
    fun recoveryLoadsOnlyPendingRowsForOwnerInStableOrder() =
        runBlocking {

            dao.upsert(
                entry(
                    entryId =
                        "ENTRY-B",
                    occurredAt =
                        200L,
                    deliveryState =
                        "PENDING",
                    ownerPrincipal =
                        "operator-a"
                )
            )

            dao.upsert(
                entry(
                    entryId =
                        "ENTRY-A2",
                    occurredAt =
                        100L,
                    deliveryState =
                        "PENDING",
                    ownerPrincipal =
                        "operator-a"
                )
            )

            dao.upsert(
                entry(
                    entryId =
                        "ENTRY-A1",
                    occurredAt =
                        100L,
                    deliveryState =
                        "PENDING",
                    ownerPrincipal =
                        "operator-a"
                )
            )

            /*
             * Same owner, but already authoritative.
             */
            dao.upsert(
                entry(
                    entryId =
                        "ENTRY-SENT",
                    occurredAt =
                        50L,
                    deliveryState =
                        "SENT",
                    ownerPrincipal =
                        "operator-a"
                )
            )

            /*
             * Pending work belonging to another account.
             */
            dao.upsert(
                entry(
                    entryId =
                        "ENTRY-OTHER",
                    occurredAt =
                        25L,
                    deliveryState =
                        "PENDING",
                    ownerPrincipal =
                        "operator-b"
                )
            )

            /*
             * Legacy pre-ownership row. Automatic recovery must
             * never infer which authenticated account owns it.
             */
            dao.upsert(
                entry(
                    entryId =
                        "ENTRY-LEGACY",
                    occurredAt =
                        10L,
                    deliveryState =
                        "PENDING",
                    ownerPrincipal =
                        ""
                )
            )

            val recovered =
                dao.loadPendingForOwner(
                    "operator-a"
                )

            assertEquals(
                listOf(
                    "ENTRY-A1",
                    "ENTRY-A2",
                    "ENTRY-B"
                ),
                recovered.map {
                    it.entryId
                }
            )

            assertEquals(
                listOf(
                    "operator-a",
                    "operator-a",
                    "operator-a"
                ),
                recovered.map {
                    it.ownerPrincipal
                }
            )

            assertEquals(
                listOf(
                    "PENDING",
                    "PENDING",
                    "PENDING"
                ),
                recovered.map {
                    it.deliveryState
                }
            )
        }


    private fun entry(
        entryId: String,
        occurredAt: Long,
        deliveryState: String,
        ownerPrincipal: String
    ): TimelineEntryEntity {

        return TimelineEntryEntity(
            entryId =
                entryId,
            incidentId =
                "INC-RECOVERY",
            message =
                "Recovery test",
            author =
                "Operator",
            occurredAt =
                occurredAt,
            deliveryState =
                deliveryState,
            ownerPrincipal =
                ownerPrincipal
        )
    }
}
