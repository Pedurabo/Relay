package com.signaldesk.relay.data.realtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.remote.model.IncidentCreatedEvent
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentSequenceGapTest {

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
            Room.inMemoryDatabaseBuilder(
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
    fun gapIsDetectedAndStateDoesNotAdvance() =
        runBlocking {

            processor.process(
                IncidentCreatedEvent(
                    eventId =
                        "EVT-GAP-101",
                    incidentId =
                        "INC-GAP",
                    occurredAt =
                        101L,
                    title =
                        "Sequence gap test",
                    status =
                        "Active",
                    sequence =
                        101L
                )
            )

            // #104 arrives while #102 and #103
            // are still missing.
            processor.process(
                IncidentUpdatedEvent(
                    eventId =
                        "EVT-GAP-104",
                    incidentId =
                        "INC-GAP",
                    occurredAt =
                        104L,
                    title = null,
                    status =
                        "Monitoring",
                    sequence =
                        104L
                )
            )

            val afterGap =
                database
                    .incidentDao()
                    .getById(
                        "INC-GAP"
                    )

            assertEquals(
                101L,
                afterGap
                    ?.latestSequence
            )

            assertEquals(
                "Active",
                afterGap
                    ?.status
            )

            val gap =
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-GAP"
                    )

            assertNotNull(
                gap
            )

            assertEquals(
                102L,
                gap?.expectedSequence
            )

            assertEquals(
                104L,
                gap?.receivedSequence
            )

            // Missing #102 arrives.
            processor.process(
                IncidentUpdatedEvent(
                    eventId =
                        "EVT-GAP-102",
                    incidentId =
                        "INC-GAP",
                    occurredAt =
                        102L,
                    title = null,
                    status =
                        "Investigating",
                    sequence =
                        102L
                )
            )

            val after102 =
                database
                    .incidentDao()
                    .getById(
                        "INC-GAP"
                    )

            assertEquals(
                102L,
                after102
                    ?.latestSequence
            )

            val gapAfter102 =
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-GAP"
                    )

            assertEquals(
                103L,
                gapAfter102
                    ?.expectedSequence
            )

            // Missing #103 arrives.
            processor.process(
                IncidentUpdatedEvent(
                    eventId =
                        "EVT-GAP-103",
                    incidentId =
                        "INC-GAP",
                    occurredAt =
                        103L,
                    title = null,
                    status =
                        "Recovering",
                    sequence =
                        103L
                )
            )

            val after103 =
                database
                    .incidentDao()
                    .getById(
                        "INC-GAP"
                    )

            assertEquals(
                103L,
                after103
                    ?.latestSequence
            )

            assertEquals(
                "Recovering",
                after103
                    ?.status
            )

            val gapAfter103 =
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-GAP"
                    )

            assertEquals(
                104L,
                gapAfter103
                    ?.expectedSequence
            )

            // The original #104 was intentionally
            // NOT applied or marked processed.
            //
            // Server replay now makes it applicable.
            processor.process(
                IncidentUpdatedEvent(
                    eventId =
                        "EVT-GAP-104",
                    incidentId =
                        "INC-GAP",
                    occurredAt =
                        104L,
                    title = null,
                    status =
                        "Monitoring",
                    sequence =
                        104L
                )
            )

            val finalState =
                database
                    .incidentDao()
                    .getById(
                        "INC-GAP"
                    )

            assertEquals(
                104L,
                finalState
                    ?.latestSequence
            )

            assertEquals(
                "Monitoring",
                finalState
                    ?.status
            )

            val finalGap =
                database
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-GAP"
                    )

            assertEquals(
                null,
                finalGap
            )
        }
}
