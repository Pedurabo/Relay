package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentSequenceGapDao {

    @Query(
        """
        SELECT *
        FROM incident_sequence_gaps
        WHERE ownerPrincipal = :ownerPrincipal
          AND incidentId = :incidentId
        LIMIT 1
        """
    )
    suspend fun getByIncidentId(
        ownerPrincipal: String,
        incidentId: String
    ): IncidentSequenceGapEntity?

    @Query(
        """
        SELECT *
        FROM incident_sequence_gaps
        WHERE ownerPrincipal = :ownerPrincipal
          AND incidentId = :incidentId
        LIMIT 1
        """
    )
    fun observeByIncidentId(
        ownerPrincipal: String,
        incidentId: String
    ): Flow<IncidentSequenceGapEntity?>

    @Query(
        """
        SELECT *
        FROM incident_sequence_gaps
        WHERE ownerPrincipal = :ownerPrincipal
        ORDER BY detectedAt ASC
        """
    )
    fun observeAll(
        ownerPrincipal: String
    ): Flow<List<IncidentSequenceGapEntity>>

    @Insert(
        onConflict = OnConflictStrategy.REPLACE
    )
    suspend fun upsert(
        gap: IncidentSequenceGapEntity
    )

    @Query(
        """
        DELETE FROM incident_sequence_gaps
        WHERE ownerPrincipal = :ownerPrincipal
          AND incidentId = :incidentId
        """
    )
    suspend fun delete(
        ownerPrincipal: String,
        incidentId: String
    ): Int
}
