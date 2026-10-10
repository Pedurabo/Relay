package com.signaldesk.relay.data.realtime

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object TimelineEntryDeliveryGate {

    private const val STRIPE_COUNT =
        64

    private val gates =
        Array(STRIPE_COUNT) {
            Mutex()
        }

    suspend fun <T> withEntry(
        entryId: String,
        block: suspend () -> T
    ): T {

        val index =
            (
                entryId.hashCode() and
                    Int.MAX_VALUE
                ) %
                gates.size

        return gates[
            index
        ].withLock {
            block()
        }
    }
}

internal suspend fun <T, R> sendTimelineEntryIfCurrent(
    entryId: String,
    loadCurrent: suspend () -> T?,
    isEligible: (T) -> Boolean,
    send: suspend (T) -> R
): R? {

    return TimelineEntryDeliveryGate
        .withEntry(
            entryId
        ) {

            val current =
                loadCurrent()
                    ?: return@withEntry null

            if (
                !isEligible(
                    current
                )
            ) {
                return@withEntry null
            }

            send(
                current
            )
        }
}
