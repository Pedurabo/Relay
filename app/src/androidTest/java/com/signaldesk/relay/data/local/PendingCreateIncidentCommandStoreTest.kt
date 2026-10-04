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
class PendingCreateIncidentCommandStoreTest {

    private lateinit var database: RelayDatabase

    private lateinit var store:
        RoomPendingCreateIncidentCommandStore

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
            RoomPendingCreateIncidentCommandStore(
                database
                    .pendingCreateIncidentCommandDao()
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
                PendingCreateIncidentCommand(
                    commandId =
                        "CMD-CREATE-A",
                    incidentId =
                        "INC-CREATE-A",
                    title =
                        "Create A",
                    status =
                        "Investigating",
                    severity =
                        "HIGH",
                    ownerPrincipal =
                        "operator-a",
                    createdAt =
                        100L
                )

            val commandB =
                PendingCreateIncidentCommand(
                    commandId =
                        "CMD-CREATE-B",
                    incidentId =
                        "INC-CREATE-B",
                    title =
                        "Create B",
                    status =
                        "Active",
                    severity =
                        "MEDIUM",
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
                    "CMD-CREATE-A"
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
                    "CMD-CREATE-B"
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
                PendingCreateIncidentCommand(
                    commandId =
                        "CMD-CREATE-LEASE",
                    incidentId =
                        "INC-CREATE-LEASE",
                    title =
                        "Create lease proof",
                    status =
                        "Investigating",
                    severity =
                        "MEDIUM",
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