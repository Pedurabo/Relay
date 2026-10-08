package com.signaldesk.relay.data.realtime

import android.content.Context
import com.signaldesk.relay.data.local.IncidentStoreFactory
import com.signaldesk.relay.data.local.PendingCreateIncidentCommand

object CreateIncidentCommandQueue {

    suspend fun enqueue(
        context: Context,
        commandId: String,
        incidentId: String,
        title: String,
        status: String,
        severity: String,
        ownerPrincipal: String
    ) {

        IncidentStoreFactory
            .pendingCreate(
                context
            )
            .save(
                PendingCreateIncidentCommand(
                    commandId =
                        commandId,
                    incidentId =
                        incidentId,
                    title =
                        title,
                    status =
                        status,
                    severity =
                        severity,
                    ownerPrincipal =
                        ownerPrincipal
                )
            )
    }
}