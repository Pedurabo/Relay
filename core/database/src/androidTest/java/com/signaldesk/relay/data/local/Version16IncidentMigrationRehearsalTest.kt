package com.signaldesk.relay.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test-only SQLite rehearsal for the proposed v16 incident identity.
 *
 * Uses the exported v15 schema and intentionally does not register
 * a production MIGRATION_15_16.
 *
 * An empty ownerPrincipal is quarantined legacy ownership.
 */
@RunWith(AndroidJUnit4::class)
class Version16IncidentMigrationRehearsalTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RelayDatabase::class.java
    )

    private val databaseName =
        "relay-246-incident-migration-rehearsal.db"

    private fun seedLegacyIncident(
        db: SupportSQLiteDatabase
    ) {
        db.execSQL(
            """
            INSERT INTO incidents
                (id, title, status, latestSequence, severity)
            VALUES
                ('INC-SHARED', 'Legacy private incident',
                 'OPEN', 8, 'HIGH')
            """.trimIndent()
        )
    }

    private fun migrateIncidentTable(
        db: SupportSQLiteDatabase
    ) {
        db.beginTransaction()

        try {
            db.execSQL(
                """
                ALTER TABLE incidents
                RENAME TO incidents_v15_legacy
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE incidents (
                    ownerPrincipal TEXT NOT NULL,
                    id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    status TEXT NOT NULL,
                    latestSequence INTEGER NOT NULL,
                    severity TEXT NOT NULL,
                    PRIMARY KEY(ownerPrincipal, id)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO incidents
                    (
                        ownerPrincipal,
                        id,
                        title,
                        status,
                        latestSequence,
                        severity
                    )
                SELECT
                    '',
                    id,
                    title,
                    status,
                    latestSequence,
                    severity
                FROM incidents_v15_legacy
                """.trimIndent()
            )

            db.execSQL(
                """
                DROP TABLE incidents_v15_legacy
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                index_incidents_ownerPrincipal_id
                ON incidents(ownerPrincipal, id)
                """.trimIndent()
            )

            db.setTransactionSuccessful()

        } finally {
            db.endTransaction()
        }
    }

    private fun count(
        db: SupportSQLiteDatabase,
        owner: String
    ): Int {
        db.query(
            """
            SELECT COUNT(*)
            FROM incidents
            WHERE ownerPrincipal = ?
            """.trimIndent(),
            arrayOf(owner)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun title(
        db: SupportSQLiteDatabase,
        owner: String,
        incidentId: String
    ): String? {
        db.query(
            """
            SELECT title
            FROM incidents
            WHERE ownerPrincipal = ?
              AND id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(owner, incidentId)
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    }

    @Test
    fun legacyRowIsPreservedButNotVisibleToOtherOwners() {
        val db = helper.createDatabase(
            databaseName,
            15
        )

        try {
            seedLegacyIncident(db)
            migrateIncidentTable(db)

            assertEquals(1, count(db, ""))
            assertEquals(0, count(db, "account-a"))
            assertEquals(0, count(db, "account-b"))

            assertEquals(
                "Legacy private incident",
                title(db, "", "INC-SHARED")
            )

            assertEquals(
                null,
                title(db, "account-a", "INC-SHARED")
            )

            assertEquals(
                null,
                title(db, "account-b", "INC-SHARED")
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun twoOwnersCanStoreSameIncidentIdIndependently() {
        val db = helper.createDatabase(
            databaseName,
            15
        )

        try {
            seedLegacyIncident(db)
            migrateIncidentTable(db)

            db.execSQL(
                """
                INSERT INTO incidents
                    (ownerPrincipal, id, title, status,
                     latestSequence, severity)
                VALUES
                    ('account-a', 'INC-SHARED',
                     'Account A incident', 'OPEN', 1, 'HIGH')
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO incidents
                    (ownerPrincipal, id, title, status,
                     latestSequence, severity)
                VALUES
                    ('account-b', 'INC-SHARED',
                     'Account B incident', 'OPEN', 2, 'LOW')
                """.trimIndent()
            )

            assertEquals(1, count(db, ""))
            assertEquals(1, count(db, "account-a"))
            assertEquals(1, count(db, "account-b"))

            assertEquals(
                "Account A incident",
                title(db, "account-a", "INC-SHARED")
            )

            assertEquals(
                "Account B incident",
                title(db, "account-b", "INC-SHARED")
            )

            assertEquals(
                "Legacy private incident",
                title(db, "", "INC-SHARED")
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun updatingOneOwnerDoesNotModifyAnotherOwner() {
        val db = helper.createDatabase(
            databaseName,
            15
        )

        try {
            migrateIncidentTable(db)

            db.execSQL(
                """
                INSERT INTO incidents
                    (ownerPrincipal, id, title, status,
                     latestSequence, severity)
                VALUES
                    ('account-a', 'INC-ONE',
                     'Original A', 'OPEN', 1, 'HIGH')
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO incidents
                    (ownerPrincipal, id, title, status,
                     latestSequence, severity)
                VALUES
                    ('account-b', 'INC-ONE',
                     'Original B', 'OPEN', 1, 'LOW')
                """.trimIndent()
            )

            db.execSQL(
                """
                UPDATE incidents
                SET title = 'Updated A'
                WHERE ownerPrincipal = 'account-a'
                  AND id = 'INC-ONE'
                """.trimIndent()
            )

            assertEquals(
                "Updated A",
                title(db, "account-a", "INC-ONE")
            )

            assertEquals(
                "Original B",
                title(db, "account-b", "INC-ONE")
            )

            assertFalse(
                title(db, "account-b", "INC-ONE") ==
                    "Updated A"
            )
        } finally {
            db.close()
        }
    }
}