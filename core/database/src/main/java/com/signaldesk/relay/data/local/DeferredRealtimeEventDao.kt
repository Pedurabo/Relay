package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeferredRealtimeEventDao {

    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insert(
        event: DeferredRealtimeEventEntity
    ): Long

    @Query(
        """
        SELECT *
        FROM deferred_realtime_events
        WHERE incidentId = :incidentId
        ORDER BY deferredAt ASC
        """
    )
    suspend fun loadForIncident(
        incidentId: String
    ): List<DeferredRealtimeEventEntity>

    @Query(
        """
        SELECT *
        FROM deferred_realtime_events
        ORDER BY deferredAt ASC
        """
    )
    suspend fun loadAll():
        List<DeferredRealtimeEventEntity>

    @Query(
        """
        DELETE FROM deferred_realtime_events
        WHERE deferredAt < :cutoffMillis
          AND NOT EXISTS (
              SELECT 1
              FROM incidents
              WHERE incidents.id =
                    deferred_realtime_events.incidentId
          )
        """
    )
    suspend fun deleteOrphansBefore(
        cutoffMillis: Long
    ): Int

    @Query(
        """
        DELETE FROM deferred_realtime_events
        WHERE eventId = :eventId
        """
    )
    suspend fun deleteByEventId(
        eventId: String
    ): Int
}
