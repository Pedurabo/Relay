package com.signaldesk.relay.data.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes session transitions and snapshot commits.
 *
 * All ownership-changing operations must participate in
 * this gate for the ordering guarantee to hold.
 */
class SessionOwnershipGate {

    private val mutex = Mutex()



    suspend fun <T> withSnapshotOwnership(
        expectedGeneration: Long,
        currentGeneration: () -> Long,
        operation: suspend () -> T
    ): T? =
        mutex.withLock {

            if (currentGeneration() != expectedGeneration) {
                return@withLock null
            }

            operation()
        }

    suspend fun <T> withSessionTransition(
        operation: suspend () -> T
    ): T =
        mutex.withLock {
            operation()
        }
}