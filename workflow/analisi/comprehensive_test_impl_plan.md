I have all the context I need. Here is the comprehensive plan.

---

# Plan: 22 End-to-End Integration Tests for the 3 Systems Working Together

## Decisions locked in (from your answers + the "tests only" constraint)

| Decision | Choice |
|---|---|
| Harness | **Triple-context in-process**: real central + real local Spring contexts + headless client emulator (no JavaFX) + embedded Moquette MQTT broker, all in one JVM. H2 (MODE=MySQL) per side. WireMock only for outbound REST where a real peer isn't available. |
| Pre-work | I'll choose **option 3** ("only add local-server H2 infra; accept the central quirk"). Reason: you said no non-test edits. So I add **test-only** infrastructure (test yml, base classes, Moquette wrapper, headless client wrapper, JSON-unwrap shim) **inside the new e2e module**. Zero production-file edits. |
| Split | **8 tests with the client emulator (MQTT path)** + **14 tests local↔central REST/DB only** = **22 tests total**. |
| Location | New Maven module **`gamehandler-platform/e2e-tests/`** depending on central-system, local-server, game-client-emulator, shared-*, plus Moquette, WireMock, H2, Awaitility, JUnit5, Mockito, ArchUnit. |

## Module layout (all NEW files, no edits to existing sources)

```
gamehandler-platform/e2e-tests/
├── pom.xml                                   # new module, parent = gamehandler-platform
└── src/test/
    ├── java/com/gameplatform/e2e/
    │   ├── harness/
    │   │   ├── DualContextTestBase.java       # boots central + local (no Moquette, no client)
    │   │   ├── TripleContextTestBase.java     # extends DualContext + Moquette + TestClientEmulator
    │   │   ├── MoquetteBroker.java            # test-only embedded broker wrapper
    │   │   ├── TestClientEmulator.java        # headless client (uses prod MqttClientAdapter + SessionPublisher etc., no JavaFX)
    │   │   ├── CentralContextLauncher.java    # SpringApplication.run(CentralSystemApplication) with test profile
    │   │   ├── LocalContextLauncher.java      # SpringApplicationBuilder for LocalServerApplication, wires central URL
    │   │   ├── CleanPayloadOutbox.java        # test-only @Primary OutboxEventRepository shim (port from MultiBuildingEndToEndIT)
    │   │   └── H2SchemaNameInterceptor.java   # ensures central/local use distinct H2 schema names
    │   ├── localcentral/                      # 14 dual-context tests (B1..B14)
    │   │   └── ... (see catalog below)
    │   └── fullstack/                         # 8 triple-context tests (A1..A8)
    │       └── ... (see catalog below)
    └── resources/
        ├── application-e2e-central.yml        # central test profile (H2, schedulers off, TLS off)
        ├── application-e2e-local.yml          # local test profile (H2, schedulers off, MQTT → Moquette URL)
        └── application-e2e-client.properties  # client emulator config (broker URL → Moquette, building-id)
```

## Test harness design (key points)

1. **Two real Spring contexts in one JVM.** `CentralContextLauncher` calls `SpringApplication.run(CentralSystemApplication.class, args)` with `--spring.profiles.active=e2e-central`. After it's up, read the actual port from `WebServerInitializedEvent`. `LocalContextLauncher` then runs `new SpringApplicationBuilder(LocalServerApplication.class).profiles("e2e-local").properties("app.central-system-url=http://localhost:" + centralPort, "app.local-base-url=http://localhost:" + localPort).run()`. Each gets its own H2 schema (`jdbc:h2:mem:central_e2e` and `jdbc:h2:mem:local_e2e`) so they don't collide.
2. **Schedulers disabled.** `app.sync-interval-ms=999999999`, `app.healthcheck-interval-ms=999999999`, `app.health.monitor-interval-ms=999999999`, `app.reconciliation.interval-ms=999999999`, `app.dlq-promotion-interval-ms=999999999`, `app.outbox-purge-interval-ms=999999999` in both yml files — every test triggers the relevant service method directly. No flakiness from background jobs.
3. **Awaitility** for all async assertions (HTTP round-trips, MQTT delivery, scheduler completion). No `Thread.sleep`.
4. **Central H2 JSON quirk**: apply the `CleanPayloadOutbox` `@Primary` shim (already proven in `MultiBuildingEndToEndIT`) — port it verbatim into the e2e module. No production code touched.
5. **Moquette** (`io.moquette:moquette-broker:0.15` test scope) starts on a random port before local + client boots; both `app.mqtt.broker-url` (local) and the client's broker URL point at it. TLS off (plain `tcp://`).
6. **Headless client emulator**: `TestClientEmulator` directly instantiates the production `MqttClientAdapter`, `SessionPublisher`, `HeartbeatPublisher`, `GameStatePublisher` against the Moquette broker. No JavaFX Stage launched. Multiple instances supported (for lobby tests).
7. **WireMock** for the cases where a *second* building's REST endpoint is needed but we don't want to boot a second full local-server context (cheaper) — multi-building tests use WireMock stubs for building-2/3, exactly like the existing `MultiBuildingTestBase`.
8. **Per-test table cleanup**: `@BeforeEach` wipes central tables (`users`, `outbox_events`, `processed_events`, `aggregated_statistics`, `local_servers`, `replication_progress`) and local tables (`users`, `outbox_events`, `game_sessions`, `games`, `reservations`, `local_signup_users`).

---

## Test catalog — 22 tests

### Category B — Dual-context (central + local, no client, no MQTT) — 14 tests

#### B1. `UserSignupDisconnectedThenConnectPropagatesToCentralIT` (the exact scenario you described)
- **Setup**: WireMock stub for central `POST /internal/sync/receive` returns **503** (central "down"). Boot central-context-only H2 (no real central HTTP needed — we drive `ReceiveSyncDataUseCase` directly when central is "up"). Actually simpler: boot both contexts; force `CentralSystemRestAdapter.isReachable()` to return false by stubbing the central probe endpoint to 503.
- **Action**: `POST /api/auth/signup` on local with `{alice, pw, alice@x.com}` → local `LocalSignupService` writes user + PENDING `USER_REGISTERED` outbox row. Assert local `users` has alice, local `outbox_events` has 1 PENDING, central `users` does NOT have alice. Then flip WireMock to **200**, call `syncSchedulerService.syncWithCentral()` on local.
- **Assert**: local outbox row → SENT; central `users` has alice (count=1); `processed_events` has the eventId.

#### B2. `CentralRegisteredUserReplicatedToLocalIT`
- **Setup**: Boot dual-context. Local registered at central.
- **Action**: Call `userService.register("bob","pw","b@x.com")` on central → central outbox `USER_REGISTERED` PENDING. Trigger `userReplicationSchedulerService.replicateUsers()`.
- **Assert**: local `users` table has bob (inbound `PUT /internal/users/sync` hit the real local `InternalSyncController`); central `replication_progress` has 1 row `(eventId, building-1)`; central outbox event is SENT.

#### B3. `UserSignupRetryOnCentralDownDoesNotDuplicateIT`
- **Setup**: Boot dual-context. Central probe returns 503.
- **Action**: Local signup alice → outbox PENDING. Trigger `syncWithCentral()` → fails → retryCount=1, status stays PENDING. Bring central up (flip WireMock). Trigger `syncWithCentral()` again → success.
- **Assert**: central `users` has exactly 1 alice; `processed_events` has exactly 1 row for the eventId; local outbox SENT. Even though the same eventId was offered twice, no duplicate.

#### B4. `LateRegistrationCatchUpReplaysExistingUsersToNewBuildingIT`
- **Setup**: Central H2 has 2 existing users (seeded via `userService.register`). Building-1 is registered. A *second* building (building-2) is represented by a WireMock stub (its `PUT /internal/users/sync` returns the M3 ack).
- **Action**: Register building-2 via `localServerRegistryPort.register(...)` → central's M8 `afterCommit` catch-up runs.
- **Assert**: building-2's WireMock stub received **2** `PUT /internal/users/sync` calls (one per existing user); `replication_progress` has 2 rows for building-2; existing building-1 unaffected.

#### B5. `LateRegistrationCatchUpDoesNotReduplicateAlreadyReplicatedUserIT`
- **Setup**: Central has 1 user; building-1 already has the `replication_progress` row for that user's eventId (pre-seeded via JDBC).
- **Action**: Re-register building-1 (simulating a restart) → M8 catch-up runs.
- **Assert**: building-1's WireMock stub receives **0** PUT calls for the already-replicated eventId (catch-up checks `existsByEventIdAndServerId` before pushing); `replication_progress` row count unchanged.

#### B6. `ReconciliationRePushesWhenLocalCountDriftsIT`
- **Setup**: Central `users` has 5 rows (seeded). WireMock `GET /internal/users/count` for building-1 returns **3** (drift).
- **Action**: Trigger `userReplicationReconciliationService.reconcile()`.
- **Assert**: building-1's WireMock stub receives 1 `PUT /internal/users/sync` with a 5-user batch; WARN log captured (`Reconciliation mismatch ... centralCount=5 ... localCount=3 ... re-pushed 5`).

#### B7. `CentralIdempotencySameEventIdReReceiveIT`
- **Setup**: Boot dual-context.
- **Action**: Local pushes `USER_REGISTERED` eventId X to central → central processes (user inserted, `processed_events(X)` saved). Simulate a local retry by re-POSTing the same batch (same eventId X) to central's `POST /internal/sync/receive`.
- **Assert**: central `users` count unchanged (still 1 alice); `processed_events` still has exactly 1 row for X; WARN/DEBUG log shows dedup.

#### B8. `PoisonEventInBatchDoesNotAbortWholeBatchIT`
- **Setup**: Boot dual-context.
- **Action**: Construct a `SyncPayloadDto` batch of 3 events: 2 valid `GAME_SESSION_COMPLETED` (different eventIds, CHESS and DARTS) + 1 with malformed JSON payload (poison eventId). Push to central `receiveSyncDataUseCase.receiveSyncPayload(batch)`.
- **Assert**: central `aggregated_statistics` has 1 row for CHESS (total_sessions=1) and 1 row for DARTS (total_sessions=1); `processed_events` has all 3 eventIds (poison marked processed with FAILURE/reason); no batch rollback.

#### B9. `MultiBuildingReplicationNoSourceSkipIT`
- **Setup**: 3 WireMock stubs for building-1/2/3 `PUT /internal/users/sync` (M3 ack). All 3 registered at central.
- **Action**: Seed a PENDING `USER_REGISTERED` outbox row (alice). Trigger `replicateUsers()`.
- **Assert**: all 3 WireMock stubs received 1 PUT each (scheduler pushes to all active — no source-skip, matching the actual scheduler semantics documented in `MultiBuildingEndToEndIT`); `replication_progress` has 3 rows; outbox event SENT.

#### B10. `HealthMonitorDeactivatesStaleBuildingStopsReplicationIT`
- **Setup**: 2 buildings registered at central. building-1's `last_seen_at` set to `now - 30 min` via JDBC; building-2's is fresh.
- **Action**: Trigger `localServerHealthMonitorService.monitor()` → building-1 `is_active` flips to false. Seed PENDING `USER_REGISTERED` outbox; trigger `replicateUsers()`.
- **Assert**: only building-2's WireMock stub received the PUT; `replication_progress` has 1 row; outbox SENT.

#### B11. `OutboxDlqPromotionAfterMaxRetriesIT`
- **Setup**: Boot dual-context. Local outbox event seeded with `retry_count=9` (one shy of the 10-retry threshold). Central probe returns 503.
- **Action**: Trigger `syncWithCentral()` → fails → `retry_count` becomes 10 → `OutboxDlqPromotionService` moves the row to `outbox_dead_letter` and deletes from `outbox_events`.
- **Assert**: local `outbox_events` PENDING count decreased by 1; local `outbox_dead_letter` has 1 row with the original payload + reason.

#### B12. `LocalServerAutoRegistrationOnStartupIT`
- **Setup**: Boot dual-context with `app.building-id=building-auto-x` and `app.local-base-url=http://localhost:<localPort>`. Local's `LocalServerRegistrationService` is wired to retry-register against the real central `POST /internal/servers/register`.
- **Action**: Just boot both contexts.
- **Assert** (after startup completes): central `local_servers` has `building-auto-x` with `is_active=true`, `base_url` matching the local port; the central `register()` was idempotent (only 1 row even after retries).

#### B13. `CentralUserUpdateReplicatesRoleReplacementToLocalIT`
- **Setup**: Boot dual-context. Pre-seed: central has user `carol` with roles `["PLAYER"]`; local has `carol` with `["PLAYER"]` (already replicated). Central outbox `USER_UPDATED` event with new roles `["OPERATOR"]`.
- **Action**: Trigger `replicateUsers()` on central.
- **Assert**: local `users` row for carol has roles `["OPERATOR"]` (full replacement, not additive merge — verifies the B10 fix from the bug plan).

#### B14. `StatsAggregationDistinctPerBuildingGamePeriodIT`
- **Setup**: 2 buildings registered. Central `receiveSyncDataUseCase` reachable.
- **Action**: Building-1 sends `GAME_SESSION_COMPLETED` for CHESS and FOOSBALL (same UTC day). Building-2 sends CHESS only.
- **Assert**: central `aggregated_statistics` has exactly 3 rows `(building-1,CHESS), (building-1,FOOSBALL), (building-2,CHESS)`; each `total_sessions=1`; no cross-pollution rows.

---

### Category A — Triple-context (central + local + client emulator + Moquette) — 8 tests

#### A1. `ClientHeartbeatKeepsBuildingActiveIT`
- **Setup**: Triple-context. Central test yml overrides `app.health.server-stale-threshold-ms=3000`. Client emulator instance publishes heartbeat on `building/building-1/game/g-1/heartbeat` every 500ms.
- **Action**: Wait 8s (Awaitility). Then stop client heartbeats. Wait another 8s.
- **Assert**: during the heartbeat phase, central `local_servers.is_active=true` and `last_seen_at` kept advancing. After heartbeats stop, `monitor()` (triggered manually at the end) deactivates the building — proving the heartbeats were what was keeping it alive.

#### A2. `ClientSessionStartEndFlowsToCentralAggregatedStatisticsIT`
- **Setup**: Triple-context.
- **Action**: `TestClientEmulator.sessionPublisher.publishStart("g-1","sess-1",CHESS,[u-1])` → local `GameSessionListener` creates IN_PROGRESS session → `publishEnd("g-1","sess-1",u-1,WIN,"{}")` → local `GameSessionService.end()` emits `GAME_SESSION_COMPLETED` outbox → local scheduler pushes to central.
- **Assert**: local `game_sessions` has sess-1 with status=COMPLETED; central `aggregated_statistics.total_sessions=1` for `building-1/CHESS`. Full chain client→MQTT→local DB→HTTP→central DB.

#### A3. `ClientSessionTimeoutAbortsAndCentralCountsAbortedOnlyIT`
- **Setup**: Triple-context. Central test yml `app.health.server-stale-threshold-ms=2000`; local `app.healthcheck-interval-ms` overridden to 1000 (so we don't wait 15min).
- **Action**: Client `publishStart("g-2","sess-2",DARTS,[u-1])`. Client stops sending heartbeats. Local `HealthCheckService.performHealthCheck()` detects 3 missed → emits `GAME_SESSION_ABORTED` (stopReason=TIMEOUT) outbox → sync to central.
- **Assert**: local `game_sessions` sess-2 status=ABORTED; central `aggregated_statistics.total_aborted_sessions=1` AND `total_sessions=0` for `building-1/DARTS`.

#### A4. `ClientPauseAndResumeSessionFlowsEndToEndIT`
- **Setup**: Triple-context.
- **Action**: Client `publishStart` → `publishPause("g-3","sess-3",u-1)` → `publishResume("g-3","sess-3")` → `publishEnd("g-3","sess-3",u-1,WIN,"{}")`.
- **Assert**: local `game_sessions` sess-3 transitions IN_PROGRESS→PAUSED→IN_PROGRESS→COMPLETED (verify via querying status history or final status + a single row); central `aggregated_statistics.total_sessions=1` for the game type.

#### A5. `ClientLobbyCreateJoinStartMultiplayerSessionIT`
- **Setup**: Triple-context with **2** `TestClientEmulator` instances (alice, bob) on the same Moquette broker.
- **Action**: alice `publishLobbyCreate("g-4",FOOSBALL,"alice")` → bob `publishLobbyJoin("g-4","sess-4","bob")` → alice `publishLobbyStart("g-4","sess-4")` → local `MultiplayerLobbyService` creates a `GameSession` with both participants → alice `publishEnd(...)`.
- **Assert**: local `game_sessions` sess-4 has 2 participants in `session_participants`; central `aggregated_statistics.total_sessions=1` for `building-1/FOOSBALL`.

#### A6. `ClientGameStateChangeUpdatesLocalGameMachineStatusIT`
- **Setup**: Triple-context. Pre-seed local `games` table with game `g-5` (status=AVAILABLE).
- **Action**: Client `gameStatePublisher.publishState("g-5",IN_USE,"u-1)` on `building/building-1/game/g-5/state`.
- **Assert**: local `games` row for g-5 has status=IN_USE (proves MQTT `GameStateListener` → `GameStateService.updateState` → DB). Central unchanged (game state is local-only — assert central has no `games` table equivalent).

#### A7. `ClientHeartbeatMissedTriggersAlertAndAbortsSessionIT`
- **Setup**: Triple-context. Client subscribes to `building/building-1/alerts` and captures via a `MqttCallback` in the test harness. Local `app.healthcheck-interval-ms=1000`, missed-threshold=3.
- **Action**: Client `publishStart("g-6","sess-6",CHESS,[u-1])`, sends 2 heartbeats, then stops. Wait for local `HealthCheckService.performHealthCheck()` to detect 3 missed → publish `AlertPayload` to `building/building-1/alerts` AND emit `GAME_SESSION_ABORTED` (stopReason=TIMEOUT) → sync to central.
- **Assert**: client's alert callback received an `AlertPayload` with `alertType=HEARTBEAT_TIMEOUT` and `gameId=g-6`; local `game_sessions` sess-6 status=ABORTED; central `aggregated_statistics.total_aborted_sessions=1` for CHESS.

#### A8. `ClientDisconnectedThenReconnectsSessionRecoveryIT`
- **Setup**: Triple-context.
- **Action**: Client `publishStart("g-7","sess-7",CHESS,[u-1])`. Simulate client crash: call `testClient.adapter.disconnect()`. Trigger local `SessionRecoveryService.start()` (simulating server restart recovery) → existing IN_PROGRESS sessions are aborted with stopReason=SERVER_RESTART → outbox `GAME_SESSION_ABORTED` → sync to central. Reconnect the client (`adapter.connect()`), start a new session sess-8, end it normally.
- **Assert**: central `aggregated_statistics` for `building-1/CHESS` has `total_aborted_sessions=1` (sess-7) and `total_sessions=1` (sess-8).

---

## Implementation order (suggested)

1. **Module skeleton** — `e2e-tests/pom.xml`, parent pom registration, dependency set (Moquette, WireMock, H2, Awaitility, JUnit5, Mockito, ArchUnit, spring-boot-starter-test). Verify `mvn -pl e2e-tests validate`.
2. **Harness: `DualContextTestBase`** — port `CleanPayloadOutbox` shim, central + local launchers, H2 schema isolation, scheduler-disable yml files. Smoke test: a trivial `@Test` that boots both contexts and asserts `central.users` table exists and `local.users` table exists.
3. **B12** (auto-registration) first — it validates the harness itself.
4. **B1, B2** — the core user-signup-disconnected and replication-back scenarios (the user's primary ask).
5. **B3, B7, B8** — idempotency/poison cluster.
6. **B4, B5, B6, B10** — late registration + reconciliation + health monitor.
7. **B9, B14** — multi-building isolation.
8. **B11, B13** — DLQ + role update.
9. **Harness: `MoquetteBroker` + `TestClientEmulator`** — extends `DualContextTestBase` to `TripleContextTestBase`. Smoke test: client connects to broker, local `HeartbeatListener` registers the subscription.
10. **A2, A3** — basic session flows (start/end, timeout abort).
11. **A1, A7** — heartbeat + alert flows.
12. **A4, A5** — pause/resume + lobby multiplayer.
13. **A6, A8** — game state + recovery.

## Risks / open issues to flag

- **Bean name collisions** when booting two `@SpringBootApplication` in one JVM. Mitigation: `SpringApplicationBuilder.parent(child)` hierarchy OR distinct `@Configuration` class names per context; if all else fails, fall back to running each in a separate thread with a separate `ContextLoader` (slower but isolated).
- **Local-server has no `@SpringBootTest` base today** — the harness itself is the first one. May surface latent wiring issues (e.g. `MqttConfig` requiring a real broker URL at startup). Mitigation: the Moquette broker is started *before* the local context boots and its URL injected via `@DynamicPropertySource`.
- **Game-client-emulator's `MqttClientAdapter` reads TLS keystores from `certs/`** — in test we use plain `tcp://` Moquette, so the TLS branch is skipped. Confirmed by reading `MqttClientAdapter.connect()` (only branches to TLS if URL starts with `ssl://`).
- **Moquette version compatibility with Paho** — pin to `moquette-broker:0.15` which works with `paho 1.2.x`. If conflicts arise, fall back to an in-process `IMqttAsyncClient` with `MemoryPersistence` and a manually-wired `IMqttMessageListener` (the plan's D4 fallback).
- **Long thresholds (15-min stale)** would make A1/A3 impractical. Mitigation: override the thresholds to 2–3s in the e2e-central yml (test-only).
- **Some tests assert on logs** (B6, B7, B8) — use Logback `ListAppender` (already the project convention; `LogCaptor` is forbidden per the project javadocs).

## Acceptance criteria

- `mvn -pl e2e-tests test` runs all 22 tests, **0 failures, 0 flaky**.
- Every test asserts state in **at least two of the three systems** (DB rows on both sides, or MQTT message received + DB row written).
- No edits to any file outside `e2e-tests/`.
- Existing tests in `central-system`, `local-server`, `shared/*` remain untouched and still pass.

---

**Ready to implement.** Shall I start with step 1 (module skeleton + pom) and step 2 (DualContextTestBase smoke), then pause for you to review the harness before writing the 22 tests?