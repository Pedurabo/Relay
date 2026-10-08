package com.signaldesk.relay.data.realtime

import android.content.Context

data class PendingSeverityCommand(
    val commandId: String,
    val incidentId: String,
    val severity: String,
    val baseSeverity: String
)

class PendingSeverityCommandStore(
    context: Context
) {

    private val preferences =
        context.getSharedPreferences(
            "relay_severity_outbox",
            Context.MODE_PRIVATE
        )

    fun save(
        command: PendingSeverityCommand
    ) {

        preferences
            .edit()
            .putString(
                "commandId",
                command.commandId
            )
            .putString(
                "incidentId",
                command.incidentId
            )
            .putString(
                "severity",
                command.severity
            )
            .putString(
                "baseSeverity",
                command.baseSeverity
            )
            .apply()
    }

    fun load():
        PendingSeverityCommand? {

        val commandId =
            preferences.getString(
                "commandId",
                null
            )
                ?: return null

        val incidentId =
            preferences.getString(
                "incidentId",
                null
            )
                ?: return null

        val severity =
            preferences.getString(
                "severity",
                null
            )
                ?: return null

        val baseSeverity =
            preferences.getString(
                "baseSeverity",
                null
            )
                ?: return null

        return PendingSeverityCommand(
            commandId =
                commandId,
            incidentId =
                incidentId,
            severity =
                severity,
            baseSeverity =
                baseSeverity
        )
    }

    fun clearIf(
        commandId: String
    ) {

        val storedCommandId =
            preferences.getString(
                "commandId",
                null
            )

        if (
            storedCommandId !=
            commandId
        ) {
            return
        }

        preferences
            .edit()
            .clear()
            .apply()
    }
}
