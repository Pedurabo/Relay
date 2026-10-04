package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName =
        "pending_status_commands"
)
data class PendingStatusCommand(
    @androidx.room.PrimaryKey
    val commandId: String,

    val incidentId: String,

    val status: String,

    val baseStatus: String,

    val ownerPrincipal: String,

    val createdAt: Long =
        System.currentTimeMillis(),

    val deliveryState: String =
        "PENDING"
)

@Dao
interface PendingStatusCommandDao {

    @Insert(
        onConflict =
            OnConflictStrategy.REPLACE
    )
    suspend fun upsert(
        command:
            PendingStatusCommand
    )

    @Query(
        """
        SELECT *
        FROM pending_status_commands
        WHERE ownerPrincipal = :ownerPrincipal
        ORDER BY createdAt ASC
        LIMIT 1
        """
    )
    suspend fun getOldestForOwner(
        ownerPrincipal: String
    ):
        PendingStatusCommand?

    @Query(
        """
        SELECT *
        FROM pending_status_commands
        WHERE incidentId = :incidentId
          AND ownerPrincipal = :ownerPrincipal
        ORDER BY createdAt ASC
        LIMIT 1
        """
    )
    suspend fun getOldestForIncident(
        incidentId: String,
        ownerPrincipal: String
    ):
        PendingStatusCommand?

    @Query(
        """
        SELECT *
        FROM pending_status_commands
        WHERE incidentId = :incidentId
          AND ownerPrincipal = :ownerPrincipal
        ORDER BY createdAt ASC
        LIMIT 1
        """
    )
    fun observeOldestForIncident(
        incidentId: String,
        ownerPrincipal: String
    ):
        Flow<PendingStatusCommand?>

    @Query(
        """
        SELECT *
        FROM pending_status_commands
        WHERE ownerPrincipal = :ownerPrincipal
        ORDER BY createdAt ASC
        """
    )
    suspend fun getAllForOwner(
        ownerPrincipal: String
    ):
        List<PendingStatusCommand>

    @Query(
        """
        DELETE FROM pending_status_commands
        WHERE commandId = :commandId
          AND ownerPrincipal = :ownerPrincipal
        """
    )
    suspend fun deleteByCommandId(
        commandId: String,
        ownerPrincipal: String
    )

    @Query(
        """
        UPDATE pending_status_commands
        SET deliveryState = 'IN_FLIGHT'
        WHERE commandId = :commandId
          AND ownerPrincipal = :ownerPrincipal
          AND deliveryState = 'PENDING'
        """
    )
    suspend fun claimForDelivery(
        commandId: String,
        ownerPrincipal: String
    ): Int

    @Query(
        """
        UPDATE pending_status_commands
        SET deliveryState = 'PENDING'
        WHERE commandId = :commandId
          AND ownerPrincipal = :ownerPrincipal
          AND deliveryState = 'IN_FLIGHT'
        """
    )
    suspend fun releaseDelivery(
        commandId: String,
        ownerPrincipal: String
    ): Int

    @Query(
        """
        UPDATE pending_status_commands
        SET deliveryState = 'PENDING'
        WHERE deliveryState = 'IN_FLIGHT'
        """
    )
    suspend fun resetInFlight():
        Int
}

class RoomPendingStatusCommandStore(
    private val dao:
        PendingStatusCommandDao
) {

    suspend fun save(
        command:
            PendingStatusCommand
    ) {

        dao.upsert(
            command
        )
    }

    suspend fun load(
        ownerPrincipal: String
    ):
        PendingStatusCommand? {

        return dao.getOldestForOwner(
            ownerPrincipal
        )
    }

    suspend fun loadForIncident(
        incidentId: String,
        ownerPrincipal: String
    ):
        PendingStatusCommand? {

        return dao.getOldestForIncident(
            incidentId,
            ownerPrincipal
        )
    }

    fun observeForIncident(
        incidentId: String,
        ownerPrincipal: String
    ):
        Flow<PendingStatusCommand?> {

        return dao.observeOldestForIncident(
            incidentId,
            ownerPrincipal
        )
    }

    suspend fun loadAll(
        ownerPrincipal: String
    ):
        List<PendingStatusCommand> {

        return dao.getAllForOwner(
            ownerPrincipal
        )
    }

    suspend fun clearIf(
        commandId: String,
        ownerPrincipal: String
    ) {

        dao.deleteByCommandId(
            commandId,
            ownerPrincipal
        )
    }

    suspend fun claim(
        commandId: String,
        ownerPrincipal: String
    ): Boolean {

        return dao.claimForDelivery(
            commandId,
            ownerPrincipal
        ) == 1
    }

    suspend fun release(
        commandId: String,
        ownerPrincipal: String
    ) {

        dao.releaseDelivery(
            commandId,
            ownerPrincipal
        )
    }

    suspend fun resetInFlight():
        Int {

        return dao.resetInFlight()
    }
}