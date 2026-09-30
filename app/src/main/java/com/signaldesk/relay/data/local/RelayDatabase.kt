package com.signaldesk.relay.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        
        PendingSeverityCommand::class,IncidentEntity::class,
        ProcessedEventEntity::class,
        TimelineEntryEntity::class,
        IncidentSequenceGapEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class RelayDatabase :
    RoomDatabase() {

    abstract fun incidentDao():
        IncidentDao

    abstract fun processedEventDao():
        ProcessedEventDao

    abstract fun timelineEntryDao():
        TimelineEntryDao

    abstract fun incidentSequenceGapDao():
        IncidentSequenceGapDao

    
    abstract fun pendingSeverityCommandDao():
        PendingSeverityCommandDao
companion object {

        @Volatile
        private var INSTANCE:
            RelayDatabase? = null

        private val MIGRATION_1_2 =
            object :
                Migration(
                    1,
                    2
                ) {

                override fun migrate(
                    db:
                        SupportSQLiteDatabase
                ) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS processed_events (
                            eventId TEXT NOT NULL,
                            processedAt INTEGER NOT NULL,
                            PRIMARY KEY(eventId)
                        )
                        """.trimIndent()
                    )
                }
            }

        private val MIGRATION_2_3 =
            object :
                Migration(
                    2,
                    3
                ) {

                override fun migrate(
                    db:
                        SupportSQLiteDatabase
                ) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS timeline_entries (
                            entryId TEXT NOT NULL,
                            incidentId TEXT NOT NULL,
                            message TEXT NOT NULL,
                            author TEXT NOT NULL,
                            occurredAt INTEGER NOT NULL,
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
            }

        private val MIGRATION_3_4 =
            object :
                Migration(
                    3,
                    4
                ) {

                override fun migrate(
                    db:
                        SupportSQLiteDatabase
                ) {
                    db.execSQL(
                        """
                        ALTER TABLE timeline_entries
                        ADD COLUMN deliveryState
                        TEXT NOT NULL
                        DEFAULT 'SENT'
                        """.trimIndent()
                    )
                }
            }

        private val MIGRATION_4_5 =
            object :
                Migration(
                    4,
                    5
                ) {

                override fun migrate(
                    db:
                        SupportSQLiteDatabase
                ) {
                    db.execSQL(
                        """
                        ALTER TABLE incidents
                        ADD COLUMN latestSequence
                        INTEGER NOT NULL
                        DEFAULT 0
                        """.trimIndent()
                    )
                }
            }

        private val MIGRATION_5_6 =
            object :
                Migration(
                    5,
                    6
                ) {

                override fun migrate(
                    db:
                        SupportSQLiteDatabase
                ) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS
                        incident_sequence_gaps (
                            incidentId TEXT NOT NULL,
                            expectedSequence INTEGER NOT NULL,
                            receivedSequence INTEGER NOT NULL,
                            detectedAt INTEGER NOT NULL,
                            PRIMARY KEY(incidentId)
                        )
                        """.trimIndent()
                    )
                }
            }

        private val MIGRATION_6_7 =
            object :
                Migration(
                    6,
                    7
                ) {

                override fun migrate(
                    db:
                        SupportSQLiteDatabase
                ) {
                    db.execSQL(
                        """
                        ALTER TABLE incidents
                        ADD COLUMN severity
                        TEXT NOT NULL
                        DEFAULT 'MEDIUM'
                        """.trimIndent()
                    )
                }
            }

        fun getInstance(
            context: Context
        ): RelayDatabase {

            return INSTANCE
                ?: synchronized(this) {

                    INSTANCE
                        ?: Room.databaseBuilder(
                            context.applicationContext,
                            RelayDatabase::class.java,
                            "relay.db"
                        )
                            .addMigrations(
                                MIGRATION_1_2,
                                MIGRATION_2_3,
                                MIGRATION_3_4,
                                MIGRATION_4_5,
                                MIGRATION_5_6,
                                MIGRATION_6_7,
                                MIGRATION_7_8,
                                MIGRATION_8_9
                            )
                            .build()
                            .also {
                                INSTANCE = it
                            }
                }
        }
    
        val MIGRATION_7_8 =
            object : Migration(
                7,
                8
            ) {

                override fun migrate(
                    db:
                        SupportSQLiteDatabase
                ) {

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS pending_severity_commands (
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
            }

        val MIGRATION_8_9 =
            object : Migration(
                8,
                9
            ) {

                override fun migrate(
                    db:
                        SupportSQLiteDatabase
                ) {

                    /*
                     * Existing v8 rows have unknown ownership.
                     * They remain durable but are intentionally
                     * ineligible for authenticated delivery.
                     */
                    db.execSQL(
                        """
                        ALTER TABLE pending_severity_commands
                        ADD COLUMN ownerPrincipal
                        TEXT NOT NULL
                        DEFAULT ''
                        """.trimIndent()
                    )
                }
            }
}
}



