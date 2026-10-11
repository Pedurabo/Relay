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
        WHERE ownerPrincipal = :ownerPrincipal
          AND incidentId = :incidentId
        ORDER BY occurredAt ASC, entryId ASC
        """
    )
    fun observeForIncident(
        ownerPrincipal: String,
        incidentId: String
    ): Flow<List<TimelineEntryEntity>>

    @Query(
        """
        SELECT *
        FROM timeline_entries
        WHERE ownerPrincipal = :ownerPrincipal
          AND entryId = :entryId
        LIMIT 1
        """
    )
    suspend fun getById(
        ownerPrincipal: String,
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(
        entry: TimelineEntryEntity
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(
        entry: TimelineEntryEntity
    )

    @Query(
        """
        UPDATE timeline_entries
        SET deliveryState = 'PENDING'
        WHERE ownerPrincipal = :ownerPrincipal
          AND entryId = :entryId
          AND deliveryState = 'FAILED'
        """
    )
    suspend fun retryFailed(
        entryId: String,
        ownerPrincipal: String
    ): Int

    @Query(
        """
        UPDATE timeline_entries
        SET deliveryState = 'FAILED'
        WHERE ownerPrincipal = :ownerPrincipal
          AND entryId = :entryId
          AND deliveryState = 'PENDING'
        """
    )
    suspend fun failPending(
        entryId: String,
        ownerPrincipal: String
    ): Int

    @Query(
        """
        UPDATE timeline_entries
        SET deliveryState = 'SENT'
        WHERE ownerPrincipal = :ownerPrincipal
          AND entryId = :entryId
          AND deliveryState = 'PENDING'
        """
    )
    suspend fun acknowledgePending(
        entryId: String,
        ownerPrincipal: String
    ): Int

    @Query(
        """
        SELECT t.*
        FROM timeline_entries t
        WHERE t.ownerPrincipal = :ownerPrincipal
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
    fun observeLatestForAllIncidents(
        ownerPrincipal: String
    ): Flow<List<TimelineEntryEntity>>
}
