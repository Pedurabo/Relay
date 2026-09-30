package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(
    tableName =
        "pending_severity_commands"
)
data class PendingSeverityCommand(
    @androidx.room.PrimaryKey
    val commandId: String,

    val incidentId: String,

    val severity: String,

    val baseSeverity: String,

    val ownerPrincipal: String,

    val createdAt: Long =
        System.currentTimeMillis(),

    val deliveryState: String =
        "PENDING"
)

@Dao
interface PendingSeverityCommandDao {

    @Insert(
        onConflict =
            OnConflictStrategy.REPLACE
    )
    suspend fun upsert(
        command:
            PendingSeverityCommand
    )

    @Query(
        """
        SELECT *
        FROM pending_severity_commands
        WHERE ownerPrincipal = :ownerPrincipal
        ORDER BY createdAt ASC
        LIMIT 1
        """
    )
    suspend fun getOldestForOwner(
        ownerPrincipal: String
    ):
        PendingSeverityCommand?

    @Query(
        """
        SELECT *
        FROM pending_severity_commands
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
        PendingSeverityCommand?

    @Query(
        """
        SELECT *
        FROM pending_severity_commands
        WHERE ownerPrincipal = :ownerPrincipal
        ORDER BY createdAt ASC
        """
    )
    suspend fun getAllForOwner(
        ownerPrincipal: String
    ):
        List<PendingSeverityCommand>

    @Query(
        """
        DELETE FROM pending_severity_commands
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
        UPDATE pending_severity_commands
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
        UPDATE pending_severity_commands
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
        UPDATE pending_severity_commands
        SET deliveryState = 'PENDING'
        WHERE deliveryState = 'IN_FLIGHT'
        """
    )
    suspend fun resetInFlight():
        Int
}

class RoomPendingSeverityCommandStore(
    private val dao:
        PendingSeverityCommandDao
) {

    suspend fun save(
        command:
            PendingSeverityCommand
    ) {

        dao.upsert(
            command
        )
    }

    suspend fun load(
        ownerPrincipal: String
    ):
        PendingSeverityCommand? {

        return dao.getOldestForOwner(
            ownerPrincipal
        )
    }

    suspend fun loadForIncident(
        incidentId: String,
        ownerPrincipal: String
    ):
        PendingSeverityCommand? {

        return dao.getOldestForIncident(
            incidentId,
            ownerPrincipal
        )
    }

    suspend fun loadAll(
        ownerPrincipal: String
    ):
        List<PendingSeverityCommand> {

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
