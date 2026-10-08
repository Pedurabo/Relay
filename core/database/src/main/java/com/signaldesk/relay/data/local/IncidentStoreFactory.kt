package com.signaldesk.relay.data.local

import android.content.Context

object IncidentStoreFactory {

    fun pendingCreate(
        context: Context
    ): RoomPendingCreateIncidentCommandStore {

        val database =
            RelayDatabase.getInstance(
                context.applicationContext
            )

        return RoomPendingCreateIncidentCommandStore(
            database.pendingCreateIncidentCommandDao()
        )
    }

    fun pendingSeverity(
        context: Context
    ): RoomPendingSeverityCommandStore {

        val database =
            RelayDatabase.getInstance(
                context.applicationContext
            )

        return RoomPendingSeverityCommandStore(
            database.pendingSeverityCommandDao()
        )
    }

    fun pendingStatus(
        context: Context
    ): RoomPendingStatusCommandStore {

        val database =
            RelayDatabase.getInstance(
                context.applicationContext
            )

        return RoomPendingStatusCommandStore(
            database.pendingStatusCommandDao()
        )
    }
}