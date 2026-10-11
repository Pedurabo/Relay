package com.signaldesk.relay.data.repository

import com.signaldesk.relay.data.session.AuthenticatedOwnershipContext
import com.signaldesk.relay.data.local.IncidentDao
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.TimelineEntryDao
import com.signaldesk.relay.data.local.TimelineEntryEntity
import com.signaldesk.relay.data.mapper.toDomain
import com.signaldesk.relay.data.remote.IncidentSnapshot
import com.signaldesk.relay.data.remote.model.TimelineEntryAddedEvent
import com.signaldesk.relay.model.DeliveryState
import com.signaldesk.relay.model.Incident
import com.signaldesk.relay.model.TimelineEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class IncidentRepository(
    private val incidentDao: IncidentDao,
    private val timelineEntryDao: TimelineEntryDao? = null
) {

    /**
     * Carries the authenticated origin into snapshot reconciliation.
     *
     * Caller must hold SessionManager.withAuthorizedOwnership.
     * This is NOT storage isolation while Room remains at v15.
     *
     * The captured owner will become the persisted owner when
     * the coordinated v16 entity and DAO conversion lands.
     */
    /**
     * The caller must execute this operation under
     * SessionManager.withAuthorizedOwnership.
     */
    suspend fun reconcileAuthoritativeSnapshotForOwner(
        snapshots: List<IncidentSnapshot>,
        ownership: AuthenticatedOwnershipContext
    ): Int {
        return reconcileAuthoritativeSnapshot(
            ownerPrincipal = ownership.ownerPrincipal,
            snapshots = snapshots
        )
    }

    /**
     * Converts authoritative snapshots into owner-keyed entities.
     *
     * Authentication and session-generation authorization
     * remain the responsibility of the caller.
     */
    suspend fun reconcileAuthoritativeSnapshot(
        ownerPrincipal: String,
        snapshots: List<IncidentSnapshot>
    ): Int {
        require(ownerPrincipal.isNotBlank())

        return incidentDao.reconcileAuthoritativeSnapshot(
            ownerPrincipal = ownerPrincipal,
            incidents = snapshots.map { snapshot ->
                IncidentEntity(
                    id = snapshot.id,
                    title = snapshot.title,
                    status = snapshot.status,
                    severity = snapshot.severity.uppercase(),
                    latestSequence = snapshot.sequence,
                    ownerPrincipal = ownerPrincipal
                )
            }
        )
    }

    fun observeIncidents(
        ownerPrincipal: String
    ): Flow<List<Incident>> {
        require(ownerPrincipal.isNotBlank())

        return incidentDao
            .observeAll(ownerPrincipal)
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }

    fun observeIncident(
        ownerPrincipal: String,
        incidentId: String
    ): Flow<Incident?> {
        require(ownerPrincipal.isNotBlank())

        return incidentDao
            .observeById(ownerPrincipal, incidentId)
            .map { it?.toDomain() }
    }

    fun observeTimeline(
        ownerPrincipal: String,
        incidentId: String
    ): Flow<List<TimelineEntry>> {
        require(ownerPrincipal.isNotBlank())
        val dao =
            requireTimelineDao()

        return dao
            .observeForIncident(ownerPrincipal, incidentId)
            .map { entities ->
                entities.map {
                    it.toDomain()
                }
            }
    }

    suspend fun createPendingTimelineEntry(
        incidentId: String,
        message: String,
        author: String,
        ownerPrincipal: String
    ): TimelineEntry {
        require(ownerPrincipal.isNotBlank())

        val dao =
            requireTimelineDao()

        val entity =
            TimelineEntryEntity(
                entryId =
                    "ENTRY-" +
                        UUID.randomUUID()
                            .toString()
                            .take(8)
                            .uppercase(),
                incidentId = incidentId,
                message = message.trim(),
                author = author,
                occurredAt =
                    System.currentTimeMillis(),
                deliveryState =
                    DeliveryState.PENDING.name,
                ownerPrincipal =
                    ownerPrincipal
            )

        dao.upsert(entity)

        return entity.toDomain()
    }

    suspend fun getTimelineEntryOwnerPrincipal(
        ownerPrincipal: String,
        entryId: String
    ): String? {
        require(ownerPrincipal.isNotBlank())

        return requireTimelineDao()
            .getById(
                ownerPrincipal = ownerPrincipal,
                entryId = entryId
            )
            ?.ownerPrincipal
    }
    suspend fun markTimelineEntryPending(
        entryId: String,
        ownerPrincipal: String
    ): Boolean {

        return requireTimelineDao()
            .retryFailed(
                entryId =
                    entryId,
                ownerPrincipal =
                    ownerPrincipal
            ) == 1
    }

    suspend fun markTimelineEntryFailed(
        entryId: String,
        ownerPrincipal: String
    ): Boolean {

        return requireTimelineDao()
            .failPending(
                entryId =
                    entryId,
                ownerPrincipal =
                    ownerPrincipal
            ) == 1
    }

    suspend fun confirmTimelineEntry(
        event: TimelineEntryAddedEvent,
        ownerPrincipal: String
    ): Boolean {

        return requireTimelineDao()
            .acknowledgePending(
                entryId =
                    event.entryId,
                ownerPrincipal =
                    ownerPrincipal
            ) == 1
    }


    suspend fun seedIfEmpty(
        ownerPrincipal: String
    ) {
        require(ownerPrincipal.isNotBlank())

        if (incidentDao.count(ownerPrincipal) != 0) {
            return
        }

        incidentDao.insertAll(
            listOf(
                IncidentEntity(
                    id = "INC-001",
                    title = "API outage",
                    status = "Active",
                    ownerPrincipal = ownerPrincipal
                ),
                IncidentEntity(
                    id = "INC-002",
                    title = "Payment delays",
                    status = "Investigating",
                    ownerPrincipal = ownerPrincipal
                ),
                IncidentEntity(
                    id = "INC-003",
                    title = "Login degradation",
                    status = "Monitoring",
                    ownerPrincipal = ownerPrincipal
                )
            )
        )
    }
    private fun requireTimelineDao(): TimelineEntryDao {
        return checkNotNull(
            timelineEntryDao
        ) {
            "TimelineEntryDao is required."
        }
    }
}
