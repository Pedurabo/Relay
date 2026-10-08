package com.signaldesk.relay.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingSeverityCommandImmutableIdentityTest {

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
    fun ownershipUsesImmutableUserId_notDisplayName() =
        runBlocking {

            store.save(
                PendingSeverityCommand(
                    commandId = "CMD-IDENTITY",
                    incidentId = "INC-IDENTITY",
                    severity = "HIGH",
                    baseSeverity = "MEDIUM",
                    ownerPrincipal = "user-123",
                    createdAt = 100L
                )
            )

            assertEquals(
                "CMD-IDENTITY",
                store
                    .load("user-123")
                    ?.commandId
            )

            assertNull(
                store.load(
                    "Relay Operator"
                )
            )

            assertNull(
                store.load(
                    "Renamed Operator"
                )
            )

            assertNull(
                store.load(
                    "user-456"
                )
            )
        }
}
