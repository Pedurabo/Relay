package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * Test-only Room v16 timeline ownership contract.
 *
 * Timeline identity is (ownerPrincipal, entryId).
 * Latest-entry selection must also partition by owner.
 */
@Dao
internal interface Version16TimelineIsolationDao {

    @Query(
        """
        SELECT entryId
        FROM timeline_entries
        WHERE ownerPrincipal = :owner
          AND incidentId = :incidentId
        ORDER BY occurredAt ASC, entryId ASC
        """
    )
    suspend fun entriesForIncident(
        owner: String,
        incidentId: String
    ): List<String>

    @Query(
        """
        SELECT deliveryState
        FROM timeline_entries
        WHERE ownerPrincipal = :owner
          AND entryId = :entryId
        LIMIT 1
        """
    )
    suspend fun deliveryState(
        owner: String,
        entryId: String
    ): String?

    @Query(
        """
        UPDATE timeline_entries
        SET deliveryState = 'SENT'
        WHERE ownerPrincipal = :owner
          AND entryId = :entryId
          AND deliveryState = 'PENDING'
        """
    )
    suspend fun acknowledge(
        owner: String,
        entryId: String
    ): Int

    @Query(
        """
        SELECT t.entryId
        FROM timeline_entries t
        WHERE t.ownerPrincipal = :owner
          AND NOT EXISTS (
              SELECT 1
              FROM timeline_entries newer
              WHERE newer.ownerPrincipal = t.ownerPrincipal
                AND newer.incidentId = t.incidentId
                AND (
                    newer.occurredAt > t.occurredAt
                    OR (
                        newer.occurredAt = t.occurredAt
                        AND newer.entryId > t.entryId
                    )
                )
          )
        ORDER BY t.occurredAt DESC, t.entryId DESC
        """
    )
    suspend fun latestEntryIds(
        owner: String
    ): List<String>
}
