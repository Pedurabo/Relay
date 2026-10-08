package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Query("SELECT * FROM incidents ORDER BY id ASC")
    fun observeAll(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE id = :incidentId LIMIT 1")
    fun observeById(incidentId: String): Flow<IncidentEntity?>

    @Query("SELECT * FROM incidents WHERE id = :incidentId LIMIT 1")
    suspend fun getById(incidentId: String): IncidentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incident: IncidentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(incidents: List<IncidentEntity>)

    @Query("SELECT COUNT(*) FROM incidents")
    suspend fun count(): Int
}
