package com.signaldesk.relay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Test-only prototype of the v16 incident and gap access boundary.
 *
 * Production IncidentDao remains unchanged at Room v15.
 */
@Dao
internal interface Version16IncidentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIncident(incident: V16Incident)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGap(gap: V16IncidentSequenceGap)

    @Query(
        """
        SELECT * FROM incidents
        WHERE ownerPrincipal = :owner
        AND id = :incidentId
        LIMIT 1
        """
    )
    suspend fun getIncident(
        owner: String,
        incidentId: String
    ): V16Incident?

    @Query(
        """
        SELECT * FROM incident_sequence_gaps
        WHERE ownerPrincipal = :owner
        AND incidentId = :incidentId
        LIMIT 1
        """
    )
    suspend fun getGap(
        owner: String,
        incidentId: String
    ): V16IncidentSequenceGap?

    @Query(
        """
        DELETE FROM incident_sequence_gaps
        WHERE ownerPrincipal = :owner
        AND incidentId = :incidentId
        """
    )
    suspend fun deleteGap(
        owner: String,
        incidentId: String
    ): Int

    /**
     * Test-only atomic owner-scoped snapshot reconciliation.
     * Mirrors the monotonic sequence and gap rules.
     */
    @Transaction
    suspend fun reconcile(
        owner: String,
        incoming: V16Incident
    ): Boolean {
        require(owner.isNotBlank())
        require(incoming.ownerPrincipal == owner)

        val current = getIncident(owner, incoming.id)

        if (!shouldApplyAuthoritativeIncidentSnapshot(
                currentLatestSequence = current?.latestSequence,
                incomingLatestSequence = incoming.latestSequence
            )
        ) {
            return false
        }

        upsertIncident(incoming)

        if (incoming.latestSequence > 0L) {
            val gap = getGap(owner, incoming.id)

            if (gap != null) {
                when {
                    incoming.latestSequence >= gap.receivedSequence ->
                        deleteGap(owner, incoming.id)

                    incoming.latestSequence >= gap.expectedSequence ->
                        upsertGap(
                            gap.copy(
                                expectedSequence =
                                    incoming.latestSequence + 1L
                            )
                        )
                }
            }
        }

        return true
    }
}
