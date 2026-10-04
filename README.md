# Relay

**Relay** is a real-time incident coordination Android application built to stay correct across unreliable networks, retries, process death, reconnects, and account changes.

The project focuses on a hard mobile-systems problem: keeping local Room state, optimistic UI, outbound commands, and authoritative server events convergent without duplicate or lost operations.

## Highlights

- Native Android UI with Kotlin and Jetpack Compose
- Room-backed incident and timeline persistence
- Real-time WebSocket incident updates
- Ordered event processing with sequence-gap detection and replay
- Authoritative incident creation, severity, status, and timeline mutations
- Durable Room-backed outboxes for create, severity, status, and timeline operations
- Atomic Room-backed delivery leases
- Exponential retry backoff with queue fairness
- Process-death recovery for stranded in-flight commands
- Stable command IDs for idempotent retries
- Session-bound outbox ownership and cross-account isolation
- Background high-priority incident notifications
- Physical-device validation on Android

## Architecture

Relay separates local state, transport, delivery coordination, and presentation responsibilities.

### Android client

The Android application uses:

- **Jetpack Compose** for UI
- **Room** for durable local state
- **Navigation Compose** for screen navigation
- **Coroutines / Flow** for asynchronous state propagation
- **OkHttp WebSockets** for real-time transport

The primary data model includes incidents, timeline entries, processed events, sequence gaps, and durable pending create, severity, and status commands.

### Realtime convergence

Incoming incident events carry sequence information. Relay handles:

- next-in-order events,
- stale events,
- duplicate events,
- sequence gaps,
- replay of missing events, and
- convergence after reconnect.

This allows the app to recover from missing or reordered server traffic without duplicating timeline state.

### Durable command delivery

Operator mutations are persisted before network delivery. Relay uses durable Room-backed delivery for incident creation, severity updates, status updates, and timeline posts.

Each pending command stores:

- a stable command ID,
- incident ID,
- requested severity,
- base severity,
- authenticated owner principal,
- creation time, and
- delivery state.

A process-scoped coordinator claims eligible commands atomically in Room, delivers them, waits for authoritative convergence, and retries when necessary.

### Delivery leases and process death

Pending commands move from `PENDING` to `IN_FLIGHT` through an atomic Room update.

If the process dies while a command is in flight, Relay resets abandoned leases on the next process start and reclaims the same durable command instead of creating a new one.

### Retry fairness

Transport failures use bounded exponential backoff:

`1s → 2s → 4s → 8s → 16s → 30s max`

The queue is evaluated fairly, so one command in cooldown does not block eligible commands for other incidents.

### Account isolation

Durable commands are owned by the authenticated principal that created them.

The coordinator only loads and claims commands for the currently signed-in owner and checks that ownership again before transport. A command created by User A cannot be sent while User B is authenticated. If User A later returns, the original command ID becomes eligible again and resumes delivery.

### Reactive pending-state monitoring

Pending status and severity commands are observed through Room `Flow` invalidation rather than periodic database polling.

## Proven failure scenarios

Relay has been exercised against deterministic failure-mode tests including:

- duplicate and stale realtime events,
- missing sequence ranges and active replay,
- reconnect convergence,
- offline timeline posting and retry,
- hostile out-of-order severity updates,
- ambiguous server commit after lost acknowledgement,
- process death with a stranded delivery lease,
- multi-command recovery,
- retry fairness under prolonged transport failure,
- signed-out outbox behavior, and
- cross-account command isolation.

The account-isolation proof verified that an A-owned command survived sign-out, remained untouched while B was authenticated, resumed with the same command ID when A returned, applied exactly once, converged in Room, and then cleared from the outbox.

## Tech stack

### Android

- Kotlin
- Jetpack Compose
- Room
- Navigation Compose
- Coroutines / Flow
- OkHttp WebSockets
- Material 3

### Development backend

- Node.js
- `ws` WebSocket library
- SQLite durable state with WAL mode and foreign-key enforcement
- schema migration and backup validation
- hashed access and refresh session-token storage
- authentication, authorization, and login rate limiting
- command deduplication
- event replay support
- push-token registration and optional Firebase push delivery

## Project structure

```text
Relay/
├── app/
│   └── src/main/java/com/signaldesk/relay/
│       ├── appstate/
│       ├── data/
│       │   ├── local/
│       │   ├── mapper/
│       │   ├── realtime/
│       │   ├── remote/
│       │   ├── repository/
│       │   └── session/
│       ├── model/
│       ├── navigation/
│       ├── notifications/
│       └── ui/
├── backend/
│   ├── package.json
│   ├── test-all.js
│   ├── storage.js
│   └── server.js
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

## Run locally

### 1. Start the development backend

From the repository root:

```bash
npm --prefix backend install
npm --prefix backend test
```

Then start the backend from `backend/`:

```bash
node server.js
```

The development WebSocket server listens on port `9000`.

### 2. Connect an Android device

For a physical Android device connected over ADB:

```bash
adb reverse tcp:9000 tcp:9000
```

### 3. Build and install

On Windows:

```powershell
.\gradlew.bat installDebug
```

On macOS/Linux:

```bash
./gradlew installDebug
```

### 4. Launch Relay

```bash
adb shell am start -n com.signaldesk.relay/.MainActivity
```

## Current status

Relay is a feature-complete engineering portfolio project focused on resilient realtime Android architecture and failure recovery. The repository includes durable mutation delivery, SQLite-backed backend persistence, authentication and authorization, optional Firebase push support, lifecycle hardening, observability, and extensive reliability testing. It should not be interpreted as a production-hosted service or Play Store release; production deployment, signing, secrets management, operational monitoring, and store publishing remain outside the current repository scope.

## Repository

Built as part of a project-driven Android engineering program focused on reliability, offline behavior, realtime consistency, and production-oriented mobile architecture.
