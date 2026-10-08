package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineEntryDao {

    @Query(
        """
        SELECT *
        FROM timeline_entries
        WHERE incidentId = :incidentId
        ORDER BY occurredAt ASC, entryId ASC
        """
    )
    fun observeForIncident(
        incidentId: String
    ): Flow<List<TimelineEntryEntity>>

    @Query(
        """
        SELECT *
        FROM timeline_entries
        WHERE entryId = :entryId
        LIMIT 1
        """
    )
    suspend fun getById(
        entryId: String
    ): TimelineEntryEntity?

    @Query(
        """
        SELECT *
        FROM timeline_entries
        WHERE ownerPrincipal = :ownerPrincipal
          AND deliveryState = 'PENDING'
        ORDER BY occurredAt ASC, entryId ASC
        """
    )
    suspend fun loadPendingForOwner(
        ownerPrincipal: String
    ): List<TimelineEntryEntity>
    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insert(
        entry: TimelineEntryEntity
    )

    @Insert(
        onConflict = OnConflictStrategy.REPLACE
    )
    suspend fun upsert(
        entry: TimelineEntryEntity
    )

    @Query(
        """
        UPDATE timeline_entries
        SET deliveryState = :deliveryState
        WHERE entryId = :entryId
          AND ownerPrincipal = :ownerPrincipal
        """
    )
    suspend fun updateDeliveryState(
        entryId: String,
        ownerPrincipal: String,
        deliveryState: String
    ): Int

    @Query(
        """
        UPDATE timeline_entries
        SET incidentId = :incidentId,
            message = :message,
            author = :author,
            occurredAt = :occurredAt,
            deliveryState = 'SENT'
        WHERE entryId = :entryId
          AND ownerPrincipal = :ownerPrincipal
          AND deliveryState = 'PENDING'
        """
    )
    suspend fun acknowledgePending(
        entryId: String,
        ownerPrincipal: String,
        incidentId: String,
        message: String,
        author: String,
        occurredAt: Long
    ): Int
    @Query(
        """
        SELECT t.*
        FROM timeline_entries t
        WHERE NOT EXISTS (
            SELECT 1
            FROM timeline_entries newer
            WHERE newer.incidentId = t.incidentId
              AND (
                    newer.occurredAt > t.occurredAt
                    OR (
                        newer.occurredAt = t.occurredAt
                        AND newer.entryId > t.entryId
                    )
              )
        )
        ORDER BY t.occurredAt DESC
        """
    )
    fun observeLatestForAllIncidents():
        Flow<List<TimelineEntryEntity>>
}
