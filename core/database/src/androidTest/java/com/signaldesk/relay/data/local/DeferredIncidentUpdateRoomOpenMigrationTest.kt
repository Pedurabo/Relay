package com.signaldesk.relay.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.signaldesk.relay.model.DeliveryState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeferredIncidentUpdateRoomOpenMigrationTest {

    private lateinit var context:
        Context

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

        context.deleteDatabase(
            DATABASE_NAME
        )
    }

    @Test
    fun roomOpenFrom13To14ValidatesSchemaAndPreservesExistingData() =
        runBlocking {

            /*
             * First let Room itself create the complete current schema.
             *
             * We then convert that physical database back to the exact
             * structural delta represented by v13:
             *
             * v14 = v13 + deferred_incident_updates.
             *
             * This avoids hand-maintaining the rest of the v13 schema.
             */
            val currentDatabase =
                Room
                    .databaseBuilder(
                        context,
                        RelayDatabase::class.java,
                        DATABASE_NAME
                    )
                    .allowMainThreadQueries()
                    .build()

            currentDatabase
                .openHelper
                .writableDatabase

            currentDatabase
                .incidentDao()
                .insert(
                    IncidentEntity(
                        id =
                            "INC-ROOM-MIGRATION",
                        title =
                            "Existing incident",
                        status =
                            "Active",
                        severity =
                            "MEDIUM",
                        latestSequence =
                            7L
                    )
                )

            currentDatabase
                .timelineEntryDao()
                .upsert(
                    TimelineEntryEntity(
                        entryId =
                            "ENTRY-ROOM-MIGRATION",
                        incidentId =
                            "INC-ROOM-MIGRATION",
                        message =
                            "Existing timeline entry",
                        author =
                            "operator",
                        occurredAt =
                            12_345L,
                        deliveryState =
                            DeliveryState.SENT.name,
                        ownerPrincipal =
                            "operator-a"
                    )
                )

            currentDatabase
                .deferredRealtimeEventDao()
                .insert(
                    DeferredRealtimeEventEntity(
                        eventId =
                            "EVENT-DEFERRED-TIMELINE",
                        incidentId =
                            "INC-MISSING",
                        entryId =
                            "ENTRY-DEFERRED-TIMELINE",
                        message =
                            "Deferred timeline message",
                        author =
                            "remote-operator",
                        occurredAt =
                            20_000L,
                        deferredAt =
                            21_000L
                    )
                )

            currentDatabase.close()

            /*
             * MIGRATION_13_14 only introduces this table/index.
             *
             * Dropping that table from a Room-created v14 database gives
             * us the complete pre-v14 structure without manually copying
             * all existing tables, indexes, affinities and constraints.
             */
            val databasePath =
                context.getDatabasePath(
                    DATABASE_NAME
                )

            val rawDatabase =
                SQLiteDatabase.openDatabase(
                    databasePath.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )

            rawDatabase.execSQL(
                """
                DROP TABLE deferred_incident_updates
                """.trimIndent()
            )

            rawDatabase.execSQL(
                """
                PRAGMA user_version = 13
                """.trimIndent()
            )

            rawDatabase.close()

            /*
             * This is the important boundary:
             *
             * Room opens a database physically marked v13, executes the
             * real MIGRATION_13_14, and validates its resulting schema
             * against the current RelayDatabase v14 definition.
             *
             * A mismatch in column affinity, nullability, primary key,
             * table shape or index definition causes this open to fail.
             */
            val migratedDatabase =
                Room
                    .databaseBuilder(
                        context,
                        RelayDatabase::class.java,
                        DATABASE_NAME
                    )
                    .addMigrations(
                        RelayDatabase.MIGRATION_13_14
                    )
                    .allowMainThreadQueries()
                    .build()

            migratedDatabase
                .openHelper
                .writableDatabase

            val incident =
                requireNotNull(
                    migratedDatabase
                        .incidentDao()
                        .getById(
                            "INC-ROOM-MIGRATION"
                        )
                )

            assertEquals(
                "Existing incident",
                incident.title
            )

            assertEquals(
                "Active",
                incident.status
            )

            assertEquals(
                "MEDIUM",
                incident.severity
            )

            assertEquals(
                7L,
                incident.latestSequence
            )

            val timelineEntry =
                requireNotNull(
                    migratedDatabase
                        .timelineEntryDao()
                        .getById(
                            "ENTRY-ROOM-MIGRATION"
                        )
                )

            assertEquals(
                "INC-ROOM-MIGRATION",
                timelineEntry.incidentId
            )

            assertEquals(
                "Existing timeline entry",
                timelineEntry.message
            )

            assertEquals(
                "operator-a",
                timelineEntry.ownerPrincipal
            )

            val deferredTimeline =
                migratedDatabase
                    .deferredRealtimeEventDao()
                    .loadForIncident(
                        "INC-MISSING"
                    )

            assertEquals(
                1,
                deferredTimeline.size
            )

            assertEquals(
                "EVENT-DEFERRED-TIMELINE",
                deferredTimeline
                    .single()
                    .eventId
            )

            /*
             * Exercise the newly migrated table through the actual
             * Room-generated DAO. Successful insertion/read proves the
             * migrated table is usable by Room, not merely present.
             */
            val insertResult =
                migratedDatabase
                    .deferredIncidentUpdateDao()
                    .insert(
                        DeferredIncidentUpdateEntity(
                            eventId =
                                "EVENT-UPDATE-ROOM",
                            incidentId =
                                "INC-ROOM-MIGRATION",
                            occurredAt =
                                30_000L,
                            sequence =
                                8L,
                            title =
                                "Updated incident",
                            status =
                                null,
                            severity =
                                "HIGH",
                            deferredAt =
                                31_000L
                        )
                    )

            assertTrue(
                insertResult != -1L
            )

            val deferredUpdates =
                migratedDatabase
                    .deferredIncidentUpdateDao()
                    .loadForIncident(
                        "INC-ROOM-MIGRATION"
                    )

            assertEquals(
                1,
                deferredUpdates.size
            )

            val deferredUpdate =
                deferredUpdates.single()

            assertEquals(
                "EVENT-UPDATE-ROOM",
                deferredUpdate.eventId
            )

            assertEquals(
                8L,
                deferredUpdate.sequence
            )

            assertEquals(
                "Updated incident",
                deferredUpdate.title
            )

            assertEquals(
                "HIGH",
                deferredUpdate.severity
            )

            assertNotNull(
                migratedDatabase
                    .openHelper
                    .writableDatabase
            )

            migratedDatabase.close()
        }

    companion object {

        private const val DATABASE_NAME =
            "relay-room-open-v13-v14-migration.db"
    }
}
