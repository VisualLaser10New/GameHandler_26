# Deep Analysis: Local Server Offline Capabilities & Central Synchronization

This document presents a comprehensive analysis of the system's ability to handle clients locally while disconnected from the central server, and how it synchronizes state (users, game sessions, and reservations) once the connection is re-established.

---

## 1. Executive Summary

- **Offline Independence:** Yes, the local server is fully capable of running independently and handling clients when disconnected from the central server. Core actions such as user signup, user login, reservation creation/cancellation, game session management, and startup recovery run against the local database and local MQTT broker.
- **Synchronization Model (Outbox Pattern):** The system uses an **outbox pattern** on both the local server and central server. Actions performed offline generate pending events in an outbox database table.
- **Bi-Directional Reconciliation:**
    - **Local ➔ Central:** Syncs locally created users, completed game sessions, and reservation histories to the central system to update central repositories and aggregated statistics.
    - **Central ➔ Local:** Replicates central user updates down to all registered local servers.
- **Resilience and Robustness:** Both local and central schedulers are designed to prevent queue blocks ("batch poisoning") from malformed events or network failures.

---

## 2. Offline Capability (Handling Clients Offline)

When the connection to the central server is down, clients communicate directly with the local server APIs. Below is a breakdown of how different components behave offline.

### A. Local User Registration (Signup)
- **Service:** [LocalSignupService](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/application/service/LocalSignupService.java)
- **Database Entity:** `LocalUserJpaEntity` via [LocalSignupUserRepositoryAdapter](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/adapter/LocalSignupUserRepositoryAdapter.java)
- **Behavior:** Users registering directly on a local server are immediately saved to the local database. A corresponding `USER_REGISTERED` outbox event is generated and stored locally in the outbox.
- **Username Uniqueness:** The local registration check validates uniqueness against both local signups and replicated central users to prevent conflicts.

### B. User Authentication (Login)
- **Service:** [LocalAuthService](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/application/service/LocalAuthService.java)
- **User Repository Adapter:** [UserRepositoryAdapter](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/adapter/UserRepositoryAdapter.java)
- **Behavior:** When authenticating a user, the local server queries two repositories:
    1. `UserJpaRepository` (containing users replicated from the central system).
    2. `LocalUserJpaRepository` (containing users who signed up locally).
- **Result:** If the user exists in either repository, the password hash is checked locally using BCrypt, and a local JWT token is generated. This allows both central-registered users (if already replicated) and local-registered users to log in completely offline.

### C. Game Sessions
- **Service:** [GameSessionService](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/application/service/GameSessionService.java)
- **Behavior:** Session state transitions (`start`, `pause`, `resume`, `end`) are processed locally using the local `GameSessionRepository`.
- **MQTT Eventing:** Machine and session events are published to a local MQTT broker. Since this broker runs within the local infrastructure (typically on `localhost:1883`), it does not require internet connectivity.
- **Outbox Logging:** When a game session is successfully finalized (completed or aborted), a `GAME_SESSION_COMPLETED` outbox event is created.

### D. Reservations
- **Service:** [ReservationService](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/application/service/ReservationService.java)
- **Behavior:** Creating or cancelling reservations modifies the local `ReservationRepository` and changes the game machine state locally.
- **Outbox Logging:** Each reservation action generates either a `RESERVATION_CREATED` or a `RESERVATION_CANCELLED` outbox event.

### E. Crash & Recovery Resilience
- **Services:** [SessionRecoveryService](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/application/service/SessionRecoveryService.java) and [SessionRecoveryHelper](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/application/service/SessionRecoveryHelper.java)
- **Behavior:** If the local server crashes or reboots offline:
    1. It fetches all active/paused sessions from the local database.
    2. Pings the game machines via local MQTT.
    3. If a machine fails to respond within 30 seconds, it aborts the session, releases the game machine, and persists a `GAME_SESSION_COMPLETED` (aborted status) event in the outbox to ensure statistics reconcile properly once online.

---

## 3. Online Synchronization (Connection Re-established)

When connectivity is restored, bi-directional synchronization resumes.

```mermaid
sequenceDiagram
    participant LocalDB as Local Server DB
    participant LocalSync as SyncSchedulerService
    participant CentralReceiver as SyncReceiverService
    participant CentralDB as Central System DB
    
    Note over LocalSync: Connection Re-established
    LocalSync->>LocalDB: Fetch PENDING events
    LocalSync->>CentralReceiver: POST /internal/sync/receive (SyncPayloadDto)
    Note over CentralReceiver: Process Events (Idempotent check)
    alt EVENT_TYPE == USER_REGISTERED
        CentralReceiver->>CentralDB: Save new Central User
    else EVENT_TYPE == GAME_SESSION_COMPLETED / RESERVATION_*
        CentralReceiver->>CentralDB: Update Aggregated Statistics
    end
    CentralReceiver-->>LocalSync: 200 OK (heartbeat updated)
    LocalSync->>LocalDB: Mark events as SENT
```

### A. Local ➔ Central Synchronization (Pushing Local Data)

1. **Scheduled Polling:** [SyncSchedulerService](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/application/service/SyncSchedulerService.java) runs periodically (configured at a rate of 5 minutes).
2. **Connectivity Check:** It calls `syncCentralSystemPort.isReachable()`. In [CentralSystemRestAdapter](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/rest/CentralSystemRestAdapter.java), this pings `/internal/sync/receive` via a lightweight GET request. If the HTTP layer responds (even with `405 Method Not Allowed`), the server is determined to be reachable.
3. **Payload Construction:** Pending events are mapped to `OutboxEventDto` and packaged into a `SyncPayloadDto` containing the local server's `buildingId`.
4. **REST Transmission:** The payload is sent via a POST request to `/internal/sync/receive`.
5. **State Update:** If the POST succeeds with a 2xx status, [OutboxSyncHelper](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/application/service/OutboxSyncHelper.java) marks the local events as `SENT`.

### B. Central System Reception & Processing

1. **Service:** [SyncReceiverService](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/central-system/src/main/java/com/gameplatform/central/application/service/SyncReceiverService.java)
2. **Idempotency & Deduplication:** To handle network disruptions where the response fails but the data was written, the central system checks `ProcessedEventRepository`. If an incoming `eventId` has already been processed, it is skipped.
3. **Event Processing:**
    - **`USER_REGISTERED`:** Parses user details and calls [UserService.registerFromSync](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/central-system/src/main/java/com/gameplatform/central/application/service/UserService.java#L77-L99) to insert the user into the central database.
    - **`GAME_SESSION_COMPLETED`:** Calculates session duration and updates/merges the central `AggregatedStatistics` database table using a pessimistic write lock to prevent race conditions.
    - **`RESERVATION_CREATED` / `RESERVATION_CANCELLED`:** Increments/decrements reservation counts in `AggregatedStatistics`.
4. **Heartbeat:** Upon processing completion, the central system updates the local server's `lastSeenAt` timestamp, serving as a heartbeat indicator.

### C. Central ➔ Local Synchronization (Replicating Central Users)

1. **Scheduler:** [UserReplicationSchedulerService](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/central-system/src/main/java/com/gameplatform/central/application/service/UserReplicationSchedulerService.java) runs periodically (using `fixedDelay = 300_000` to prevent overlapping runs).
2. **Target Selection:** It retrieves active local servers from the database.
3. **Replication Progress Tracking:** The scheduler queries `ReplicationProgressRepository` to identify which local servers have not yet received specific user events.
4. **Push Mechanism:** It pushes user details to the local server's `/internal/sync/users` REST endpoint.
5. **Marking Progress:**
    - If a local server is online and accepts the push, a progress entry is recorded.
    - If a server is offline, the exception is caught, and that server is skipped. The outbox event remains pending on the central system for that specific server and will be retried in subsequent cycles.
    - The central outbox event is only marked as `SENT` globally when *all* active local servers have successfully acknowledged the replication.

---

## 4. Key Resilience Design Patterns

The codebase implements several patterns to guarantee eventual consistency and robustness:

| Pattern / Technique | Class & Implementation Detail | Benefit |
| :--- | :--- | :--- |
| **Outbox Pattern** | `OutboxEvent` & `OutboxEventRepository` on both servers | Decouples local execution from remote dependencies. Guarantees eventual consistency. |
| **Idempotency** | [SyncReceiverService](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/central-system/src/main/java/com/gameplatform/central/application/service/SyncReceiverService.java#L89-L92) via `ProcessedEventRepository` | Prevents duplicate registration and double-counting of statistics during retry transmissions. |
| **Queue-Poisoning Isolation** | `SyncReceiverService` (`processEvent`) & `UserReplicationSchedulerService` (`replicateUsers`) | Malformed payloads are logged and marked as failed or processed. A bad event never blocks subsequent messages. |
| **Pessimistic Locking** | `SyncReceiverService.updateSessionStats` via `statisticsRepository.findByBuildingAndTypeAndPeriodWithLock` | Prevents lost update problems (race conditions) when multiple local servers sync concurrently. |
| **Transaction Isolation** | `@Transactional(propagation = Propagation.REQUIRES_NEW)` in [UserService.registerFromSync](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/central-system/src/main/java/com/gameplatform/central/application/service/UserService.java#L75) | Ensures unique constraint violations during user sync do not fail the outer sync batch transaction. |

---

## 5. Potential Limitations & Edge Cases

1. **Central Signup Login Delay:** A user signing up on the Central Server cannot log in at a Local Server while that local server is offline, as the user data replication cannot reach it until it returns online.
2. **Local Sync Transactional Atomicity (Bug L-05):** As highlighted by [BugL05_SyncSchedulerNonAtomicMarkAsSentTest](file:///c:/Users/VLT14/Documents/UNI/PISSIR/Progetto/gamehandler-platform/local-server/src/test/java/com/gameplatform/local/application/service/BugL05_SyncSchedulerNonAtomicMarkAsSentTest.java), the local `SyncSchedulerService` does not run in a single transaction. If a failure occurs mid-loop while marking events as `SENT` locally, a subset of events remains pending. The local system will retry the pending events on the next run. Fortunately, the central system's idempotency check prevents duplicate statistics or entities.
3. **Data Loss on Local Server Destruction:** Since offline data is stored in the local server's database, a catastrophic hardware failure of the local server before synchronization would result in the loss of offline statistics and user signups.
