package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow


internal fun shouldApplyAuthoritativeIncidentSnapshot(
    currentLatestSequence: Long?,
    incomingLatestSequence: Long
): Boolean {

    if (
        currentLatestSequence ==
        null
    ) {
        return true
    }

    if (
        currentLatestSequence <=
        0L
    ) {
        return true
    }

    return incomingLatestSequence >
        currentLatestSequence
}


internal fun reconcileSnapshotSequenceGap(
    gap: IncidentSequenceGapEntity,
    snapshotSequence: Long
): IncidentSequenceGapEntity? {

    if (snapshotSequence >= gap.receivedSequence) {
        return null
    }

    if (snapshotSequence < gap.expectedSequence) {
        return gap
    }

    return gap.copy(
        expectedSequence =
            snapshotSequence + 1L
    )
}
@Dao
interface IncidentDao {

    @Query(
        """
        SELECT *
        FROM incidents
        WHERE ownerPrincipal = :ownerPrincipal
        ORDER BY id ASC
        """
    )
    fun observeAll(
        ownerPrincipal: String
    ): Flow<List<IncidentEntity>>

    @Query(
        """
        SELECT *
        FROM incidents
        WHERE ownerPrincipal = :ownerPrincipal
          AND id = :incidentId
        LIMIT 1
        """
    )
    fun observeById(
        ownerPrincipal: String,
        incidentId: String
    ): Flow<IncidentEntity?>

    @Query(
        """
        SELECT *
        FROM incidents
        WHERE ownerPrincipal = :ownerPrincipal
          AND id = :incidentId
        LIMIT 1
        """
    )
    suspend fun getById(
        ownerPrincipal: String,
        incidentId: String
    ): IncidentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incident: IncidentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(incidents: List<IncidentEntity>)

    @Query(
        """
        SELECT *
        FROM incident_sequence_gaps
        WHERE ownerPrincipal = :ownerPrincipal
          AND incidentId = :incidentId
        LIMIT 1
        """
    )
    suspend fun getSnapshotSequenceGap(
        ownerPrincipal: String,
        incidentId: String
    ): IncidentSequenceGapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshotSequenceGap(
        gap: IncidentSequenceGapEntity
    )

    @Query(
        """
        DELETE FROM incident_sequence_gaps
        WHERE ownerPrincipal = :ownerPrincipal
          AND incidentId = :incidentId
        """
    )
    suspend fun deleteSnapshotSequenceGap(
        ownerPrincipal: String,
        incidentId: String
    ): Int

    @Transaction
    suspend fun reconcileAuthoritativeSnapshot(
        ownerPrincipal: String,
        incidents: List<IncidentEntity>
    ): Int {
        require(ownerPrincipal.isNotBlank()) {
            "Authenticated owner is required."
        }

        require(incidents.all {
            it.ownerPrincipal == ownerPrincipal
        }) {
            "Snapshot batch contains another account's incident."
        }

        var applied = 0

        for (incoming in incidents) {
            val key = AccountScopedIncidentId(
                ownerPrincipal = ownerPrincipal,
                incidentId = incoming.id
            )

            val current = getById(
                ownerPrincipal,
                incoming.id
            )

            val gap = getSnapshotSequenceGap(
                ownerPrincipal,
                incoming.id
            )

            val decision = OwnerScopedSnapshotPolicy.decide(
                key = key,
                currentIncidentKey =
                    if (current == null) null else key,
                currentLatestSequence = current?.latestSequence,
                incomingLatestSequence = incoming.latestSequence,
                currentGap = gap?.let {
                    OwnerScopedGapState(
                        key = key,
                        expectedSequence = it.expectedSequence,
                        receivedSequence = it.receivedSequence
                    )
                }
            )

            if (!decision.applyIncident) {
                continue
            }

            insert(incoming)

            if (decision.deleteExistingGap) {
                deleteSnapshotSequenceGap(
                    ownerPrincipal,
                    incoming.id
                )
            } else {
                val replacement = decision.replacementGap

                if (
                    gap != null &&
                    replacement != null &&
                    replacement.expectedSequence != gap.expectedSequence
                ) {
                    upsertSnapshotSequenceGap(
                        gap.copy(
                            expectedSequence = replacement.expectedSequence
                        )
                    )
                }
            }

            applied++
        }

        return applied
    }

    @Query(
        """
        SELECT COUNT(*)
        FROM incidents
        WHERE ownerPrincipal = :ownerPrincipal
        """
    )
    suspend fun count(ownerPrincipal: String): Int
}
