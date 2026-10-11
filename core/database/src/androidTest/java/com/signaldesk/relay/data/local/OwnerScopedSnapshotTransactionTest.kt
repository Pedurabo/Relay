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
 * Test-only SQL prototype for owner-scoped snapshot reconciliation.
 *
 * This does not exercise the current production Room DAO.
 * It documents the required behavior for the v16 conversion.
 * Only an independent in-memory database is used.
 */
@RunWith(AndroidJUnit4::class)
class OwnerScopedSnapshotTransactionTest {

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

        db.execSQL(
            """
            CREATE TABLE incident_sequence_gaps (
                ownerPrincipal TEXT NOT NULL,
                incidentId TEXT NOT NULL,
                expectedSequence INTEGER NOT NULL,
                receivedSequence INTEGER NOT NULL,
                detectedAt INTEGER NOT NULL,
                PRIMARY KEY(ownerPrincipal, incidentId)
            )
            """.trimIndent()
        )

        for (owner in listOf("", "account-a", "account-b")) {
            db.execSQL(
                """
                INSERT INTO incidents VALUES
                (?, 'INC-SHARED', ?, 'OPEN', 2, 'HIGH')
                """.trimIndent(),
                arrayOf(owner, "$owner original")
            )

            db.execSQL(
                """
                INSERT INTO incident_sequence_gaps VALUES
                (?, 'INC-SHARED', 3, 8, 100)
                """.trimIndent(),
                arrayOf(owner)
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun latestSequence(owner: String): Long {
        db.rawQuery(
            """
            SELECT latestSequence FROM incidents
            WHERE ownerPrincipal = ? AND id = 'INC-SHARED'
            """.trimIndent(),
            arrayOf(owner)
        ).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getLong(0)
        }
    }

    private fun gapExpected(owner: String): Long? {
        db.rawQuery(
            """
            SELECT expectedSequence FROM incident_sequence_gaps
            WHERE ownerPrincipal = ? AND incidentId = 'INC-SHARED'
            """.trimIndent(),
            arrayOf(owner)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0)
            else null
        }
    }

    /**
     * Test-only representation of an owner-scoped atomic snapshot.
     */
    private fun reconcile(
        owner: String,
        sequence: Long
    ) {
        require(owner.isNotBlank())

        db.beginTransaction()
        try {
            val current = latestSequence(owner)

            if (sequence > current) {
                db.execSQL(
                    """
                    UPDATE incidents
                    SET latestSequence = ?, title = ?
                    WHERE ownerPrincipal = ? AND id = 'INC-SHARED'
                    """.trimIndent(),
                    arrayOf(sequence, "$owner updated", owner)
                )

                val expected = gapExpected(owner)

                if (expected != null) {
                    if (sequence >= 8L) {
                        db.execSQL(
                            """
                            DELETE FROM incident_sequence_gaps
                            WHERE ownerPrincipal = ?
                              AND incidentId = 'INC-SHARED'
                            """.trimIndent(),
                            arrayOf(owner)
                        )
                    } else if (sequence >= expected) {
                        db.execSQL(
                            """
                            UPDATE incident_sequence_gaps
                            SET expectedSequence = ?
                            WHERE ownerPrincipal = ?
                              AND incidentId = 'INC-SHARED'
                            """.trimIndent(),
                            arrayOf(sequence + 1L, owner)
                        )
                    }
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Test
    fun snapshotForAccountAChangesOnlyAccountA() {
        reconcile("account-a", 5L)

        assertEquals(5L, latestSequence("account-a"))
        assertEquals(6L, gapExpected("account-a"))

        assertEquals(2L, latestSequence("account-b"))
        assertEquals(3L, gapExpected("account-b"))

        assertEquals(2L, latestSequence(""))
        assertEquals(3L, gapExpected(""))
    }

    @Test
    fun gapResolutionForOneOwnerDoesNotDeleteOtherGaps() {
        reconcile("account-a", 8L)

        assertEquals(8L, latestSequence("account-a"))
        assertNull(gapExpected("account-a"))

        assertEquals(3L, gapExpected("account-b"))
        assertEquals(3L, gapExpected(""))
    }

    @Test
    fun staleSnapshotDoesNotRegressSequenceOrGapState() {
        reconcile("account-a", 6L)
        reconcile("account-a", 4L)

        assertEquals(6L, latestSequence("account-a"))
        assertEquals(7L, gapExpected("account-a"))

        assertEquals(2L, latestSequence("account-b"))
        assertEquals(3L, gapExpected("account-b"))
    }
}
