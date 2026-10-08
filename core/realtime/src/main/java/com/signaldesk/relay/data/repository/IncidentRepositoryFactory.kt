package com.signaldesk.relay.data.repository

import android.content.Context
import com.signaldesk.relay.data.local.RelayDatabase

object IncidentRepositoryFactory {

    fun create(
        context: Context
    ): IncidentRepository {

        val database =
            RelayDatabase.getInstance(
                context.applicationContext
            )

        return IncidentRepository(
            incidentDao =
                database.incidentDao(),
            timelineEntryDao =
                database.timelineEntryDao()
        )
    }
}