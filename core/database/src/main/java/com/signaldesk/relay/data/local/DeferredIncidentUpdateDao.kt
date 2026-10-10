package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeferredIncidentUpdateDao {

    @Insert(
        onConflict = OnConflictStrategy.IGNORE
    )
    suspend fun insert(
        event: DeferredIncidentUpdateEntity
    ): Long

    @Query(
        """
        SELECT *
        FROM deferred_incident_updates
        WHERE incidentId = :incidentId
        ORDER BY sequence ASC, deferredAt ASC, eventId ASC
        """
    )
    suspend fun loadForIncident(
        incidentId: String
    ): List<DeferredIncidentUpdateEntity>

    @Query(
        """
        SELECT *
        FROM deferred_incident_updates
        ORDER BY deferredAt ASC, eventId ASC
        """
    )
    suspend fun loadAll():
        List<DeferredIncidentUpdateEntity>

    @Query(
        """
        DELETE FROM deferred_incident_updates
        WHERE eventId = :eventId
        """
    )
    suspend fun deleteByEventId(
        eventId: String
    ): Int

    @Query(
        """
        DELETE FROM deferred_incident_updates
        WHERE deferredAt < :cutoffMillis
          AND NOT EXISTS (
              SELECT 1
              FROM incidents
              WHERE incidents.id =
                    deferred_incident_updates.incidentId
          )
        """
    )
    suspend fun deleteOrphansBefore(
        cutoffMillis: Long
    ): Int
}
