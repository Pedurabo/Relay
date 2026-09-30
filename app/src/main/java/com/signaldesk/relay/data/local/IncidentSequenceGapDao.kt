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
        WHERE incidentId = :incidentId
        LIMIT 1
        """
    )
    suspend fun getByIncidentId(
        incidentId: String
    ): IncidentSequenceGapEntity?

    @Query(
        """
        SELECT *
        FROM incident_sequence_gaps
        WHERE incidentId = :incidentId
        LIMIT 1
        """
    )
    fun observeByIncidentId(
        incidentId: String
    ): Flow<IncidentSequenceGapEntity?>

    @Query(
        """
        SELECT *
        FROM incident_sequence_gaps
        ORDER BY detectedAt ASC
        """
    )
    fun observeAll():
        Flow<List<IncidentSequenceGapEntity>>

    @Insert(
        onConflict =
            OnConflictStrategy.REPLACE
    )
    suspend fun upsert(
        gap: IncidentSequenceGapEntity
    )

    @Query(
        """
        DELETE FROM incident_sequence_gaps
        WHERE incidentId = :incidentId
        """
    )
    suspend fun delete(
        incidentId: String
    )
}
