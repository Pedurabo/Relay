package com.signaldesk.relay.data.local

/**
 * A gap state associated with one authenticated account
 * and one incident. This is not a Room entity.
 */
data class OwnerScopedGapState(
    val key: AccountScopedIncidentId,
    val expectedSequence: Long,
    val receivedSequence: Long
)

/**
 * A pure snapshot decision.
 *
 * Database mutation must occur separately in one
 * owner-scoped Room transaction, under the session gate.
 */
data class OwnerScopedSnapshotDecision(
    val key: AccountScopedIncidentId,
    val applyIncident: Boolean,
    val replacementGap: OwnerScopedGapState?,
    val deleteExistingGap: Boolean
)

object OwnerScopedSnapshotPolicy {

    fun decide(
        key: AccountScopedIncidentId,
        currentIncidentKey: AccountScopedIncidentId?,
        currentLatestSequence: Long?,
        incomingLatestSequence: Long,
        currentGap: OwnerScopedGapState?
    ): OwnerScopedSnapshotDecision {

        require(
            currentIncidentKey == null ||
                currentIncidentKey == key
        ) {
            "Cannot reconcile another owner's incident."
        }

        require(
            currentGap == null ||
                currentGap.key == key
        ) {
            "Cannot reconcile another owner's sequence gap."
        }

        require(
            currentIncidentKey != null ||
                currentLatestSequence == null
        ) {
            "Sequence cannot exist without a current incident."
        }

        val apply =
            shouldApplyAuthoritativeIncidentSnapshot(
                currentLatestSequence = currentLatestSequence,
                incomingLatestSequence = incomingLatestSequence
            )

        if (!apply) {
            return OwnerScopedSnapshotDecision(
                key = key,
                applyIncident = false,
                replacementGap = currentGap,
                deleteExistingGap = false
            )
        }

        if (currentGap == null || incomingLatestSequence <= 0L) {
            return OwnerScopedSnapshotDecision(
                key = key,
                applyIncident = true,
                replacementGap = currentGap,
                deleteExistingGap = false
            )
        }

        val updatedGap = when {
            incomingLatestSequence >= currentGap.receivedSequence ->
                null

            incomingLatestSequence < currentGap.expectedSequence ->
                currentGap

            else ->
                currentGap.copy(
                    expectedSequence = incomingLatestSequence + 1L
                )
        }

        return OwnerScopedSnapshotDecision(
            key = key,
            applyIncident = true,
            replacementGap = updatedGap,
            deleteExistingGap = updatedGap == null
        )
    }
}
