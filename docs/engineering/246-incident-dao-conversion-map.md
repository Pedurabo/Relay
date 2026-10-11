# Relay 246 — Incident DAO Conversion Map

Status: Pre-implementation inventory
Baseline: Production Room version 15
Target: Room version 16

## Required ownership contracts

- Incident identity: (ownerPrincipal, id)
- Sequence-gap identity: (ownerPrincipal, incidentId)
- All authenticated reads must filter by ownerPrincipal.
- All updates and deletes must filter by ownerPrincipal.
- Snapshot reconciliation must preserve the owner across incident and gap operations.
- Never claim legacy empty-owner rows for a signed-in account.
- Reject blank authenticated owner values.
- Continue using SessionManager.withAuthorizedOwnership for protected commits.

## Existing DAO operations to review

The following entries are extracted from production DAO source files.

- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:56 — @Query("SELECT * FROM incidents ORDER BY id ASC")
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:57 — fun observeAll(): Flow<List<IncidentEntity>>
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:59 — @Query("SELECT * FROM incidents WHERE id = :incidentId LIMIT 1")
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:60 — fun observeById(incidentId: String): Flow<IncidentEntity?>
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:62 — @Query("SELECT * FROM incidents WHERE id = :incidentId LIMIT 1")
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:63 — suspend fun getById(incidentId: String): IncidentEntity?
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:65 — @Insert(onConflict = OnConflictStrategy.REPLACE)
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:66 — suspend fun insert(incident: IncidentEntity)
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:68 — @Insert(onConflict = OnConflictStrategy.REPLACE)
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:69 — suspend fun insertAll(incidents: List<IncidentEntity>)
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:73 — SELECT *
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:79 — suspend fun getSnapshotSequenceGap(
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:83 — @Insert(onConflict = OnConflictStrategy.REPLACE)
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:84 — suspend fun upsertSnapshotSequenceGap(
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:90 — DELETE FROM incident_sequence_gaps
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:94 — suspend fun deleteSnapshotSequenceGap(
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:97 — @Transaction
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:98 — suspend fun reconcileAuthoritativeSnapshot(
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:165 — @Query("SELECT COUNT(*) FROM incidents")
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentDao.kt:166 — suspend fun count(): Int
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentSequenceGapDao.kt:14 — SELECT *
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentSequenceGapDao.kt:20 — suspend fun getByIncidentId(
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentSequenceGapDao.kt:26 — SELECT *
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentSequenceGapDao.kt:32 — fun observeByIncidentId(
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentSequenceGapDao.kt:38 — SELECT *
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentSequenceGapDao.kt:43 — fun observeAll():
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentSequenceGapDao.kt:46 — @Insert(
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentSequenceGapDao.kt:50 — suspend fun upsert(
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentSequenceGapDao.kt:56 — DELETE FROM incident_sequence_gaps
- core/database/src/main/java/com/signaldesk/relay/data/local/IncidentSequenceGapDao.kt:60 — suspend fun delete(

## Production call sites

- app\src\main\java\com\signaldesk\relay\notifications\IncidentNotificationManager.kt:164 — .incidentDao()
- app\src\main\java\com\signaldesk\relay\notifications\IncidentNotificationManager.kt:181 — .incidentDao()
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\CreateIncidentOutboxCoordinator.kt:493 — .incidentDao()
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\CreateIncidentOutboxCoordinator.kt:689 — .incidentDao()
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\CreateIncidentOutboxCoordinator.kt:750 — .incidentDao()
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\IncidentEventProcessor.kt:43 — database.incidentDao()
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\IncidentEventProcessor.kt:58 — database.incidentSequenceGapDao()
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\IncidentEventProcessor.kt:562 — gapDao.getByIncidentId(
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\IncidentEventProcessor.kt:603 — gapDao.getByIncidentId(
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\RealtimeIncidentCoordinator.kt:734 — .getByIncidentId(
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\SeverityOutboxCoordinator.kt:789 — .incidentDao()
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\SeverityOutboxCoordinator.kt:970 — .incidentDao()
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\SeverityOutboxCoordinator.kt:1044 — .incidentDao()
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\StatusOutboxCoordinator.kt:500 — .incidentDao()
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\StatusOutboxCoordinator.kt:653 — .incidentDao()
- core\realtime\src\main\java\com\signaldesk\relay\data\realtime\StatusOutboxCoordinator.kt:715 — .incidentDao()
- core\realtime\src\main\java\com\signaldesk\relay\data\repository\IncidentRepository.kt:47 — .reconcileAuthoritativeSnapshot(
- core\realtime\src\main\java\com\signaldesk\relay\data\repository\IncidentRepositoryFactory.kt:19 — database.incidentDao(),
- feature\incidents\src\main\java\com\signaldesk\relay\ui\incidents\IncidentsViewModel.kt:64 — database.incidentDao(),
- feature\incidents\src\main\java\com\signaldesk\relay\ui\incidents\IncidentsViewModel.kt:186 — .incidentSequenceGapDao(),
- feature\incidents\src\main\java\com\signaldesk\relay\ui\incidents\IncidentsViewModel.kt:334 — .reconcileAuthoritativeSnapshotForOwner(

## Coordinated implementation gates

1. Add composite-key owner fields to production incident and gap entities.
2. Replace globally scoped incident and gap queries with owner-filtered signatures.
3. Update snapshot reconciliation as one owner-scoped transaction.
4. Propagate owner through repository, realtime, outbox, and notification callers.
5. Convert remaining four ownership-sensitive entities and DAOs.
6. Register and validate the complete version-16 migration.
7. Run account A/B/A, legacy quarantine, and stale-session regressions.

Do not activate Room v16 until these gates are satisfied.
