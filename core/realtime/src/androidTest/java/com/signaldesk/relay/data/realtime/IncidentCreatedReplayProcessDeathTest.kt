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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncidentCreatedReplayProcessDeathTest {

    private lateinit var context:
        Context

    private var database:
        RelayDatabase? =
            null

    @Before
    fun setUp() {

        context =
            ApplicationProvider
                .getApplicationContext()

        context.deleteDatabase(
            DATABASE_NAME
        )
    }

    @After
    fun tearDown() {

        database
            ?.close()

        context.deleteDatabase(
            DATABASE_NAME
        )
    }

    @Test
    fun originalCreateReplayRemainsDuplicateAfterRestartAndCannotRegressNewerUpdate() =
        runBlocking {

            database =
                Room
                    .databaseBuilder(
                        context,
                        RelayDatabase::class.java,
                        DATABASE_NAME
                    )
                    .allowMainThreadQueries()
                    .build()

            val firstDatabase =
                requireNotNull(
                    database
                )

            val firstProcessor =
                IncidentEventProcessor(
                    firstDatabase
                )

            val create =
                IncidentCreatedEvent(
                    eventId =
                        "EVENT-CREATE-PERSISTED",
                    incidentId =
                        "INC-CREATE-PERSISTED",
                    occurredAt =
                        1_000L,
                    title =
                        "Created title",
                    status =
                        "Active",
                    severity =
                        "LOW",
                    sequence =
                        1L
                )

            assertEquals(
                EventProcessingResult.APPLIED,
                firstProcessor.process(
                    create
                )
            )

            assertEquals(
                EventProcessingResult.APPLIED,
                firstProcessor.process(
                    IncidentUpdatedEvent(
                        eventId =
                            "EVENT-UPDATE-PERSISTED",
                        incidentId =
                            "INC-CREATE-PERSISTED",
                        occurredAt =
                            2_000L,
                        title =
                            "Updated title",
                        status =
                            "Resolved",
                        severity =
                            "HIGH",
                        sequence =
                            2L
                    )
                )
            )

            val beforeRestart =
                requireNotNull(
                    firstDatabase
                        .incidentDao()
                        .getById(
                            "INC-CREATE-PERSISTED"
                        )
                )

            assertEquals(
                2L,
                beforeRestart.latestSequence
            )

            assertEquals(
                "Updated title",
                beforeRestart.title
            )

            firstDatabase.close()

            database =
                Room
                    .databaseBuilder(
                        context,
                        RelayDatabase::class.java,
                        DATABASE_NAME
                    )
                    .allowMainThreadQueries()
                    .build()

            val recreatedDatabase =
                requireNotNull(
                    database
                )

            val recreatedProcessor =
                IncidentEventProcessor(
                    recreatedDatabase
                )

            /*
             * Same durable eventId after process recreation must remain a
             * pure duplicate and never revisit creation semantics.
             */
            assertEquals(
                EventProcessingResult.DUPLICATE,
                recreatedProcessor.process(
                    create.copy(
                        title =
                            "Conflicting replay title",
                        status =
                            "Investigating",
                        severity =
                            "MEDIUM"
                    )
                )
            )

            val afterRestart =
                requireNotNull(
                    recreatedDatabase
                        .incidentDao()
                        .getById(
                            "INC-CREATE-PERSISTED"
                        )
                )

            assertEquals(
                2L,
                afterRestart.latestSequence
            )

            assertEquals(
                "Updated title",
                afterRestart.title
            )

            assertEquals(
                "Resolved",
                afterRestart.status
            )

            assertEquals(
                "HIGH",
                afterRestart.severity
            )

            assertTrue(
                recreatedDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-CREATE-PERSISTED"
                    )
            )

            assertTrue(
                recreatedDatabase
                    .processedEventDao()
                    .exists(
                        "EVENT-UPDATE-PERSISTED"
                    )
            )

            assertEquals(
                null,
                recreatedDatabase
                    .incidentSequenceGapDao()
                    .getByIncidentId(
                        "INC-CREATE-PERSISTED"
                    )
            )
        }

    companion object {

        private const val DATABASE_NAME =
            "incident-create-replay-process-death.db"
    }
}
