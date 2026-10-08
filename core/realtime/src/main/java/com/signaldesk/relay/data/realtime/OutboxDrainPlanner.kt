package com.signaldesk.relay.data.realtime

internal object OutboxDrainPlanner {

    fun eligibleCommandIds(
        commandIds: List<String>,
        retryNotBeforeMillis:
            Map<String, Long>,
        nowMillis: Long
    ): List<String> {

        return commandIds.filter { commandId ->

            val notBefore =
                retryNotBeforeMillis[
                    commandId
                ]

            notBefore == null ||
                notBefore <=
                nowMillis
        }
    }

    fun nearestRetryAtMillis(
        commandIds: List<String>,
        retryNotBeforeMillis:
            Map<String, Long>,
        nowMillis: Long
    ): Long? {

        return commandIds
            .mapNotNull { commandId ->
                retryNotBeforeMillis[
                    commandId
                ]
            }
            .filter { retryAt ->
                retryAt >
                    nowMillis
            }
            .minOrNull()
    }
}
