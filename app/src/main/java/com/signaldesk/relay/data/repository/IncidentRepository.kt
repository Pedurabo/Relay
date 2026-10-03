package com.signaldesk.relay.data.repository

import com.signaldesk.relay.data.local.IncidentDao
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.TimelineEntryDao
import com.signaldesk.relay.data.local.TimelineEntryEntity
import com.signaldesk.relay.data.mapper.toDomain
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

    fun observeIncidents(): Flow<List<Incident>> {
        return incidentDao
            .observeAll()
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }

    fun observeIncident(
        incidentId: String
    ): Flow<Incident?> {
        return incidentDao
            .observeById(incidentId)
            .map { it?.toDomain() }
    }

    fun observeTimeline(
        incidentId: String
    ): Flow<List<TimelineEntry>> {
        val dao =
            requireTimelineDao()

        return dao
            .observeForIncident(incidentId)
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
        entryId: String
    ): String? {
        return requireTimelineDao()
            .getById(
                entryId
            )
            ?.ownerPrincipal
    }
    suspend fun markTimelineEntryPending(
        entryId: String,
        ownerPrincipal: String
    ): Boolean {

        return requireTimelineDao()
            .updateDeliveryState(
                entryId =
                    entryId,
                ownerPrincipal =
                    ownerPrincipal,
                deliveryState =
                    DeliveryState.PENDING.name
            ) == 1
    }

    suspend fun markTimelineEntryFailed(
        entryId: String,
        ownerPrincipal: String
    ): Boolean {

        return requireTimelineDao()
            .updateDeliveryState(
                entryId =
                    entryId,
                ownerPrincipal =
                    ownerPrincipal,
                deliveryState =
                    DeliveryState.FAILED.name
            ) == 1
    }

    suspend fun confirmTimelineEntry(
        event: TimelineEntryAddedEvent,
        ownerPrincipal: String
    ) {
        requireTimelineDao()
            .upsert(
                TimelineEntryEntity(
                    entryId =
                        event.entryId,
                    incidentId =
                        event.incidentId,
                    message =
                        event.message,
                    author =
                        event.author,
                    occurredAt =
                        event.occurredAt,
                    deliveryState =
                        DeliveryState.SENT.name,
                    ownerPrincipal =
                        ownerPrincipal
                )
            )
    }

    suspend fun createIncident(
        title: String,
        status: String
    ) {
        val id =
            "INC-" +
                UUID.randomUUID()
                    .toString()
                    .take(6)
                    .uppercase()

        incidentDao.insert(
            IncidentEntity(
                id = id,
                title = title.trim(),
                status = status.trim()
            )
        )
    }

    suspend fun seedIfEmpty() {
        if (incidentDao.count() != 0) {
            return
        }

        incidentDao.insertAll(
            listOf(
                IncidentEntity(
                    id = "INC-001",
                    title = "API outage",
                    status = "Active"
                ),
                IncidentEntity(
                    id = "INC-002",
                    title = "Payment delays",
                    status = "Investigating"
                ),
                IncidentEntity(
                    id = "INC-003",
                    title = "Login degradation",
                    status = "Monitoring"
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
