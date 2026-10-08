package com.signaldesk.relay.data.local

import android.content.Context
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

@RunWith(AndroidJUnit4::class)
class PendingSeverityCommandLeaseRecoveryTest {

    private lateinit var database: RelayDatabase
    private lateinit var store: RoomPendingSeverityCommandStore

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
            RoomPendingSeverityCommandStore(
                database
                    .pendingSeverityCommandDao()
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun abandonedInFlightLease_isResetAndCanBeReclaimedByOwner() =
        runBlocking {

            val command =
                PendingSeverityCommand(
                    commandId = "CMD-LEASE",
                    incidentId = "INC-LEASE",
                    severity = "CRITICAL",
                    baseSeverity = "HIGH",
                    ownerPrincipal = "operator-a",
                    createdAt = 100L
                )

            store.save(
                command
            )

            assertTrue(
                store.claim(
                    commandId = command.commandId,
                    ownerPrincipal = command.ownerPrincipal
                )
            )

            assertEquals(
                "IN_FLIGHT",
                store
                    .load(command.ownerPrincipal)
                    ?.deliveryState
            )

            assertFalse(
                store.claim(
                    commandId = command.commandId,
                    ownerPrincipal = command.ownerPrincipal
                )
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
                    .load(command.ownerPrincipal)
                    ?.deliveryState
            )

            assertTrue(
                store.claim(
                    commandId = command.commandId,
                    ownerPrincipal = command.ownerPrincipal
                )
            )

            assertEquals(
                "IN_FLIGHT",
                store
                    .load(command.ownerPrincipal)
                    ?.deliveryState
            )

            assertFalse(
                store.claim(
                    commandId = command.commandId,
                    ownerPrincipal = "operator-b"
                )
            )
        }
}
