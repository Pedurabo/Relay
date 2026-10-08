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
class PendingStatusCommandMigrationTest {

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
                                    10
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
    fun migration10To11_createsDurableOwnerScopedStatusQueue() {

        RelayDatabase
            .MIGRATION_10_11
            .migrate(
                database
            )

        val expectedColumns =
            setOf(
                "commandId",
                "incidentId",
                "status",
                "baseStatus",
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
                    pending_status_commands
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
            INSERT INTO pending_status_commands (
                commandId,
                incidentId,
                status,
                baseStatus,
                ownerPrincipal,
                createdAt,
                deliveryState
            )
            VALUES (
                'CMD-STATUS-MIGRATION',
                'INC-STATUS-MIGRATION',
                'Monitoring',
                'Active',
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
                    status,
                    baseStatus,
                    ownerPrincipal,
                    deliveryState
                FROM pending_status_commands
                WHERE commandId =
                    'CMD-STATUS-MIGRATION'
                """.trimIndent()
            )
            .use { cursor ->

                assertTrue(
                    cursor.moveToFirst()
                )

                assertEquals(
                    "INC-STATUS-MIGRATION",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "incidentId"
                        )
                    )
                )

                assertEquals(
                    "Monitoring",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "status"
                        )
                    )
                )

                assertEquals(
                    "Active",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "baseStatus"
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
            "relay-migration-10-11-test.db"
    }
}