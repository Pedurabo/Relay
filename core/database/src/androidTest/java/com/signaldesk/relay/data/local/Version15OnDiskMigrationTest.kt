package com.signaldesk.relay.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-disk v15 baseline for the future v15 -> v16 migration.
 *
 * This class creates a real SQLite database using Room's
 * exported historical v15 schema.
 *
 * It currently reopens that database with the v15 Room model.
 * It does not yet exercise MIGRATION_15_16.
 */
@RunWith(AndroidJUnit4::class)
class Version15OnDiskMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RelayDatabase::class.java
    )

    private val databaseName =
        "relay-246-v15-migration-fixture.db"

    private fun createLegacyDatabase() {
        val db = helper.createDatabase(
            databaseName,
            15
        )

        try {
            db.execSQL(
                """
                INSERT INTO incidents
                (id, title, status, latestSequence, severity)
                VALUES
                ('INC-LEGACY-246', 'Legacy cached incident',
                 'OPEN', 7, 'HIGH')
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO pending_create_incident_commands
                (commandId, incidentId, title, status, severity,
                 ownerPrincipal, createdAt, deliveryState)
                VALUES
                ('CMD-246-A', 'INC-NEW-A', 'Account A draft',
                 'OPEN', 'HIGH', 'account-a', 100, 'PENDING')
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO pending_create_incident_commands
                (commandId, incidentId, title, status, severity,
                 ownerPrincipal, createdAt, deliveryState)
                VALUES
                ('CMD-246-B', 'INC-NEW-B', 'Account B draft',
                 'OPEN', 'LOW', 'account-b', 200, 'PENDING')
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO timeline_entries
                (entryId, incidentId, message, author,
                 occurredAt, deliveryState, ownerPrincipal)
                VALUES
                ('ENTRY-246-LEGACY', 'INC-LEGACY-246',
                 'Legacy timeline', 'Unknown', 1000,
                 'SENT', '')
                """.trimIndent()
            )
        } finally {
            db.close()
        }
    }

    private fun openCurrentVersion(): RelayDatabase {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        return Room.databaseBuilder(
            context,
            RelayDatabase::class.java,
            databaseName
        ).build()
    }

    @Test
    fun exportedV15DatabaseOpensWithCurrentRoomModel() =
        runBlocking {

            createLegacyDatabase()

            val room = openCurrentVersion()

            try {
                val incident =
                    room.incidentDao()
                        .getById("", "INC-LEGACY-246")

                assertNotNull(incident)

                assertEquals(
                    "Legacy cached incident",
                    incident?.title
                )

                assertEquals(
                    7L,
                    incident?.latestSequence
                )

                val legacyTimeline =
                    room.timelineEntryDao()
                        .getById("", "ENTRY-246-LEGACY")

                assertNotNull(legacyTimeline)
                assertEquals(
                    "",
                    legacyTimeline?.ownerPrincipal
                )
            } finally {
                room.close()
            }
        }

    @Test
    fun ownerKnownPendingCommandsSurviveDiskReopen() =
        runBlocking {

            createLegacyDatabase()

            val room = openCurrentVersion()

            try {
                val dao =
                    room.pendingCreateIncidentCommandDao()

                val accountA =
                    dao.getOldestForOwner("account-a")

                val accountB =
                    dao.getOldestForOwner("account-b")

                assertNotNull(accountA)
                assertNotNull(accountB)

                assertEquals(
                    "CMD-246-A",
                    accountA?.commandId
                )

                assertEquals(
                    "CMD-246-B",
                    accountB?.commandId
                )

                assertEquals(
                    "account-a",
                    accountA?.ownerPrincipal
                )

                assertEquals(
                    "account-b",
                    accountB?.ownerPrincipal
                )

                assertNull(
                    dao.getOldestForOwner("account-c")
                )
            } finally {
                room.close()
            }
        }
}