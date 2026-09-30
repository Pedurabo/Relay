package com.signaldesk.relay.data.realtime

import androidx.room.withTransaction
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

class IncidentEventProcessor(
    private val database: RelayDatabase
) {

    private val incidentDao =
        database.incidentDao()

    private val processedEventDao =
        database.processedEventDao()

    private val timelineEntryDao =
        database.timelineEntryDao()

    private val gapDao =
        database.incidentSequenceGapDao()

    suspend fun process(
        event: IncidentEvent
    ): EventProcessingResult {

        return database.withTransaction {

            if (
                processedEventDao.exists(
                    event.eventId
                )
            ) {
                return@withTransaction EventProcessingResult.DUPLICATE
            }

            val result =
                when (event) {

                    is IncidentCreatedEvent ->
                        processCreated(event)

                    is IncidentUpdatedEvent ->
                        processUpdated(event)

                    is TimelineEntryAddedEvent ->
                        processTimeline(event)
                }

            when (result) {

                EventProcessingResult.APPLIED,
                EventProcessingResult.IGNORED_STALE -> {

                    processedEventDao.insert(
                        ProcessedEventEntity(
                            eventId =
                                event.eventId,
                            processedAt =
                                System.currentTimeMillis()
                        )
                    )
                }

                EventProcessingResult.DEFERRED,
                EventProcessingResult.GAP_DETECTED,
                EventProcessingResult.DUPLICATE -> Unit
            }

            result
        }
    }

    private suspend fun processCreated(
        event: IncidentCreatedEvent
    ): EventProcessingResult {

        val current =
            incidentDao.getById(
                event.incidentId
            )

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
                            event.sequence
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
                id = event.incidentId,
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
                event.sequence
        )

        return EventProcessingResult.APPLIED
    }

    private suspend fun processUpdated(
        event: IncidentUpdatedEvent
    ): EventProcessingResult {

        val current =
            incidentDao.getById(
                event.incidentId
            )
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
                        event.sequence
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
                event.sequence
        )

        return EventProcessingResult.APPLIED
    }

    private suspend fun processTimeline(
        event: TimelineEntryAddedEvent
    ): EventProcessingResult {

        incidentDao.getById(
            event.incidentId
        )
            ?: return EventProcessingResult.DEFERRED

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
                    DeliveryState.SENT.name
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
        incomingSequence: Long
    ) {
        val existing =
            gapDao.getByIncidentId(
                incidentId
            )

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
                    incidentId,
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
        appliedSequence: Long
    ) {
        if (appliedSequence <= 0L) {
            return
        }

        val gap =
            gapDao.getByIncidentId(
                incidentId
            )
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
            gapDao.delete(
                incidentId
            )
        }
    }

    private enum class SequenceResult {
        UNSEQUENCED,
        NEXT,
        STALE,
        GAP
    }
}

