package com.signaldesk.relay.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Characterization tests for the existing Room v15 schema.
 *
 * These tests intentionally document unsafe pre-isolation behavior.
 * Passing does NOT mean cross-account isolation is implemented.
 *
 * Replace these assertions with isolation acceptance tests when
 * the owner-scoped schema and DAO contract are introduced.
 */
@RunWith(AndroidJUnit4::class)
class AccountIsolationCharacterizationTest {

    private lateinit var database: RelayDatabase

    @Before
    fun setUp() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        database = Room.inMemoryDatabaseBuilder(
            context,
            RelayDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun incident(
        id: String,
        title: String
    ) = IncidentEntity(
        ownerPrincipal = "account-a",
        id = id,
        title = title,
        status = "OPEN",
        latestSequence = 1L,
        severity = "HIGH"
    )

    @Test
    fun existingIncidentObservationHasNoAccountBoundary() =
        runBlocking {

            database.incidentDao().insert(
                incident(
                    id = "INC-246-A",
                    title = "Account A confidential incident"
                )
            )

            /*
             * No principal can be supplied to observeAll().
             * A new observer receives every cached incident.
             */
            val observed =
                database.incidentDao()
                    .observeAll("account-a")
                    .first()

            assertEquals(1, observed.size)

            assertEquals(
                "Account A confidential incident",
                observed.single().title
            )
        }

    @Test
    fun existingIncidentPrimaryKeyCannotSeparateOwners() =
        runBlocking {

            database.incidentDao().insert(
                incident(
                    id = "INC-SHARED",
                    title = "Account A incident"
                )
            )

            /*
             * A second logical owner has no owner field.
             * REPLACE updates the same globally keyed row.
             */
            database.incidentDao().insert(
                incident(
                    id = "INC-SHARED",
                    title = "Account B incident"
                ).copy(ownerPrincipal = "account-b")
            )

            assertEquals(
                1,
                database.incidentDao().count("account-a")
            )

            val stored =
                database.incidentDao()
                    .getById("account-b", "INC-SHARED")

            assertNotNull(stored)

            assertEquals(
                "Account B incident",
                stored?.title
            )
        }

    @Test
    fun existingLatestTimelineObservationHasNoAccountBoundary() =
        runBlocking {

            database.incidentDao().insert(
                incident(
                    id = "INC-246-T",
                    title = "Account A timeline incident"
                )
            )

            database.timelineEntryDao().insert(
                TimelineEntryEntity(
                    entryId = "ENTRY-246-A",
                    incidentId = "INC-246-T",
                    message = "Account A private timeline",
                    author = "Account A",
                    occurredAt = 1000L,
                    deliveryState = "SENT",
                    ownerPrincipal = "account-a"
                )
            )

            /*
             * Even though the timeline row has ownerPrincipal,
             * the global latest-entry query does not filter it.
             */
            val latest =
                database.timelineEntryDao()
                    .observeLatestForAllIncidents("account-a")
                    .first()

            assertEquals(1, latest.size)

            assertEquals(
                "account-a",
                latest.single().ownerPrincipal
            )

            assertEquals(
                "Account A private timeline",
                latest.single().message
            )
        }
}