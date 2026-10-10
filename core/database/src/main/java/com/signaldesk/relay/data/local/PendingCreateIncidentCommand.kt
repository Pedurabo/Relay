package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Entity(
    tableName = "pending_create_incident_commands",
    indices = [
        Index(
            value = [
                "ownerPrincipal",
                "createdAt",
                "commandId"
            ]
        ),
        Index(
            value = [
                "incidentId",
                "ownerPrincipal",
                "createdAt",
                "commandId"
            ]
        )
    ]
)
data class PendingCreateIncidentCommand(
    @androidx.room.PrimaryKey
    val commandId: String,

    val incidentId: String,

    val title: String,

    val status: String,

    val severity: String,

    val ownerPrincipal: String,

    val createdAt: Long =
        System.currentTimeMillis(),

    val deliveryState: String =
        "PENDING"
)

@Dao
interface PendingCreateIncidentCommandDao {

    @Insert(
        onConflict =
            OnConflictStrategy.REPLACE
    )
    suspend fun upsert(
        command:
            PendingCreateIncidentCommand
    )

    @Query(
        """
        SELECT *
        FROM pending_create_incident_commands
        WHERE ownerPrincipal = :ownerPrincipal
        ORDER BY createdAt ASC
        LIMIT 1
        """
    )
    suspend fun getOldestForOwner(
        ownerPrincipal: String
    ):
        PendingCreateIncidentCommand?

    @Query(
        """
        SELECT *
        FROM pending_create_incident_commands
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
        PendingCreateIncidentCommand?

    @Query(
        """
        SELECT *
        FROM pending_create_incident_commands
        WHERE ownerPrincipal = :ownerPrincipal
        ORDER BY createdAt ASC
        """
    )
    suspend fun getAllForOwner(
        ownerPrincipal: String
    ):
        List<PendingCreateIncidentCommand>

    @Query(
        """
        DELETE FROM pending_create_incident_commands
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
        UPDATE pending_create_incident_commands
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
        UPDATE pending_create_incident_commands
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
        UPDATE pending_create_incident_commands
        SET deliveryState = 'PENDING'
        WHERE deliveryState = 'IN_FLIGHT'
        """
    )
    suspend fun resetInFlight():
        Int
}

class RoomPendingCreateIncidentCommandStore(
    private val dao:
        PendingCreateIncidentCommandDao
) {

    suspend fun save(
        command:
            PendingCreateIncidentCommand
    ) {

        dao.upsert(
            command
        )
    }

    suspend fun load(
        ownerPrincipal: String
    ):
        PendingCreateIncidentCommand? {

        return dao.getOldestForOwner(
            ownerPrincipal
        )
    }

    suspend fun loadForIncident(
        incidentId: String,
        ownerPrincipal: String
    ):
        PendingCreateIncidentCommand? {

        return dao.getOldestForIncident(
            incidentId,
            ownerPrincipal
        )
    }

    suspend fun loadAll(
        ownerPrincipal: String
    ):
        List<PendingCreateIncidentCommand> {

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