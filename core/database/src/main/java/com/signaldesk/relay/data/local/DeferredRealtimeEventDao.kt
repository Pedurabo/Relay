package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeferredRealtimeEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(
        event: DeferredRealtimeEventEntity
    ): Long

    @Query(
        """
        SELECT *
        FROM deferred_realtime_events
        WHERE ownerPrincipal = :ownerPrincipal
          AND incidentId = :incidentId
        ORDER BY deferredAt ASC, eventId ASC
        """
    )
    suspend fun loadForIncident(
        ownerPrincipal: String,
        incidentId: String
    ): List<DeferredRealtimeEventEntity>

    @Query(
        """
        SELECT *
        FROM deferred_realtime_events
        WHERE ownerPrincipal = :ownerPrincipal
        ORDER BY deferredAt ASC, eventId ASC
        """
    )
    suspend fun loadAll(
        ownerPrincipal: String
    ): List<DeferredRealtimeEventEntity>

    @Query(
        """
        DELETE FROM deferred_realtime_events
        WHERE ownerPrincipal = :ownerPrincipal
          AND deferredAt < :cutoffMillis
          AND NOT EXISTS (
              SELECT 1
              FROM incidents
              WHERE incidents.ownerPrincipal =
                    deferred_realtime_events.ownerPrincipal
                AND incidents.id =
                    deferred_realtime_events.incidentId
          )
        """
    )
    suspend fun deleteOrphansBefore(
        ownerPrincipal: String,
        cutoffMillis: Long
    ): Int

    @Query(
        """
        DELETE FROM deferred_realtime_events
        WHERE ownerPrincipal = :ownerPrincipal
          AND eventId = :eventId
        """
    )
    suspend fun deleteByEventId(
        ownerPrincipal: String,
        eventId: String
    ): Int
}
