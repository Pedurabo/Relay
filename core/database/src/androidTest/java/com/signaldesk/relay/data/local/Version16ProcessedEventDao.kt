package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * Staged v16 processed-event access contract.
 *
 * Event identity is (ownerPrincipal, eventId).
 * This DAO is test-only until the coordinated production migration.
 */
@Dao
internal interface Version16ProcessedEventDao {

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM processed_events
            WHERE ownerPrincipal = :owner
              AND eventId = :eventId
        )
        """
    )
    suspend fun exists(
        owner: String,
        eventId: String
    ): Boolean

    @Query(
        """
        SELECT COUNT(*)
        FROM processed_events
        WHERE ownerPrincipal = :owner
        """
    )
    suspend fun countForOwner(
        owner: String
    ): Int

    @Query(
        """
        DELETE FROM processed_events
        WHERE ownerPrincipal = :owner
          AND eventId = :eventId
        """
    )
    suspend fun deleteForOwner(
        owner: String,
        eventId: String
    ): Int
}
