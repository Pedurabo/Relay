package com.signaldesk.relay.data.realtime

import androidx.room.withTransaction
import com.signaldesk.relay.data.local.DeferredIncidentUpdateEntity
import com.signaldesk.relay.data.local.DeferredRealtimeEventEntity
import com.signaldesk.relay.data.local.IncidentEntity
import com.signaldesk.relay.data.local.IncidentSequenceGapEntity
import com.signaldesk.relay.data.local.ProcessedEventEntity
import com.signaldesk.relay.data.local.RelayDatabase
import com.signaldesk.relay.data.local.TimelineEntryEntity
import com.signaldesk.relay.data.remote.model.IncidentCreatedEvent
import com.signaldesk.relay.data.remote.model.IncidentEvent
import com.signaldesk.relay.data.remote.model.IncidentUpdatedEvent
import com.signaldesk.relay.data.remote.model.TimelineEntryAddedEvent
import com.signaldesk.relay.model.DeliveryState

internal const val DEFERRED_TIMELINE_ORPHAN_RETENTION_MILLIS =
    30L * 24L * 60L * 60L * 1_000L

internal fun deferredTimelineRetentionCutoff(
    nowMillis: Long
): Long =
    nowMillis -
        DEFERRED_TIMELINE_ORPHAN_RETENTION_MILLIS


internal fun shouldRecoverDeferredTimelineEvents(
    result: EventProcessingResult
): Boolean =
    result ==
        EventProcessingResult.APPLIED ||
        result ==
        EventProcessingResult.DUPLICATE ||
        result ==
        EventProcessingResult.IGNORED_STALE


class IncidentEventProcessor(
    private val database: RelayDatabase
) {

    // PROCESSOR_INTERNAL_OWNER_246
    // PROCESSOR_TRANSACTION_OWNER_246
    // PROCESSOR_RECOVERY_OWNER_246
    private val incidentDao =
        database.incidentDao()

    private val processedEventDao =
        database.processedEventDao()

    private val deferredRealtimeEventDao =
        database.deferredRealtimeEventDao()

    private val deferredIncidentUpdateDao =
        database.deferredIncidentUpdateDao()

    private val timelineEntryDao =
        database.timelineEntryDao()

    private val gapDao =
        database.incidentSequenceGapDao()

    suspend fun process(
        event: IncidentEvent,
        ownerPrincipal: String
    ): EventProcessingResult {
        // PROCESSOR_OWNER_CONTEXT_246
        require(ownerPrincipal.isNotBlank())

        val result =
            if (
                event is TimelineEntryAddedEvent
            ) {

                TimelineEntryDeliveryGate
                    .withEntry(
                        event.entryId
                    ) {

                        processInTransaction(event, ownerPrincipal)
                    }

            } else {

                processInTransaction(event, ownerPrincipal)
            }

        if (
            event is IncidentCreatedEvent &&
            (
                result == EventProcessingResult.APPLIED ||
                result == EventProcessingResult.DUPLICATE ||
                result == EventProcessingResult.IGNORED_STALE
            )
        ) {

            recoverDeferredIncidentUpdatesForIncident(event.incidentId, ownerPrincipal)

            recoverDeferredTimelineEventsForIncident(event.incidentId, ownerPrincipal)
        }

        if (
            event is IncidentUpdatedEvent &&
            shouldRecoverDeferredIncidentUpdatesAfterSequenceProgress(
                result
            )
        ) {

            recoverDeferredIncidentUpdatesForIncident(event.incidentId, ownerPrincipal)
        }
        return result
    }


    private suspend fun processInTransaction(
        event: IncidentEvent,
        ownerPrincipal: String
    ): EventProcessingResult {
        require(ownerPrincipal.isNotBlank())

        return database.withTransaction {

            if (
                processedEventDao.exists(ownerPrincipal, event.eventId)
            ) {

                deferredRealtimeEventDao
                    .deleteByEventId(
                        ownerPrincipal, event.eventId)

                deferredIncidentUpdateDao
                    .deleteByEventId(
                        ownerPrincipal, event.eventId)

                return@withTransaction EventProcessingResult.DUPLICATE
            }

            val result =
                when (event) {

                    is IncidentCreatedEvent ->
                        processCreated(event, ownerPrincipal)

                    is IncidentUpdatedEvent ->
                        processUpdated(event, ownerPrincipal)

                    is TimelineEntryAddedEvent ->
                        processTimeline(event, ownerPrincipal)
                }

            when (result) {

                EventProcessingResult.APPLIED,
                EventProcessingResult.IGNORED_STALE -> {

                    processedEventDao.insert(
                        ProcessedEventEntity(
                            eventId =
                                event.eventId,ownerPrincipal = ownerPrincipal,
                            processedAt =
                                System.currentTimeMillis()
                        )
                    )

                    deferredRealtimeEventDao
                        .deleteByEventId(
                            ownerPrincipal, event.eventId)
                    deferredIncidentUpdateDao
                        .deleteByEventId(
                            ownerPrincipal, event.eventId)
                }

                EventProcessingResult.DEFERRED -> {

                    if (
                        event is TimelineEntryAddedEvent
                    ) {

                        deferredRealtimeEventDao
                            .insert(
                                DeferredRealtimeEventEntity(
                                    eventId =
                                        event.eventId,ownerPrincipal = ownerPrincipal,
                                    incidentId =
                                        event.incidentId,
                                    entryId =
                                        event.entryId,
                                    message =
                                        event.message,
                                    author =
                                        event.author,
                                    occurredAt =
                                        event.occurredAt,
                                    deferredAt =
                                        System.currentTimeMillis()
                                )
                            )
                    }
                    
                    if (
                        event is IncidentUpdatedEvent
                    ) {

                        val now =
                            System.currentTimeMillis()

                        deferredIncidentUpdateDao
                            .insert(
                                DeferredIncidentUpdateEntity(
                                    eventId =
                                        event.eventId,ownerPrincipal = ownerPrincipal,
                                    incidentId =
                                        event.incidentId,
                                    occurredAt =
                                        event.occurredAt,
                                    sequence =
                                        event.sequence,
                                    title =
                                        event.title,
                                    status =
                                        event.status,
                                    severity =
                                        event.severity,
                                    deferredAt =
                                        now
                                )
                            )

                        deferredIncidentUpdateDao
                            .deleteOrphansBefore(
                                ownerPrincipal, now -
                                    DEFERRED_INCIDENT_UPDATE_ORPHAN_RETENTION_MILLIS
                            )
                    }

                    pruneDeferredTimelineOrphans(ownerPrincipal)
                }

                EventProcessingResult.GAP_DETECTED -> {

                    /*
                     * GAP_DETECTED is non-terminal, but the first observed
                     * authoritative payload must remain stable while its
                     * predecessor is missing.
                     *
                     * Reuse deferred_incident_updates as the durable
                     * unresolved-event store. Its eventId primary key with
                     * IGNORE semantics makes the first payload win.
                     */
                    if (
                        event is IncidentUpdatedEvent
                    ) {
                        deferredIncidentUpdateDao
                            .insert(
                                DeferredIncidentUpdateEntity(
                                    eventId =
                                        event.eventId,ownerPrincipal = ownerPrincipal,
                                    incidentId =
                                        event.incidentId,
                                    occurredAt =
                                        event.occurredAt,
                                    sequence =
                                        event.sequence,
                                    title =
                                        event.title,
                                    status =
                                        event.status,
                                    severity =
                                        event.severity,
                                    deferredAt =
                                        System.currentTimeMillis()
                                )
                            )
                    }
                }

                EventProcessingResult.DUPLICATE -> Unit
            }

            result
        }
    }

    private suspend fun processCreated(
        event: IncidentCreatedEvent,
        ownerPrincipal: String
    ): EventProcessingResult {

        val current =
            incidentDao.getById(ownerPrincipal, event.incidentId)

        /*
         * Creation establishes incident identity exactly once.
         *
         * An existing latestSequence == 0 row may still be optimistic or
         * pre-sequence state and can be canonicalized by its authoritative
         * create event.
         *
         * Once sequenced state has begun, however, every later create is
         * stale regardless of whether its sequence is lower, equal, NEXT,
         * or farther ahead. A create must never behave like an update.
         */
        if (
            current != null &&
            current.latestSequence > 0L
        ) {
            return EventProcessingResult
                .IGNORED_STALE
        }

        if (
            current != null &&
            event.sequence > 0L
        ) {
            when (
                classifySequence(
                    latestSequence =
                        current.latestSequence,
                    incomingSequence =
                        event.sequence
                )
            ) {

                SequenceResult.STALE ->
                    return EventProcessingResult
                        .IGNORED_STALE

                SequenceResult.GAP -> {

                    recordGap(
                        incidentId =
                            event.incidentId,
                        latestSequence =
                            current.latestSequence,
                        incomingSequence =
                            event.sequence,
                            ownerPrincipal = ownerPrincipal
        )

                    return EventProcessingResult
                        .GAP_DETECTED
                }

                SequenceResult.NEXT,
                SequenceResult.UNSEQUENCED ->
                    Unit
            }
        }

        incidentDao.insert(
            IncidentEntity(
                id = event.incidentId,ownerPrincipal = ownerPrincipal,
                title = event.title,
                status = event.status,
                severity =
                    event.severity.uppercase(),
                latestSequence =
                    if (
                        event.sequence > 0L
                    ) {
                        event.sequence
                    } else {
                        current
                            ?.latestSequence
                            ?: 0L
                    }
            )
        )

        updateGapAfterAppliedSequence(
            incidentId =
                event.incidentId,
            appliedSequence =
                event.sequence,
                ownerPrincipal = ownerPrincipal
        )

        return EventProcessingResult.APPLIED
    }

    private suspend fun processUpdated(
        event: IncidentUpdatedEvent,
        ownerPrincipal: String
    ): EventProcessingResult {

        val current =
            incidentDao.getById(ownerPrincipal, event.incidentId)
                ?: return EventProcessingResult.DEFERRED

        when (
            classifySequence(
                latestSequence =
                    current.latestSequence,
                incomingSequence =
                    event.sequence
            )
        ) {

            SequenceResult.STALE ->
                return EventProcessingResult
                    .IGNORED_STALE

            SequenceResult.GAP -> {

                recordGap(
                    incidentId =
                        event.incidentId,
                    latestSequence =
                        current.latestSequence,
                    incomingSequence =
                        event.sequence,
                        ownerPrincipal = ownerPrincipal
        )

                return EventProcessingResult
                    .GAP_DETECTED
            }

            SequenceResult.NEXT,
            SequenceResult.UNSEQUENCED ->
                Unit
        }

        incidentDao.insert(
            current.copy(
                title =
                    event.title
                        ?: current.title,
                status =
                    event.status
                        ?: current.status,
                severity =
                    event.severity
                        ?.uppercase()
                        ?: current.severity,
                latestSequence =
                    if (
                        event.sequence > 0L
                    ) {
                        event.sequence
                    } else {
                        current.latestSequence
                    }
            )
        )

        updateGapAfterAppliedSequence(
            incidentId =
                event.incidentId,
            appliedSequence =
                event.sequence,
                ownerPrincipal = ownerPrincipal
        )

        return EventProcessingResult.APPLIED
    }

    private suspend fun processTimeline(
        event: TimelineEntryAddedEvent,
        ownerPrincipal: String
    ): EventProcessingResult {

        incidentDao.getById(ownerPrincipal, event.incidentId)
            ?: return EventProcessingResult.DEFERRED

        /*
         * A broadcast confirmation can race the dedicated sender ACK.
         *
         * If this entry already exists locally, preserve the durable
         * principal that created it. Truly remote entries have no
         * local row and therefore remain ownerless.
         */
        val existingTimelineEntry =
            timelineEntryDao
                .getById(
                    ownerPrincipal, event.entryId)

        if (
            existingTimelineEntry != null &&
            !isCompatibleTimelineAuthoritativeEvent(
                existingIncidentId =
                    existingTimelineEntry.incidentId,
                existingMessage =
                    existingTimelineEntry.message,
                incomingIncidentId =
                    event.incidentId,
                incomingMessage =
                    event.message
            )
        ) {
            return EventProcessingResult.IGNORED_STALE
        }

        timelineEntryDao.upsert(
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
                    existingTimelineEntry?.ownerPrincipal ?: ownerPrincipal
            )
        )

        return EventProcessingResult.APPLIED
    }

    private fun classifySequence(
        latestSequence: Long,
        incomingSequence: Long
    ): SequenceResult {

        if (incomingSequence <= 0L) {
            return SequenceResult.UNSEQUENCED
        }

        if (latestSequence <= 0L) {
            return SequenceResult.NEXT
        }

        if (
            incomingSequence <=
            latestSequence
        ) {
            return SequenceResult.STALE
        }

        if (
            incomingSequence ==
            latestSequence + 1L
        ) {
            return SequenceResult.NEXT
        }

        return SequenceResult.GAP
    }

    private suspend fun recordGap(
        incidentId: String,
        latestSequence: Long,
        incomingSequence: Long,
        ownerPrincipal: String
    ) {
        val existing =
            gapDao.getByIncidentId(ownerPrincipal, incidentId)

        val expected =
            latestSequence + 1L

        val received =
            if (existing == null) {
                incomingSequence
            } else {
                maxOf(
                    existing.receivedSequence,
                    incomingSequence
                )
            }

        gapDao.upsert(
            IncidentSequenceGapEntity(
                incidentId =
                    incidentId,ownerPrincipal = ownerPrincipal,
                expectedSequence =
                    expected,
                receivedSequence =
                    received,
                detectedAt =
                    existing?.detectedAt
                        ?: System.currentTimeMillis()
            )
        )
    }

    private suspend fun updateGapAfterAppliedSequence(
        incidentId: String,
        appliedSequence: Long,
        ownerPrincipal: String
    ) {
        if (appliedSequence <= 0L) {
            return
        }

        val gap =
            gapDao.getByIncidentId(ownerPrincipal, incidentId)
                ?: return

        val nextExpected =
            appliedSequence + 1L

        if (
            nextExpected <=
            gap.receivedSequence
        ) {
            gapDao.upsert(
                gap.copy(
                    expectedSequence =
                        nextExpected
                )
            )
        } else {
            gapDao.delete(ownerPrincipal, incidentId)
        }
    }

    private enum class SequenceResult {
        UNSEQUENCED,
        NEXT,
        STALE,
        GAP
    }

    suspend fun recoverDeferredIncidentUpdates(ownerPrincipal: String) {

        val now =
            System.currentTimeMillis()

        deferredIncidentUpdateDao
            .deleteOrphansBefore(
                ownerPrincipal, now -
                    DEFERRED_INCIDENT_UPDATE_ORPHAN_RETENTION_MILLIS
            )

        val incidentIds =
            deferredIncidentUpdateDao
                .loadAll(ownerPrincipal)
                .map {
                    it.incidentId
                }
                .distinct()

        for (
            incidentId in incidentIds
        ) {

            if (
                incidentDao.getById(ownerPrincipal, incidentId) == null
            ) {
                continue
            }

            /*
             * Use the per-incident DAO ordering:
             *
             * sequence ASC,
             * deferredAt ASC,
             * eventId ASC.
             *
             * This avoids processing a stale global snapshot out of
             * sequence and lets contiguous durable work converge in
             * one bounded drain without re-entering public process().
             */
            recoverDeferredIncidentUpdatesForIncident(incidentId, ownerPrincipal)
        }
    }

    private suspend fun recoverDeferredIncidentUpdatesForIncident(
        incidentId: String,
        ownerPrincipal: String
    ) {

        val deferred =
            deferredIncidentUpdateDao
                .loadForIncident(
                    ownerPrincipal, incidentId)

        for (
            stored in deferred
        ) {

            processInTransaction(stored.toIncidentUpdatedEvent(), ownerPrincipal)
        }
    }


    private suspend fun recoverDeferredTimelineEventsForIncident(
        incidentId: String,
        ownerPrincipal: String
    ) {

        val deferred =
            deferredRealtimeEventDao
                .loadForIncident(
                    ownerPrincipal, incidentId)

        for (
            stored in deferred
        ) {

            process(TimelineEntryAddedEvent(
                    eventId =
                        stored.eventId,
                    incidentId =
                        stored.incidentId,
                    occurredAt =
                        stored.occurredAt,
                    entryId =
                        stored.entryId,
                    message =
                        stored.message,
                    author =
                        stored.author
                ), ownerPrincipal)
        }
    }


    private fun DeferredIncidentUpdateEntity.toIncidentUpdatedEvent():
        IncidentUpdatedEvent =
        IncidentUpdatedEvent(
            eventId =
                eventId,
            incidentId =
                incidentId,
            occurredAt =
                occurredAt,
            title =
                title,
            status =
                status,
            severity =
                severity,
            sequence =
                sequence
        )


    suspend fun recoverDeferredTimelineEvents(ownerPrincipal: String) {

        pruneDeferredTimelineOrphans(ownerPrincipal)

        val deferred =
            deferredRealtimeEventDao
                .loadAll(ownerPrincipal)

        for (
            stored in deferred
        ) {

            if (
                incidentDao.getById(ownerPrincipal, stored.incidentId) == null
            ) {
                continue
            }

            process(TimelineEntryAddedEvent(
                    eventId =
                        stored.eventId,
                    incidentId =
                        stored.incidentId,
                    occurredAt =
                        stored.occurredAt,
                    entryId =
                        stored.entryId,
                    message =
                        stored.message,
                    author =
                        stored.author
                ), ownerPrincipal)
        }
    }


    private suspend fun pruneDeferredTimelineOrphans(ownerPrincipal: String) {

        deferredRealtimeEventDao
            .deleteOrphansBefore(
                ownerPrincipal, deferredTimelineRetentionCutoff(
                    System.currentTimeMillis()
                )
            )
    }

    companion object {

        private const val
            DEFERRED_INCIDENT_UPDATE_ORPHAN_RETENTION_MILLIS =
                30L *
                    24L *
                    60L *
                    60L *
                    1_000L
    }
}


internal fun shouldRecoverDeferredIncidentUpdatesAfterSequenceProgress(
    result: EventProcessingResult
): Boolean =
    when (
        result
    ) {

        EventProcessingResult.APPLIED,
        EventProcessingResult.DUPLICATE,
        EventProcessingResult.IGNORED_STALE ->
            true

        EventProcessingResult.DEFERRED,
        EventProcessingResult.GAP_DETECTED ->
            false
    }

internal fun isCompatibleTimelineAuthoritativeEvent(
    existingIncidentId: String,
    existingMessage: String,
    incomingIncidentId: String,
    incomingMessage: String
): Boolean =
    existingIncidentId == incomingIncidentId &&
        existingMessage == incomingMessage