# Relay 246 - Production v16 Integration Manifest

Baseline: Production Room v15
Target: Room v16, six owner-scoped tables

## Verified prerequisites

- Validated migration object present
- Staged Room isolation tests present
- Production Room migration not activated

## Production references

- app/src/main/java/com/signaldesk/relay/notifications/IncidentNotificationManager.kt:164 — .incidentDao()
- app/src/main/java/com/signaldesk/relay/notifications/IncidentNotificationManager.kt:181 — .incidentDao()
- app/src/main/java/com/signaldesk/relay/RelayApplication.kt:55 — .recoverDeferredTimelineEvents()
- app/src/main/java/com/signaldesk/relay/RelayApplication.kt:58 — .recoverDeferredIncidentUpdates()
- core/database/src/main/java/com/signaldesk/relay/data/local/DeferredIncidentUpdateEntity.kt:26 — data class DeferredIncidentUpdateEntity(
- core/database/src/main/java/com/signaldesk/relay/data/local/DeferredRealtimeEventEntity.kt:24 — data class DeferredRealtimeEventEntity(
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:98 — suspend fun reconcileAuthoritativeSnapshot(
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentEntity.kt:9 — data class IncidentEntity(
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentSequenceGapEntity.kt:7 — data class IncidentSequenceGapEntity(
- core/database/src/main/java/com/signaldesk/relay/data/local/ProcessedEventEntity.kt:7 — data class ProcessedEventEntity(
- core/database/src/main/java/com/signaldesk/relay/data/local/RelayDatabase.kt:29 — abstract fun incidentDao():
- core/database/src/main/java/com/signaldesk/relay/data/local/RelayDatabase.kt:32 — abstract fun processedEventDao():
- core/database/src/main/java/com/signaldesk/relay/data/local/RelayDatabase.kt:35 — abstract fun deferredRealtimeEventDao():
- core/database/src/main/java/com/signaldesk/relay/data/local/RelayDatabase.kt:38 — abstract fun deferredIncidentUpdateDao():
- core/database/src/main/java/com/signaldesk/relay/data/local/RelayDatabase.kt:41 — abstract fun timelineEntryDao():
- core/database/src/main/java/com/signaldesk/relay/data/local/RelayDatabase.kt:44 — abstract fun incidentSequenceGapDao():
- core/database/src/main/java/com/signaldesk/relay/data/local/TimelineEntryDao.kt:123 — fun observeLatestForAllIncidents():
- core/database/src/main/java/com/signaldesk/relay/data/local/TimelineEntryEntity.kt:27 — data class TimelineEntryEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/CreateIncidentOutboxCoordinator.kt:493 — .incidentDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/CreateIncidentOutboxCoordinator.kt:689 — .incidentDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/CreateIncidentOutboxCoordinator.kt:750 — .incidentDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:43 — database.incidentDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:46 — database.processedEventDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:49 — database.deferredRealtimeEventDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:52 — database.deferredIncidentUpdateDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:55 — database.timelineEntryDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:58 — database.incidentSequenceGapDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:163 — ProcessedEventEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:189 — DeferredRealtimeEventEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:217 — DeferredIncidentUpdateEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:263 — DeferredIncidentUpdateEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:359 — IncidentEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:503 — TimelineEntryEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:580 — IncidentSequenceGapEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:635 — suspend fun recoverDeferredIncidentUpdates() {
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/IncidentEventProcessor.kt:758 — suspend fun recoverDeferredTimelineEvents() {
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/SeverityOutboxCoordinator.kt:789 — .incidentDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/SeverityOutboxCoordinator.kt:970 — .incidentDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/SeverityOutboxCoordinator.kt:1044 — .incidentDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/StatusOutboxCoordinator.kt:500 — .incidentDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/StatusOutboxCoordinator.kt:653 — .incidentDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/StatusOutboxCoordinator.kt:715 — .incidentDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/realtime/TimelineOutboxCoordinator.kt:203 — .timelineEntryDao()
- core/realtime/src/main/java/com/signaldesk/relay/data/repository/IncidentRepository.kt:32 — suspend fun reconcileAuthoritativeSnapshotForOwner(
- core/realtime/src/main/java/com/signaldesk/relay/data/repository/IncidentRepository.kt:40 — return reconcileAuthoritativeSnapshot(snapshots)
- core/realtime/src/main/java/com/signaldesk/relay/data/repository/IncidentRepository.kt:42 — suspend fun reconcileAuthoritativeSnapshot(
- core/realtime/src/main/java/com/signaldesk/relay/data/repository/IncidentRepository.kt:47 — .reconcileAuthoritativeSnapshot(
- core/realtime/src/main/java/com/signaldesk/relay/data/repository/IncidentRepository.kt:51 — IncidentEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/repository/IncidentRepository.kt:111 — TimelineEntryEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/repository/IncidentRepository.kt:193 — IncidentEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/repository/IncidentRepository.kt:198 — IncidentEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/repository/IncidentRepository.kt:203 — IncidentEntity(
- core/realtime/src/main/java/com/signaldesk/relay/data/repository/IncidentRepositoryFactory.kt:19 — database.incidentDao(),
- core/realtime/src/main/java/com/signaldesk/relay/data/repository/IncidentRepositoryFactory.kt:21 — database.timelineEntryDao()
- feature/incidents/src/main/java/com/signaldesk/relay/ui/incidents/IncidentsViewModel.kt:64 — database.incidentDao(),
- feature/incidents/src/main/java/com/signaldesk/relay/ui/incidents/IncidentsViewModel.kt:66 — database.timelineEntryDao()
- feature/incidents/src/main/java/com/signaldesk/relay/ui/incidents/IncidentsViewModel.kt:82 — .timelineEntryDao()
- feature/incidents/src/main/java/com/signaldesk/relay/ui/incidents/IncidentsViewModel.kt:83 — .observeLatestForAllIncidents()
- feature/incidents/src/main/java/com/signaldesk/relay/ui/incidents/IncidentsViewModel.kt:186 — .incidentSequenceGapDao(),
- feature/incidents/src/main/java/com/signaldesk/relay/ui/incidents/IncidentsViewModel.kt:334 — .reconcileAuthoritativeSnapshotForOwner(

## Integration acceptance gates

1. Six production entities use owner-composite primary keys.
2. All ownership-sensitive DAO reads, writes and deletes are scoped.
3. Snapshot reconciliation remains transactional and monotonic.
4. Realtime processing and deferred recovery are owner-scoped.
5. Timeline and pending-command writes enforce their owners.
6. Notifications and UI do not expose another account's cache.
7. Room v16 migration validation and application compilation pass.
8. Samsung account A/B/A verification passes before publication.

No destructive migration or legacy ownership reassignment.
