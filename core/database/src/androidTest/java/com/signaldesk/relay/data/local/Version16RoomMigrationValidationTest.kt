package com.signaldesk.relay.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test-only Room model matching the proposed v16 schema.
 *
 * This is not RelayDatabase and is never used by the app.
 * exportSchema is false because this test validates the
 * generated Room model at runtime, not an exported schema file.
 */
@Database(
    entities = [
        V16Incident::class,
        V16TimelineEntry::class,
        V16IncidentSequenceGap::class,
        V16ProcessedEvent::class,
        V16DeferredRealtimeEvent::class,
        V16DeferredIncidentUpdate::class,
        PendingSeverityCommand::class,
        PendingStatusCommand::class,
        PendingCreateIncidentCommand::class
    ],
    version = 16,
    exportSchema = false
)
internal abstract class Version16TestDatabase : RoomDatabase() {
    abstract fun version16IncidentDao(): Version16IncidentDao
    abstract fun version16ProcessedEventDao(): Version16ProcessedEventDao
    abstract fun version16DeferredIsolationDao(): Version16DeferredIsolationDao
    abstract fun version16TimelineIsolationDao(): Version16TimelineIsolationDao
}

@RunWith(AndroidJUnit4::class)
class Version16RoomMigrationValidationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RelayDatabase::class.java
    )

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun name(suffix: String) =
        "relay-246-room-v16-validation-$suffix.db"

    private fun createV15(suffix: String) {
        val db = helper.createDatabase(name(suffix), 15)
        try {
            db.execSQL(
                """
                INSERT INTO incidents
                (id, title, status, latestSequence, severity)
                VALUES ('INC-SHARED', 'Legacy incident',
                        'OPEN', 9, 'HIGH')
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO timeline_entries
                (entryId, incidentId, message, author,
                 occurredAt, deliveryState, ownerPrincipal)
                VALUES ('ENTRY-A', 'INC-SHARED', 'Account A draft',
                        'A', 100, 'PENDING', 'account-a')
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO pending_create_incident_commands
                (commandId, incidentId, title, status, severity,
                 ownerPrincipal, createdAt, deliveryState)
                VALUES ('CREATE-A', 'INC-A', 'Draft A', 'OPEN',
                        'HIGH', 'account-a', 100, 'PENDING')
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO pending_status_commands
                (commandId, incidentId, status, baseStatus,
                 ownerPrincipal, createdAt, deliveryState)
                VALUES ('STATUS-B', 'INC-SHARED', 'RESOLVED',
                        'OPEN', 'account-b', 200, 'PENDING')
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO pending_severity_commands
                (commandId, incidentId, severity, baseSeverity,
                 ownerPrincipal, createdAt, deliveryState)
                VALUES ('SEVERITY-A', 'INC-SHARED', 'CRITICAL',
                        'HIGH', 'account-a', 300, 'PENDING')
                """.trimIndent()
            )
        } finally {
            db.close()
        }
    }

    private fun openV16(suffix: String): Version16TestDatabase {
        return Room.databaseBuilder(
            context,
            Version16TestDatabase::class.java,
            name(suffix)
        )
            .addMigrations(RELAY_MIGRATION_15_16)
            .build()
    }

    private fun count(
        db: SupportSQLiteDatabase,
        table: String,
        where: String,
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

    @Test
    fun actualMigrationPassesRoomV16SchemaValidation() {
        createV15("schema")

        val room = openV16("schema")
        try {
            // Forces Room to migrate and validate all nine tables.
            val db = room.openHelper.writableDatabase

            assertEquals(
                16,
                db.version
            )

            assertEquals(
                1,
                count(
                    db,
                    "incidents",
                    "ownerPrincipal = ? AND id = ?",
                    arrayOf("", "INC-SHARED")
                )
            )
        } finally {
            room.close()
        }
    }

    @Test
    fun pendingCommandsSurviveRoomValidatedMigration() {
        createV15("pending")

        val room = openV16("pending")
        try {
            val db = room.openHelper.writableDatabase

            assertEquals(
                1,
                count(
                    db,
                    "pending_create_incident_commands",
                    "commandId = ? AND ownerPrincipal = ? " +
                        "AND deliveryState = ?",
                    arrayOf("CREATE-A", "account-a", "PENDING")
                )
            )

            assertEquals(
                1,
                count(
                    db,
                    "pending_status_commands",
                    "commandId = ? AND ownerPrincipal = ? " +
                        "AND deliveryState = ?",
                    arrayOf("STATUS-B", "account-b", "PENDING")
                )
            )

            assertEquals(
                1,
                count(
                    db,
                    "pending_severity_commands",
                    "commandId = ? AND ownerPrincipal = ? " +
                        "AND deliveryState = ?",
                    arrayOf("SEVERITY-A", "account-a", "PENDING")
                )
            )

            assertEquals(
                1,
                count(
                    db,
                    "timeline_entries",
                    "entryId = ? AND ownerPrincipal = ?",
                    arrayOf("ENTRY-A", "account-a")
                )
            )
        } finally {
            room.close()
        }
    }

    @Test
    fun migratedDatabaseSupportsIndependentAccountIncidentIds() {
        createV15("owners")

        val room = openV16("owners")
        try {
            val db = room.openHelper.writableDatabase

            db.execSQL(
                """
                INSERT INTO incidents
                (ownerPrincipal, id, title, status,
                 latestSequence, severity)
                VALUES
                ('account-a', 'INC-SHARED', 'A incident',
                 'OPEN', 1, 'HIGH'),
                ('account-b', 'INC-SHARED', 'B incident',
                 'OPEN', 2, 'LOW')
                """.trimIndent()
            )

            for (owner in listOf("", "account-a", "account-b")) {
                assertEquals(
                    1,
                    count(
                        db,
                        "incidents",
                        "ownerPrincipal = ? AND id = ?",
                        arrayOf(owner, "INC-SHARED")
                    )
                )
            }

            assertEquals(
                0,
                count(
                    db,
                    "incidents",
                    "ownerPrincipal = ?",
                    arrayOf("account-c")
                )
            )

            assertTrue(db.isOpen)
        } finally {
            room.close()
        }
    }
}
