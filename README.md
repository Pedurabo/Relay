# Relay

**Relay** is a realtime incident-coordination Android application engineered to remain correct across unreliable networks, reordered events, retries, reconnects, process death, and account changes.

The project focuses on a difficult mobile-systems problem: keeping durable local Room state, optimistic operator actions, outbound commands, and authoritative realtime server events convergent without duplicate effects, lost work, stale ownership, or incorrect recovery.

## Highlights

- Native Android application built with Kotlin and Jetpack Compose
- Room-backed durable incident and timeline state
- Processed-event persistence for idempotent realtime handling
- Durable sequence-gap detection and replay
- Deferred incident-update persistence
- Deferred realtime/timeline event persistence
- Process-death-safe recovery
- Durable command outboxes
- Stable command and event identities
- Atomic delivery leases
- Authoritative acknowledgement convergence
- Generation-safe replay-job ownership
- Explicit coroutine cancellation barriers
- Bounded retry backoff
- Cross-incident queue fairness
- Session-bound command ownership
- Cross-account isolation
- Room database version 14 with exported schema history
- Unit, migration, instrumentation, and physical-device reliability validation

## Architecture

Relay separates durable local state, transport, realtime convergence, command delivery, and presentation responsibilities.

### Android client

The Android application uses:

- Kotlin
- Jetpack Compose
- Room
- Navigation Compose
- Coroutines and Flow
- OkHttp WebSockets
- Material 3
- Firebase integration

Core persisted state includes:

- incidents,
- timeline entries,
- processed realtime events,
- incident sequence gaps,
- deferred incident updates,
- deferred realtime events,
- pending incident-creation commands,
- pending severity commands,
- pending status commands, and
- durable timeline delivery state.

## Realtime convergence

Incoming realtime events can be:

- next-in-order,
- duplicate,
- stale,
- future events that reveal a sequence gap,
- updates whose parent incident does not exist yet, or
- authoritative timeline events racing local delivery.

Relay does not simply discard events that cannot yet be applied.

Instead, deferred events are stored durably and reconsidered when the local state becomes ready.

### Deferred incident updates

An incident update can arrive before its incident exists or before missing predecessor sequences have been replayed.

Relay persists those events.

Deferred updates are processed deterministically by:

1. sequence,
2. deferred timestamp,
3. event ID.

The same durable payload survives process death.

For duplicate event IDs, the first persisted payload wins.

### Durable sequence gaps

When a future update reveals a sequence gap, Relay persists both:

- the gap metadata, and
- the event that exposed the gap.

The event is not marked processed until it reaches a terminal outcome such as:

- applied,
- duplicate, or
- intentionally ignored as stale.

This prevents process death from losing the event that originally revealed missing history.

Gap ranges shrink as missing predecessors arrive.

## Replay architecture

Reconnect replay has been decomposed into production seams instead of relying on a parallel lifecycle simulator.

### RealtimeReplayInputCollector

Combines:

- durable sequence-gap state, and
- realtime connection state.

### RealtimeReplayReconciliation

Calculates:

- resolved incident replay owners that should stop,
- whether all replay work must stop because the connection is unavailable, and
- incident IDs that require new replay workers.

### RealtimeReplayReconciliationExecution

Applies reconciliation decisions to registered replay jobs.

Resolved jobs are removed and stopped.

Disconnect clears registered replay ownership and waits for cancellation to finish.

### Registered replay ownership

Replay jobs are registered before they start.

Cleanup uses generation-safe conditional removal.

An older coroutine therefore cannot remove a replacement replay owner during stale `finally` cleanup.

### Cancellation barriers

Disconnect and lifecycle teardown cancel replay jobs and then join them.

Shutdown does not claim to be complete while cancelled jobs are still unwinding.

### SequenceGapReplayWorker

The replay worker reloads durable gap state on each attempt.

That means the next replay request uses the current authoritative persisted range rather than stale bounds captured when the coroutine originally started.

## Reconnect lifecycle consolidation

The original large reconnect lifecycle simulator was retired after its behaviors were migrated into direct production-seam tests.

Replay behavior is now tested through:

- input collection,
- reconciliation policy,
- reconciliation execution,
- job registration,
- ownership generation,
- cancellation barriers,
- worker behavior, and
- real stale-cleanup races.

Covered scenarios include:

- gap deletion,
- gap reappearance,
- replacement ownership,
- repeated reconnect epochs,
- offline membership mutation,
- introduction of new gaps while disconnected,
- stale cleanup after replacement,
- disconnect during stale cleanup,
- generation-safe multi-incident cleanup, and
- current durable range usage after reconnect.

## Durable command delivery

Operator mutations are persisted before network delivery.

Relay uses durable Room-backed command delivery for:

- incident creation,
- severity changes,
- status changes, and
- timeline messages.

Each durable operation keeps a stable identity across retries and process restarts.

The delivery coordinator claims eligible work atomically, sends it, waits for authoritative convergence, and retries when appropriate.

## Delivery leases and process death

Pending commands transition through durable delivery states.

If the Android process dies while work is in flight, startup recovery returns abandoned work to an eligible state without generating a new command identity.

The same command resumes.

This prevents process death from creating duplicate logical operations.

## Timeline convergence

Timeline delivery is protected against local-send and authoritative-event races.

The timeline delivery gate serializes work by stable timeline-entry identity.

This supports scenarios where:

- authoritative SENT arrives before the local send boundary,
- an ACK arrives before the realtime broadcast,
- the realtime broadcast arrives before a late ACK,
- duplicate timeline events are received, or
- incompatible authoritative data arrives for an existing entry ID.

Only eligible pending state transitions to SENT.

Authoritative state does not regress because of stale local completion.

## Retry behavior and fairness

Transient failures use bounded exponential retry.

Queue processing is designed so one delayed command or incident does not unnecessarily block unrelated eligible work.

Failure-isolation coverage includes:

- unexpected outbox failures,
- startup reset failures,
- post-drain handoff failures,
- replay failures,
- gap-recovery failures,
- cancellation escaping retry boundaries, and
- stopping retries when the relevant coordinator is no longer active.

## Account isolation

Durable commands belong to the authenticated principal that created them.

The coordinator:

- loads work for the current owner,
- claims work for that owner,
- rechecks ownership before transport.

A command created by User A cannot be sent while User B is authenticated.

When User A returns, the original command can resume with the same stable ID.

## Room database evolution

Relay currently uses **Room database version 14** with schema export enabled.

The project includes migration coverage for durable deferred realtime state.

Migration validation includes Room-open tests so the schema is exercised through the actual Room database opening path.

Historical schema files are treated as real engineering artifacts rather than reconstructed after the fact.

## Proven reliability scenarios

Relay has deterministic coverage for scenarios including:

- duplicate realtime events,
- stale realtime events,
- sequence gaps,
- missing predecessor recovery,
- gap shrinking,
- gap expansion,
- stable upper and lower gap bounds,
- multiple future updates,
- process death during unresolved gaps,
- startup recovery of complete deferred chains,
- startup recovery of blocked deferred chains,
- cross-incident recovery isolation,
- concurrent startup and live recovery,
- incident updates arriving before parent creation,
- stale incident creation,
- sequenced incident creation,
- replay after process death,
- same event ID with conflicting payloads,
- first durable payload preservation,
- offline timeline posting,
- authoritative timeline convergence,
- ACK-first delivery,
- broadcast-first delivery,
- duplicate timeline delivery,
- process death with stranded delivery leases,
- retry fairness,
- reconnect membership mutation,
- repeated connection epochs,
- replay replacement,
- stale replay cleanup,
- disconnect during stale cleanup,
- signed-out outbox behavior, and
- cross-account command isolation.

## Coordinator lifecycle

Relay also directly tests coordinator lifecycle behavior.

Current lifecycle coverage includes:

- initial start,
- duplicate-start prevention,
- restart after completion,
- restart availability after cancellation cleanup,
- state-collector cancellation barriers, and
- replay cancellation barriers.

The next reliability phase is focused on non-replay coordinator failure boundaries, especially event-collector and connection-attempt failure semantics.

## Tech stack

### Android

- Kotlin
- Jetpack Compose
- Room
- Navigation Compose
- Coroutines / Flow
- OkHttp WebSockets
- Material 3
- Firebase

### Development backend

- Node.js
- WebSockets
- SQLite
- WAL mode
- foreign-key enforcement
- schema migration
- authentication
- authorization
- hashed access and refresh session tokens
- login rate limiting
- command deduplication
- event replay
- push-token registration
- optional Firebase push delivery

## Project structure

~~~text
Relay/
├── app/
│   └── src/
├── core/
│   ├── database/
│   │   ├── schemas/
│   │   └── src/
│   └── realtime/
│       └── src/
├── backend/
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
~~~

The reliability-sensitive Android architecture is split into dedicated database and realtime modules so persistence, convergence, replay, migrations, and coroutine lifecycle behavior can be tested independently.

## Run locally

### Start the backend

~~~bash
npm --prefix backend install
npm --prefix backend test
node backend/server.js
~~~

The development WebSocket server listens on port `9000`.

### Connect a physical Android device

~~~bash
adb reverse tcp:9000 tcp:9000
~~~

### Build and install

Windows:

~~~powershell
.\gradlew installDebug
~~~

macOS/Linux:

~~~bash
./gradlew installDebug
~~~

### Launch Relay

~~~bash
adb shell am start -n com.signaldesk.relay/.MainActivity
~~~

## Testing

Run the realtime unit suite:

~~~powershell
.\gradlew :core:realtime:testDebugUnitTest
~~~

Compile the reliability-sensitive modules:

~~~powershell
.\gradlew :core:database:compileDebugKotlin :core:realtime:compileDebugKotlin :app:compileDebugKotlin
~~~

Instrumentation tests are intentionally kept separate from manual physical-device verification.

Connected instrumentation runs can replace or remove the installed debug application, so manual product testing uses `installDebug` separately.

## Current status

Relay is an active engineering portfolio project focused on resilient realtime Android architecture.

The current hardening phase has established:

- durable deferred realtime processing,
- Room v14 migration infrastructure,
- process-death-safe gap recovery,
- deterministic deferred update ordering,
- durable first-payload preservation,
- generation-safe replay ownership,
- cancellation-safe disconnect behavior,
- production-seam replay tests,
- durable command delivery,
- timeline convergence,
- account isolation, and
- broad reliability regression coverage.

The project should not be interpreted as a production-hosted service or Play Store release.

Production hosting, signing and secrets operations, monitoring, deployment infrastructure, and store publishing remain outside the current repository scope.

## Engineering focus

Relay is being developed as part of a project-driven Android engineering program centered on:

- realtime consistency,
- local-first architecture,
- durable state machines,
- process-death recovery,
- coroutine lifecycle correctness,
- cancellation safety,
- reliable background work,
- deterministic failure testing, and
- production-oriented mobile system design.
