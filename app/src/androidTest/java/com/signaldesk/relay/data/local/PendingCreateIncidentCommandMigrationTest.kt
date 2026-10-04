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
class PendingCreateIncidentCommandMigrationTest {

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
                                    11
                                ) {

                                override fun onCreate(
                                    db:
                                        SupportSQLiteDatabase
                                ) = Unit

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
    fun migration11To12_createsDurableOwnerScopedIncidentCreateQueue() {

        RelayDatabase
            .MIGRATION_11_12
            .migrate(
                database
            )

        val expectedColumns =
            setOf(
                "commandId",
                "incidentId",
                "title",
                "status",
                "severity",
                "ownerPrincipal",
                "createdAt",
                "deliveryState"
            )

        val actualColumns =
            mutableSetOf<String>()

        database
            .query(
                """
                PRAGMA table_info(
                    pending_create_incident_commands
                )
                """.trimIndent()
            )
            .use { cursor ->

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

        database.execSQL(
            """
            INSERT INTO pending_create_incident_commands (
                commandId,
                incidentId,
                title,
                status,
                severity,
                ownerPrincipal,
                createdAt,
                deliveryState
            )
            VALUES (
                'CMD-CREATE-MIGRATION',
                'INC-CREATE-MIGRATION',
                'Database migration proof',
                'Investigating',
                'HIGH',
                'operator-a',
                12345,
                'PENDING'
            )
            """.trimIndent()
        )

        database
            .query(
                """
                SELECT
                    incidentId,
                    title,
                    status,
                    severity,
                    ownerPrincipal,
                    deliveryState
                FROM pending_create_incident_commands
                WHERE commandId =
                    'CMD-CREATE-MIGRATION'
                """.trimIndent()
            )
            .use { cursor ->

                assertTrue(
                    cursor.moveToFirst()
                )

                assertEquals(
                    "INC-CREATE-MIGRATION",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "incidentId"
                        )
                    )
                )

                assertEquals(
                    "Database migration proof",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "title"
                        )
                    )
                )

                assertEquals(
                    "Investigating",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "status"
                        )
                    )
                )

                assertEquals(
                    "HIGH",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "severity"
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

                assertEquals(
                    "PENDING",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "deliveryState"
                        )
                    )
                )
            }
    }

    private companion object {

        const val DATABASE_NAME =
            "relay-migration-11-12-test.db"
    }
}