package com.signaldesk.relay.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Proposed migration from Relay Room v15 to v16.
 *
 * IMPORTANT:
 * - This object is intentionally not registered yet.
 * - RelayDatabase must remain at version 15 until entities,
 *   DAO operations, repositories and migration tests agree.
 * - Empty ownerPrincipal denotes quarantined legacy data.
 * - Existing owner-known pending command tables are untouched.
 *
 * Room invokes migrations within its upgrade transaction.
 * Do not independently commit partial table rebuilds here.
 */
val RELAY_MIGRATION_15_16: Migration =
    object : Migration(15, 16) {

        override fun migrate(db: SupportSQLiteDatabase) {

            rebuild(
                db = db,
                table = "incidents",
                create = """
                    CREATE TABLE incidents (
                        ownerPrincipal TEXT NOT NULL,
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        status TEXT NOT NULL,
                        latestSequence INTEGER NOT NULL,
                        severity TEXT NOT NULL,
                        PRIMARY KEY(ownerPrincipal, id)
                    )
                """,
                copy = """
                    INSERT INTO incidents
                        (ownerPrincipal, id, title, status,
                         latestSequence, severity)
                    SELECT '', id, title, status,
                           latestSequence, severity
                    FROM incidents_v15_legacy
                """
            )

            rebuild(
                db = db,
                table = "timeline_entries",
                create = """
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
                """,
                copy = """
                    INSERT INTO timeline_entries
                        (ownerPrincipal, entryId, incidentId,
                         message, author, occurredAt, deliveryState)
                    SELECT ownerPrincipal, entryId, incidentId,
                           message, author, occurredAt, deliveryState
                    FROM timeline_entries_v15_legacy
                """
            )

            rebuild(
                db = db,
                table = "incident_sequence_gaps",
                create = """
                    CREATE TABLE incident_sequence_gaps (
                        ownerPrincipal TEXT NOT NULL,
                        incidentId TEXT NOT NULL,
                        expectedSequence INTEGER NOT NULL,
                        receivedSequence INTEGER NOT NULL,
                        detectedAt INTEGER NOT NULL,
                        PRIMARY KEY(ownerPrincipal, incidentId)
                    )
                """,
                copy = """
                    INSERT INTO incident_sequence_gaps
                        (ownerPrincipal, incidentId, expectedSequence,
                         receivedSequence, detectedAt)
                    SELECT '', incidentId, expectedSequence,
                           receivedSequence, detectedAt
                    FROM incident_sequence_gaps_v15_legacy
                """
            )

            rebuild(
                db = db,
                table = "processed_events",
                create = """
                    CREATE TABLE processed_events (
                        ownerPrincipal TEXT NOT NULL,
                        eventId TEXT NOT NULL,
                        processedAt INTEGER NOT NULL,
                        PRIMARY KEY(ownerPrincipal, eventId)
                    )
                """,
                copy = """
                    INSERT INTO processed_events
                        (ownerPrincipal, eventId, processedAt)
                    SELECT '', eventId, processedAt
                    FROM processed_events_v15_legacy
                """
            )

            rebuild(
                db = db,
                table = "deferred_realtime_events",
                create = """
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
                """,
                copy = """
                    INSERT INTO deferred_realtime_events
                        (ownerPrincipal, eventId, incidentId, entryId,
                         message, author, occurredAt, deferredAt)
                    SELECT '', eventId, incidentId, entryId,
                           message, author, occurredAt, deferredAt
                    FROM deferred_realtime_events_v15_legacy
                """
            )

            rebuild(
                db = db,
                table = "deferred_incident_updates",
                create = """
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
                """,
                copy = """
                    INSERT INTO deferred_incident_updates
                        (ownerPrincipal, eventId, incidentId,
                         occurredAt, sequence, title,
                         status, severity, deferredAt)
                    SELECT '', eventId, incidentId,
                           occurredAt, sequence, title,
                           status, severity, deferredAt
                    FROM deferred_incident_updates_v15_legacy
                """
            )

            // Index names and column order must match the future
            // v16 Room entity declarations and exported schema.

            db.execSQL(
                """
                CREATE INDEX
                index_timeline_entries_ownerPrincipal_incidentId_occurredAt_entryId
                ON timeline_entries(
                    ownerPrincipal, incidentId, occurredAt, entryId
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                index_timeline_entries_ownerPrincipal_deliveryState_occurredAt_entryId
                ON timeline_entries(
                    ownerPrincipal, deliveryState, occurredAt, entryId
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                index_deferred_realtime_events_ownerPrincipal_incidentId_deferredAt
                ON deferred_realtime_events(
                    ownerPrincipal, incidentId, deferredAt
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                index_deferred_realtime_events_ownerPrincipal_deferredAt_eventId
                ON deferred_realtime_events(
                    ownerPrincipal, deferredAt, eventId
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                index_deferred_incident_updates_ownerPrincipal_incidentId_sequence_deferredAt_eventId
                ON deferred_incident_updates(
                    ownerPrincipal, incidentId, sequence,
                    deferredAt, eventId
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX
                index_deferred_incident_updates_ownerPrincipal_deferredAt_eventId
                ON deferred_incident_updates(
                    ownerPrincipal, deferredAt, eventId
                )
                """.trimIndent()
            )
        }

        private fun rebuild(
            db: SupportSQLiteDatabase,
            table: String,
            create: String,
            copy: String
        ) {
            val legacy = "${table}_v15_legacy"

            // Names are supplied only from this migration's
            // fixed, developer-controlled table list.
            db.execSQL(
                "ALTER TABLE $table RENAME TO $legacy"
            )

            db.execSQL(create.trimIndent())
            db.execSQL(copy.trimIndent())

            db.execSQL("DROP TABLE $legacy")
        }
    }
