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
class DeferredRealtimeEventMigrationTest {

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
                                    12
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
                                        CREATE TABLE timeline_entries (
                                            entryId TEXT NOT NULL,
                                            incidentId TEXT NOT NULL,
                                            message TEXT NOT NULL,
                                            author TEXT NOT NULL,
                                            occurredAt INTEGER NOT NULL,
                                            deliveryState TEXT NOT NULL,
                                            ownerPrincipal TEXT NOT NULL,
                                            PRIMARY KEY(entryId)
                                        )
                                        """.trimIndent()
                                    )
                                }

                                override fun onUpgrade(
                                    db:
                                        SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int
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
    fun migration12To13PreservesExistingRowsAndCreatesDeferredSchema() {

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
                'INC-MIGRATION',
                'Migration incident',
                'Active',
                'HIGH',
                42
            )
            """.trimIndent()
        )

        database.execSQL(
            """
            INSERT INTO timeline_entries (
                entryId,
                incidentId,
                message,
                author,
                occurredAt,
                deliveryState,
                ownerPrincipal
            )
            VALUES (
                'ENTRY-MIGRATION',
                'INC-MIGRATION',
                'Existing timeline entry',
                'operator',
                12345,
                'SENT',
                'operator-a'
            )
            """.trimIndent()
        )

        RelayDatabase
            .MIGRATION_12_13
            .migrate(
                database
            )

        database
            .query(
                """
                SELECT
                    title,
                    latestSequence
                FROM incidents
                WHERE id = 'INC-MIGRATION'
                """.trimIndent()
            )
            .use {
                cursor ->

                assertTrue(
                    cursor.moveToFirst()
                )

                assertEquals(
                    "Migration incident",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "title"
                        )
                    )
                )

                assertEquals(
                    42L,
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
                    message,
                    ownerPrincipal
                FROM timeline_entries
                WHERE entryId = 'ENTRY-MIGRATION'
                """.trimIndent()
            )
            .use {
                cursor ->

                assertTrue(
                    cursor.moveToFirst()
                )

                assertEquals(
                    "INC-MIGRATION",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "incidentId"
                        )
                    )
                )

                assertEquals(
                    "Existing timeline entry",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "message"
                        )
                    )
                )

                assertEquals(
                    "operator-a",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "ownerPrincipal"
                        )
                    )
                )
            }

        val expectedColumns =
            setOf(
                "eventId",
                "incidentId",
                "entryId",
                "message",
                "author",
                "occurredAt",
                "deferredAt"
            )

        val actualColumns =
            mutableSetOf<String>()

        database
            .query(
                """
                PRAGMA table_info(
                    deferred_realtime_events
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

                    actualColumns +=
                        cursor.getString(
                            nameIndex
                        )
                }
            }

        assertEquals(
            expectedColumns,
            actualColumns
        )

        var foundIncidentIndex =
            false

        database
            .query(
                """
                PRAGMA index_list(
                    deferred_realtime_events
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

                    if (
                        cursor.getString(
                            nameIndex
                        ) ==
                        "index_deferred_realtime_events_incidentId"
                    ) {

                        foundIncidentIndex =
                            true

                        break
                    }
                }
            }

        assertTrue(
            foundIncidentIndex
        )
    }

    private companion object {

        const val DATABASE_NAME =
            "deferred-realtime-migration-test.db"
    }
}
