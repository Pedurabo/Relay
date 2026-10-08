package com.signaldesk.relay.model

enum class IncidentSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW;

    companion object {

        fun fromStoredValue(
            value: String
        ): IncidentSeverity {

            return entries
                .firstOrNull {
                    it.name.equals(
                        value,
                        ignoreCase = true
                    )
                }
                ?: MEDIUM
        }
    }
}
