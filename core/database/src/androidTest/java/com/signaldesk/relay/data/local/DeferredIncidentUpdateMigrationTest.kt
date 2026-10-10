package com.signaldesk.relay.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeferredIncidentUpdateMigrationTest {

    private lateinit var context:
        Context

    private lateinit var helper:
        SupportSQLiteOpenHelper

    private lateinit var database:
        SupportSQLiteDatabase

    @Before
    fun setUp() {

        context =
            ApplicationProvider
                .getApplicationContext()

        context.deleteDatabase(
            DATABASE_NAME
        )

        helper =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    SupportSQLiteOpenHelper
                        .Configuration
                        .builder(
                            context
                        )
                        .name(
                            DATABASE_NAME
                        )
                        .callback(
                            object :
                                SupportSQLiteOpenHelper.Callback(
                                    13
                                ) {

                                override fun onCreate(
                                    db:
                                        SupportSQLiteDatabase
                                ) {

                                    db.execSQL(
                                        """
                                        CREATE TABLE incidents (
                                            id TEXT NOT NULL,
                                            title TEXT NOT NULL,
                                            status TEXT NOT NULL,
                                            severity TEXT NOT NULL,
                                            latestSequence INTEGER NOT NULL,
                                            PRIMARY KEY(id)
                                        )
                                        """.trimIndent()
                                    )

                                    db.execSQL(
                                        """
                                        CREATE TABLE deferred_realtime_events (
                                            eventId TEXT NOT NULL,
                                            incidentId TEXT NOT NULL,
                                            entryId TEXT NOT NULL,
                                            message TEXT NOT NULL,
                                            author TEXT NOT NULL,
                                            occurredAt INTEGER NOT NULL,
                                            deferredAt INTEGER NOT NULL,
                                            PRIMARY KEY(eventId)
                                        )
                                        """.trimIndent()
                                    )

                                    db.execSQL(
                                        """
                                        CREATE INDEX
                                        index_deferred_realtime_events_incidentId
                                        ON deferred_realtime_events(incidentId)
                                        """.trimIndent()
                                    )
                                }

                                override fun onUpgrade(
                                    db:
                                        SupportSQLiteDatabase,
                                    oldVersion:
                                        Int,
                                    newVersion:
                                        Int
                                ) = Unit
                            }
                        )
                        .build()
                )

        database =
            helper.writableDatabase
    }

    @After
    fun tearDown() {

        helper.close()

        context.deleteDatabase(
            DATABASE_NAME
        )
    }

    @Test
    fun migration13To14PreservesExistingRowsAndCreatesDeferredIncidentUpdates() {

        database.execSQL(
            """
            INSERT INTO incidents (
                id,
                title,
                status,
                severity,
                latestSequence
            )
            VALUES (
                'INC-V13',
                'Existing incident',
                'Active',
                'MEDIUM',
                7
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            INSERT INTO deferred_realtime_events (
                eventId,
                incidentId,
                entryId,
                message,
                author,
                occurredAt,
                deferredAt
            )
            VALUES (
                'EVENT-TIMELINE-V13',
                'INC-MISSING',
                'ENTRY-V13',
                'Deferred timeline message',
                'operator',
                12000,
                13000
            )
            """.trimIndent()
        )

        RelayDatabase
            .MIGRATION_13_14
            .migrate(
                database
            )

        database
            .query(
                """
                SELECT
                    title,
                    status,
                    severity,
                    latestSequence
                FROM incidents
                WHERE id = 'INC-V13'
                """.trimIndent()
            )
            .use {
                cursor ->

                assertTrue(
                    cursor.moveToFirst()
                )

                assertEquals(
                    "Existing incident",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "title"
                        )
                    )
                )

                assertEquals(
                    "Active",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "status"
                        )
                    )
                )

                assertEquals(
                    "MEDIUM",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "severity"
                        )
                    )
                )

                assertEquals(
                    7L,
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            "latestSequence"
                        )
                    )
                )
            }

        database
            .query(
                """
                SELECT
                    incidentId,
                    entryId,
                    message,
                    author,
                    occurredAt,
                    deferredAt
                FROM deferred_realtime_events
                WHERE eventId = 'EVENT-TIMELINE-V13'
                """.trimIndent()
            )
            .use {
                cursor ->

                assertTrue(
                    cursor.moveToFirst()
                )

                assertEquals(
                    "INC-MISSING",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "incidentId"
                        )
                    )
                )

                assertEquals(
                    "ENTRY-V13",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "entryId"
                        )
                    )
                )

                assertEquals(
                    "Deferred timeline message",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "message"
                        )
                    )
                )

                assertEquals(
                    "operator",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "author"
                        )
                    )
                )

                assertEquals(
                    12000L,
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            "occurredAt"
                        )
                    )
                )

                assertEquals(
                    13000L,
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(
                            "deferredAt"
                        )
                    )
                )
            }

        val columns =
            mutableSetOf<String>()

        database
            .query(
                """
                PRAGMA table_info(
                    deferred_incident_updates
                )
                """.trimIndent()
            )
            .use {
                cursor ->

                val nameIndex =
                    cursor.getColumnIndexOrThrow(
                        "name"
                    )

                while (
                    cursor.moveToNext()
                ) {

                    columns +=
                        cursor.getString(
                            nameIndex
                        )
                }
            }

        assertEquals(
            setOf(
                "eventId",
                "incidentId",
                "occurredAt",
                "sequence",
                "title",
                "status",
                "severity",
                "deferredAt"
            ),
            columns
        )

        database
            .query(
                """
                PRAGMA index_list(
                    deferred_incident_updates
                )
                """.trimIndent()
            )
            .use {
                cursor ->

                val nameIndex =
                    cursor.getColumnIndexOrThrow(
                        "name"
                    )

                var found =
                    false

                while (
                    cursor.moveToNext()
                ) {

                    if (
                        cursor.getString(
                            nameIndex
                        ) ==
                        "index_deferred_incident_updates_incidentId"
                    ) {
                        found =
                            true

                        break
                    }
                }

                assertTrue(
                    found
                )
            }
    }

    companion object {

        private const val DATABASE_NAME =
            "deferred-incident-update-migration-test.db"
    }
}
