package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeferredIncidentUpdateDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(
        event: DeferredIncidentUpdateEntity
    ): Long

    @Query(
        """
        SELECT *
        FROM deferred_incident_updates
        WHERE ownerPrincipal = :ownerPrincipal
          AND incidentId = :incidentId
        ORDER BY sequence ASC, deferredAt ASC, eventId ASC
        """
    )
    suspend fun loadForIncident(
        ownerPrincipal: String,
        incidentId: String
    ): List<DeferredIncidentUpdateEntity>

    @Query(
        """
        SELECT *
        FROM deferred_incident_updates
        WHERE ownerPrincipal = :ownerPrincipal
        ORDER BY deferredAt ASC, eventId ASC
        """
    )
    suspend fun loadAll(
        ownerPrincipal: String
    ): List<DeferredIncidentUpdateEntity>

    @Query(
        """
        DELETE FROM deferred_incident_updates
        WHERE ownerPrincipal = :ownerPrincipal
          AND eventId = :eventId
        """
    )
    suspend fun deleteByEventId(
        ownerPrincipal: String,
        eventId: String
    ): Int

    @Query(
        """
        DELETE FROM deferred_incident_updates
        WHERE ownerPrincipal = :ownerPrincipal
          AND deferredAt < :cutoffMillis
          AND NOT EXISTS (
              SELECT 1
              FROM incidents
              WHERE incidents.ownerPrincipal =
                    deferred_incident_updates.ownerPrincipal
                AND incidents.id =
                    deferred_incident_updates.incidentId
          )
        """
    )
    suspend fun deleteOrphansBefore(
        ownerPrincipal: String,
        cutoffMillis: Long
    ): Int
}
