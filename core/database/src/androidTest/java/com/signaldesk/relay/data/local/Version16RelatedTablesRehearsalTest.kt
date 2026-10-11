package com.signaldesk.relay.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test-only, on-disk v15 -> proposed v16 SQL rehearsal.
 *
 * No production migration is registered here.
 * Only databases with the unique relay-246-c2 prefix are used.
 * Room's production relay.db is never opened by this class.
 */
@RunWith(AndroidJUnit4::class)
class Version16RelatedTablesRehearsalTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RelayDatabase::class.java
    )

    private fun openFixture(suffix: String): SupportSQLiteDatabase {
        return helper.createDatabase(
            "relay-246-c2-$suffix.db",
            15
        )
    }

    private fun rebuild(
        db: SupportSQLiteDatabase,
        table: String,
        create: String,
        copy: String
    ) {
        db.execSQL("ALTER TABLE $table RENAME TO ${table}_legacy")
        db.execSQL(create)
        db.execSQL(copy)
        db.execSQL("DROP TABLE ${table}_legacy")
    }

    private fun migrateRelatedTables(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            rebuild(
                db,
                "timeline_entries",
                """
                CREATE TABLE timeline_entries (
                    ownerPrincipal TEXT NOT NULL,
                    entryId TEXT NOT NULL,
                    incidentId TEXT NOT NULL,
                    message TEXT NOT NULL,
                    author TEXT NOT NULL,
                    occurredAt INTEGER NOT NULL,
                    deliveryState TEXT NOT NULL,
                    PRIMARY KEY(ownerPrincipal, entryId)
                )
                """.trimIndent(),
                """
                INSERT INTO timeline_entries
                    (ownerPrincipal, entryId, incidentId, message,
                     author, occurredAt, deliveryState)
                SELECT ownerPrincipal, entryId, incidentId, message,
                       author, occurredAt, deliveryState
                FROM timeline_entries_legacy
                """.trimIndent()
            )

            rebuild(
                db,
                "processed_events",
                """
                CREATE TABLE processed_events (
                    ownerPrincipal TEXT NOT NULL,
                    eventId TEXT NOT NULL,
                    processedAt INTEGER NOT NULL,
                    PRIMARY KEY(ownerPrincipal, eventId)
                )
                """.trimIndent(),
                """
                INSERT INTO processed_events
                    (ownerPrincipal, eventId, processedAt)
                SELECT '', eventId, processedAt
                FROM processed_events_legacy
                """.trimIndent()
            )

            rebuild(
                db,
                "incident_sequence_gaps",
                """
                CREATE TABLE incident_sequence_gaps (
                    ownerPrincipal TEXT NOT NULL,
                    incidentId TEXT NOT NULL,
                    expectedSequence INTEGER NOT NULL,
                    receivedSequence INTEGER NOT NULL,
                    detectedAt INTEGER NOT NULL,
                    PRIMARY KEY(ownerPrincipal, incidentId)
                )
                """.trimIndent(),
                """
                INSERT INTO incident_sequence_gaps
                    (ownerPrincipal, incidentId, expectedSequence,
                     receivedSequence, detectedAt)
                SELECT '', incidentId, expectedSequence,
                       receivedSequence, detectedAt
                FROM incident_sequence_gaps_legacy
                """.trimIndent()
            )

            rebuild(
                db,
                "deferred_realtime_events",
                """
                CREATE TABLE deferred_realtime_events (
                    ownerPrincipal TEXT NOT NULL,
                    eventId TEXT NOT NULL,
                    incidentId TEXT NOT NULL,
                    entryId TEXT NOT NULL,
                    message TEXT NOT NULL,
                    author TEXT NOT NULL,
                    occurredAt INTEGER NOT NULL,
                    deferredAt INTEGER NOT NULL,
                    PRIMARY KEY(ownerPrincipal, eventId)
                )
                """.trimIndent(),
                """
                INSERT INTO deferred_realtime_events
                    (ownerPrincipal, eventId, incidentId, entryId,
                     message, author, occurredAt, deferredAt)
                SELECT '', eventId, incidentId, entryId,
                       message, author, occurredAt, deferredAt
                FROM deferred_realtime_events_legacy
                """.trimIndent()
            )

            rebuild(
                db,
                "deferred_incident_updates",
                """
                CREATE TABLE deferred_incident_updates (
                    ownerPrincipal TEXT NOT NULL,
                    eventId TEXT NOT NULL,
                    incidentId TEXT NOT NULL,
                    occurredAt INTEGER NOT NULL,
                    sequence INTEGER NOT NULL,
                    title TEXT,
                    status TEXT,
                    severity TEXT,
                    deferredAt INTEGER NOT NULL,
                    PRIMARY KEY(ownerPrincipal, eventId)
                )
                """.trimIndent(),
                """
                INSERT INTO deferred_incident_updates
                    (ownerPrincipal, eventId, incidentId, occurredAt,
                     sequence, title, status, severity, deferredAt)
                SELECT '', eventId, incidentId, occurredAt,
                       sequence, title, status, severity, deferredAt
                FROM deferred_incident_updates_legacy
                """.trimIndent()
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun count(
        db: SupportSQLiteDatabase,
        table: String,
        owner: String
    ): Int {
        db.query(
            "SELECT COUNT(*) FROM $table WHERE ownerPrincipal = ?",
            arrayOf(owner)
        ).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun textValue(
        db: SupportSQLiteDatabase,
        sql: String,
        owner: String
    ): String? {
        db.query(sql, arrayOf(owner)).use { cursor ->
            return if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    }

    @Test
    fun oldRowsRemainQuarantined() {
        val db = openFixture("legacy")
        try {
            db.execSQL(
                """
                INSERT INTO timeline_entries
                VALUES('ENTRY-1','INC-1','legacy timeline',
                       'Legacy',100,'SENT','')
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO processed_events VALUES('EVENT-1',100)"
            )
            db.execSQL(
                """
                INSERT INTO incident_sequence_gaps
                VALUES('INC-1',2,5,100)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO deferred_realtime_events
                VALUES('EVENT-1','INC-1','ENTRY-1',
                       'deferred timeline','Legacy',100,200)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO deferred_incident_updates
                VALUES('EVENT-1','INC-1',100,5,
                       'legacy title','OPEN','HIGH',200)
                """.trimIndent()
            )

            migrateRelatedTables(db)

            for (table in listOf(
                "timeline_entries",
                "processed_events",
                "incident_sequence_gaps",
                "deferred_realtime_events",
                "deferred_incident_updates"
            )) {
                assertEquals(1, count(db, table, ""))
                assertEquals(0, count(db, table, "account-a"))
                assertEquals(0, count(db, table, "account-b"))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun differentOwnersCanReuseAllChildIds() {
        val db = openFixture("collisions")
        try {
            migrateRelatedTables(db)

            for (owner in listOf("account-a", "account-b")) {
                db.execSQL(
                    """
                    INSERT INTO timeline_entries
                    VALUES(?, 'ENTRY-SHARED','INC-SHARED',
                           ?, 'Author',100,'SENT')
                    """.trimIndent(),
                    arrayOf(owner, "$owner timeline")
                )
                db.execSQL(
                    """
                    INSERT INTO processed_events
                    VALUES(?, 'EVENT-SHARED',100)
                    """.trimIndent(),
                    arrayOf(owner)
                )
                db.execSQL(
                    """
                    INSERT INTO incident_sequence_gaps
                    VALUES(?, 'INC-SHARED',2,5,100)
                    """.trimIndent(),
                    arrayOf(owner)
                )
                db.execSQL(
                    """
                    INSERT INTO deferred_realtime_events
                    VALUES(?, 'EVENT-SHARED','INC-SHARED',
                           'ENTRY-SHARED',?, 'Author',100,200)
                    """.trimIndent(),
                    arrayOf(owner, "$owner deferred")
                )
                db.execSQL(
                    """
                    INSERT INTO deferred_incident_updates
                    VALUES(?, 'EVENT-SHARED','INC-SHARED',
                           100,5,?,'OPEN','HIGH',200)
                    """.trimIndent(),
                    arrayOf(owner, "$owner update")
                )
            }

            for (table in listOf(
                "timeline_entries",
                "processed_events",
                "incident_sequence_gaps",
                "deferred_realtime_events",
                "deferred_incident_updates"
            )) {
                assertEquals(1, count(db, table, "account-a"))
                assertEquals(1, count(db, table, "account-b"))
                assertEquals(0, count(db, table, ""))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun timelineUpdatesRemainOwnerSpecific() {
        val db = openFixture("updates")
        try {
            migrateRelatedTables(db)

            for (owner in listOf("account-a", "account-b")) {
                db.execSQL(
                    """
                    INSERT INTO timeline_entries
                    VALUES(?, 'ENTRY-SHARED','INC-SHARED',
                           ?, 'Author',100,'SENT')
                    """.trimIndent(),
                    arrayOf(owner, "$owner message")
                )
            }

            db.execSQL(
                """
                UPDATE timeline_entries
                SET message = 'updated A'
                WHERE ownerPrincipal = 'account-a'
                  AND entryId = 'ENTRY-SHARED'
                """.trimIndent()
            )

            val sql = """
                SELECT message FROM timeline_entries
                WHERE ownerPrincipal = ?
                  AND entryId = 'ENTRY-SHARED'
            """.trimIndent()

            assertEquals(
                "updated A",
                textValue(db, sql, "account-a")
            )
            assertEquals(
                "account-b message",
                textValue(db, sql, "account-b")
            )
            assertNull(
                textValue(db, sql, "")
            )
        } finally {
            db.close()
        }
    }
}