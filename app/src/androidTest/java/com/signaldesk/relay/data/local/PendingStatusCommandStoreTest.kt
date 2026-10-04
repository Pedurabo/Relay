package com.signaldesk.relay.data.local

import android.content.Context
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

@RunWith(AndroidJUnit4::class)
class PendingStatusCommandStoreTest {

    private lateinit var database: RelayDatabase
    private lateinit var store: RoomPendingStatusCommandStore

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

        store =
            RoomPendingStatusCommandStore(
                database
                    .pendingStatusCommandDao()
            )
    }

    @After
    fun tearDown() {

        database.close()
    }

    @Test
    fun ownerScopedQueue_cannotBeClaimedReleasedOrClearedByAnotherPrincipal() =
        runBlocking {

            val commandA =
                PendingStatusCommand(
                    commandId =
                        "CMD-STATUS-A",
                    incidentId =
                        "INC-A",
                    status =
                        "Monitoring",
                    baseStatus =
                        "Active",
                    ownerPrincipal =
                        "operator-a",
                    createdAt =
                        100L
                )

            val commandB =
                PendingStatusCommand(
                    commandId =
                        "CMD-STATUS-B",
                    incidentId =
                        "INC-B",
                    status =
                        "Resolved",
                    baseStatus =
                        "Monitoring",
                    ownerPrincipal =
                        "operator-b",
                    createdAt =
                        200L
                )

            store.save(
                commandA
            )

            store.save(
                commandB
            )

            assertEquals(
                listOf(
                    "CMD-STATUS-A"
                ),
                store
                    .loadAll(
                        "operator-a"
                    )
                    .map {
                        it.commandId
                    }
            )

            assertEquals(
                listOf(
                    "CMD-STATUS-B"
                ),
                store
                    .loadAll(
                        "operator-b"
                    )
                    .map {
                        it.commandId
                    }
            )

            assertFalse(
                store.claim(
                    commandId =
                        commandA.commandId,
                    ownerPrincipal =
                        "operator-b"
                )
            )

            assertTrue(
                store.claim(
                    commandId =
                        commandA.commandId,
                    ownerPrincipal =
                        commandA.ownerPrincipal
                )
            )

            assertEquals(
                "IN_FLIGHT",
                store
                    .load(
                        commandA.ownerPrincipal
                    )
                    ?.deliveryState
            )

            store.release(
                commandId =
                    commandA.commandId,
                ownerPrincipal =
                    "operator-b"
            )

            assertEquals(
                "IN_FLIGHT",
                store
                    .load(
                        commandA.ownerPrincipal
                    )
                    ?.deliveryState
            )

            store.clearIf(
                commandId =
                    commandA.commandId,
                ownerPrincipal =
                    "operator-b"
            )

            assertEquals(
                commandA.commandId,
                store
                    .load(
                        commandA.ownerPrincipal
                    )
                    ?.commandId
            )

            store.release(
                commandId =
                    commandA.commandId,
                ownerPrincipal =
                    commandA.ownerPrincipal
            )

            assertEquals(
                "PENDING",
                store
                    .load(
                        commandA.ownerPrincipal
                    )
                    ?.deliveryState
            )

            store.clearIf(
                commandId =
                    commandA.commandId,
                ownerPrincipal =
                    commandA.ownerPrincipal
            )

            assertNull(
                store.load(
                    commandA.ownerPrincipal
                )
            )

            assertEquals(
                commandB.commandId,
                store
                    .load(
                        commandB.ownerPrincipal
                    )
                    ?.commandId
            )
        }

    @Test
    fun abandonedInFlightLease_isResetAndCanBeReclaimedByOwner() =
        runBlocking {

            val command =
                PendingStatusCommand(
                    commandId =
                        "CMD-STATUS-LEASE",
                    incidentId =
                        "INC-STATUS-LEASE",
                    status =
                        "Resolved",
                    baseStatus =
                        "Monitoring",
                    ownerPrincipal =
                        "operator-a",
                    createdAt =
                        100L
                )

            store.save(
                command
            )

            assertTrue(
                store.claim(
                    commandId =
                        command.commandId,
                    ownerPrincipal =
                        command.ownerPrincipal
                )
            )

            assertFalse(
                store.claim(
                    commandId =
                        command.commandId,
                    ownerPrincipal =
                        command.ownerPrincipal
                )
            )

            assertEquals(
                "IN_FLIGHT",
                store
                    .load(
                        command.ownerPrincipal
                    )
                    ?.deliveryState
            )

            val resetCount =
                store.resetInFlight()

            assertEquals(
                1,
                resetCount
            )

            assertEquals(
                "PENDING",
                store
                    .load(
                        command.ownerPrincipal
                    )
                    ?.deliveryState
            )

            assertTrue(
                store.claim(
                    commandId =
                        command.commandId,
                    ownerPrincipal =
                        command.ownerPrincipal
                )
            )

            assertFalse(
                store.claim(
                    commandId =
                        command.commandId,
                    ownerPrincipal =
                        "operator-b"
                )
            )
        }
}