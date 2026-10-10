package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.local.TimelineEntryEntity
import com.signaldesk.relay.data.remote.model.TimelineEntryAddedEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineAckFirstCanonicalConvergenceTest {

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
    fun authoritativeEventRefreshesServerOwnedFieldsAfterAck() =
        runBlocking {

            database
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-CONVERGENCE",
                        title =
                            "Canonical convergence",
                        status =
                            "Active"
                    )
                )

            /*
             * Represents the local optimistic row after the direct
             * transport ACK has already changed PENDING -> SENT.
             *
             * Payload fields are still the optimistic/local values.
             */
            database
                .timelineEntryDao()
                .upsert(
                    TimelineEntryEntity(
                        entryId =
                            "ENTRY-CONVERGENCE",
                        incidentId =
                            "INC-CONVERGENCE",
                        message =
                            "Engineer investigating",
                        author =
                            "You",
                        occurredAt =
                            1_000L,
                        deliveryState =
                            "SENT",
                        ownerPrincipal =
                            "operator-a"
                    )
                )

            val result =
                processor.process(
                    TimelineEntryAddedEvent(
                        eventId =
                            "EVENT-CONVERGENCE",
                        incidentId =
                            "INC-CONVERGENCE",
                        occurredAt =
                            2_000L,
                        entryId =
                            "ENTRY-CONVERGENCE",
                        message =
                            "Engineer investigating",
                        author =
                            "Alex"
                    )
                )

            assertEquals(
                EventProcessingResult.APPLIED,
                result
            )

            val stored =
                requireNotNull(
                    database
                        .timelineEntryDao()
                        .getById(
                            "ENTRY-CONVERGENCE"
                        )
                )

            /*
             * Immutable identity remains the same.
             */
            assertEquals(
                "ENTRY-CONVERGENCE",
                stored.entryId
            )

            assertEquals(
                "INC-CONVERGENCE",
                stored.incidentId
            )

            assertEquals(
                "Engineer investigating",
                stored.message
            )

            /*
             * Authoritative server-owned values replace the optimistic
             * local values even though the row was already SENT.
             */
            assertEquals(
                "Alex",
                stored.author
            )

            assertEquals(
                2_000L,
                stored.occurredAt
            )

            /*
             * The ACK state stays terminal and the local principal
             * remains attached to the row.
             */
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
