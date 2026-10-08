package com.signaldesk.relay.data.realtime

import android.content.Context
import com.signaldesk.relay.data.local.IncidentStoreFactory
import com.signaldesk.relay.data.local.PendingSeverityCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PendingSeverityMutation(
    val commandId: String,
    val incidentId: String,
    val severity: String,
    val baseSeverity: String,
    val ownerPrincipal: String
)

class SeverityCommandGateway(
    context: Context
) {

    private val store =
        IncidentStoreFactory.pendingSeverity(
            context.applicationContext
        )

    suspend fun save(
        mutation: PendingSeverityMutation
    ) {

        store.save(
            PendingSeverityCommand(
                commandId =
                    mutation.commandId,
                incidentId =
                    mutation.incidentId,
                severity =
                    mutation.severity,
                baseSeverity =
                    mutation.baseSeverity,
                ownerPrincipal =
                    mutation.ownerPrincipal
            )
        )
    }

    suspend fun loadForIncident(
        incidentId: String,
        ownerPrincipal: String
    ): PendingSeverityMutation? {

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
    ): Flow<PendingSeverityMutation?> {

        return store
            .observeForIncident(
                incidentId,
                ownerPrincipal
            )
            .map { command ->
                command?.toMutation()
            }
    }

    private fun PendingSeverityCommand.toMutation():
        PendingSeverityMutation {

        return PendingSeverityMutation(
            commandId =
                commandId,
            incidentId =
                incidentId,
            severity =
                severity,
            baseSeverity =
                baseSeverity,
            ownerPrincipal =
                ownerPrincipal
        )
    }
}