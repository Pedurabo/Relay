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

    @Query("SELECT * FROM incidents ORDER BY id ASC")
    fun observeAll(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE id = :incidentId LIMIT 1")
    fun observeById(incidentId: String): Flow<IncidentEntity?>

    @Query("SELECT * FROM incidents WHERE id = :incidentId LIMIT 1")
    suspend fun getById(incidentId: String): IncidentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incident: IncidentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(incidents: List<IncidentEntity>)

    @Query(
        """
        SELECT *
        FROM incident_sequence_gaps
        WHERE incidentId = :incidentId
        LIMIT 1
        """
    )
    suspend fun getSnapshotSequenceGap(
        incidentId: String
    ): IncidentSequenceGapEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshotSequenceGap(
        gap: IncidentSequenceGapEntity
    )

    @Query(
        """
        DELETE FROM incident_sequence_gaps
        WHERE incidentId = :incidentId
        """
    )
    suspend fun deleteSnapshotSequenceGap(
        incidentId: String
    )
    @Transaction
    suspend fun reconcileAuthoritativeSnapshot(
        incidents: List<IncidentEntity>
    ): Int {

        var applied =
            0

        incidents.forEach {
            incoming ->

            val current =
                getById(
                    incoming.id
                )

            if (
                shouldApplyAuthoritativeIncidentSnapshot(
                    currentLatestSequence =
                        current
                            ?.latestSequence,
                    incomingLatestSequence =
                        incoming
                            .latestSequence
                )
            ) {

                insert(
                    incoming
                )

                if (incoming.latestSequence > 0L) {

                    val gap =
                        getSnapshotSequenceGap(
                            incoming.id
                        )

                    if (gap != null) {

                        val reconciled =
                            reconcileSnapshotSequenceGap(
                                gap = gap,
                                snapshotSequence =
                                    incoming.latestSequence
                            )

                        if (reconciled == null) {
                            deleteSnapshotSequenceGap(
                                incoming.id
                            )
                        } else {
                            upsertSnapshotSequenceGap(
                                reconciled
                            )
                        }
                    }
                }

                applied +=
                    1
            }
        }

        return applied
    }


    @Query("SELECT COUNT(*) FROM incidents")
    suspend fun count(): Int
}
