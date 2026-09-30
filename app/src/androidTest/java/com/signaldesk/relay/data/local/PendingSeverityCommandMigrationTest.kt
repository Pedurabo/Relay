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
class PendingSeverityCommandMigrationTest {

    private lateinit var context: Context
    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var database: SupportSQLiteDatabase

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
                        .builder(context)
                        .name(DATABASE_NAME)
                        .callback(
                            object :
                                SupportSQLiteOpenHelper.Callback(
                                    8
                                ) {

                                override fun onCreate(
                                    db:
                                        SupportSQLiteDatabase
                                ) {

                                    db.execSQL(
                                        """
                                        CREATE TABLE pending_severity_commands (
                                            commandId TEXT NOT NULL,
                                            incidentId TEXT NOT NULL,
                                            severity TEXT NOT NULL,
                                            baseSeverity TEXT NOT NULL,
                                            createdAt INTEGER NOT NULL,
                                            deliveryState TEXT NOT NULL,
                                            PRIMARY KEY(commandId)
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
    fun migration8To9_addsEmptyOwnerToLegacyRows() {

        database.execSQL(
            """
            INSERT INTO pending_severity_commands (
                commandId,
                incidentId,
                severity,
                baseSeverity,
                createdAt,
                deliveryState
            )
            VALUES (
                'CMD-LEGACY',
                'INC-LEGACY',
                'CRITICAL',
                'HIGH',
                12345,
                'PENDING'
            )
            """.trimIndent()
        )

        RelayDatabase
            .MIGRATION_8_9
            .migrate(
                database
            )

        database
            .query(
                """
                PRAGMA table_info(
                    pending_severity_commands
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
                    commandId,
                    ownerPrincipal
                FROM pending_severity_commands
                WHERE commandId = 'CMD-LEGACY'
                """.trimIndent()
            )
            .use { cursor ->

                assertTrue(
                    cursor.moveToFirst()
                )

                assertEquals(
                    "CMD-LEGACY",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "commandId"
                        )
                    )
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
            "relay-migration-8-9-test.db"
    }
}
