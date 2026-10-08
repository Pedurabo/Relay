package com.signaldesk.relay.data.realtime

import android.content.Context
import com.signaldesk.relay.data.local.IncidentStoreFactory
import com.signaldesk.relay.data.local.PendingStatusCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PendingStatusMutation(
    val commandId: String,
    val incidentId: String,
    val status: String,
    val baseStatus: String,
    val ownerPrincipal: String
)

class StatusCommandGateway(
    context: Context
) {

    private val store =
        IncidentStoreFactory.pendingStatus(
            context.applicationContext
        )

    suspend fun save(
        mutation: PendingStatusMutation
    ) {

        store.save(
            PendingStatusCommand(
                commandId =
                    mutation.commandId,
                incidentId =
                    mutation.incidentId,
                status =
                    mutation.status,
                baseStatus =
                    mutation.baseStatus,
                ownerPrincipal =
                    mutation.ownerPrincipal
            )
        )
    }

    suspend fun loadForIncident(
        incidentId: String,
        ownerPrincipal: String
    ): PendingStatusMutation? {

        return store
            .loadForIncident(
                incidentId,
                ownerPrincipal
            )
            ?.toMutation()
    }

    fun observeForIncident(
        incidentId: String,
        ownerPrincipal: String
    ): Flow<PendingStatusMutation?> {

        return store
            .observeForIncident(
                incidentId,
                ownerPrincipal
            )
            .map { command ->
                command?.toMutation()
            }
    }

    private fun PendingStatusCommand.toMutation():
        PendingStatusMutation {

        return PendingStatusMutation(
            commandId =
                commandId,
            incidentId =
                incidentId,
            status =
                status,
            baseStatus =
                baseStatus,
            ownerPrincipal =
                ownerPrincipal
        )
    }
}