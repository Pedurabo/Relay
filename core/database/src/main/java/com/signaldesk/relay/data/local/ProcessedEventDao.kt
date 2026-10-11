package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProcessedEventDao {

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM processed_events
            WHERE ownerPrincipal = :ownerPrincipal
              AND eventId = :eventId
        )
        """
    )
    suspend fun exists(
        ownerPrincipal: String,
        eventId: String
    ): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(
        event: ProcessedEventEntity
    )
}
