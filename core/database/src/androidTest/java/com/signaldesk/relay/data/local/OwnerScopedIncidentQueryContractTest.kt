package com.signaldesk.relay.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proposed version-16 authenticated incident query contract.
 *
 * Uses an independent in-memory SQLite database, not relay.db.
 * Does not exercise production Room DAOs or activate v16.
 */
@RunWith(AndroidJUnit4::class)
class OwnerScopedIncidentQueryContractTest {

    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        db = SQLiteDatabase.create(null)

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

        insert("", "INC-SHARED", "Legacy confidential")
        insert("account-a", "INC-SHARED", "Account A incident")
        insert("account-b", "INC-SHARED", "Account B incident")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insert(owner: String, id: String, title: String) {
        db.execSQL(
            """
            INSERT INTO incidents
                (ownerPrincipal, id, title, status,
                 latestSequence, severity)
            VALUES (?, ?, ?, 'OPEN', 1, 'HIGH')
            """.trimIndent(),
            arrayOf(owner, id, title)
        )
    }

    private fun read(identity: AccountScopedIncidentId): String? {
        db.rawQuery(
            """
            SELECT title FROM incidents
            WHERE ownerPrincipal = ? AND id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(identity.ownerPrincipal, identity.incidentId)
        ).use { cursor ->
            return if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    }

    private fun update(
        identity: AccountScopedIncidentId,
        title: String
    ) {
        db.execSQL(
            """
            UPDATE incidents SET title = ?
            WHERE ownerPrincipal = ? AND id = ?
            """.trimIndent(),
            arrayOf(
                title,
                identity.ownerPrincipal,
                identity.incidentId
            )
        )
    }

    @Test
    fun identicalIncidentIdsAreReadWithinTheirOwnAccounts() {
        val a = AccountScopedIncidentId("account-a", "INC-SHARED")
        val b = AccountScopedIncidentId("account-b", "INC-SHARED")

        assertEquals("Account A incident", read(a))
        assertEquals("Account B incident", read(b))
    }

    @Test
    fun legacyRowsCannotBeReadUsingAuthenticatedIdentity() {
        val a = AccountScopedIncidentId("account-a", "INC-SHARED")
        val b = AccountScopedIncidentId("account-b", "INC-SHARED")

        assertEquals("Account A incident", read(a))
        assertEquals("Account B incident", read(b))

        assertNull(
            read(AccountScopedIncidentId("account-c", "INC-SHARED"))
        )
    }

    @Test
    fun accountScopedUpdatesDoNotCrossOwnerBoundaries() {
        val a = AccountScopedIncidentId("account-a", "INC-SHARED")
        val b = AccountScopedIncidentId("account-b", "INC-SHARED")

        update(a, "Updated A")

        assertEquals("Updated A", read(a))
        assertEquals("Account B incident", read(b))

        db.rawQuery(
            """
            SELECT title FROM incidents
            WHERE ownerPrincipal = '' AND id = 'INC-SHARED'
            """.trimIndent(),
            null
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Legacy confidential", cursor.getString(0))
        }
    }
}
