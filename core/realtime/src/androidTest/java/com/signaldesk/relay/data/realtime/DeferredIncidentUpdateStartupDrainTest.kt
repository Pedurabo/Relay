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
class DeferredIncidentUpdateStartupDrainTest {

    private lateinit var database:
        RelayDatabase

    private lateinit var processor:
        IncidentEventProcessor

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

        processor =
            IncidentEventProcessor(
                database
            )
    }

    @After
    fun tearDown() {

        database.close()
    }

    @Test
    fun startupRecoveryDrainsContiguousDeferredChainInSinglePass() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-STARTUP",
                        title =
                            "Initial",
                        status =
                            "Active",
                        severity =
                            "LOW",
                        latestSequence =
                            1L
                    )
                )

            /*
             * Insert out of arrival order. DAO ordering should expose
             * them as seq=2 then seq=3 during the one startup scan.
             */
            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-STARTUP-3",
                        incidentId =
                            "INC-STARTUP",
                        occurredAt =
                            3_000L,
                        sequence =
                            3L,
                        title =
                            "Third",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        deferredAt =
                            100L
                    )
                )

            database
                .deferredIncidentUpdateDao()
                .insert(
                    DeferredIncidentUpdateEntity(
                        eventId =
                            "EVENT-STARTUP-2",
                        incidentId =
                            "INC-STARTUP",
                        occurredAt =
                            2_000L,
                        sequence =
                            2L,
                        title =
                            "Second",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM",
                        deferredAt =
                            200L
                    )
                )

            processor
                .recoverDeferredIncidentUpdates()

            val incident =
                requireNotNull(
                    database
                        .incidentDao()
                        .getById(
                            "INC-STARTUP"
                        )
                )

            assertEquals(
                3L,
                incident.latestSequence
            )

            assertEquals(
                "Third",
                incident.title
            )

            assertEquals(
                "Resolved",
                incident.status
            )

            assertEquals(
                "HIGH",
                incident.severity
            )

            assertEquals(
                0,
                database
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-STARTUP"
                    )
                    .size
            )

            assertEquals(
                null,
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-STARTUP"
                    )
            )
        }
}
