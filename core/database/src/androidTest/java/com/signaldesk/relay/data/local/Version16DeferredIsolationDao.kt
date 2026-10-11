package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * Staged v16 DAO for recovery and orphan cleanup.
 * Every query is explicitly owner-filtered.
 */
@Dao
internal interface Version16DeferredIsolationDao {

    @Query(
        """
        SELECT eventId FROM deferred_realtime_events
        WHERE ownerPrincipal = :owner
          AND incidentId = :incidentId
        ORDER BY deferredAt ASC, eventId ASC
        """
    )
    suspend fun timelineEvents(
        owner: String,
        incidentId: String
    ): List<String>

    @Query(
        """
        SELECT eventId FROM deferred_incident_updates
        WHERE ownerPrincipal = :owner
          AND incidentId = :incidentId
        ORDER BY sequence ASC, deferredAt ASC, eventId ASC
        """
    )
    suspend fun incidentUpdates(
        owner: String,
        incidentId: String
    ): List<String>

    @Query(
        """
        DELETE FROM deferred_realtime_events
        WHERE ownerPrincipal = :owner
          AND eventId = :eventId
        """
    )
    suspend fun deleteTimelineEvent(
        owner: String,
        eventId: String
    ): Int

    @Query(
        """
        DELETE FROM deferred_incident_updates
        WHERE ownerPrincipal = :owner
          AND eventId = :eventId
        """
    )
    suspend fun deleteIncidentUpdate(
        owner: String,
        eventId: String
    ): Int

    @Query(
        """
        DELETE FROM deferred_realtime_events
        WHERE ownerPrincipal = :owner
          AND deferredAt < :cutoff
          AND NOT EXISTS (
              SELECT 1 FROM incidents
              WHERE incidents.ownerPrincipal =
                    deferred_realtime_events.ownerPrincipal
                AND incidents.id =
                    deferred_realtime_events.incidentId
          )
        """
    )
    suspend fun deleteTimelineOrphans(
        owner: String,
        cutoff: Long
    ): Int

    @Query(
        """
        DELETE FROM deferred_incident_updates
        WHERE ownerPrincipal = :owner
          AND deferredAt < :cutoff
          AND NOT EXISTS (
              SELECT 1 FROM incidents
              WHERE incidents.ownerPrincipal =
                    deferred_incident_updates.ownerPrincipal
                AND incidents.id =
                    deferred_incident_updates.incidentId
          )
        """
    )
    suspend fun deleteUpdateOrphans(
        owner: String,
        cutoff: Long
    ): Int
}
