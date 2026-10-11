package com.signaldesk.relay.data.local

/**
 * An authoritative snapshot associated with its authenticated owner.
 *
 * This is independent of the Room entity schema so it can be
 * introduced before the coordinated version-16 migration.
 */
data class OwnerScopedIncidentSnapshot(
    val key: AccountScopedIncidentId,
    val title: String,
    val status: String,
    val severity: String,
    val latestSequence: Long
)

/**
 * Storage operations required by owner-scoped reconciliation.
 *
 * The version-16 Room implementation must filter every operation
 * by both ownerPrincipal and incidentId.
 *
 * withTransaction must provide a real atomic database transaction.
 */
interface OwnerScopedSnapshotStore {

    suspend fun <T> withTransaction(
        operation: suspend () -> T
    ): T

    suspend fun latestSequence(
        key: AccountScopedIncidentId
    ): Long?

    suspend fun sequenceGap(
        key: AccountScopedIncidentId
    ): OwnerScopedGapState?

    suspend fun upsertIncident(
        snapshot: OwnerScopedIncidentSnapshot
    )

    suspend fun upsertGap(
        gap: OwnerScopedGapState
    )

    suspend fun deleteGap(
        key: AccountScopedIncidentId
    )
}

/**
 * Coordinates the existing snapshot decision policy and storage
 * mutations inside one transaction.
 *
 * Callers must separately hold the shared authenticated session
 * ownership gate. This class does not authorize a session.
 */
class OwnerScopedSnapshotReconciler(
    private val store: OwnerScopedSnapshotStore
) {

    suspend fun reconcile(
        ownerPrincipal: String,
        snapshots: List<OwnerScopedIncidentSnapshot>
    ): Int {
        require(ownerPrincipal.isNotBlank()) {
            "Authenticated snapshot owner is required."
        }

        require(snapshots.all {
            it.key.ownerPrincipal == ownerPrincipal
        }) {
            "Snapshot batch contains a different owner."
        }

        return store.withTransaction {
            var applied = 0

            for (snapshot in snapshots) {
                val key = snapshot.key
                val currentSequence = store.latestSequence(key)
                val currentGap = store.sequenceGap(key)

                val decision = OwnerScopedSnapshotPolicy.decide(
                    key = key,
                    currentIncidentKey =
                        if (currentSequence == null) null else key,
                    currentLatestSequence = currentSequence,
                    incomingLatestSequence = snapshot.latestSequence,
                    currentGap = currentGap
                )

                if (!decision.applyIncident) {
                    continue
                }

                store.upsertIncident(snapshot)

                if (decision.deleteExistingGap) {
                    store.deleteGap(key)
                } else {
                    val replacement = decision.replacementGap
                    if (
                        replacement != null &&
                        replacement != currentGap
                    ) {
                        store.upsertGap(replacement)
                    }
                }

                applied++
            }

            applied
        }
    }
}
