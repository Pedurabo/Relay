# Relay Milestone 246 — Authenticated Data Isolation Contract

Status: Proposed implementation contract
Baseline: Milestone 245, Room schema version 15

## Goal

Account B must never read, mutate, acknowledge, replay, or deliver
Account A's locally cached account-specific data.

Retain offline-first functionality without exposing one account's
private state to a different account.

## Account identity

- Use the authenticated, stable user principal as the ownership key.
- Do not derive ownership from display names or access tokens.
- Normalize ownership consistently across repositories and modules.
- Signed-out sessions have no permission to access account-owned data.
- A change of session generation requires fresh write authorization.
- Confirm whether the existing ownerPrincipal convention corresponds
  to SessionState.SignedIn.userId before implementing the migration.

## Isolation invariants

### Incident identity

The logical local identity of an incident is:

    (ownerPrincipal, incidentId)

Identical incident IDs under different owners represent distinct rows.

### Timeline identity

Timeline reads and writes must be owner-scoped, including latest-entry
aggregation, optimistic entries, acknowledgement, and retry.

A timeline entry must not be attached to another account's incident.

### Realtime state

Processed-event deduplication, sequence gaps, deferred incident
updates, and deferred timeline events must be owner-scoped.

Events originating under an obsolete session must not commit into
the new session's local state.

### Pending operations

Pending severity, status, create-incident, and timeline operations
must retain their original owner.

Delivery workers must never select or acknowledge another owner's
pending operations.

### Snapshot reconciliation

The authoritative snapshot must write only to its originating
owner's records.

The session-ownership gate from milestone 245 remains necessary,
but does not replace database-level account scoping.

### UI and observation

All account-owned observations must be scoped by current owner.

Signed-out users must not see account-owned cached data.

Switching accounts must replace the observable data set rather than
briefly exposing rows from the preceding account.

### Legacy data

Existing unowned incident, timeline, event, and gap rows must not
be automatically claimed by the next authenticated principal.

Prefer quarantining legacy unowned state until a safe migration or
explicit rebuild policy is established.

Do not apply a destructive migration without an approved data
retention and pending-command strategy.

## Schema and migration requirements

- Introduce account ownership consistently, not on incidents alone.
- Use owner-aware uniqueness constraints and appropriate indexes.
- Preserve referential relationships within the same owner.
- Update all affected DAOs, repositories, and realtime processors.
- Review Room migration and exported schema compatibility.
- Preserve offline-owned pending commands where ownership is known.
- Plan primary-key changes carefully because incident IDs and event IDs
  are currently globally keyed in several tables.

## Acceptance tests

1. Account A can view its own cached incidents.
2. Account B cannot view Account A's incidents after switching.
3. Two owners can cache the same incident ID independently.
4. Latest timeline aggregation never crosses owners.
5. Deferred events cannot be recovered under another owner.
6. Processed-event deduplication is owner-specific.
7. Pending commands remain bound to their original owner.
8. A stale realtime event cannot write after an ownership transition.
9. A signed-out user cannot read account-owned incident data.
10. Unowned legacy rows are not assigned to a newly signed-in user.
11. Returning to Account A restores its retained offline data.
12. Room migration preserves schema integrity and eligible commands.

## Implementation sequence

246C: Reproduce cross-account exposure in Android database tests.
246D: Implement versioned Room schema and legacy-row policy.
246E: Scope DAO queries and repository methods.
246F: Scope realtime writes, deduplication, and gap recovery.
246G: Bind UI observations and pending delivery to account ownership.
246H: Execute Account A / Account B / Account A instrumentation tests.
246I: Full regression, migration validation, and milestone commit.

## Explicit non-goals

- No automatic deletion of all cached data on ordinary sign-out.
- No assignment of unowned legacy rows to the next account.
- No reliance on ViewModel filtering as the only security boundary.
- No backend authorization changes without separate analysis.
- No destructive Room fallback.

## Version-16 migration design

Status: Design baseline; not yet implemented.

### Identity and ownership

Authenticated owner identity is derived from the stable session user ID,
subject to verification of uniqueness across backend environments.

Use ownerPrincipal consistently across local persistence.

Logical uniqueness:
- incidents: (ownerPrincipal, id)
- timeline_entries: (ownerPrincipal, entryId)
- incident_sequence_gaps: (ownerPrincipal, incidentId)
- processed_events: (ownerPrincipal, eventId)
- deferred_realtime_events: (ownerPrincipal, eventId)
- deferred_incident_updates: (ownerPrincipal, eventId)

Owner-scoped incident references must resolve within the same owner.

Global IDs alone must not authorize a read, write, update, delete,
acknowledgement, deduplication decision, or deferred recovery.

### Legacy owner handling

Version-15 incident and realtime rows do not carry provable ownership.

Do not assign them to the next signed-in principal.

Quarantine those rows under an explicitly unowned legacy identity,
inaccessible to normal authenticated DAO queries.

Keep legacy preservation separate from pending-operation delivery.

Legacy timeline entries with an empty ownerPrincipal are likewise
unowned and ineligible for authenticated presentation or delivery.

Never backfill ownership based only on matching incident IDs.

### Pending operations

Retain existing ownerPrincipal values on status, severity,
and create-incident pending commands.

Preserve owner-known commands and their original command IDs.

Unknown-owner commands remain ineligible for authenticated delivery.

Review all pending command mutations for ownership checks.

In particular, review the global resetInFlight operation in
PendingCreateIncidentCommandDao for cross-session consequences.

Ownership checks must cover claim, release, failure,
acknowledgement, retry, replay, and deletion.

### Schema implementation constraints

Plan table rebuilds for primary-key changes rather than assuming
ALTER TABLE can modify existing primary keys.

Define indexes from the new entity models and validate against
Room's exported JSON schema.

Preserve legacy rows transactionally and verify migrated row counts.

Never introduce fallbackToDestructiveMigration.

Keep Room schema at version 15 until the new entities,
migration, and tests are ready to compile together.

### Security boundary

DAO read methods require an authenticated owner.

Write entrypoints require both the originating owner and
valid session-generation authorization.

Background jobs and realtime processors must use the same owner
captured when work was accepted.

Returning to a previous account must reveal only that account's
preserved records and pending operations.

### Migration acceptance gate

Before shipping version 16, verify:
- Old version-15 databases open successfully.
- Owner-known pending commands survive migration.
- Unowned legacy incidents never appear in authenticated queries.
- Two owners may use identical incident, entry, and event IDs.
- Sequence gaps and deduplication remain owner-specific.
- Snapshot and realtime writes cannot cross accounts.
- Account A / B / A switching preserves isolation.
- Room schema validation and full regression tests pass.

The characterization tests created in 246C establish the unsafe
version-15 baseline. They are not security acceptance tests.

## Version-16 index and preservation checklist

Status: Implementation prerequisite; not yet satisfied.

### Six owner-scoped table rebuilds

The target tables are:
- incidents
- timeline_entries
- incident_sequence_gaps
- processed_events
- deferred_realtime_events
- deferred_incident_updates

Every target table must have a composite primary key beginning
with ownerPrincipal.

Do not ship the isolated incident table without corresponding
isolation for dependent realtime and timeline state.

### Index requirements

The SQL rehearsals validate composite primary-key behavior only.

Production migration must restore necessary secondary indexes.

Review each version-15 index for whether ownerPrincipal should
be added as the leading query column.

Room entity index declarations, migration SQL, and exported
version-16 schema must agree exactly.

Do not add redundant secondary indexes that duplicate the
composite primary-key index without a query-driven reason.

### Pending-command preservation

The three pending-command tables already carry ownerPrincipal.

Preserve their rows, delivery state, command IDs, and indexes.

Do not rewrite, reassign, or delete owner-known commands
during the ownership-sensitive table migration.

Verify preservation for accounts A and B separately.

Unknown-owner commands remain ineligible for delivery.

### Referential ownership

Incident references in timelines, sequence gaps, deferred events,
and pending commands must resolve within the same owner.

Global incident IDs alone cannot identify an account-owned row.

Audit cleanup, retry, recovery, and acknowledgement queries.

### Migration validation

A production version-16 migration is not complete until:

1. The full six-table migration runs as one atomic upgrade.
2. Legacy unowned rows remain quarantined.
3. Owner-known pending commands survive unchanged.
4. All required owner-aware indexes are present.
5. Exported Room schema validation succeeds.
6. No destructive migration fallback is introduced.
7. Account A/B/A isolation tests pass.
8. Existing snapshot and realtime regressions pass.

The prior SQL rehearsals do not satisfy these gates by themselves.
