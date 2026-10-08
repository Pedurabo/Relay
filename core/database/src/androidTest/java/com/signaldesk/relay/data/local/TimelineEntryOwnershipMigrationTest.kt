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
class TimelineEntryOwnershipMigrationTest {

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
                                    9
                                ) {

                                override fun onCreate(
                                    db:
                                        SupportSQLiteDatabase
                                ) {

                                    db.execSQL(
                                        """
                                        CREATE TABLE timeline_entries (
                                            entryId TEXT NOT NULL,
                                            incidentId TEXT NOT NULL,
                                            message TEXT NOT NULL,
                                            author TEXT NOT NULL,
                                            occurredAt INTEGER NOT NULL,
                                            deliveryState TEXT NOT NULL,
                                            PRIMARY KEY(entryId)
                                        )
                                        """.trimIndent()
                                    )

                                    db.execSQL(
                                        """
                                        CREATE INDEX IF NOT EXISTS
                                        index_timeline_entries_incidentId
                                        ON timeline_entries(incidentId)
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
    fun migration9To10AddsEmptyOwnerToLegacyTimelineRows() {

        database.execSQL(
            """
            INSERT INTO timeline_entries (
                entryId,
                incidentId,
                message,
                author,
                occurredAt,
                deliveryState
            )
            VALUES (
                'ENTRY-LEGACY',
                'INC-LEGACY',
                'legacy update',
                'operator',
                12345,
                'PENDING'
            )
            """.trimIndent()
        )

        RelayDatabase
            .MIGRATION_9_10
            .migrate(
                database
            )

        database
            .query(
                """
                PRAGMA table_info(
                    timeline_entries
                )
                """.trimIndent()
            )
            .use { cursor ->

                var foundOwnerColumn =
                    false

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
                        "ownerPrincipal"
                    ) {

                        foundOwnerColumn =
                            true

                        break
                    }
                }

                assertTrue(
                    foundOwnerColumn
                )
            }

        database
            .query(
                """
                SELECT
                    entryId,
                    ownerPrincipal
                FROM timeline_entries
                WHERE entryId = 'ENTRY-LEGACY'
                """.trimIndent()
            )
            .use { cursor ->

                assertTrue(
                    cursor.moveToFirst()
                )

                assertEquals(
                    "",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "ownerPrincipal"
                        )
                    )
                )
            }
    }


    private companion object {

        const val DATABASE_NAME =
            "relay-migration-9-10-test.db"
    }
}
