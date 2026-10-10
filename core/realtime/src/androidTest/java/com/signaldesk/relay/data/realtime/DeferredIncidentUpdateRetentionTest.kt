package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.DeferredIncidentUpdateEntity
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeferredIncidentUpdateRetentionTest {

    private lateinit var database:
        RelayDatabase

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
    }

    @After
    fun tearDown() {

        database.close()
    }

    @Test
    fun retentionDeletesOnlyOldOrphansAndPreservesParentBackedDeferredUpdates() =
        runBlocking {

            val dao =
                database
                    .deferredIncidentUpdateDao()

            val cutoff =
                1_000_000L

            /*
             * Old orphan:
             * no parent incident exists, so retention may delete it.
             */
            dao.insert(
                DeferredIncidentUpdateEntity(
                    eventId =
                        "EVENT-OLD-ORPHAN",
                    incidentId =
                        "INC-OLD-ORPHAN",
                    occurredAt =
                        100L,
                    sequence =
                        2L,
                    title =
                        "Old orphan",
                    status =
                        "Investigating",
                    severity =
                        "HIGH",
                    deferredAt =
                        cutoff - 1L
                )
            )

            /*
             * Fresh orphan:
             * no parent yet, but it is younger than the cutoff.
             */
            dao.insert(
                DeferredIncidentUpdateEntity(
                    eventId =
                        "EVENT-FRESH-ORPHAN",
                    incidentId =
                        "INC-FRESH-ORPHAN",
                    occurredAt =
                        200L,
                    sequence =
                        2L,
                    title =
                        "Fresh orphan",
                    status =
                        "Investigating",
                    severity =
                        "MEDIUM",
                    deferredAt =
                        cutoff + 1L
                )
            )

            /*
             * Parent-backed deferred work:
             *
             * It is intentionally older than the cutoff. Age alone must
             * never permit pruning because this is recoverable work for
             * an incident that now exists locally.
             */
            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-PARENT-BACKED",
                        title =
                            "Parent",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        latestSequence =
                            1L
                    )
                )

            dao.insert(
                DeferredIncidentUpdateEntity(
                    eventId =
                        "EVENT-PARENT-BACKED",
                    incidentId =
                        "INC-PARENT-BACKED",
                    occurredAt =
                        300L,
                    sequence =
                        3L,
                    title =
                        "Parent-backed update",
                    status =
                        "Resolved",
                    severity =
                        "HIGH",
                    deferredAt =
                        cutoff - 10_000L
                )
            )

            val deleted =
                dao.deleteOrphansBefore(
                    cutoff
                )

            assertEquals(
                1,
                deleted
            )

            assertEquals(
                0,
                dao
                    .loadForIncident(
                        "INC-OLD-ORPHAN"
                    )
                    .size
            )

            assertEquals(
                1,
                dao
                    .loadForIncident(
                        "INC-FRESH-ORPHAN"
                    )
                    .size
            )

            assertEquals(
                1,
                dao
                    .loadForIncident(
                        "INC-PARENT-BACKED"
                    )
                    .size
            )

            assertEquals(
                "EVENT-PARENT-BACKED",
                dao
                    .loadForIncident(
                        "INC-PARENT-BACKED"
                    )
                    .single()
                    .eventId
            )
        }
}
