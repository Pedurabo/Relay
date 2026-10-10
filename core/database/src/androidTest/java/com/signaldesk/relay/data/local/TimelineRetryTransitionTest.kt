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
class TimelineRetryTransitionTest {

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
    fun retryOnlyTransitionsMatchingOwnedFailedRow() =
        runBlocking {

            dao.upsert(
                entry(
                    entryId = "ENTRY-FAILED",
                    deliveryState = "FAILED",
                    ownerPrincipal = "operator-a"
                )
            )

            dao.upsert(
                entry(
                    entryId = "ENTRY-PENDING",
                    deliveryState = "PENDING",
                    ownerPrincipal = "operator-a"
                )
            )

            dao.upsert(
                entry(
                    entryId = "ENTRY-SENT",
                    deliveryState = "SENT",
                    ownerPrincipal = "operator-a"
                )
            )

            dao.upsert(
                entry(
                    entryId = "ENTRY-WRONG-OWNER",
                    deliveryState = "FAILED",
                    ownerPrincipal = "operator-a"
                )
            )

            val failedChanged =
                dao.retryFailed(
                    entryId = "ENTRY-FAILED",
                    ownerPrincipal = "operator-a"
                )

            val pendingChanged =
                dao.retryFailed(
                    entryId = "ENTRY-PENDING",
                    ownerPrincipal = "operator-a"
                )

            val sentChanged =
                dao.retryFailed(
                    entryId = "ENTRY-SENT",
                    ownerPrincipal = "operator-a"
                )

            val wrongOwnerChanged =
                dao.retryFailed(
                    entryId = "ENTRY-WRONG-OWNER",
                    ownerPrincipal = "operator-b"
                )

            assertEquals(
                1,
                failedChanged
            )

            assertEquals(
                "PENDING",
                dao.getById(
                    "ENTRY-FAILED"
                )?.deliveryState
            )

            assertEquals(
                0,
                pendingChanged
            )

            assertEquals(
                "PENDING",
                dao.getById(
                    "ENTRY-PENDING"
                )?.deliveryState
            )

            assertEquals(
                0,
                sentChanged
            )

            assertEquals(
                "SENT",
                dao.getById(
                    "ENTRY-SENT"
                )?.deliveryState
            )

            assertEquals(
                0,
                wrongOwnerChanged
            )

            assertEquals(
                "FAILED",
                dao.getById(
                    "ENTRY-WRONG-OWNER"
                )?.deliveryState
            )
        }

    @Test
    fun failureOnlyTransitionsMatchingOwnedPendingRow() =
        runBlocking {

            dao.upsert(
                entry(
                    entryId = "FAIL-PENDING",
                    deliveryState = "PENDING",
                    ownerPrincipal = "operator-a"
                )
            )

            dao.upsert(
                entry(
                    entryId = "FAIL-FAILED",
                    deliveryState = "FAILED",
                    ownerPrincipal = "operator-a"
                )
            )

            dao.upsert(
                entry(
                    entryId = "FAIL-SENT",
                    deliveryState = "SENT",
                    ownerPrincipal = "operator-a"
                )
            )

            dao.upsert(
                entry(
                    entryId = "FAIL-WRONG-OWNER",
                    deliveryState = "PENDING",
                    ownerPrincipal = "operator-a"
                )
            )

            val pendingChanged =
                dao.failPending(
                    entryId = "FAIL-PENDING",
                    ownerPrincipal = "operator-a"
                )

            val failedChanged =
                dao.failPending(
                    entryId = "FAIL-FAILED",
                    ownerPrincipal = "operator-a"
                )

            val sentChanged =
                dao.failPending(
                    entryId = "FAIL-SENT",
                    ownerPrincipal = "operator-a"
                )

            val wrongOwnerChanged =
                dao.failPending(
                    entryId = "FAIL-WRONG-OWNER",
                    ownerPrincipal = "operator-b"
                )

            assertEquals(
                1,
                pendingChanged
            )

            assertEquals(
                "FAILED",
                dao.getById(
                    "FAIL-PENDING"
                )?.deliveryState
            )

            assertEquals(
                0,
                failedChanged
            )

            assertEquals(
                "FAILED",
                dao.getById(
                    "FAIL-FAILED"
                )?.deliveryState
            )

            assertEquals(
                0,
                sentChanged
            )

            assertEquals(
                "SENT",
                dao.getById(
                    "FAIL-SENT"
                )?.deliveryState
            )

            assertEquals(
                0,
                wrongOwnerChanged
            )

            assertEquals(
                "PENDING",
                dao.getById(
                    "FAIL-WRONG-OWNER"
                )?.deliveryState
            )
        }
    private fun entry(
        entryId: String,
        deliveryState: String,
        ownerPrincipal: String
    ): TimelineEntryEntity {

        return TimelineEntryEntity(
            entryId = entryId,
            incidentId = "INC-RETRY",
            message = "Retry transition test",
            author = "Operator",
            occurredAt = 1_000L,
            deliveryState = deliveryState,
            ownerPrincipal = ownerPrincipal
        )
    }
}
