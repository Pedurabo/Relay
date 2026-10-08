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
class PendingSeverityCommandOwnershipTest {

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
    fun ownerScopedQueue_cannotBeClaimedOrClearedByAnotherPrincipal() =
        runBlocking {

            val commandA =
                PendingSeverityCommand(
                    commandId = "CMD-A",
                    incidentId = "INC-A",
                    severity = "CRITICAL",
                    baseSeverity = "HIGH",
                    ownerPrincipal = "operator-a",
                    createdAt = 100L
                )

            val commandB =
                PendingSeverityCommand(
                    commandId = "CMD-B",
                    incidentId = "INC-B",
                    severity = "HIGH",
                    baseSeverity = "MEDIUM",
                    ownerPrincipal = "operator-b",
                    createdAt = 200L
                )

            store.save(commandA)
            store.save(commandB)

            assertEquals(
                listOf("CMD-A"),
                store
                    .loadAll("operator-a")
                    .map { it.commandId }
            )

            assertEquals(
                listOf("CMD-B"),
                store
                    .loadAll("operator-b")
                    .map { it.commandId }
            )

            assertFalse(
                store.claim(
                    commandId = "CMD-A",
                    ownerPrincipal = "operator-b"
                )
            )

            assertTrue(
                store.claim(
                    commandId = "CMD-A",
                    ownerPrincipal = "operator-a"
                )
            )

            assertEquals(
                "IN_FLIGHT",
                store
                    .load("operator-a")
                    ?.deliveryState
            )

            store.release(
                commandId = "CMD-A",
                ownerPrincipal = "operator-b"
            )

            assertEquals(
                "IN_FLIGHT",
                store
                    .load("operator-a")
                    ?.deliveryState
            )

            store.clearIf(
                commandId = "CMD-A",
                ownerPrincipal = "operator-b"
            )

            assertEquals(
                "CMD-A",
                store
                    .load("operator-a")
                    ?.commandId
            )

            store.release(
                commandId = "CMD-A",
                ownerPrincipal = "operator-a"
            )

            assertEquals(
                "PENDING",
                store
                    .load("operator-a")
                    ?.deliveryState
            )

            store.clearIf(
                commandId = "CMD-A",
                ownerPrincipal = "operator-a"
            )

            assertNull(
                store.load("operator-a")
            )

            assertEquals(
                "CMD-B",
                store
                    .load("operator-b")
                    ?.commandId
            )
        }
}
