# Comprehensive Resolution & Missing-Implementation Plan

Scope: parts 1, 2, 5 of the prior audit (data-sharing integrity, statistics propagation, multi-local-server handling). Every fix is anchored to `path:line` of the current code. Read-only research; no file was modified.

Test conventions verified by subagents:
- Central integration tests: `central-system/src/test/java/com/gameplatform/central/integration/`, `@SpringBootTest @ActiveProfiles("test")` against H2 (`central-system/src/test/resources/application-test.yml:3`, `MODE=MySQL`, `ddl-auto: create-drop`), base `ContractTestBase.java:41-49` (WireMock + `@MockBean LocalServerRegistryPort`).
- Unit tests: `@ExtendWith(MockitoExtension.class)`.
- Local-server has NO `integration/` package (`SchemaAlignmentTest.java:53-57` documents why: `MqttConfig` eagerly connects to `tcp://localhost:1883`). Local tests are Mockito unit / `@DataJpaTest`.
- Spring Retry + WireMock already on the classpath.

Reference plan: `workflow/analisi/risoluzione_comunicazioni_local_central.md` (partially implemented — B1/B2/B4/B5/B6/B7/B13/B14/B17 mostly done; gaps listed below).

---

## Area 1 — Data-sharing integrity (local↔central)

### R1 — Late-server registration lost events (Medium)
**Root cause:** `LateRegistrationCatchUpService.catchUpNewlyRegisteredServer` (`central-system/.../application/service/LateRegistrationCatchUpService.java:64-115`) is invoked from `LocalServerRepositoryAdapter.register` (`:61-63`) on first registration, but (a) after a successful `pushUsers` (`:106`) it never writes `replication_progress` rows, and (b) it queries only `STATUS_SENT` (`:42,68-70`), so PENDING user events at registration time are missed (relying on the next 5-min `UserReplicationSchedulerService` tick to cover them).

**Files to edit:**
- `central-system/.../infrastructure/adapters/out/mysql/repository/OutboxEventJpaRepository.java` — add `findByStatusInAndEventTypeInOrderByCreatedAtAsc(Collection<String> statuses, Collection<String> eventTypes)` (optional `Pageable` overload).
- `central-system/.../application/service/LateRegistrationCatchUpService.java` — replace `STATUS_SENT` constant with `REPLAY_STATUSES = List.of("SENT","PENDING")`; refactor `usersToPush` into a parallel list `pushedEventIds` so progress can be recorded per-event; after a successful `pushUsers` (`:106`), loop `replicationProgressJpaRepository.save(new ReplicationProgressJpaEntity(eventId+"_"+serverId, eventId, serverId))` per pushed event, swallowing `DataIntegrityViolationException` (race with the scheduler — same pattern as `SyncEventProcessor.java:84-89`). **Recommended:** refactor from one batch push (`pushUsers(usersToPush, server)` at `:106`) to **per-event** pushes (`List.of(user)` each) so a poison user doesn't abort the whole batch and progress is recorded per-event immediately.
- Inject `ReplicationProgressRepository` (domain port) instead of the JPA repo directly (`:7-9`) to keep the hexagonal boundary.
- Optional `application.yml:31-34`: add `central.replication.catch-up-batch-size: 200` to bound the catch-up REST call.

**DB migration:** None — reuses `replication_progress` UK (`init.sql:85`).
**Backfill (existing deployments):**
```sql
INSERT INTO replication_progress (id, event_id, server_id)
SELECT CONCAT(e.id,'_',s.building_id), e.id, s.building_id
FROM outbox_events e CROSS JOIN local_servers s
WHERE e.event_type IN ('USER_REGISTERED','USER_UPDATED') AND e.status='SENT'
  AND NOT EXISTS (SELECT 1 FROM replication_progress rp WHERE rp.event_id=e.id AND rp.server_id=s.building_id);
```
**Tests:** `LateRegistrationCatchUpReplaysPendingAndRecordsProgressTest` (Mockito: assert SENT+PENDING replayed, progress saved per-event, duplicate progress tolerated, empty case no-op); `LateRegistrationCatchUpProgressPersistenceIT` (H2 + WireMock: 2 events seeded, register new building, assert 2 `replication_progress` rows).
**Risks:** Latency added to `POST /internal/servers/register` (bounded by `read-timeout-ms=5000` × batch); local retries on timeout. Backfill only fires for newly-registered buildings.

### R2 — Password-hash regression under retry reordering (Medium-High) — depends on R4
**Root cause:** `local-server/.../application/service/UserSyncService.java:30-40` does JPA `saveAll` with no ordering guard; `replicated_users` (`infrastructure/mysql-local/init.sql:96-102`) has no `event_time`/`updated_at`/`version`. If `USER_UPDATED` is delivered before a retried `USER_REGISTERED`, the older password silently overwrites the newer one.

**Files to edit:**
- `infrastructure/mysql-local/init.sql:96-102` — add `event_time DATETIME(6) NOT NULL`, `updated_at DATETIME(6) NOT NULL`, `version BIGINT NOT NULL DEFAULT 0`, and `CONSTRAINT uk_replicated_users_username UNIQUE (username)` (closes a latent duplicate-username bug).
- `local-server/.../infrastructure/adapters/out/mysql/entity/UserJpaEntity.java` — add the 3 fields; `@Version` on `version`; canonical constructor updated.
- `local-server/.../domain/model/User.java` — add `eventTime`, `updatedAt`; keep `getSyncedAt()` as deprecated alias of `getEventTime()`.
- `local-server/.../infrastructure/adapters/out/mysql/mapper/UserMapper.java:32-44` — map the new fields.
- `local-server/.../domain/ports/out/UserRepository.java:7-11` — add `Optional<User> findById(UserId)`.
- `local-server/.../infrastructure/adapters/out/mysql/adapter/UserRepositoryAdapter.java` — implement `findById`.
- `local-server/.../application/service/UserSyncService.java:14-42` — inject `Clock`; replace bulk `saveAll` with a per-dto loop applying the ordering guard:
  ```java
  Optional<User> existing = userRepository.findById(new UserId(dto.userId()));
  if (existing.isPresent() && existing.get().getEventTime().isAfter(dto.occurredAt())) {
      log.warn("Stale user replication event for userId={}: existing eventTime={} > incoming occurredAt={}; skipping",
               dto.userId(), existing.get().getEventTime(), dto.occurredAt());
      continue;
  }
  userRepository.save(new User(new UserId(dto.userId()), dto.username(), dto.email(),
                               dto.hashedPassword(), dto.roles(), dto.occurredAt(), Instant.now(clock)));
  ```

**Why `@Version` alone is NOT sufficient:** `@Version` prevents lost updates within overlapping concurrent tx, but a retried event arriving later in a separate tx would just bump the version and overwrite — `@Version` doesn't detect "this event is older than the state I already have". The event-time comparison is the real guard; `@Version` is defence in depth (catch-up + scheduler racing → loser throws `OptimisticLockException` → central retries → guard decides).

**DB migration:**
```sql
ALTER TABLE replicated_users
  ADD COLUMN IF NOT EXISTS event_time DATETIME(6) NULL AFTER synced_at,
  ADD COLUMN IF NOT EXISTS updated_at DATETIME(6) NULL AFTER event_time,
  ADD COLUMN IF NOT EXISTS version   BIGINT NOT NULL DEFAULT 0 AFTER updated_at;
UPDATE replicated_users SET event_time=synced_at, updated_at=synced_at WHERE event_time IS NULL;
ALTER TABLE replicated_users MODIFY event_time DATETIME(6) NOT NULL;
ALTER TABLE replicated_users MODIFY updated_at DATETIME(6) NOT NULL;
-- Pre-audit: SELECT username, COUNT(*) FROM replicated_users GROUP BY username HAVING COUNT(*)>1;
ALTER TABLE replicated_users ADD UNIQUE KEY uk_replicated_users_username (username);
```
**Tests:** `UserSyncServiceOrderingGuardTest` (skip stale, apply newer, first-ever, mixed batch); `UserRepositoryAdapterOrderingGuardIT` (`@DataJpaTest` + H2: stale event leaves row unchanged + `version=0`; newer event updates + `version=1`).
**Risks:** Schema migration REQUIRED before JAR deploy (`ddl-auto: validate` will refuse boot). Unique key may fail on pre-existing duplicate usernames — pre-audit. **R2 MUST be deployed with R4** (the guard has no `occurredAt` to compare without R4).

### R3 — Atomicity gap in `HealthCheckService` TIMEOUT outbox emission (Medium)
**Root cause:** `local-server/.../application/service/HealthCheckService.java:124-149` aborts the session + releases the game + writes the `GAME_SESSION_ABORTED` outbox row inside a `try { ... } catch (Exception e) { log.error(...); }` (`:147-149`). If `objectMapper.writeValueAsString` (`:135`) or `outboxEventRepository.save` (`:146`) throws, the exception is swallowed and the class-level `@Transactional` (`:34`) commits the abort WITHOUT the outbox row — central stats permanently understated. The sibling `SessionRecoveryHelper.abortSession` (`:51-100`) correctly propagates; its caller `SessionRecoveryService.java:79-83` wraps the try/catch — confirming propagation is the intended contract.

**Fix (Option A — recommended):** new collaborator for per-game `REQUIRES_NEW` atomicity.
- **CREATE** `local-server/.../application/service/SessionAbortHelper.java` — `@Component` with `@Transactional(propagation = REQUIRES_NEW) void abortAndEmit(GameSession, StopReason, String stopReasonCode) throws Exception`. Builds the payload, saves the session, releases the game, and saves the outbox row in ONE tx — any failure rolls back all three. Reuse `deferMqttPublish` for the MQTT side-effects (`afterCommit`).
- **EDIT** `HealthCheckService.java` — inject `SessionAbortHelper`; replace `:111-150` with `try { sessionAbortHelper.abortAndEmit(session, StopReason.TIMEOUT, "TIMEOUT"); } catch (Exception e) { log.error("Per-game abort+outbox failed for gameId={}; tx rolled back, will retry next tick", gameId, e); }`; change class-level `@Transactional` (`:34`) on `performHealthCheck` to `Propagation.NEVER` so the sweep does NOT hold a tx across all games (each game's abort runs in its own `REQUIRES_NEW`).
- **EDIT** `SessionRecoveryHelper.java:51-101` — generalise to `abortSession(GameSession, StopReason, String stopReasonCode)`; OR delete it and have `SessionRecoveryService.java:80` call `SessionAbortHelper.abortAndEmit(session, StopReason.ABORTED, "SERVER_RESTART")` directly (DRY preferred).

**Why Option B (inline swallow-removal) is insufficient:** removing the try/catch without splitting the tx means a thrown exception marks the class-level tx rollback-only → ALL prior games' mutations in the same sweep roll back. Self-invocation of a `@Transactional(REQUIRES_NEW)` method on `this` bypasses the Spring proxy. A separate bean is required.

**Tests:** `HealthCheckServiceOutboxAtomicityTest` (Mockito: outbox-save failure propagates and `outboxEventRepository.save` never called after the throw; happy path emits `GAME_SESSION_ABORTED`); `SessionAbortHelperIT` (`@DataJpaTest` + H2: persist IN_PROGRESS session + IN_USE game, call `abortAndEmit`, assert `game_sessions.status='ABORTED'` + `game_catalog.status='AVAILABLE'` + `outbox_events` row present; force ObjectMapper throw → assert session STILL `IN_PROGRESS` and `outbox_events` empty — rollback).
**Risks:** Behavioral change — operators will see sessions linger `IN_PROGRESS` an extra tick if outbox is unhealthy (correct, no partial commit). Update runbooks.

### R4 — `UserSyncDto` omits `email`/`occurredAt` (Low-Med) — prerequisite for R2
**Root cause:** `shared/shared-dto/.../UserSyncDto.java:5-10` has only `(userId, username, hashedPassword, roles)`. The central `User` has both `email` and `createdAt` (`central-system/.../domain/model/User.java:13,15`) but `UserService.saveUserOnDB` (`:123-140`) and `getAllUsersForSync` (`:49-53`) drop them. `UserSyncService.java:36` uses `Instant.now()` instead of the event time, making the R2 guard impossible. The plan's D3 intentionally froze this — the audit now reopens it.

**Files to edit:**
- `shared/shared-dto/.../UserSyncDto.java` — extend to `(userId, username, email, hashedPassword, roles, occurredAt)` (use `occurredAt`, NOT `createdAt` — the R2 guard needs the **event** time, monotonic per event; for `USER_UPDATED` the user's `createdAt` is the original creation time and is identical across consecutive updates, unusable for ordering). Add `import java.time.Instant;`.
- `central-system/.../application/service/UserService.java:41-45` — inject `Clock`; at `:49-53` and `:126` populate `email = savedUser.getEmail()` and `occurredAt = Instant.now(clock)` (replace the `Instant.now()` at `:135` too for consistency).
- `infrastructure/mysql-local/init.sql:96-102` — add `email VARCHAR(100)` (nullable for the transition; do NOT add unique key on `email` locally during transition — multiple NULLs allowed).
- `local-server/.../infrastructure/adapters/out/mysql/entity/UserJpaEntity.java`, `local-server/.../domain/model/User.java`, `local-server/.../infrastructure/adapters/out/mysql/mapper/UserMapper.java` — add `email` field + mapping.
- `local-server/.../application/service/UserSyncService.java:30-40` — pass `dto.email()` and `dto.occurredAt()` into the `User` constructor (this overlaps with R2 — apply together).

**DB migration:** `ALTER TABLE replicated_users ADD COLUMN IF NOT EXISTS email VARCHAR(100) AFTER password_hash;` — backfill via the M4 reconciliation job once R4 + M4 are deployed.
**Backward-compat:** Jackson sets missing fields to `null` on the local when central is not yet upgraded; the R2 guard must treat `occurredAt=null` as "apply" (do NOT skip). Upgrade order is flexible.
**Tests:** `UserSyncDtoSerializationTest` (round-trip + 4-field backward-compat); `UserServiceDtoPopulationTest` (assert `occurredAt` of `USER_UPDATED` > `occurredAt` of prior `USER_REGISTERED` for same user); extend `InternalSyncControllerTest.java:31-39` body with `email` + `occurredAt`.
**Risks:** Wire-format change (internal-only); email backfill needed for existing rows (M4).

### Schema asymmetry — `outbox_events.payload` JSON locally vs TEXT centrally (Low)
**Root cause:** `infrastructure/mysql-local/init.sql:76` is `payload JSON NOT NULL`; `infrastructure/mysql-central/init.sql:67` is `payload TEXT NOT NULL`. Mirrored in entities (`OutboxEventJpaEntity.java:20` local vs `:23` central). The plan's B17 called for alignment — not done. `SchemaAlignmentTest.java:42-51` javadoc is stale (claims central `retry_count` exists — it does NOT).

**Files to edit:**
- `infrastructure/mysql-central/init.sql:64-72` — `payload JSON NOT NULL` (optionally add `retry_count INT NOT NULL DEFAULT 0` for symmetry, though central doesn't need it today).
- `central-system/.../infrastructure/adapters/out/mysql/entity/OutboxEventJpaEntity.java:23-24` — `columnDefinition = "JSON"`.
- `central-system/src/test/java/com/gameplatform/central/integration/SchemaAlignmentTest.java:42-51` — correct stale javadoc; extend `:75-106` to assert `outbox_events.payload` TYPE is `JSON` (H2 mapping may differ — document).

**DB migration:**
```sql
SELECT id, payload FROM outbox_events WHERE NOT JSON_VALID(payload);  -- pre-audit; must be empty
ALTER TABLE outbox_events MODIFY COLUMN payload JSON NOT NULL;
```
**Tests:** extend `SchemaAlignmentTest`; `OutboxPayloadJsonValidationIT` (insert `payload='not-json'` → expect `DataIntegrityViolationException`; on H2 may not enforce — tag `@EnabledIfSystemProperty` for MySQL or document).
**Risks:** `ddl-auto: validate` rejects boot if column not yet altered → migration MUST precede JAR deploy. H2 vs MySQL JSON enforcement diverges.

---

## Area 2 — Statistics propagation integrity

### S1 — Double-count across bins (Medium)
**Root cause:** `GameSessionService.end()` (`local-server/.../application/service/GameSessionService.java:177-256`) tolerates an ABORTED session (`:182` `wasAborted = ...`) and calls `session.complete(result, Instant.now(clock))` (`:185`) — `GameSession.complete(...)` accepts `ABORTED` as a valid source state (`local-server/.../domain/model/GameSession.java:88`). `end()` then UNCONDITIONALLY emits a fresh `GAME_SESSION_COMPLETED` outbox event with a NEW `eventId` (`:232,243-251`). The earlier ABORTED path emitted its own `GAME_SESSION_ABORTED` with its own fresh `eventId` (`HealthCheckService.java:127,137` / `SessionRecoveryHelper.java:81,93`). Central dedup keyed on `eventId` (`SyncEventProcessor.java:72` `existsByEventId` → `processed_events.event_id` PK) does NOT catch the duplicate → same logical `sessionId` counted in BOTH `total_aborted_sessions` (`:124-133`) AND `total_sessions` (`:111-122`).

**Fix — Option (a2) RECOMMENDED (producer-side guard, no schema change):**
- **EDIT** `GameSessionService.java:224-255` — wrap the `GAME_SESSION_COMPLETED` outbox emission in `if (!wasAborted) { ... }`. KEEP `session.complete(...)` + `gameSessionRepository.save(session)` (`:185-186`) so the local row records the final result/winner for local win-rate stats; KEEP the MQTT session-end publish (`:198-222`) for client UI. Only the central-stats outbox event is suppressed.

**Option (b) — central dedup by `sessionId` (defense-in-depth, optional):** new `processed_session_events(session_id PK)` table; inside `SyncEventProcessor.processEvent`'s `GAME_SESSION_*` branches, parse `sessionId`, check/insert into the new table (same `existsByEventId`+PK-catch pattern at `:72-89`); skip stats if present. More invasive (new table/port/entity/adapter/migration).

**Recommendation:** adopt (a2) now as the root-cause fix; track (b) as optional hardening. (a2) is ~3 lines, no schema, preserves local result recording; the central `aggregated_statistics.data` JSON column never consumed `resultJson` anyway.

**Tests:** `GameSessionStateMachineAbortedTerminalTest` (domain: `abort()` then `complete()` succeeds, status COMPLETED, result set — pins preserved local-recording behavior); `GameSessionLateEndDoesNotEmitCompletedOutboxEventTest` (the S1 regression: `verify(outboxEventRepository, never()).save(any())` for an ABORTED→end() call, BUT `gameSessionRepository.save(session)` WAS called); `GameSessionLateEndStillEmitsWhenInProgressTest` (happy path still emits exactly one COMPLETED); optional `SyncEventProcessorAbortedThenCompletedNoDoubleCountContractTest` (central side, only if (b) is adopted).
**Risks:** Zero migration. If some FUTURE code path besides `end()` emits `GAME_SESSION_COMPLETED` for an aborted session, double-count returns — mitigate with the contract test #4.

### S2 — Missing/invalid `durationSeconds` silently coerced to 0 (Low-Med)
**Root cause:** `SyncEventProcessor.extractDuration` (`central-system/.../application/service/SyncEventProcessor.java:199-215`) only logs WARN at the final fallback (`:213-214`). On the earlier branches `JsonNode.asInt()` (`:201,207,210`) returns `0` with NO exception and NO log when the node is JSON `null` or non-coercible — silent corruption of `avg_duration_seconds`.

**Fix (recommended — no schema change):**
- **EDIT** `SyncEventProcessor.java:199-215` — change return type to `Optional<Integer>`; on each branch check `n.isNumber() && n.canConvertToInt() && !n.isNull()`; log a WARN identifying the precise failure mode (`"null"`, `"non-numeric: <value>"`, `"resultJson fallback missing"`); return `Optional.empty()` on failure.
- **EDIT** caller `:120-121` — `Optional<Integer> dur = extractDuration(payloadNode, eventDto.eventId()); int durationSeconds = dur.orElse(0); updateSessionStats(buildingId, gameType, periodStart, durationSeconds);` — KEEP the invariant that a COMPLETED session always increments `totalSessions` by 1 (losing the count is worse than losing the duration contribution).

**Optional (if queryability required):** add a first-class `missing_duration_count INT NOT NULL DEFAULT 0` column to `aggregated_statistics` (`init.sql:37-49`) + entity + domain + `StatisticsMapper` + `mergeWith` + DTO (M2 surface). Do NOT use the `data` JSON column — it is dead storage (see M3 below).

**Tests:** `SyncEventExtractDurationNullAndNonNumericLogTest` (sibling to existing `SyncReceiverExtractDurationWarnTest:79-111`, which only covers the no-key path — extend coverage to `{"durationSeconds": null}` and `{"durationSeconds": "abc"}`; use a Logback `ListAppender` since LogCaptor is forbidden per `:36-44`); `SyncEventExtractDurationOptionalContractTest`.
**Risks:** Zero migration. Past rows keep their stored `avg_duration_seconds`. Always-log closes the silent hole.

### S3 — First-bucket insert race on `aggregated_statistics` (Medium) — same as C-R1
**Root cause:** `PESSIMISTIC_WRITE` lock (`StatisticsJpaRepository.java:17-22`) only locks an EXISTING row. Two concurrent first-events for the SAME `(building_id, game_type, period_start)` both see `Optional.empty()` (`SyncEventProcessor.java:223-224`) → both `save(newStats)` (`:253`, also `:297` aborted, `:339` reservation). UK `uk_building_type_period` (`init.sql:48`) throws `DataIntegrityViolationException` at flush, which is NOT caught by `processOne`'s catch (`:79-83` — only catches duplicate-`processed_events`); it propagates to `SyncReceiverService.java:91-99` which calls `markProcessed(event.eventId())` (`:95`) → the loser's stats increment is permanently lost AND the event is marked processed.

**Fix (Option 1 — RECOMMENDED, portable, H2-testable):**
- **EDIT** `central-system/.../infrastructure/adapters/out/mysql/adapter/StatisticsRepositoryAdapter.java:29-33` — change `jpaRepository.save(entity)` to `jpaRepository.saveAndFlush(entity)` so UK throws at the call site (inside the application service's reachable try-catch) instead of at tx commit.
- **EDIT** `SyncEventProcessor.java` `updateSessionStats` (`:221-255`), `updateAbortedStats` (`:264-299`), `updateReservationStats` (`:304-341`) — wrap the `else { save(newStats); }` branch in `try { save(newStats); } catch (DataIntegrityViolationException dup) { log.info("First-bucket race ..."); AggregatedStatistics winner = statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(...).orElseThrow(...); winner.mergeWith(delta); statisticsRepository.save(winner); }`. Both branches collapse to a single `save` call delegated to the adapter; the adapter's eager flush makes the UK catchable.

**Option 2 (rejected):** native `INSERT ... ON DUPLICATE KEY UPDATE` in `StatisticsJpaRepository` — MySQL-specific, breaks H2 test dialect, duplicates the domain math in SQL.

**Idempotency / ordering notes:** Current `processOne` ordering (`:77-89`) — stats update THEN `processed_events` save, both in one `REQUIRES_NEW` tx — is correct (a stats failure rolls back the dedup write). The bug is NOT the ordering; it's that the stats `save` fails on the UK race and the wrong layer "heals" it by `markProcessed` without the stats. The fix makes the stats `save` race-safe so it no longer fails → `:91-99` is not reached for this race → `markProcessed` not called → stats not lost. **No reordering needed.**

**Tests:** `AggregatedStatisticsFirstBucketInsertRaceConcurrencyIT` (H2 + 2 threads + `CountDownLatch`, distinct `eventId`s but same `(building, CHESS, period)`; assert `total_sessions==2`, both eventIds in `processed_events`, no exception propagated — RED before fix, GREEN after); `SyncEventProcessorFirstBucketRetryMergeUnitTest`, `SyncEventProcessorAbortedFirstBucketRaceTest`, `SyncEventProcessorReservationFirstBucketRaceTest` (mock the retry contract per method).
**Risks:** `saveAndFlush` is called only from the three `update*Stats` methods (verified by grep) — no other caller affected. Retry handles N-way races (each loser serialises on `FOR UPDATE`). H2's `FOR UPDATE` blocks like InnoDB under `MODE=MySQL`.

---

## Area 5 — Multi-local-server handling

### C-R1 — Within-building first-bucket insert race (Medium)
Same as S3. The cross-building case is safe (lock and UK include `building_id` → different rows, no contention); the within-building first-insert race is the only divergence. Fix S3 closes it. **Coordinate:** fix C-R1/S3 before any load test driving concurrent sync-receive calls.

### C-R4 — Single-thread `TaskScheduler` + `fixedDelay=300_000` (Medium)
**Root cause:** `central-system/.../infrastructure/config/SchedulerConfig.java:27-35` declares only a `Clock` bean — no `ThreadPoolTaskScheduler`. Spring falls back to a single-thread `ConcurrentTaskScheduler`. `UserReplicationSchedulerService.replicateUsers` (`:73`, `@Scheduled(fixedDelay=300_000)`) pushes to each active server SEQUENTIALLY (`:113-127`). Worst case per failing server ≈ 3 × (5s + 5s) + backoff ≈ 30s (`LocalServerRestAdapter.java:52-58,35-36`). One slow building delays all others in the tick (sum) and delays the next tick for all.

**Fix (recommended — Option A + inner-loop parallelism):**
- **EDIT** `SchedulerConfig.java` — add a `ThreadPoolTaskScheduler` bean (pool size from `app.scheduler.pool-size`, default 4) and a dedicated `ThreadPoolTaskExecutor` bean named `replicationPushExecutor` (parallelism from `app.replication.push-parallelism`, default 4).
- **EDIT** `central-system/src/main/resources/application.yml` — add `app.scheduler.pool-size: ${SCHEDULER_POOL_SIZE:4}` and `app.replication.push-parallelism: ${REPLICATION_PUSH_PARALLELISM:4}`.
- **EDIT** `UserReplicationSchedulerService.java:113-127` — parallelize the per-server loop with `CompletableFuture.runAsync(..., replicationPushExecutor)` + `allOf(...).join()`. Use `AtomicBoolean allSucceeded`. KEEP `fixedDelay` (no `@Async` on the method itself — that breaks fixedDelay no-self-overlap).
- **PREREQUISITE:** fix C-R5 first so duplicate `replication_progress` inserts (race between parallel pushes) are treated as success, not failure.

```java
@Bean
public ThreadPoolTaskScheduler taskScheduler(@Value("${app.scheduler.pool-size:4}") int poolSize) {
    ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
    s.setPoolSize(poolSize); s.setThreadNamePrefix("central-sched-");
    s.setWaitForTasksToCompleteOnShutdown(true); s.setAwaitTerminationSeconds(30);
    return s;
}
@Bean("replicationPushExecutor")
public ThreadPoolTaskExecutor replicationPushExecutor(@Value("${app.replication.push-parallelism:4}") int p) {
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(p); ex.setMaxPoolSize(p); ex.setQueueCapacity(64);
    ex.setThreadNamePrefix("repl-push-");
    ex.setWaitForTasksToCompleteOnShutdown(true); ex.setAwaitTerminationSeconds(30);
    return ex;
}
```

**Sequence (corrected):** Before — scheduler thread runs the per-server `for` loop sequentially; tick T0 takes 30+N×0.2s; T1 delayed by 30s; other `@Scheduled` methods blocked. After — `central-sched-1` dispatches `repl-push-1/2/3` concurrently; building-1 and building-3 complete at t≈0.2s while building-2 runs 30s; `allOf().join()` waits; tick completes at t≈30s (= max, not sum); a second `@Scheduled` method (e.g. `LocalServerHealthMonitorService`) can fire on `central-sched-2` during step 3.

**Tests:** `SchedulerConfigTest` (assert `TaskScheduler` is `ThreadPoolTaskScheduler` with `poolSize==4`, override via property); extend `UserReplicationSchedulerServiceTest` with `replicateUsers_pushesToAllServersInParallelAndDoesNotBlockOnSlowServer` (stub server-1 to block on a `CountDownLatch`, server-2 completes immediately; assert server-2's progress saved before server-1's latch released).
**Risks:** Parallel ticks may double-push if UK + per-server progress isn't tight — mitigated by `fixedDelay` (no self-overlap) + C-R5 (duplicate progress = success) + local upsert. `SimpleClientHttpRequestFactory` (`LocalServerRestAdapter.java:37`) is not connection-pooled — bounded by 5s timeouts; do not switch to `HttpComponentsClientHttpRequestFactory` without re-verifying cert truststore wiring.

### C-R5 — `replication_progress` UK race (Low) — prerequisite for C-R4
**Root cause:** `UserReplicationSchedulerService.java:118-126` wraps BOTH `pushUsers` AND `replicationProgressRepository.save` in one `try { ... } catch (Exception e) { allSucceeded=false; }`. The generic `Exception` catch eats `DataIntegrityViolationException` from the UK `uk_replication_event_server` (`init.sql:85`) — a duplicate-insert race (future `@Async`, manual replay, or C-R4's parallelism) would mark `allSucceeded=false` and keep the event PENDING forever despite the push having succeeded.

**Fix:**
- **EDIT** `UserReplicationSchedulerService.java:117-126` — split the try: keep `pushUsers` in a try that catches `Exception` (real push failure → `allSucceeded=false`, `continue`); wrap ONLY `replicationProgressRepository.save(...)` in its own `try { ... } catch (DataIntegrityViolationException dup) { log.info("replication_progress already present ... — treating as success"); }` (do NOT flip `allSucceeded`).
- **EDIT** `central-system/.../domain/ports/out/ReplicationProgressRepository.java` — add `boolean existsByEventIdAndServerId(String, String)` (optional pre-check, reduces log noise).
- **EDIT** `ReplicationProgressJpaRepository.java` and `ReplicationProgressRepositoryAdapter.java` — implement the new method.

**Tests:** extend `UserReplicationSchedulerServiceTest` with `replicateUsers_treatsDuplicateProgressAsSuccessAndStillMarksSent` (stub `save` to throw `DataIntegrityViolationException`; assert `markAsSent` IS called); `replicateUsers_usesExistsByEventIdAndServerIdPreCheck`.
**Risks:** Treating UK violation as success could mask real schema issues — mitigate by catching ONLY `DataIntegrityViolationException`, logging the (event, server) pair at INFO, optionally checking `dup.getMessage()` contains `uk_replication_event_server` before treating as success.

### C-R6 — Global user uniqueness across buildings (Low)
**Root cause:** `users.username`/`email` globally unique (`infrastructure/mysql-central/init.sql:19-20`); `UserService.registerFromSync` (`:75-99`) silently swallows the losing building's `DataIntegrityViolationException` at `:96-98`, keeping whichever password arrived first.

**Fix (recommended — Option 1, no code change to semantics):**
- **EDIT** `UserService.java:96-98` — replace the silent `log.warn` with a louder structured warn explicitly stating "central user already exists; keeping existing password; the losing building is still locally consistent".
- Optionally CREATE an audit table `registration_collisions(user_id, username, occurred_at)` and record the collision.
- **CREATE** a short doc note (append to `README.md` or `docs/user-uniqueness.md`) stating the contract: username is global; first registration wins the password hash; later attempts are idempotent no-ops; per-building password divergence is expected if buildings register the same username with different passwords.

Option 2 (per-building password stores via a new `building_users` table) is a major refactor that breaks the global-user assumption central `AuthService` relies on — **NOT recommended**; document as future work only.
**Tests:** extend `UserServiceFromSyncTest` with `registerFromSync_keepsFirstPasswordWhenTwoBuildingsRegisterSameUsername`.
**Risks:** Silent collision can confuse operators — mitigated by the louder log + audit + doc.

### Late-registration catch-up gaps (Low) — overlap with R1
Covered by R1: `LateRegistrationCatchUpService` (a) does not write `replication_progress` rows and (b) only replays SENT events. The R1 fix (replay SENT+PENDING, write progress per-event) closes both. Additional design hardening: move the catch-up invocation from `LocalServerRepositoryAdapter.register:62` to a `TransactionSynchronization.afterCommit` callback so the REST call latency is decoupled from the registration tx and phantom progress rows are impossible if `register` rolls back.

### Empirical gap — never run with >1 building (Medium)
**Root cause:** `infrastructure/mysql-central/init.sql` has no `INSERT`s (no `building-2`/`building-3` seed); `docker-compose.yml` provisions only `local-server-1` with `BUILDING_ID=building-1` (`:70-107`); `EndToEndSimulationIT.java:58-149` is central-only and mocks `LocalServerRegistryPort` to return `List.of()` (`:68`), skipping auto-registration and replication push. Multi-building has never been exercised end-to-end.

**Fix:**
- **CREATE** `docker-compose.multi.yml` (override, `docker compose -f docker-compose.yml -f docker-compose.multi.yml up`) — `local-db-2`/`local-db-3` (host ports 3308/3309), `mqtt-broker-2`/`mqtt-broker-3` (host ports 8884/8885 — separate brokers recommended; the current `mosquitto.conf` has no ACL file so a shared broker would let any client publish anywhere), `local-server-2`/`local-server-3` (`BUILDING_ID=building-2`/`building-3`, own `LOCAL_BASE_URL`, own MQTT URL), `game-client-3`/`game-client-4` pointing at buildings 2/3. Networks `local-net-2`/`local-net-3` + shared `integration-net`. TLS: generate per-building certs via `infrastructure/tls/generate-certs.ps1` (verify SAN covers `local-server-2`/`local-server-3`).
- **CREATE** `infrastructure/mysql-local/init-building-2.sql` and `init-building-3.sql` — identical to `init.sql` except the `INSERT INTO game_catalog ... 'building-2'/'building-3'` rows (MySQL init scripts don't do shell substitution, so per-building files are required).
- **CREATE** `central-system/src/test/java/com/gameplatform/central/integration/MultiBuildingEndToEndIT.java` — four scenarios: (1) building-2 + building-3 self-register → both rows in `local_servers`; (2) a `USER_REGISTERED` at building-2 → replicated to building-1 AND building-3 (assert 2 `replication_progress` rows, event SENT); (3) `GAME_SESSION_COMPLETED` for building-2 CHESS and building-3 FOOSBALL → two distinct `aggregated_statistics` rows, no cross-building pollution; (4) re-send same `USER_REGISTERED` from building-2 → no second push (`processed_events` dedup). Use a new `MultiBuildingTestBase` that does NOT mock the registry (or stubs it to a real list).

**Tests:** `MultiBuildingEndToEndIT` covering the four scenarios; `docker-compose.multi.yml` smoke test documented in `README.md`.
**Risks:** TLS SAN mismatch (mitigate: per-building certs); MQTT namespace collision (mitigate: separate brokers); H2 vs MySQL dialect differences for `PESSIMISTIC_WRITE`/upsert (the C-R1/S3 fix must be verified on both).

---

## Missing Implementations (cross-cutting)

### M1 — `LateRegistrationCatchUpService` does not record `replication_progress` (R1 gap a) — covered by R1 fix.

### M2 — `LateRegistrationCatchUpService` only replays SENT events (R1 gap b) — covered by R1 fix.

### M3 — No `/internal/users/sync` per-user ACK contract (NEW gap)
**Confirmed missing.** `local-server/.../infrastructure/adapters/in/rest/InternalSyncController.java:20-26` returns `ResponseEntity.ok().build()` for the WHOLE batch regardless of per-user outcomes. `UserSyncService.syncUsers` (`:24-41`) has NO per-user try/catch — a single poison user (blank `username` → `IllegalArgumentException` from `User.java:14-29`) aborts the stream, the class-level `@Transactional` (`:15`) rolls back the WHOLE batch, the central gets a 500, `LocalServerRestAdapter` retries 3× then throws, `UserReplicationSchedulerService:121-126` catches → `allSucceeded=false` → event stuck PENDING forever. The catch-up (R1) batches multiple users in one PUT — same poison-user aborts the whole batch and no progress recorded for anyone.

**Proposed design:**
- **CREATE** `shared/shared-dto/.../UserSyncAckDto.java` — `record UserSyncAckDto(String userId, boolean applied, String reason)` (`reason` null on success; `"STALE_EVENT"` | `"VALIDATION_ERROR: ..."` on failure).
- **EDIT** `InternalSyncController.syncUsers` — return `ResponseEntity<List<UserSyncAckDto>>` (one ack per input user).
- **EDIT** `SyncUsersUseCase.syncUsers` signature — from `void syncUsers(List<UserSyncDto>)` to `List<UserSyncAckDto> syncUsers(List<UserSyncDto>)`. Each dto in its own try/catch; poison user returns `applied=false, reason="VALIDATION_ERROR: ..."` and does NOT abort the batch.
- **EDIT** `LocalServerRestAdapter.pushUsers` — return `List<UserSyncAckDto>` (instead of `void`) so the caller decides per-user whether to record progress.
- **EDIT** `UserReplicationSchedulerService` and `LateRegistrationCatchUpService` — record `replication_progress(eventId, serverId)` ONLY for users whose ack is `applied=true` OR `reason="STALE_EVENT"` (the event was deliberately skipped, not failed). For `applied=false` with `reason="VALIDATION_ERROR"`, mark the event `FAILED` (poison isolation).

This is the per-user analogue of the per-event poison-isolation work done in B7 (`workflow/analisi/risoluzione_comunicazioni_local_central.md:91-96`). Not in the original plan.

### M4 — No periodic reconciliation job (central audit users vs replicated_users per server) (NEW gap)
**Confirmed absent.** Grepped all `@Scheduled` services — none compares central `users` count to local `replicated_users` count per server.

**Proposed:**
- **CREATE** `central-system/.../domain/ports/out/QueryLocalServerUserCountPort.java` — `long countReplicatedUsers(RegisteredLocalServer server)`.
- **CREATE** `central-system/.../infrastructure/adapters/out/rest/LocalServerUserCountRestAdapter.java` — `GET {baseUrl}/internal/users/count` with `X-Internal-Api-Key`.
- **EDIT** `InternalSyncController` (local) — add `GET /internal/users/count` returning `ResponseEntity<Long>`.
- **CREATE** `central-system/.../application/service/UserReplicationReconciliationService.java` — `@Scheduled(fixedDelayString="${app.reconciliation-interval-ms:3600000}")` (default 1h); for each active server, compare counts; on mismatch, re-push `userService.getAllUsersForSync()` to that server (catch-up style).
- **EDIT** `central-system/src/main/resources/application.yml` — `app.reconciliation-interval-ms: ${RECONCILIATION_INTERVAL_MS:3600000}`.

This also serves as the backfill mechanism for R4's email on existing rows. Not in the original plan.

### M5 — `LocalServerRestAdapter` retry is per-batch, not per-user (B14 partial)
B14 (Spring Retry, configurable timeouts) is DONE (`LocalServerRestAdapter.java:52-58,35-39`). Remaining gap: retry operates on the WHOLE batch — a poison user blocks the good ones. Closed by the M3 per-user ACK contract + per-user push. `UserReplicationSchedulerService.java:119` already pushes `List.of(user)` (one user per call) in the normal flow, so per-user == per-event there; the catch-up batch is the remaining case.

### M6 — Central `outbox_events` lacks `retry_count` (B13 partial, central side)
**No gap for the current design.** The central outbox is only written by `UserService.saveUserOnDB` and read by `UserReplicationSchedulerService` which never retries a SENT event. The local DLQ (B13) is DONE. The `SchemaAlignmentTest.java:42-51` javadoc claiming the central column exists is **stale** — correct it as part of the schema-asymmetry fix.

### M7 — `UserReplicationSchedulerService.replicateUsers` is `fixedDelay` not `fixedDelayString` (B6 partial)
**Minor gap.** `UserReplicationSchedulerService.java:73` uses `@Scheduled(fixedDelay = 300_000)` (hardcoded, NOT configurable). B6 called for `fixedDelayString = "${app.sync-interval-ms:300000}"`. The local side is DONE (`SyncSchedulerService.java:33`, `HealthCheckService.java:93`). The central was missed. **Fix:** change `:73` to `fixedDelayString = "${app.sync-interval-ms:300000}"` (`application-test.yml:27` already has `sync-interval-ms: 999999999` which would then apply).

### M8 — Catch-up runs INSIDE the `register` tx (R1 design risk)
`LocalServerRepositoryAdapter.register:42-64` calls `lateRegistrationCatchUpService.catchUpNewlyRegisteredServer` (`:62`) inside the `@Transactional` `register` — the catch-up's REST call executes BEFORE `register` commits the new server row. If `register` rolls back, the catch-up's `replication_progress` writes (after R1) are already committed (REQUIRES_NEW) — phantom rows. Acceptable (UK makes re-registration safe), but **recommend** moving the catch-up to a `TransactionSynchronization.afterCommit` callback to decouple REST latency and eliminate phantom rows. Design hardening, not a bug.

### M9 — `USER_UPDATED` is NOT a missing central branch (premise corrected)
`USER_UPDATED` is a **central→local** event (`UserService.java:120` → central outbox → `UserReplicationSchedulerService:42-43,138-141`), NEVER a **local→central** sync event. It does NOT reach `SyncEventProcessor.processEvent`'s unknown-event branch (`:159-163`). `EventTypeContractTest.java:41-44` documents this explicitly. **No action required.** Adding a no-op `else if ("USER_UPDATED".equals(...))` branch solely to silence a hypothetical future WARN is a style choice — today there is no WARN to silence.

### M10 — `GET /api/statistics` does NOT surface `total_aborted_sessions` (NEW gap)
**Confirmed.** `StatisticsAggregationService.toDto` (`:45-54`) builds `new StatisticsDto(buildingId, gameType, periodStart, periodEnd, totalSessions, avgDuration, totalReservations, jsonData)` — it DROPS `totalAbortedSessions` (the domain getter `AggregatedStatistics.getTotalAbortedSessions()` exists at `:195-197` but is never read). `StatisticsDto` (`shared/shared-dto/.../StatisticsDto.java:5-14`) has no `totalAbortedSessions` component. So the column is persisted (`init.sql:46`), written by `updateAbortedStats` (`SyncEventProcessor.java:264-299`), but invisible via the audit endpoint.

**Fix:** extend `StatisticsDto` with `Integer totalAbortedSessions`; extend `StatisticsAggregationService.toDto` to pass `stats.getTotalAbortedSessions()`; extend `StatisticsControllerTest` accordingly. This is an existing instance of the same service→DTO drift class that any future field (e.g. S2's `missing_duration_count`) must avoid.

### M11 — `aggregated_statistics.data` JSON column is dead storage
**Confirmed.** Every producer in `SyncEventProcessor` constructs the delta with `new java.util.HashMap<>()` as the `data` argument (`:237,251,280,295,337`) — always `{}` in production. No drift (round-trip symmetric via `StatisticsMapper`), but the column is unused. **Recommendation:** if S2's `missing_duration_count` is added, make it a first-class `INT` column (mirroring `total_aborted_sessions`), NOT a `data` JSON entry — otherwise `StatisticsMapper`, `AggregatedStatistics`, `StatisticsAggregationService.toDto`, AND `StatisticsDto` all need extending in lockstep or they drift apart (exactly the M10 drift).

### M12 — No per-server health dashboard (Area 5 missing)
**Confirmed.** No `GET /internal/servers` or admin endpoint. `StatisticsController` exposes only `GET /api/statistics` (ADMIN + JWT). `InternalApiKeyFilter`-protected `/internal/**` has only `POST /internal/sync/receive` and `POST /internal/servers/register`.

**Proposed:**
- **CREATE** `central-system/.../infrastructure/adapters/in/rest/AdminServerController.java` — `GET /internal/servers` (protected by `InternalApiKeyFilter`); returns `List<ServerHealthDto>` (`buildingId, baseUrl, lastSeenAt, isActive, pendingReplicationCount`).
- **EDIT** `LocalServerRegistryPort.java` — add `List<RegisteredLocalServer> findAll()`.
- **EDIT** `LocalServerRepositoryAdapter.java` + `LocalServerJpaRepository.java` — implement `findAll` / `findAllByOrderByLastSeenAtDesc`.
- **EDIT** `ReplicationProgressRepository` / `OutboxEventJpaRepository` — add a `countPendingReplicationForServer(serverId)` query.

### M13 — No scheduled cleanup / deactivation on long absence (Area 5 missing)
**Confirmed.** `LocalServerRegistryPort` has `getActiveLocalServers`, `register`, `updateLastSeenAt` — **no deactivation method**. `RegisteredLocalServer.setActive(false)` has NO caller in production. `is_active` is ALWAYS TRUE for any building that ever registered — dead buildings keep getting pushed every 5 min, `allSucceeded=false` keeps events PENDING forever.

**Proposed:**
- **CREATE** `central-system/.../application/service/LocalServerHealthMonitorService.java` — `@Scheduled(fixedDelay=...) void monitor()`; loads `findAll()`, flips `is_active=false` for `last_seen_at < now − threshold` (configurable `app.health.server-stale-threshold-ms`, default 900000 = 15 min, > 2× `SYNC_INTERVAL_MS=300000`).
- **EDIT** `LocalServerRegistryPort.java` — add `void deactivate(BuildingId)`.
- **EDIT** `LocalServerRepositoryAdapter.java` — implement `deactivate`.
- **EDIT** `application.yml` — `app.health.server-stale-threshold-ms: ${SERVER_STALE_THRESHOLD_MS:900000}`.
- **Behavioral consequence:** once deactivated, `getActiveLocalServers()` no longer returns the building → `replicateUsers` stops pushing to it → events for that building's users stay PENDING (other active buildings still receive them via per-server progress). Re-registration (`POST /internal/servers/register`) re-activates and triggers the R1 catch-up.

### M14 — `LocalServerRestAdapter.pushUsers` retry/timeouts (B14 verification)
**Verified DONE.** `LocalServerRestAdapter.java:52-58` builds `RetryTemplate.builder().maxAttempts(3).exponentialBackoff(100, 2.0, 10000).retryOn(TransientPushException.class).build()`; timeouts `connect-timeout-ms:5000` / `read-timeout-ms:5000` from `application.yml:33-34` (`:35-39`). Total time bounded at ≈ 30s per server. `SimpleClientHttpRequestFactory` (`:37`) is not connection-pooled but is bounded by the read timeout; do not switch to `HttpComponentsClientHttpRequestFactory` without re-verifying cert truststore wiring.

---

## Execution Order (dependency-respecting)

1. **R4** (DTO extension: `email` + `occurredAt`) — prerequisite for R2; backward-compatible on the wire.
2. **R2** (local ordering guard: `event_time`/`updated_at`/`version` + per-dto loop) — DEPENDS on R4; requires local DB migration FIRST.
3. **R3** (SessionAbortHelper, `REQUIRES_NEW` per-game) — independent.
4. **S1** (producer guard `if (!wasAborted)`) — independent, 3 lines.
5. **S2** (`extractDuration` returns `Optional<Integer>`, always-log) — independent.
6. **S3 / C-R1** (adapter `saveAndFlush` + retry-on-DIVE in the three `update*Stats`) — independent; portability-tested on H2.
7. **C-R5** (split try, narrow DIVE catch on `replication_progress.save`) — PREREQUISITE for C-R4.
8. **C-R4** (`ThreadPoolTaskScheduler` + `replicationPushExecutor` + parallel inner loop) — depends on C-R5.
9. **R1** (catch-up replays SENT+PENDING, writes progress per-event, per-event push) — independent but should land with M3.
10. **M3** (per-user ACK contract: `UserSyncAckDto`, `InternalSyncController` returns acks, `UserSyncService` per-user try/catch, `LocalServerRestAdapter.pushUsers` returns acks) — closes R1's per-batch poison gap and M5.
11. **M10** (`StatisticsDto` + `toDto` surface `totalAbortedSessions`) — independent.
12. **M12** (`GET /internal/servers` admin endpoint) — independent.
13. **M13** (`LocalServerHealthMonitorService` + `deactivate`) — depends on C-R4 (runs on the new scheduler pool).
14. **M4** (reconciliation job) — depends on R1 + R4 (uses catch-up style; backfills emails).
15. **Schema asymmetry** (`payload JSON` centrally) — independent; requires pre-migration `JSON_VALID` audit.
16. **C-R6** (louder log + doc, Option 1) — independent.
17. **M7** (`fixedDelayString` on central `replicateUsers`) — one line.
18. **M8** (catch-up moved to `afterCommit`) — design hardening, after R1.
19. **Empirical gap** (`docker-compose.multi.yml` + `MultiBuildingEndToEndIT`) — after R1, S3, C-R4, C-R5 land; validates the whole multi-building story.

Each phase ends with `mvn test` green + `docker compose -f docker-compose.multi.yml up` smoke before the next. Schema migrations (`init.sql` edits + `ALTER` scripts) MUST land before the corresponding JAR deploy (`ddl-auto: validate` on both sides).

---

## Acceptance Criteria

- R1: `LateRegistrationCatchUpReplaysPendingAndRecordsProgressTest` + `LateRegistrationCatchUpProgressPersistenceIT` green; new building's `replication_progress` rows count == pushed events.
- R2: `UserSyncServiceOrderingGuardTest` + `UserRepositoryAdapterOrderingGuardIT` green; stale event skipped with WARN; newer event applied with `version` bump.
- R3: `HealthCheckServiceOutboxAtomicityTest` + `SessionAbortHelperIT` green; outbox-save failure rolls back the abort.
- R4: `UserSyncDtoSerializationTest` + `UserServiceDtoPopulationTest` green; `occurredAt` monotonic across `USER_REGISTERED` → `USER_UPDATED`.
- S1: `GameSessionLateEndDoesNotEmitCompletedOutboxEventTest` green; ABORTED-then-end() emits NO `GAME_SESSION_COMPLETED` outbox.
- S2: `SyncEventExtractDurationNullAndNonNumericLogTest` green; `{"durationSeconds": null}` logs WARN and uses 0.
- S3/C-R1: `AggregatedStatisticsFirstBucketInsertRaceConcurrencyIT` green; two concurrent first-bucket threads → `total_sessions==2`, both eventIds in `processed_events`, no exception.
- C-R4: `SchedulerConfigTest` + `replicateUsers_pushesToAllServersInParallelAndDoesNotBlockOnSlowServer` green.
- C-R5: `replicateUsers_treatsDuplicateProgressAsSuccessAndStillMarksSent` green.
- C-R6: doc + louder log; test green.
- Empirical: `MultiBuildingEndToEndIT` four scenarios green; `docker-compose.multi.yml` smoke run clean for 15 min (no ERROR/WARN, outbox drains, stats per building distinct).
- M3: per-user ACK contract; poison user does not block the batch.
- M4: reconciliation job detects mismatch and re-pushes.
- M10: `GET /api/statistics` returns `totalAbortedSessions`.
- M12: `GET /internal/servers` returns per-server health.
- M13: dead building deactivated after 15 min; re-registration re-activates with catch-up.

No file was modified during this plan.