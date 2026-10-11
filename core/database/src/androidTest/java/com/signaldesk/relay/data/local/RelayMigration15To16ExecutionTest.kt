package com.signaldesk.relay.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Executes the actual, currently unregistered production migration.
 *
 * Uses only distinct relay-246-c2-execution fixture databases.
 * Does not open the application's relay.db.
 *
 * A successful test is not equivalent to Room v16 schema validation.
 */
@RunWith(AndroidJUnit4::class)
class RelayMigration15To16ExecutionTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RelayDatabase::class.java
    )

    private fun fixture(name: String): SupportSQLiteDatabase {
        return helper.createDatabase(
            "relay-246-c2-execution-$name.db",
            15
        )
    }

    private fun seed(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO incidents
            (id, title, status, latestSequence, severity)
            VALUES ('INC-SHARED', 'Legacy secret', 'OPEN', 9, 'HIGH')
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO timeline_entries
            (entryId, incidentId, message, author,
             occurredAt, deliveryState, ownerPrincipal)
            VALUES
            ('ENTRY-LEGACY', 'INC-SHARED', 'Unowned timeline',
             'Legacy', 100, 'SENT', ''),
            ('ENTRY-OWNED', 'INC-SHARED', 'Owner A pending',
             'Account A', 110, 'PENDING', 'account-a')
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO incident_sequence_gaps
            (incidentId, expectedSequence, receivedSequence, detectedAt)
            VALUES ('INC-SHARED', 2, 5, 100)
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO processed_events
            (eventId, processedAt)
            VALUES ('EVENT-SHARED', 100)
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO deferred_realtime_events
            (eventId, incidentId, entryId, message, author,
             occurredAt, deferredAt)
            VALUES
            ('EVENT-SHARED', 'INC-SHARED', 'ENTRY-LEGACY',
             'Deferred message', 'Legacy', 100, 200)
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO deferred_incident_updates
            (eventId, incidentId, occurredAt, sequence,
             title, status, severity, deferredAt)
            VALUES
            ('EVENT-SHARED', 'INC-SHARED', 100, 5,
             'Legacy title', 'OPEN', 'HIGH', 200)
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO pending_create_incident_commands
            (commandId, incidentId, title, status, severity,
             ownerPrincipal, createdAt, deliveryState)
            VALUES
            ('CREATE-A', 'INC-NEW', 'Account A draft',
             'OPEN', 'HIGH', 'account-a', 100, 'PENDING')
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO pending_status_commands
            (commandId, incidentId, status, baseStatus,
             ownerPrincipal, createdAt, deliveryState)
            VALUES
            ('STATUS-B', 'INC-SHARED', 'RESOLVED', 'OPEN',
             'account-b', 200, 'PENDING')
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO pending_severity_commands
            (commandId, incidentId, severity, baseSeverity,
             ownerPrincipal, createdAt, deliveryState)
            VALUES
            ('SEVERITY-A', 'INC-SHARED', 'CRITICAL', 'HIGH',
             'account-a', 300, 'PENDING')
            """.trimIndent()
        )
    }

    private fun count(
        db: SupportSQLiteDatabase,
        table: String,
        where: String = "1 = 1",
        args: Array<Any> = emptyArray()
    ): Int {
        db.query(
            "SELECT COUNT(*) FROM $table WHERE $where",
            args
        ).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun columnExists(
        db: SupportSQLiteDatabase,
        table: String,
        column: String
    ): Boolean {
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    return true
                }
            }
        }
        return false
    }

    private fun applyTransactionally(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            RELAY_MIGRATION_15_16.migrate(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Test
    fun migrationPreservesLegacyAndAllPendingCommandTypes() {
        val db = fixture("preserve")
        try {
            seed(db)
            applyTransactionally(db)

            for (table in listOf(
                "incidents",
                "incident_sequence_gaps",
                "processed_events",
                "deferred_realtime_events",
                "deferred_incident_updates"
            )) {
                assertEquals(
                    1,
                    count(db, table, "ownerPrincipal = ?", arrayOf(""))
                )
                assertEquals(
                    0,
                    count(
                        db, table,
                        "ownerPrincipal = ?",
                        arrayOf("account-a")
                    )
                )
                assertEquals(
                    0,
                    count(
                        db, table,
                        "ownerPrincipal = ?",
                        arrayOf("account-b")
                    )
                )
            }

            assertEquals(
                1,
                count(
                    db, "timeline_entries",
                    "ownerPrincipal = ?", arrayOf("")
                )
            )
            assertEquals(
                1,
                count(
                    db, "timeline_entries",
                    "ownerPrincipal = ?", arrayOf("account-a")
                )
            )

            assertEquals(
                1,
                count(
                    db, "pending_create_incident_commands",
                    "commandId = ? AND ownerPrincipal = ? " +
                        "AND deliveryState = ?",
                    arrayOf("CREATE-A", "account-a", "PENDING")
                )
            )

            assertEquals(
                1,
                count(
                    db, "pending_status_commands",
                    "commandId = ? AND ownerPrincipal = ? " +
                        "AND deliveryState = ?",
                    arrayOf("STATUS-B", "account-b", "PENDING")
                )
            )

            assertEquals(
                1,
                count(
                    db, "pending_severity_commands",
                    "commandId = ? AND ownerPrincipal = ? " +
                        "AND deliveryState = ?",
                    arrayOf("SEVERITY-A", "account-a", "PENDING")
                )
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun migratedKeysAndRequiredIndexesAreUsable() {
        val db = fixture("keys")
        try {
            seed(db)
            applyTransactionally(db)

            val tables = listOf(
                "incidents",
                "timeline_entries",
                "incident_sequence_gaps",
                "processed_events",
                "deferred_realtime_events",
                "deferred_incident_updates"
            )

            for (table in tables) {
                assertTrue(columnExists(db, table, "ownerPrincipal"))
            }

            db.execSQL(
                """
                INSERT INTO incidents
                (ownerPrincipal, id, title, status,
                 latestSequence, severity)
                VALUES
                ('account-a', 'INC-SHARED', 'A', 'OPEN', 1, 'HIGH'),
                ('account-b', 'INC-SHARED', 'B', 'OPEN', 2, 'LOW')
                """.trimIndent()
            )

            assertEquals(
                1,
                count(
                    db, "incidents",
                    "ownerPrincipal = ? AND id = ?",
                    arrayOf("account-a", "INC-SHARED")
                )
            )
            assertEquals(
                1,
                count(
                    db, "incidents",
                    "ownerPrincipal = ? AND id = ?",
                    arrayOf("account-b", "INC-SHARED")
                )
            )

            val requiredIndexes = listOf(
                "index_timeline_entries_ownerPrincipal_incidentId_occurredAt_entryId",
                "index_timeline_entries_ownerPrincipal_deliveryState_occurredAt_entryId",
                "index_deferred_realtime_events_ownerPrincipal_incidentId_deferredAt",
                "index_deferred_realtime_events_ownerPrincipal_deferredAt_eventId",
                "index_deferred_incident_updates_ownerPrincipal_incidentId_sequence_deferredAt_eventId",
                "index_deferred_incident_updates_ownerPrincipal_deferredAt_eventId"
            )

            for (index in requiredIndexes) {
                assertEquals(
                    1,
                    count(
                        db, "sqlite_master",
                        "type = 'index' AND name = ?",
                        arrayOf(index)
                    )
                )
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun transactionRollbackRestoresOriginalV15TablesAndRows() {
        val db = fixture("rollback")
        try {
            seed(db)

            db.beginTransaction()
            try {
                RELAY_MIGRATION_15_16.migrate(db)

                // Deliberately leave the transaction unsuccessful.
                // This simulates an interrupted upgrade after SQL ran.
            } finally {
                db.endTransaction()
            }

            assertFalse(
                columnExists(db, "incidents", "ownerPrincipal")
            )

            assertEquals(
                1,
                count(
                    db, "incidents",
                    "id = ?", arrayOf("INC-SHARED")
                )
            )

            assertEquals(
                1,
                count(
                    db, "pending_create_incident_commands",
                    "commandId = ?", arrayOf("CREATE-A")
                )
            )

            assertEquals(
                0,
                count(
                    db, "sqlite_master",
                    "type = 'table' AND name LIKE ?",
                    arrayOf("%_v15_legacy")
                )
            )
        } finally {
            db.close()
        }
    }
}
