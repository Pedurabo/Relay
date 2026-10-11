package com.signaldesk.relay.data.local

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Persistence operations for the owner-scoped incident model.
 *
 * Implementations must use both parts of AccountScopedIncidentId
 * in every read, update, and delete.
 */
interface OwnerScopedIncidentPersistence {

    suspend fun findIncident(
        key: AccountScopedIncidentId
    ): OwnerScopedIncidentSnapshot?

    suspend fun findGap(
        key: AccountScopedIncidentId
    ): OwnerScopedPersistedGap?

    suspend fun saveIncident(
        snapshot: OwnerScopedIncidentSnapshot
    )

    suspend fun saveGap(
        gap: OwnerScopedPersistedGap
    )

    suspend fun removeGap(
        key: AccountScopedIncidentId
    )
}

/**
 * Retains the original gap detection timestamp when reconciling.
 */
data class OwnerScopedPersistedGap(
    val state: OwnerScopedGapState,
    val detectedAt: Long
)

/**
 * Production Room transaction adapter.
 *
 * This is schema-independent until the v16 DAO implements
 * OwnerScopedIncidentPersistence.
 *
 * Session authorization remains the caller's responsibility.
 */
class OwnerScopedRoomSnapshotStore(
    private val database: RoomDatabase,
    private val persistence: OwnerScopedIncidentPersistence
) : OwnerScopedSnapshotStore {

    override suspend fun <T> withTransaction(
        operation: suspend () -> T
    ): T = database.withTransaction {
        operation()
    }

    override suspend fun latestSequence(
        key: AccountScopedIncidentId
    ): Long? =
        persistence.findIncident(key)?.latestSequence

    override suspend fun sequenceGap(
        key: AccountScopedIncidentId
    ): OwnerScopedGapState? =
        persistence.findGap(key)?.state

    override suspend fun upsertIncident(
        snapshot: OwnerScopedIncidentSnapshot
    ) {
        persistence.saveIncident(snapshot)
    }

    override suspend fun upsertGap(
        gap: OwnerScopedGapState
    ) {
        val previous = persistence.findGap(gap.key)
            ?: error("Cannot update a missing sequence gap.")

        persistence.saveGap(
            previous.copy(state = gap)
        )
    }

    override suspend fun deleteGap(
        key: AccountScopedIncidentId
    ) {
        persistence.removeGap(key)
    }
}
