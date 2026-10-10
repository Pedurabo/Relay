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
class TimelineAcknowledgementTransitionTest {

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
            database.timelineEntryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun acknowledgementMarksPendingSentWithoutChangingPayload() =
        runBlocking {

            dao.upsert(
                TimelineEntryEntity(
                    entryId =
                        "ENTRY-ACK",
                    incidentId =
                        "INC-ACK",
                    message =
                        "Original message",
                    author =
                        "You",
                    occurredAt =
                        1_000L,
                    deliveryState =
                        "PENDING",
                    ownerPrincipal =
                        "operator-a"
                )
            )

            val changed =
                dao.acknowledgePending(
                    entryId =
                        "ENTRY-ACK",
                    ownerPrincipal =
                        "operator-a"
                )

            assertEquals(
                1,
                changed
            )

            val acknowledged =
                requireNotNull(
                    dao.getById(
                        "ENTRY-ACK"
                    )
                )

            assertEquals(
                "SENT",
                acknowledged.deliveryState
            )

            assertEquals(
                "INC-ACK",
                acknowledged.incidentId
            )

            assertEquals(
                "Original message",
                acknowledged.message
            )

            assertEquals(
                "You",
                acknowledged.author
            )

            assertEquals(
                1_000L,
                acknowledged.occurredAt
            )
        }

    @Test
    fun acknowledgementCannotRewriteAlreadySentEntry() =
        runBlocking {

            dao.upsert(
                TimelineEntryEntity(
                    entryId =
                        "ENTRY-SENT",
                    incidentId =
                        "INC-ACK",
                    message =
                        "Canonical message",
                    author =
                        "Server author",
                    occurredAt =
                        2_000L,
                    deliveryState =
                        "SENT",
                    ownerPrincipal =
                        "operator-a"
                )
            )

            val changed =
                dao.acknowledgePending(
                    entryId =
                        "ENTRY-SENT",
                    ownerPrincipal =
                        "operator-a"
                )

            assertEquals(
                0,
                changed
            )
        }

    @Test
    fun lateAcknowledgementAfterAuthoritativeSentIsNoOp() =
        runBlocking {

            dao.upsert(
                TimelineEntryEntity(
                    entryId =
                        "ENTRY-AUTH-FIRST",
                    incidentId =
                        "INC-AUTH-FIRST",
                    message =
                        "Canonical message",
                    author =
                        "Server author",
                    occurredAt =
                        5_000L,
                    deliveryState =
                        "SENT",
                    ownerPrincipal =
                        "operator-a"
                )
            )

            val changed =
                dao.acknowledgePending(
                    entryId =
                        "ENTRY-AUTH-FIRST",
                    ownerPrincipal =
                        "operator-a"
                )

            assertEquals(
                0,
                changed
            )

            val stored =
                requireNotNull(
                    dao.getById(
                        "ENTRY-AUTH-FIRST"
                    )
                )

            assertEquals(
                "INC-AUTH-FIRST",
                stored.incidentId
            )

            assertEquals(
                "Canonical message",
                stored.message
            )

            assertEquals(
                "Server author",
                stored.author
            )

            assertEquals(
                5_000L,
                stored.occurredAt
            )

            assertEquals(
                "SENT",
                stored.deliveryState
            )

            assertEquals(
                "operator-a",
                stored.ownerPrincipal
            )
        }
}
