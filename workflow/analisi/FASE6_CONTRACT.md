# FASE 6 — Structural Contract (Signatures Only)

> **Purpose:** verbatim input for implementation subagents (STEP 3). NO internal logic —
> only signatures, Javadoc-style intent comments, and exact diff-equivalent additions.
> All paths are absolute from
> `C:\Users\VLT14\Documents\UNI\PISSIR\Progetto\gamehandler-platform\`.
>
> **Confirmed facts from the codebase (Read pass):**
> - `WinCondition` enum = `{ WIN, DRAW, ABANDONED, TIMEOUT }` (no `TEAM_VICTORY`, no `VICTORY`) → **add `TEAM_VICTORY`**.
> - `GameResult` interface = `{ UserId getWinnerId(); List<UserId> getWinnerIds(); WinCondition getWinCondition(); }`.
> - `TeamId` = `record TeamId(String value)`; `UserId` = value object (`new UserId(String)`).
> - `MqttPayloadSerializer` mixin has **7** existing `@JsonSubTypes.Type` entries (CHESS…SLOT).
> - `CreateSessionRequestDto` = record of **4** fields `(gameId, gameType, participants, reservationId)`.
> - `TournamentMatchScheduledDto` = record of **12** fields.
> - `TournamentMatchResultDto` = record `(matchId, winner, resultData, status)`.
> - `TournamentMatchDto` = record of **10** fields `(id, round, bracketPosition, participantA, participantB, buildingId, gameId, status, scheduledAt, winner)`.
> - `TournamentMatchStatus` = `{ SCHEDULED, IN_PROGRESS, COMPLETED, ABANDONED, BYE }`.
> - `TournamentMatch` (central domain) = 14-arg immutable ctor; has `buildingId` field already.
> - `Tournament.complete(Instant)` already forward-declared in FASE 4.
> - `LocalGameDefinitionRestAdapter`: ctor `(SSLContext, @Value api-key, @Value connect-timeout, @Value read-timeout)` + package-private test ctor `(RestTemplate, apiKey)`; `PUT /internal/metadata/game-definitions/sync`; `RetryTemplate` 3 attempts.
> - `GameSessionService` ctor = **8** params; `start(GameId, GameType, List<UserId>, ReservationId)`; `end(GameSessionId, GameResult)`.
> - `SessionAbortHelper` ctor = **6** params; `@Component`; `abortAndEmit` `@Transactional(REQUIRES_NEW)`.
> - `SyncEventProcessor`: `@Autowired` 7-arg ctor + package-private 6-arg & 5-arg delegating ctors (nullable-ctor backward-compat pattern).
> - `TournamentBracketService`: `@Service @Transactional`; ctor **6** params; implements `ScheduleTournamentMatchesUseCase, ListTournamentMatchesUseCase`.
> - `TournamentStandingsService`: `@Service @Transactional`; ctor **3** params; package-visible `seedStandings`.
> - `UserReplicationSchedulerService`: ctor **8** params (last = `PushGameDefinitionToLocalServersPort`).
> - `LateRegistrationCatchUpService`: ctor **7** params; `REPLICATION_EVENT_TYPES` list.
> - `GameSession` (local domain): 12-arg ctor (delegates to 13-arg with `version=0`) + 11-arg (no participants) + 13-arg primary (with validation).
> - `GameSessionJpaEntity`: `@Entity @Table(name="game_sessions")`, `@Version` column present.
> - `EventTypeContractTest.EXPECTED_EVENT_TYPES` = 5 literals.
> - `TournamentBuildingRepository.findByTournament(TournamentId)` returns `List<String>` (building ids).
> - All 3 `infrastructure/mysql-local/init*.sql` are byte-identical in structure (166 lines, `game_sessions` + `game_definitions_local` present; NO `tournament_matches_local`).

---

## SECTION 1 — New file inventory table (per module)

### Module 1 — `shared-domain`

| # | Absolute path | Package | Type | Purpose |
|---|---------------|---------|------|---------|
| 1 | `shared-domain/src/main/java/com/gameplatform/shared/domain/result/TeamResult.java` | `com.gameplatform.shared.domain.result` | `record` | `GameResult` variant for team-tournament matches; single-winner simplification (ambiguity F/G). |

### Module 4 — `central-system`

| # | Absolute path | Package | Type | Purpose |
|---|---------------|---------|------|---------|
| 2 | `central-system/src/main/java/com/gameplatform/central/domain/ports/out/PushTournamentMatchToLocalServersPort.java` | `com.gameplatform.central.domain.ports.out` | `interface` | Out-port to push `TournamentMatchScheduledDto` batch to a single local server's `/internal/tournaments/matches/sync`. |
| 3 | `central-system/src/main/java/com/gameplatform/central/infrastructure/adapters/out/rest/LocalTournamentMatchRestAdapter.java` | `com.gameplatform.central.infrastructure.adapters.out.rest` | `@Component class` | REST adapter implementing port #2; structural twin of `LocalGameDefinitionRestAdapter`. |

> **NOTE on item #3 of STEP 1 (CompleteTournamentUseCase):** Per the RECOMMENDED option, **skip** the in-port. `TournamentBracketService.completeIfDone` (a concrete `@Service` method) is called directly from `SyncEventProcessor`. **No new file** for `CompleteTournamentUseCase`.

### Module 5 — `local-server`

| # | Absolute path | Package | Type | Purpose |
|---|---------------|---------|------|---------|
| 4 | `local-server/src/main/java/com/gameplatform/local/domain/model/TournamentMatchLocal.java` | `com.gameplatform.local.domain.model` | `class` (POJO) | Read-only replica of a tournament match destined for THIS building (PIANO §3.4). |
| 5 | `local-server/src/main/java/com/gameplatform/local/domain/ports/out/TournamentMatchLocalRepository.java` | `com.gameplatform.local.domain.ports.out` | `interface` | Out-port for `tournament_matches_local` persistence. |
| 6 | `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/entity/TournamentMatchLocalJpaEntity.java` | `com.gameplatform.local.infrastructure.adapters.out.mysql.entity` | `@Entity class` | JPA entity for `tournament_matches_local`. |
| 7 | `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/repository/TournamentMatchLocalJpaRepository.java` | `com.gameplatform.local.infrastructure.adapters.out.mysql.repository` | `@Repository interface` | Spring Data JPA repo. |
| 8 | `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/mapper/TournamentMatchLocalMapper.java` | `com.gameplatform.local.infrastructure.adapters.out.mysql.mapper` | `@Component class` | Domain↔entity mapper. |
| 9 | `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/adapter/TournamentMatchLocalRepositoryAdapter.java` | `com.gameplatform.local.infrastructure.adapters.out.mysql.adapter` | `@Component class` | Adapter implementing port #5; upsert-by-PK (idempotent). |
| 10 | `local-server/src/main/java/com/gameplatform/local/application/service/TournamentMatchLocalSyncService.java` | `com.gameplatform.local.application.service` | `@Service class` | Applies replicated `TournamentMatchScheduledDto` events idempotently. Mirror of `GameDefinitionSyncService`. |
| 11 | `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/in/rest/InternalTournamentController.java` | `com.gameplatform.local.infrastructure.adapters.in.rest` | `@RestController class` | `PUT /internal/tournaments/matches/sync` (secured by `InternalApiKeyFilter`). |
| 12 | `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/in/rest/PlayerTournamentController.java` | `com.gameplatform.local.infrastructure.adapters.in.rest` | `@RestController class` | `GET /api/players/tournaments/me/matches` + `POST /api/players/tournaments/matches/{matchId}/start`. |
| 13 | `local-server/src/main/java/com/gameplatform/local/domain/exception/TournamentMatchNotFoundException.java` | `com.gameplatform.local.domain.exception` | `class` (extends `RuntimeException`) | 404. |
| 14 | `local-server/src/main/java/com/gameplatform/local/domain/exception/TournamentMatchNotScheduledException.java` | `com.gameplatform.local.domain.exception` | `class` (extends `RuntimeException`) | 409. |
| 15 | `local-server/src/main/java/com/gameplatform/local/domain/exception/TournamentMatchBuildingMismatchException.java` | `com.gameplatform.local.domain.exception` | `class` (extends `RuntimeException`) | 403 (kept for future; Local trusts routing per ambiguity O). |
| 16 | `local-server/src/main/java/com/gameplatform/local/domain/exception/TournamentMatchValidationException.java` | `com.gameplatform.local.domain.exception` | `class` (extends `RuntimeException`) | 400 (team_allowed mismatch / participant mismatch). |

### Module 5 — Test files (new)

| # | Absolute path | Type | Purpose |
|---|---------------|------|---------|
| 17 | `local-server/src/test/java/com/gameplatform/local/application/service/TournamentMatchLocalSyncServiceTest.java` | unit slice | Idempotent upsert of `applyEvents`. |
| 18 | `local-server/src/test/java/com/gameplatform/local/infrastructure/adapters/in/rest/InternalTournamentControllerTest.java` | slice | Delegation of `/sync`. |
| 19 | `local-server/src/test/java/com/gameplatform/local/infrastructure/adapters/in/rest/PlayerTournamentControllerTest.java` | slice | `/me/matches` + `/matches/{id}/start` happy & error paths. |
| 20 | `local-server/src/test/java/com/gameplatform/local/application/service/GameSessionServiceTournamentTest.java` | slice | `start`+`end` with `tournamentMatchId`. |
| 21 | `local-server/src/test/java/com/gameplatform/local/application/service/SessionAbortHelperTournamentTest.java` | slice | abort with `tournamentMatchId` → `TOURNAMENT_MATCH_COMPLETED` ABANDONED. |

### Module 4 — Test files (new)

| # | Absolute path | Type | Purpose |
|---|---------------|------|---------|
| 22 | `central-system/src/test/java/com/gameplatform/central/application/service/TournamentFlowEndToEndIT.java` | `@SpringBootTest` IT (H2) | schedule → 4× `TOURNAMENT_MATCH_COMPLETED` → `advanceWinner` → `completeIfDone` → standings+rank. |

**New-file total: 22 (1 shared-domain + 2 central-system main + 13 local-server main + 5 local tests + 1 central IT).**

---

## SECTION 2 — Modified file inventory table (per module)

### Module 1 — `shared-domain`

| # | Absolute path | What is added | Backward-compat note |
|---|---------------|---------------|----------------------|
| M1 | `shared-domain/src/main/java/com/gameplatform/shared/domain/model/WinCondition.java` | new enum constant `TEAM_VICTORY` | Additive; existing constants untouched. |

### Module 2 — `shared-mqtt`

| # | Absolute path | What is added | Backward-compat note |
|---|---------------|---------------|----------------------|
| M2 | `shared-mqtt/src/main/java/com/gameplatform/shared/mqtt/MqttPayloadSerializer.java` | 8th `@JsonSubTypes.Type(value = TeamResult.class, name = "TEAM")` inside existing `@JsonSubTypes` block | Additive; existing 7 entries stay. |

### Module 3 — `shared-dto`

| # | Absolute path | What is added | Backward-compat note |
|---|---------------|---------------|----------------------|
| M3 | `shared-dto/src/main/java/com/gameplatform/shared/dto/CreateSessionRequestDto.java` | 5th record component `String tournamentMatchId` (LAST; optional) | Additive optional field; existing 4 stay in identical order. |
| M4 | `shared-dto/src/main/java/com/gameplatform/shared/dto/TournamentMatchScheduledDto.java` | 13th record component `String buildingId` (LAST, after `scheduledAt`) | Additive; the FASE 5 `TournamentMatchOutboxAdapter` MUST be updated (M11) to pass `buildingId=null`. |

### Module 4 — `central-system`

| # | Absolute path | What is added | Backward-compat note |
|---|---------------|---------------|----------------------|
| M5 | `central-system/.../application/service/UserReplicationSchedulerService.java` | 9th ctor param `PushTournamentMatchToLocalServersPort`; constant `TOURNAMENT_MATCH_SCHEDULED_EVENT`; `isTournamentMatchEvent` predicate; `isReplicationEvent` extended; new branch `replicateTournamentMatchEvent(...)` | Additive branch; existing user/metadata/game-def branches unchanged. |
| M6 | `central-system/.../application/service/LateRegistrationCatchUpService.java` | 8th ctor param `PushTournamentMatchToLocalServersPort`; `TOURNAMENT_MATCH_SCHEDULED` added to `REPLICATION_EVENT_TYPES`; `isTournamentMatchEvent` predicate; new catch-up branch | Additive branch. |
| M7 | `central-system/.../application/service/SyncEventProcessor.java` | 4 new ctor params (`TournamentBracketService, TournamentStandingsService, TournamentRepository, TournamentMatchRepository`); new `@Autowired` 11-arg ctor; old 7-arg becomes delegating package-private (null for new ports); new `else if ("TOURNAMENT_MATCH_COMPLETED"...)` branch + private `handleTournamentMatchCompleted(...)` | FASE 3 nullable-ctor backward-compat pattern preserved; existing branches unchanged. |
| M8 | `central-system/.../application/service/TournamentBracketService.java` | 2 new public methods `advanceWinner(...)` + `completeIfDone(...)` | No ctor change; new methods only. |
| M9 | `central-system/.../application/service/TournamentStandingsService.java` | 2 new public methods `recomputeAfterCompletion(...)` + `assignFinalRanks(...)` | No ctor change; new methods only. |
| M10 | `central-system/.../domain/ports/out/TournamentRepository.java` + `TournamentJpaRepository.java` + `TournamentRepositoryAdapter.java` | new method `Optional<Tournament> findByIdForUpdate(TournamentId)` (port + JPA `@Lock(PESSIMISTIC_WRITE)` query + adapter) | Additive; existing methods unchanged. |
| M11 | `central-system/.../domain/ports/out/TournamentMatchRepository.java` + `TournamentMatchJpaRepository.java` + `TournamentMatchRepositoryAdapter.java` | new methods `Optional<TournamentMatch> findByIdForUpdate(TournamentMatchId)` AND `Optional<TournamentMatch> findByTournamentIdAndRoundAndBracketPositionForUpdate(TournamentId, int, int)` (port + JPA `@Lock(PESSIMISTIC_WRITE)` queries + adapter) | Additive. |
| M12 | `central-system/.../domain/ports/out/TournamentStandingRepository.java` + `TournamentStandingJpaRepository.java` + `TournamentStandingRepositoryAdapter.java` | new method `List<TournamentStanding> findByTournamentIdForUpdate(TournamentId)` (port + JPA `@Lock(PESSIMISTIC_WRITE)` query + adapter) | Additive. |
| M13 | `central-system/.../infrastructure/adapters/out/mysql/adapter/TournamentMatchOutboxAdapter.java` | `TournamentMatchScheduledDto` ctor call updated to pass 13th arg `buildingId=null` | FASE 4 behaviour preserved (null = assigned later by drain branch). |
| M14 | `central-system/src/test/java/.../application/service/EventTypeContractTest.java` | `"TOURNAMENT_MATCH_COMPLETED"` added to `EXPECTED_EVENT_TYPES` | Additive; test still passes because M7 adds the matching branch. |

### Module 5 — `local-server`

| # | Absolute path | What is added | Backward-compat note |
|---|---------------|---------------|----------------------|
| M15 | `local-server/.../domain/model/GameSession.java` | 2 new `final` fields `tournamentMatchId`, `tournamentId`; new primary 15-arg ctor + 14-arg delegating ctor; old 13/12/11-arg ctors delegate with `null` for the 2 new fields; 2 getters | Old ctors stay callable (backward-compat); new fields default to `null`. |
| M16 | `local-server/.../infrastructure/adapters/out/mysql/entity/GameSessionJpaEntity.java` | 2 new nullable `@Column`s `tournament_match_id`, `tournament_id`; ctor updated (+2 params) | Additive nullable columns. |
| M17 | `local-server/.../infrastructure/adapters/out/mysql/mapper/GameSessionMapper.java` | map the 2 new fields on `toDomain` + `toEntity` | New fields default null for legacy rows. |
| M18 | `local-server/.../application/service/GameSessionService.java` | 9th ctor param `TournamentMatchLocalRepository`; 10th ctor param `@Value("${app.building-id}") String buildingId`; new `start(...)` 5-arg overload; `end(...)` extended to emit `TOURNAMENT_MATCH_COMPLETED` + update local match status | Existing 4-arg `start` kept (delegates with `null`); existing `end` behaviour for non-tournament sessions unchanged. |
| M19 | `local-server/.../application/service/SessionAbortHelper.java` | 7th ctor param `TournamentMatchLocalRepository`; `abortAndEmit` extended to emit `TOURNAMENT_MATCH_COMPLETED` (ABANDONED) + update local match status when `session.getTournamentMatchId() != null` | Non-tournament abort path unchanged (guarded by null check). |
| M20 | `local-server/.../infrastructure/adapters/in/rest/GameSessionController.java` | `start` endpoint extracts `tournamentMatchId` from `CreateSessionRequestDto` and passes `new TournamentMatchId(...)` to extended `start(...)` | `tournamentMatchId==null` → existing 4-arg behaviour. |
| M21 | `local-server/.../infrastructure/adapters/in/rest/GlobalExceptionHandler.java` | 4 new `@ExceptionHandler` methods | Additive. |
| M22 | `infrastructure/mysql-local/init.sql` | `game_sessions` +2 columns & index; new `tournament_matches_local` table | Additive DDL. |
| M23 | `infrastructure/mysql-local/init-building-2.sql` | identical changes as M22 | Additive DDL. |
| M24 | `infrastructure/mysql-local/init-building-3.sql` | identical changes as M22 | Additive DDL. |

**Modified-file total: 24 (1 shared-domain + 1 shared-mqtt + 2 shared-dto + 10 central + 7 local-main + 3 sql).**

---

## SECTION 3 — Complete signatures

> Convention: `// ...` = body omitted (implementation subagent fills). Only allowed
> annotations per module-isolation rules are shown.

### 3.1 NEW files

---

#### (1) `shared-domain/src/main/java/com/gameplatform/shared/domain/result/TeamResult.java`

```java
package com.gameplatform.shared.domain.result;

import com.gameplatform.shared.domain.model.TeamId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;

import java.util.List;

/**
 * {@link GameResult} variant for team-tournament matches. Approved
 * simplification (ambiguity F/G): a team match yields a SINGLE winner
 * ({@code winnerId}) derived from {@code winnerTeamId}; {@code getWinnerIds()}
 * returns a one-element list.
 *
 * <p>PURE Java record — no annotations (shared-domain rule). Serialised via the
 * shared-mqtt {@code GameResultMixIn} under discriminator {@code "TEAM"}.</p>
 */
public record TeamResult(
        UserId winnerId,
        List<UserId> winnerIds,
        TeamId winnerTeamId,
        WinCondition winCondition
) implements GameResult {

    /**
     * Canonicalising compact constructor: {@code winnerId} is derived from
     * {@code winnerTeamId} when the caller passes it as {@code null}
     * ({@code new UserId(winnerTeamId.value())}); {@code winnerIds} defaults to
     * {@code List.of(winnerId)} when the caller passes {@code null} or empty.
     */
    public TeamResult {
        // ...
    }

    @Override
    public UserId getWinnerId() {
        // ...
    }

    @Override
    public List<UserId> getWinnerIds() {
        // ...
    }

    @Override
    public WinCondition getWinCondition() {
        // ...
    }
}
```

**Construction contract for implementer:** the public 4-arg canonical ctor is the
primary entry point; implement the compact constructor so that
`new TeamResult(null, null, teamId, wc)` produces `winnerId = new UserId(teamId.value())`
and `winnerIds = List.of(winnerId)`. `getWinnerId()` returns `winnerId`;
`getWinnerIds()` returns `List.of(winnerId)` (single-element);
`getWinCondition()` returns `winCondition` (typically `TEAM_VICTORY`).

---

#### (2) `central-system/src/main/java/com/gameplatform/central/domain/ports/out/PushTournamentMatchToLocalServersPort.java`

```java
package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;

import java.util.List;

/**
 * Outbound port for pushing a batch of {@code TOURNAMENT_MATCH_SCHEDULED}
 * events to a single Local Server's {@code PUT /internal/tournaments/matches/sync}
 * endpoint. Structural twin of {@link PushGameDefinitionToLocalServersPort}.
 *
 * <p>No ack / poison-isolation: the local upsert is idempotent by PK
 * ({@code matchId}), so a transient transport failure just retries via the
 * outbox on the next scheduler tick.</p>
 */
public interface PushTournamentMatchToLocalServersPort {

    /**
     * Pushes a batch of tournament-match scheduled events to a single local
     * server.
     *
     * @throws com.gameplatform.central.domain.exception.TransientPushException
     *         on transient transport failure (caller retries via the outbox)
     */
    void pushTournamentMatch(List<TournamentMatchScheduledDto> events, RegisteredLocalServer server);
}
```

---

#### (3) `central-system/src/main/java/com/gameplatform/central/infrastructure/adapters/out/rest/LocalTournamentMatchRestAdapter.java`

```java
package com.gameplatform.central.infrastructure.adapters.out.rest;

import com.gameplatform.central.domain.exception.TransientPushException;
import com.gameplatform.central.domain.model.RegisteredLocalServer;
import com.gameplatform.central.domain.ports.out.PushTournamentMatchToLocalServersPort;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.util.List;

/**
 * Pushes {@code TOURNAMENT_MATCH_SCHEDULED} events to a single local server's
 * {@code PUT /internal/tournaments/matches/sync} endpoint. Structural twin of
 * {@link LocalGameDefinitionRestAdapter} (same SSLContext wiring, timeouts,
 * {@code X-Internal-Api-Key} header, {@link RetryTemplate} 3 attempts).
 */
@Component
public class LocalTournamentMatchRestAdapter implements PushTournamentMatchToLocalServersPort {

    private static final String ENDPOINT_PATH = "/internal/tournaments/matches/sync";

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final RetryTemplate retryTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public LocalTournamentMatchRestAdapter(
            SSLContext sslContext,
            @Value("${internal.api-key}") String apiKey,
            @Value("${central.replication.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${central.replication.read-timeout-ms:5000}") int readTimeoutMs) {
        // ... identical SSL+timeout factory wiring as LocalGameDefinitionRestAdapter
    }

    /** Package-private constructor for testing. */
    LocalTournamentMatchRestAdapter(RestTemplate restTemplate, String apiKey) {
        // ...
    }

    @Override
    public void pushTournamentMatch(List<TournamentMatchScheduledDto> events, RegisteredLocalServer server) {
        // ... PUT {server.baseUrl()}/internal/tournaments/matches/sync
        //     headers: Content-Type JSON + X-Internal-Api-Key
        //     body: List<TournamentMatchScheduledDto>
        //     retryTemplate 3 attempts; reuse isTransient(...) logic
    }
}
```

**Implementer note:** copy `buildDefaultRetryTemplate()` and `isTransient(Exception)`
verbatim from `LocalGameDefinitionRestAdapter` (same private static helpers).

---

#### (4) `local-server/src/main/java/com/gameplatform/local/domain/model/TournamentMatchLocal.java`

```java
package com.gameplatform.local.domain.model;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Read-only replica of a tournament match destined for THIS building
 * (PIANO §3.4). NO {@code buildingId} (the table only holds matches routed
 * to this building — ambiguity O), NO {@code winner}, NO {@code playedAt},
 * NO {@code resultData} (those are central-only). Pure Java POJO, immutable,
 * identity = {@code id}. {@code status} is mutable via a new-instance
 * {@code withStatus(...)} helper so the sync service can flip SCHEDULED →
 * IN_PROGRESS → COMPLETED/ABANDONED idempotently.
 */
public class TournamentMatchLocal {

    private final TournamentMatchId id;
    private final TournamentId tournamentId;
    private final int round;
    private final int bracketPosition;
    private final String participantA;
    private final String participantB;   // nullable (BYE never replicated, but kept nullable)
    private final GameType gameType;
    private final String gameId;          // nullable
    private final TournamentMatchStatus status;
    private final Instant scheduledAt;    // nullable

    public TournamentMatchLocal(TournamentMatchId id, TournamentId tournamentId, int round, int bracketPosition,
                                String participantA, String participantB, GameType gameType,
                                String gameId, TournamentMatchStatus status, Instant scheduledAt) {
        // ... null/blank guards on id, tournamentId, gameType, status; participantA non-blank
    }

    public TournamentMatchId getId() { /* ... */ }
    public TournamentId getTournamentId() { /* ... */ }
    public int getRound() { /* ... */ }
    public int getBracketPosition() { /* ... */ }
    public String getParticipantA() { /* ... */ }
    public String getParticipantB() { /* ... */ }
    public GameType getGameType() { /* ... */ }
    public String getGameId() { /* ... */ }
    public TournamentMatchStatus getStatus() { /* ... */ }
    public Instant getScheduledAt() { /* ... */ }

    /** New immutable copy with updated status (used by start/end/abort flows). */
    public TournamentMatchLocal withStatus(TournamentMatchStatus newStatus) {
        // ...
    }

    @Override
    public boolean equals(Object o) { /* identity by id */ }
    @Override
    public int hashCode() { /* Objects.hash(id) */ }
}
```

---

#### (5) `local-server/src/main/java/com/gameplatform/local/domain/ports/out/TournamentMatchLocalRepository.java`

```java
package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;

import java.util.List;
import java.util.Optional;

/**
 * Out-port for the {@code tournament_matches_local} read-only replica.
 * {@code save} is an idempotent upsert by PK {@code id} (mirror of
 * {@link GameDefinitionLocalRepository#save}).
 */
public interface TournamentMatchLocalRepository {
    TournamentMatchLocal save(TournamentMatchLocal match);
    Optional<TournamentMatchLocal> findById(TournamentMatchId id);
    List<TournamentMatchLocal> findByTournamentId(TournamentId tournamentId);
    List<TournamentMatchLocal> findScheduledByParticipant(String userId);
    void deleteById(TournamentMatchId id);
}
```

> `findScheduledByParticipant(userId)` → filters `participant_a = userId OR
> participant_b = userId` AND `status = 'SCHEDULED'` (ambiguity F: team
> matches where the user is not a direct participant cannot be matched here).

---

#### (6) `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/entity/TournamentMatchLocalJpaEntity.java`

```java
package com.gameplatform.local.infrastructure.adapters.out.mysql.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for {@code tournament_matches_local} (PIANO §3.4 lines 416-427).
 * Read-only replica updated only by sync; no {@code @OneToMany}, no
 * {@code @Version} (mirror of {@code GameDefinitionLocalJpaEntity}).
 */
@Entity
@Table(name = "tournament_matches_local")
public class TournamentMatchLocalJpaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tournament_id", length = 36, nullable = false)
    private String tournamentId;

    @Column(name = "round", nullable = false)
    private Integer round;

    @Column(name = "bracket_position", nullable = false)
    private Integer bracketPosition;

    @Column(name = "participant_a", length = 36, nullable = false)
    private String participantA;

    @Column(name = "participant_b", length = 36)
    private String participantB;

    @Column(name = "game_type", length = 50, nullable = false)
    private String gameType;

    @Column(name = "game_id", length = 100)
    private String gameId;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    public TournamentMatchLocalJpaEntity() {}

    public TournamentMatchLocalJpaEntity(String id, String tournamentId, Integer round, Integer bracketPosition,
                                         String participantA, String participantB, String gameType,
                                         String gameId, String status, Instant scheduledAt) {
        // ...
    }

    // standard getters/setters for every field
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    // ... tournamentId, round, bracketPosition, participantA, participantB,
    //     gameType, gameId, status, scheduledAt
}
```

---

#### (7) `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/repository/TournamentMatchLocalJpaRepository.java`

```java
package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentMatchLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentMatchLocalJpaRepository
        extends JpaRepository<TournamentMatchLocalJpaEntity, String> {

    Optional<TournamentMatchLocalJpaEntity> findById(String id);

    List<TournamentMatchLocalJpaEntity> findByTournamentId(String tournamentId);

    @Query("SELECT m FROM TournamentMatchLocalJpaEntity m " +
           "WHERE (m.participantA = :userId OR m.participantB = :userId) " +
           "AND m.status = :status")
    List<TournamentMatchLocalJpaEntity> findByParticipantAndStatus(
            @Param("userId") String userId,
            @Param("status") String status);
}
```

---

#### (8) `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/mapper/TournamentMatchLocalMapper.java`

```java
package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentMatchLocalJpaEntity;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import org.springframework.stereotype.Component;

@Component
public class TournamentMatchLocalMapper {

    public TournamentMatchLocal toDomain(TournamentMatchLocalJpaEntity entity) {
        // ... field-for-field; GameType.valueOf(entity.getGameType());
        //     TournamentMatchStatus.valueOf(entity.getStatus());
    }

    public TournamentMatchLocalJpaEntity toEntity(TournamentMatchLocal domain) {
        // ... field-for-field; enum → .name()
    }
}
```

---

#### (9) `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/adapter/TournamentMatchLocalRepositoryAdapter.java`

```java
package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.TournamentMatchLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TournamentMatchLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adapter implementing {@link TournamentMatchLocalRepository}. {@code save} is
 * an idempotent upsert by PK {@code id} (mirror of
 * {@code GameDefinitionLocalRepositoryAdapter}).
 */
@Component
public class TournamentMatchLocalRepositoryAdapter implements TournamentMatchLocalRepository {

    private final TournamentMatchLocalJpaRepository jpaRepository;
    private final TournamentMatchLocalMapper mapper;

    public TournamentMatchLocalRepositoryAdapter(TournamentMatchLocalJpaRepository jpaRepository,
                                                TournamentMatchLocalMapper mapper) {
        // ...
    }

    @Override
    @Transactional
    public TournamentMatchLocal save(TournamentMatchLocal match) { /* upsert by PK */ }

    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentMatchLocal> findById(TournamentMatchId id) { /* null-safe */ }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentMatchLocal> findByTournamentId(TournamentId tournamentId) { /* null-safe */ }

    @Override
    @Transactional(readOnly = true)
    public List<TournamentMatchLocal> findScheduledByParticipant(String userId) {
        // ... delegate to findByParticipantAndStatus(userId, "SCHEDULED"); null-safe
    }

    @Override
    @Transactional
    public void deleteById(TournamentMatchId id) { /* null-safe */ }
}
```

---

#### (10) `local-server/src/main/java/com/gameplatform/local/application/service/TournamentMatchLocalSyncService.java`

```java
package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Receives {@code TOURNAMENT_MATCH_SCHEDULED} events replicated from the
 * Central via outbox and applies them idempotently to the
 * {@code tournament_matches_local} table. Mirror of
 * {@link GameDefinitionSyncService}; idempotency is by PK {@code matchId}.
 */
@Service
@Transactional
public class TournamentMatchLocalSyncService {

    static final String EVENT_TOURNAMENT_MATCH_SCHEDULED = "TOURNAMENT_MATCH_SCHEDULED";

    private final TournamentMatchLocalRepository tournamentMatchLocalRepository;
    private final Clock clock;

    public TournamentMatchLocalSyncService(TournamentMatchLocalRepository tournamentMatchLocalRepository,
                                           Clock clock) {
        // ...
    }

    public void applyEvents(List<TournamentMatchScheduledDto> events) {
        // ... for each event: null/blank guards; build TournamentMatchLocal from
        //     DTO fields + event.scheduledAt(); status =
        //     TournamentMatchStatus.valueOf(event.status()) (default SCHEDULED);
        //     save (idempotent upsert by PK matchId). Ignore unknown event types
        //     with a log.warn. NO buildingId stored (Local trusts routing).
    }
}
```

---

#### (11) `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/in/rest/InternalTournamentController.java`

```java
package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.TournamentMatchLocalSyncService;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal endpoint secured by {@code InternalApiKeyFilter} (NO
 * {@code @PreAuthorize}). Mirror of the existing
 * {@code InternalMetadataController} / {@code InternalGameDefinitionController}.
 */
@RestController
@RequestMapping("/internal/tournaments/matches")
public class InternalTournamentController {

    private final TournamentMatchLocalSyncService tournamentMatchLocalSyncService;

    public InternalTournamentController(TournamentMatchLocalSyncService tournamentMatchLocalSyncService) {
        // ...
    }

    @PutMapping("/sync")
    public ResponseEntity<Void> syncTournamentMatches(@RequestBody List<TournamentMatchScheduledDto> events) {
        // ... delegate to tournamentMatchLocalSyncService.applyEvents(events); return 200
    }
}
```

---

#### (12) `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/in/rest/PlayerTournamentController.java`

```java
package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.domain.exception.TournamentMatchNotFoundException;
import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.GameSessionDto;
import com.gameplatform.shared.dto.TournamentMatchDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/players/tournaments")
public class PlayerTournamentController {

    private final TournamentMatchLocalRepository tournamentMatchLocalRepository;
    private final CurrentUserService currentUserService;
    private final GameSessionService gameSessionService;   // concrete @Service (or its in-port)

    public PlayerTournamentController(TournamentMatchLocalRepository tournamentMatchLocalRepository,
                                      CurrentUserService currentUserService,
                                      GameSessionService gameSessionService) {
        // ...
    }

    /**
     * Returns the SCHEDULED tournament matches where the authenticated player is
     * a direct participant (participant_a == userId OR participant_b == userId).
     * Ambiguity F: team matches where the user is not a direct participant
     * cannot be resolved here and are NOT returned.
     */
    @GetMapping("/me/matches")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<List<TournamentMatchDto>> myMatches() {
        // ... resolve userId via currentUserService.getCurrentUserId() (404/empty if absent);
        //     findScheduledByParticipant(userId.value()); map to TournamentMatchDto
        //     (buildingId=null, gameId=local.getGameId(), winner=null)
    }

    /**
     * Starts the game session bound to a tournament match: loads the local match,
     * validates status==SCHEDULED (else TournamentMatchNotScheduledException → 409),
     * delegates to GameSessionService.start(... tournamentMatchId).
     */
    @PostMapping("/matches/{matchId}/start")
    @PreAuthorize("hasRole('PLAYER')")
    public ResponseEntity<GameSessionDto> startMatch(@PathVariable String matchId,
                                                     @RequestParam(name = "gameId", required = false) String gameId) {
        // ... load via findById(new TournamentMatchId(matchId)); 404 if absent;
        //     validate SCHEDULED; resolve GameId (local.getGameId() != null ? ... : new GameId(gameId));
        //     call gameSessionService.start(gameId, local.getGameType(), participants, null, new TournamentMatchId(matchId));
        //     return 200 with GameSessionDto
    }
}
```

> **Implementer note:** the `startMatch` participant list is built from the local
> match's `participantA` / `participantB` (filtered to non-null). The
> `gameSessionService.start(...)` 5-arg overload (M18) performs the
> team_allowed + status validation internally.

---

#### (13–16) Exception classes (`local-server/.../domain/exception/`)

```java
package com.gameplatform.local.domain.exception;

/** 404 — match id not present in tournament_matches_local. */
public class TournamentMatchNotFoundException extends RuntimeException {
    public TournamentMatchNotFoundException(String message) { super(message); }
}
```

```java
package com.gameplatform.local.domain.exception;

/** 409 — match not in SCHEDULED status when a start was attempted. */
public class TournamentMatchNotScheduledException extends RuntimeException {
    public TournamentMatchNotScheduledException(String message) { super(message); }
}
```

```java
package com.gameplatform.local.domain.exception;

/** 403 — reserved for future building-routing validation (Local trusts routing per ambiguity O). */
public class TournamentMatchBuildingMismatchException extends RuntimeException {
    public TournamentMatchBuildingMismatchException(String message) { super(message); }
}
```

```java
package com.gameplatform.local.domain.exception;

/** 400 — team_allowed mismatch or participant mismatch. */
public class TournamentMatchValidationException extends RuntimeException {
    public TournamentMatchValidationException(String message) { super(message); }
}
```

---

### 3.2 MODIFIED files — exact diff-equivalent additions

---

#### (M1) `WinCondition.java` — add enum constant

```java
public enum WinCondition {
    WIN,
    DRAW,
    ABANDONED,
    TIMEOUT,
    TEAM_VICTORY          // <-- NEW (FASE 6)
}
```

---

#### (M2) `MqttPayloadSerializer.java` — add 8th subtype

```java
    @JsonSubTypes({
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.ChessResult.class, name = "CHESS"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.DartsResult.class, name = "DARTS"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.FoosballResult.class, name = "FOOSBALL"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.MonopolyResult.class, name = "MONOPOLY"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.RiskResult.class, name = "RISK"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.RouletteResult.class, name = "ROULETTE"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.SlotResult.class, name = "SLOT"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.TeamResult.class, name = "TEAM")  // <-- NEW
    })
```

---

#### (M3) `CreateSessionRequestDto.java` — add 5th field

```java
public record CreateSessionRequestDto(
    String gameId,
    GameType gameType,
    List<String> participants,
    String reservationId,
    String tournamentMatchId     // <-- NEW (optional, LAST, no @NotBlank)
) {}
```

---

#### (M4) `TournamentMatchScheduledDto.java` — add 13th field

```java
public record TournamentMatchScheduledDto(
        String eventId,
        String eventType,
        String matchId,
        String tournamentId,
        int round,
        int bracketPosition,
        String participantA,
        String participantB,
        GameType gameType,
        String gameId,
        String status,
        Instant scheduledAt,
        String buildingId          // <-- NEW (LAST)
) {
}
```

---

#### (M5) `UserReplicationSchedulerService.java`

**Constructor — add 9th param (LAST):**

```java
    public UserReplicationSchedulerService(
            OutboxEventRepository outboxEventRepository,
            LocalServerRegistryPort localServerRegistryPort,
            PushUserToLocalServersPort pushUserToLocalServersPort,
            ReplicationProgressRepository replicationProgressRepository,
            ObjectMapper objectMapper,
            @Qualifier("replicationPushExecutor") Executor replicationPushExecutor,
            PushMetadataToLocalServersPort pushMetadataToLocalServersPort,
            PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort,
            PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort  // <-- NEW
    ) { /* ...assign new field... */ }
```

**New field + constant + predicates:**

```java
    private final PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort;

    private static final String TOURNAMENT_MATCH_SCHEDULED_EVENT = "TOURNAMENT_MATCH_SCHEDULED";

    private boolean isTournamentMatchEvent(OutboxEvent event) {
        return TOURNAMENT_MATCH_SCHEDULED_EVENT.equals(event.getEventType());
    }
```

**`isReplicationEvent` extended:**

```java
    private boolean isReplicationEvent(OutboxEvent event) {
        return isUserReplicationEvent(event) || isMetadataEvent(event)
                || isGameDefinitionEvent(event) || isTournamentMatchEvent(event);
    }
```

**`replicateUsers()` loop — add branch before the user default:**

```java
            } else if (isTournamentMatchEvent(event)) {
                replicateTournamentMatchEvent(event, activeLocalServers);
                continue;
            }
```

**New branch method (signature + intent):**

```java
    /**
     * Drains a single {@code TOURNAMENT_MATCH_SCHEDULED} event: deserialises the
     * payload to {@link TournamentMatchScheduledDto}, loads the involved buildings
     * via {@code tournamentBuildingRepository.findByTournament(tournamentId)},
     * round-robin-assigns a {@code buildingId} to the central {@link TournamentMatch}
     * row (load via TournamentMatchRepository.findById, rebuild with buildingId,
     * save), sets the dto's buildingId, filters activeLocalServers to those whose
     * buildingId matches the assigned one, pushes via
     * {@code pushTournamentMatchToLocalServersPort.pushTournamentMatch(List.of(enrichedDto), targetServer)},
     * records ReplicationProgress, and markAsSent.
     */
    private void replicateTournamentMatchEvent(OutboxEvent event,
                                               List<RegisteredLocalServer> activeLocalServers) {
        // ...
    }
```

> **Implementer note:** the branch needs `TournamentBuildingRepository` and
> `TournamentMatchRepository` to load+patch the central match. **Add them as a
> 10th and 11th ctor param** if not already injected (the STEP 1 scope listed
> only the push port as the 9th; the building/match repo lookups require these
> two extra ports — see SECTION 8 Q1). Keep the parallel-push +
> `replication_progress` + `markAsSent` shape identical to
> `replicateGameDefinitionEvent`, except the server filter narrows to the single
> building whose id == the assigned `buildingId`.

---

#### (M6) `LateRegistrationCatchUpService.java`

**Constructor — add 8th param (LAST):**

```java
    public LateRegistrationCatchUpService(OutboxEventJpaRepository outboxEventJpaRepository,
                                       OutboxEventRepository outboxEventRepository,
                                       PushUserToLocalServersPort pushUserToLocalServersPort,
                                       ObjectMapper objectMapper,
                                       ReplicationProgressRepository replicationProgressRepository,
                                       PushMetadataToLocalServersPort pushMetadataToLocalServersPort,
                                       PushGameDefinitionToLocalServersPort pushGameDefinitionToLocalServersPort,
                                       PushTournamentMatchToLocalServersPort pushTournamentMatchToLocalServersPort) { // <-- NEW
        // ...
    }
```

**`REPLICATION_EVENT_TYPES` — add the new literal:**

```java
    private static final List<String> REPLICATION_EVENT_TYPES = List.of(
            "USER_REGISTERED", "USER_UPDATED",
            "LOCAL_ADMIN_BUILDING_ASSIGNED", "LOCAL_ADMIN_BUILDING_REVOKED",
            "GAME_DEFINITION_UPSERTED",
            "TOURNAMENT_MATCH_SCHEDULED");   // <-- NEW
```

**New predicate + branch (mirrors the game-definition branch):**

```java
    private static boolean isTournamentMatchEvent(String eventType) {
        return "TOURNAMENT_MATCH_SCHEDULED".equals(eventType);
    }
```

```java
            // FASE 6 — tournament-match scheduled event branch.
            // Catch-up: the building is ALREADY assigned (the dto carries
            // buildingId OR the central match row does). Push ONLY if the
            // server's buildingId matches the dto's buildingId.
            if (isTournamentMatchEvent(eventType)) {
                // ... deserialise TournamentMatchScheduledDto; resolve buildingId
                //     (dto.buildingId() != null ? dto.buildingId() : load match row);
                //     if !server.getBuildingId().id().equals(buildingId) → skip (continue);
                //     pushTournamentMatchToLocalServersPort.pushTournamentMatch(List.of(dto), server);
                //     best-effort + replication_progress (mirror game-definition branch)
                continue;
            }
```

> **Implementer note:** the catch-up branch also needs `TournamentMatchRepository`
> to resolve the buildingId when the dto's `buildingId` is null — add it as a 9th
> ctor param if necessary (see SECTION 8 Q1).

---

#### (M7) `SyncEventProcessor.java`

**New fields + new `@Autowired` ctor (11-arg); old 7-arg ctor becomes delegating package-private (null for the 4 new ports):**

```java
    private final TournamentBracketService tournamentBracketService;
    private final TournamentStandingsService tournamentStandingsService;
    private final TournamentRepository tournamentRepository;
    private final TournamentMatchRepository tournamentMatchRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public SyncEventProcessor(ProcessedEventRepository processedEventRepository,
                              StatisticsRepository statisticsRepository,
                              RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
                              ObjectMapper objectMapper,
                              Clock clock,
                              StatisticsFirstBucketRaceRetryHelper retryHelper,
                              PlayerStatisticsProjectionService playerStatisticsProjection,
                              TournamentBracketService tournamentBracketService,
                              TournamentStandingsService tournamentStandingsService,
                              TournamentRepository tournamentRepository,
                              TournamentMatchRepository tournamentMatchRepository) {
        // ... assign all 11 fields
    }

    // Existing 7-arg ctor — now package-private, delegates with null for the 4 new ports:
    SyncEventProcessor(ProcessedEventRepository processedEventRepository,
                       StatisticsRepository statisticsRepository,
                       RegisterUserFromSyncUseCase registerUserFromSyncUseCase,
                       ObjectMapper objectMapper,
                       Clock clock,
                       StatisticsFirstBucketRaceRetryHelper retryHelper,
                       PlayerStatisticsProjectionService playerStatisticsProjection) {
        this(processedEventRepository, statisticsRepository,
                registerUserFromSyncUseCase, objectMapper, clock, retryHelper,
                playerStatisticsProjection, null, null, null, null);
    }
```

> The existing 6-arg and 5-arg package-private ctors keep delegating to the
> 7-arg (unchanged behaviour).

**New branch in `processEvent(...)` if-chain (insert before the final `log.warn`):**

```java
        } else if ("TOURNAMENT_MATCH_COMPLETED".equals(eventDto.eventType())) {
            TournamentMatchResultDto dto = objectMapper.readValue(eventDto.payload(),
                    TournamentMatchResultDto.class);
            handleTournamentMatchCompleted(buildingId, dto);
            return true;
        }
```

**New private method:**

```java
    /**
     * Handles a {@code TOURNAMENT_MATCH_COMPLETED} event from a local server:
     * (a) loads the match via
     *     {@code tournamentMatchRepository.findByIdForUpdate(new TournamentMatchId(dto.matchId()))};
     * (b) if {@code dto.status() == "ABANDONED"} → rebuild TournamentMatch with
     *     status=ABANDONED, winner=null, playedAt=Instant.now(clock); save;
     * (c) else → rebuild TournamentMatch with status=COMPLETED,
     *     winner=dto.winner(), resultData=dto.resultData(), playedAt=now; save;
     * (d) {@code tournamentStandingsService.recomputeAfterCompletion(matchId)}
     *     — ONLY for COMPLETED, NOT for ABANDONED;
     * (e) {@code TournamentMatch parent = tournamentBracketService.advanceWinner(matchId, winnerId)}
     *     where winnerId = dto.winner() (may be null for ABANDONED — see advanceWinner contract);
     * (f) if parent == null → {@code tournamentBracketService.completeIfDone(tournamentId)}.
     *
     * <p>Runs inside the existing {@code @Transactional(REQUIRES_NEW)} of
     * {@link #processOne} — match update + standings recompute + bracket
     * advancement + (optional) next-round outbox emission are atomic.</p>
     */
    private void handleTournamentMatchCompleted(BuildingId buildingId,
                                                TournamentMatchResultDto dto) {
        // ...
    }
```

> **New import needed:**
> `import com.gameplatform.shared.dto.TournamentMatchResultDto;`
> `import com.gameplatform.shared.domain.model.TournamentMatchId;`

---

#### (M8) `TournamentBracketService.java` — add 2 public methods

```java
    /**
     * Advances the winner of a completed match into the parent slot of the
     * next round. The match row itself is already updated (status/winner/
     * playedAt) by {@code SyncEventProcessor.handleTournamentMatchCompleted}
     * before this is called, so this method ONLY computes the parent and
     * patches the slot.
     *
     * <p>parentRound = round + 1;
     * parentBracketPosition = (bracketPosition + 1) / 2. Parent loaded via
     * {@code tournamentMatchRepository.findByTournamentIdAndRoundAndBracketPositionForUpdate(...)}.
     * If the parent does not exist → return {@code null} (signals tournament
     * completion — caller invokes {@link #completeIfDone}). If the parent
     * exists → patch {@code participantA} (when child bracketPosition is ODD)
     * or {@code participantB} (when EVEN) by rebuilding the parent
     * {@link TournamentMatch} with merged fields → save. If the parent is now
     * fully populated (participantA != null AND participantB != null) → call
     * {@code tournamentMatchOutboxPort.publishScheduled(parent, tournament)} to
     * emit a new {@code TOURNAMENT_MATCH_SCHEDULED} outbox row. Return the
     * parent.</p>
     *
     * @param matchId  the completed match id
     * @param winnerId the winner participant id (nullable for ABANDONED — see
     *                 SECTION 8 Q2; when null, do NOT patch the parent slot
     *                 and do NOT emit a scheduled event, but still return the
     *                 parent so completion can be probed)
     * @return the parent match, or {@code null} if no parent exists
     */
    public TournamentMatch advanceWinner(TournamentMatchId matchId, String winnerId) {
        // ...
    }

    /**
     * Completes the tournament if all matches are terminal (COMPLETED /
     * ABANDONED / BYE — no SCHEDULED or IN_PROGRESS remaining). Loads the
     * tournament via {@code tournamentRepository.findByIdForUpdate(...)};
     * if status is already COMPLETED → no-op; else if all matches are
     * terminal → {@code tournament.complete(Instant.now(clock))} + save, then
     * {@code tournamentStandingsService.assignFinalRanks(tournamentId)}.
     */
    public void completeIfDone(TournamentId tournamentId) {
        // ...
    }
```

> **New imports needed:**
> `import com.gameplatform.shared.domain.model.TournamentMatchId;` (already present)
> The existing `TournamentRepository` / `TournamentMatchRepository` fields are
> already injected; the new `findByIdForUpdate` / `findByTournamentIdAndRoundAndBracketPositionForUpdate`
> methods are added to those ports (M10/M11).

---

#### (M9) `TournamentStandingsService.java` — add 2 public methods

```java
    /**
     * Incrementally recomputes standings after a COMPLETED match: load the
     * match, identify winner & loser; for the winner
     * {@code findByTournamentAndParticipantId} → rebuild with wins+1, points+3
     * → save; for the loser rebuild with losses+1 → save. For ABANDONED
     * matches (winner==null) → no update. Uses
     * {@code findByTournamentIdForUpdate(...)} for race protection on the
     * whole standings set when needed.
     *
     * <p>NO-OP for ABANDONED (caller — SyncEventProcessor — already guards
     * this, but the method is defensive).</p>
     */
    public void recomputeAfterCompletion(TournamentMatchId matchId) {
        // ...
    }

    /**
     * Assigns final ranks: load all standings via {@code findByTournament(...)},
     * sort by {@code points desc, wins desc, participantId asc}, assign
     * {@code rank = 1, 2, 3, ...} by rebuilding each {@link TournamentStanding}
     * with the new rank → save.
     */
    public void assignFinalRanks(TournamentId tournamentId) {
        // ...
    }
```

> **New imports needed:**
> `import com.gameplatform.central.domain.model.TournamentMatch;`
> `import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;`
> The service needs `TournamentMatchRepository` to load the match — **add it
> as a 4th ctor param** (see SECTION 8 Q3).

---

#### (M10) `TournamentRepository` port + JPA + adapter — add `findByIdForUpdate`

Port:
```java
    Optional<Tournament> findByIdForUpdate(TournamentId id);
```

JPA repo (`TournamentJpaRepository.java`):
```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TournamentJpaEntity t WHERE t.id = :id")
    Optional<TournamentJpaEntity> findByIdForUpdate(@Param("id") String id);
```
> New imports: `import jakarta.persistence.LockModeType;`

Adapter (`TournamentRepositoryAdapter.java`):
```java
    @Override
    @Transactional
    public Optional<Tournament> findByIdForUpdate(TournamentId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepo.findByIdForUpdate(id.value()).map(mapper::toDomain);
    }
```
> Note: `@Transactional` (NOT readOnly) because the pessimistic write lock must
> participate in a write-capable tx (the caller — `completeIfDone` — will save).

---

#### (M11) `TournamentMatchRepository` port + JPA + adapter — add 2 methods

Port:
```java
    Optional<TournamentMatch> findByIdForUpdate(TournamentMatchId id);

    Optional<TournamentMatch> findByTournamentIdAndRoundAndBracketPositionForUpdate(
            TournamentId tournamentId, int round, int bracketPosition);
```

JPA repo (`TournamentMatchJpaRepository.java`):
```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM TournamentMatchJpaEntity m WHERE m.id = :id")
    Optional<TournamentMatchJpaEntity> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM TournamentMatchJpaEntity m " +
           "WHERE m.tournamentId = :tid AND m.round = :round AND m.bracketPosition = :pos")
    Optional<TournamentMatchJpaEntity> findByTournamentIdAndRoundAndBracketPositionForUpdate(
            @Param("tid") String tournamentId,
            @Param("round") int round,
            @Param("pos") int bracketPosition);
```
> New imports: `import jakarta.persistence.LockModeType;`

Adapter (`TournamentMatchRepositoryAdapter.java`):
```java
    @Override
    @Transactional
    public Optional<TournamentMatch> findByIdForUpdate(TournamentMatchId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepo.findByIdForUpdate(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<TournamentMatch> findByTournamentIdAndRoundAndBracketPositionForUpdate(
            TournamentId tournamentId, int round, int bracketPosition) {
        if (tournamentId == null) {
            return Optional.empty();
        }
        return jpaRepo.findByTournamentIdAndRoundAndBracketPositionForUpdate(
                tournamentId.value(), round, bracketPosition).map(mapper::toDomain);
    }
```

---

#### (M12) `TournamentStandingRepository` port + JPA + adapter — add `findByTournamentIdForUpdate`

Port:
```java
    List<TournamentStanding> findByTournamentIdForUpdate(TournamentId tournamentId);
```

JPA repo (`TournamentStandingJpaRepository.java`):
```java
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TournamentStandingJpaEntity s WHERE s.tournamentId = :tid")
    List<TournamentStandingJpaEntity> findByTournamentIdForUpdate(@Param("tid") String tournamentId);
```
> New imports: `import jakarta.persistence.LockModeType;`

Adapter (`TournamentStandingRepositoryAdapter.java`):
```java
    @Override
    @Transactional
    public List<TournamentStanding> findByTournamentIdForUpdate(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentStandingJpaEntity> entities =
                jpaRepo.findByTournamentIdForUpdate(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }
```

---

#### (M13) `TournamentMatchOutboxAdapter.java` — update DTO ctor (13 args)

```java
        TournamentMatchScheduledDto dto = new TournamentMatchScheduledDto(
                eventId,
                EVENT_TYPE,
                match.getMatchId().value(),
                tournament.getTournamentId().value(),
                match.getRound(),
                match.getBracketPosition(),
                match.getParticipantA(),
                match.getParticipantB(),
                tournament.getGameType(),
                null,                      // gameId — assigned later (FASE 6 push)
                match.getStatus().name(),
                match.getScheduledAt(),    // null in FASE 5
                null                       // <-- NEW: buildingId=null (drain assigns later)
        );
```

---

#### (M14) `EventTypeContractTest.java` — add literal

```java
    private static final Set<String> EXPECTED_EVENT_TYPES = Set.of(
            "USER_REGISTERED",
            "RESERVATION_CREATED",
            "RESERVATION_CANCELLED",
            "GAME_SESSION_COMPLETED",
            "GAME_SESSION_ABORTED",
            "TOURNAMENT_MATCH_COMPLETED"   // <-- NEW
    );
```

---

#### (M15) `GameSession.java` (local domain) — add 2 final fields + ctors + getters

```java
    private final TournamentMatchId tournamentMatchId;   // <-- NEW (nullable)
    private final TournamentId tournamentId;             // <-- NEW (nullable)
```

**New primary ctor (15-arg):**

```java
    public GameSession(GameSessionId id, GameId gameId, GameType gameType, BuildingId buildingId,
                       GameStatus status, Instant startedAt, Instant endedAt, Integer durationSeconds,
                       UserId winnerId, WinCondition winCondition, GameResult result,
                       List<UserId> participants, long version,
                       TournamentMatchId tournamentMatchId, TournamentId tournamentId) {
        // ... existing validation; assign the 2 new final fields (nullable allowed)
    }
```

**New 14-arg delegating ctor (version=0):**

```java
    public GameSession(GameSessionId id, GameId gameId, GameType gameType, BuildingId buildingId,
                       GameStatus status, Instant startedAt, Instant endedAt, Integer durationSeconds,
                       UserId winnerId, WinCondition winCondition, GameResult result,
                       List<UserId> participants, TournamentMatchId tournamentMatchId,
                       TournamentId tournamentId) {
        this(id, gameId, gameType, buildingId, status, startedAt, endedAt, durationSeconds,
             winnerId, winCondition, result, participants, 0L, tournamentMatchId, tournamentId);
    }
```

**Existing ctors — updated to delegate with `null` for the 2 new fields (backward-compat):**

```java
    // old 13-arg (with version) — now delegates to 15-arg with null tournament fields
    public GameSession(GameSessionId id, GameId gameId, GameType gameType, BuildingId buildingId,
                       GameStatus status, Instant startedAt, Instant endedAt, Integer durationSeconds,
                       UserId winnerId, WinCondition winCondition, GameResult result,
                       List<UserId> participants, long version) {
        this(id, gameId, gameType, buildingId, status, startedAt, endedAt, durationSeconds,
             winnerId, winCondition, result, participants, version, null, null);
    }

    // old 12-arg — delegates to 13-arg (unchained to 15-arg)
    // old 11-arg — delegates to 12-arg (unchanged)
```

**New getters:**

```java
    public TournamentMatchId getTournamentMatchId() { return tournamentMatchId; }
    public TournamentId getTournamentId() { return tournamentId; }
```

> **New imports:**
> `import com.gameplatform.shared.domain.model.TournamentMatchId;`
> `import com.gameplatform.shared.domain.model.TournamentId;`

---

#### (M16) `GameSessionJpaEntity.java` — add 2 columns + ctor params

```java
    @Column(name = "tournament_match_id", length = 36)
    private String tournamentMatchId;

    @Column(name = "tournament_id", length = 36)
    private String tournamentId;
```

**Updated ctor (+2 params at the end):**

```java
    public GameSessionJpaEntity(String id, String gameId, String gameType, String buildingId,
                                String status, Instant startedAt, Instant endedAt,
                                Integer durationSeconds, String winnerId, String winCondition,
                                String resultData, List<SessionParticipantJpaEntity> participants,
                                String tournamentMatchId, String tournamentId) {
        // ... existing assignments + the 2 new
    }
```

**Standard getters/setters for the 2 new fields.**

---

#### (M17) `GameSessionMapper.java` — map the 2 new fields

`toDomain(...)`:
```java
        return new GameSession(
            new GameSessionId(entity.getId()),
            // ... existing 11 args ...
            version,
            entity.getTournamentMatchId() != null ? new TournamentMatchId(entity.getTournamentMatchId()) : null,  // <-- NEW
            entity.getTournamentId() != null ? new TournamentId(entity.getTournamentId()) : null                 // <-- NEW
        );
```

`toEntity(...)`:
```java
        entity.setTournamentMatchId(domain.getTournamentMatchId() != null ? domain.getTournamentMatchId().value() : null);  // <-- NEW
        entity.setTournamentId(domain.getTournamentId() != null ? domain.getTournamentId().value() : null);                // <-- NEW
```

> **New imports:**
> `import com.gameplatform.shared.domain.model.TournamentMatchId;`
> `import com.gameplatform.shared.domain.model.TournamentId;`

---

#### (M18) `GameSessionService.java` (local)

**Constructor — add 9th + 10th params:**

```java
    public GameSessionService(
            GameSessionRepository gameSessionRepository,
            GameRepository gameRepository,
            OutboxEventRepository outboxEventRepository,
            PublishGameStatePort publishGameStatePort,
            ReservationRepository reservationRepository,
            Clock clock,
            ObjectMapper objectMapper,
            GameDefinitionLocalRepository gameDefinitionLocalRepository,
            TournamentMatchLocalRepository tournamentMatchLocalRepository,                // <-- NEW (9th)
            @org.springframework.beans.factory.annotation.Value("${app.building-id}") String buildingId) {  // <-- NEW (10th)
        // ...
    }
```

**New fields:**

```java
    private final TournamentMatchLocalRepository tournamentMatchLocalRepository;
    private final String buildingId;
```

**New `start(...)` 5-arg overload (keep existing 4-arg for backward-compat — delegate with `null`):**

```java
    /**
     * FASE 6 tournament-aware start. When {@code tournamentMatchId != null}:
     * load the {@link TournamentMatchLocal} via
     * {@code tournamentMatchLocalRepository.findById}; validate status==SCHEDULED
     * (else {@link com.gameplatform.local.domain.exception.TournamentMatchNotScheduledException});
     * NO building validation on Local (ambiguity O — the central push only
     * sends to the involved building, so receiving the match implies it belongs
     * here); validate the requester is among participants (participantA ==
     * userId OR participantB == userId) for INDIVIDUAL matches — for team
     * matches skip participant check (ambiguity F); load {@link GameDefinitionLocal},
     * validate {@code team_allowed} against the match (if def.teamAllowed()
     * doesn't match the tournament's teamBased expectation →
     * {@link com.gameplatform.local.domain.exception.TournamentMatchValidationException});
     * update the {@link TournamentMatchLocal} status to IN_PROGRESS via
     * {@code withStatus(...)} + save; pass {@code tournamentMatchId} +
     * {@code tournamentId} (resolved from the local match) to the
     * {@link GameSession} constructor. Reuses the existing reservation +
     * machine-state + min/max validation.
     */
    public GameSession start(GameId gameId, GameType gameType, List<UserId> participants,
                             ReservationId reservationId, TournamentMatchId tournamentMatchId) {
        // ...
    }
```

> The existing `@Override public GameSession start(GameId, GameType, List<UserId>, ReservationId)`
> stays and delegates: `return start(gameId, gameType, participants, reservationId, null);`
> (so the `StartGameSessionUseCase` port signature is unchanged).

> **`StartGameSessionUseCase` port is NOT modified** (the 5-arg overload is an
> extra method on the concrete service, not on the in-port interface — the
> `PlayerTournamentController` depends on the concrete `GameSessionService`).

**`end(...)` extended (guarded by `session.getTournamentMatchId() != null`):**

```java
    @Override
    public void end(GameSessionId sessionId, GameResult result) {
        // ... existing body up to the GAME_SESSION_COMPLETED outbox write ...

        // FASE 6 — when the session is bound to a tournament match, emit a
        // second outbox row TOURNAMENT_MATCH_COMPLETED (atomic in this tx) and
        // flip the local match row to COMPLETED.
        if (session.getTournamentMatchId() != null) {
            // ... build TournamentMatchResultDto(matchId, winner, resultData, "COMPLETED")
            //     where matchId = session.getTournamentMatchId().value(),
            //     winner = session.getWinnerId() != null ? session.getWinnerId().value() : null,
            //     resultData = objectMapper.writeValueAsString(result);
            // ... outboxEventRepository.save(new OutboxEvent(UUID, "TOURNAMENT_MATCH_COMPLETED",
            //     objectMapper.writeValueAsString(dto), "PENDING", Instant.now(clock), null, 0));
            // ... tournamentMatchLocalRepository.findById → withStatus(COMPLETED) → save
        }
    }
```

> **New imports needed:**
> `import com.gameplatform.local.domain.model.TournamentMatchLocal;`
> `import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;`
> `import com.gameplatform.shared.domain.model.TournamentMatchId;`
> `import com.gameplatform.shared.domain.model.TournamentId;`
> `import com.gameplatform.shared.dto.TournamentMatchResultDto;`

---

#### (M19) `SessionAbortHelper.java` (local)

**Constructor — add 7th param:**

```java
    public SessionAbortHelper(
            GameSessionRepository gameSessionRepository,
            GameRepository gameRepository,
            OutboxEventRepository outboxEventRepository,
            PublishGameStatePort publishGameStatePort,
            Clock clock,
            ObjectMapper objectMapper,
            TournamentMatchLocalRepository tournamentMatchLocalRepository) {   // <-- NEW (7th)
        // ...
    }
```

**`abortAndEmit(...)` extended (guarded by `session.getTournamentMatchId() != null`):**

```java
        // ... existing GAME_SESSION_ABORTED outbox write ...

        // FASE 6 — when the session is bound to a tournament match, emit a
        // second outbox row TOURNAMENT_MATCH_COMPLETED with status="ABANDONED",
        // winner=null, and flip the local match row to ABANDONED. Atomic in
        // this REQUIRES_NEW tx.
        if (session.getTournamentMatchId() != null) {
            // ... build TournamentMatchResultDto(matchId, null, null, "ABANDONED")
            // ... outboxEventRepository.save(new OutboxEvent(UUID, "TOURNAMENT_MATCH_COMPLETED",
            //     payloadJson, "PENDING", Instant.now(clock), null, 0));
            // ... tournamentMatchLocalRepository.findById → withStatus(ABANDONED) → save
        }
```

> **New imports needed:** same set as M18 (TournamentMatchLocal, TournamentMatchLocalRepository,
> TournamentMatchId, TournamentMatchResultDto).

---

#### (M20) `GameSessionController.java` (local) — `start` endpoint

```java
    @PostMapping("/start")
    public ResponseEntity<GameSessionDto> start(@RequestBody CreateSessionRequestDto req) {
        List<UserId> participants = req.participants() != null
                ? req.participants().stream().map(UserId::new).toList()
                : List.of();

        ReservationId reservationId = req.reservationId() != null && !req.reservationId().isBlank()
                ? new ReservationId(req.reservationId())
                : null;

        // <-- NEW: extract optional tournamentMatchId
        TournamentMatchId tournamentMatchId = req.tournamentMatchId() != null
                && !req.tournamentMatchId().isBlank()
                ? new TournamentMatchId(req.tournamentMatchId())
                : null;

        GameSession session = startGameSessionUseCase.start(
                new GameId(req.gameId()),
                req.gameType(),
                participants,
                reservationId
                // <-- NEW: the controller must call the 5-arg overload when
                // tournamentMatchId != null. Because the in-port only exposes
                // the 4-arg signature, inject the concrete GameSessionService
                // (or cast) and call the 5-arg overload:
                // ((GameSessionService) startGameSessionUseCase).start(..., tournamentMatchId)
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(session));
    }
```

> **Implementer note:** to call the 5-arg overload, add a `GameSessionService
> gameSessionService` field to the controller (inject the concrete bean
> alongside the `StartGameSessionUseCase` in-port), and call
> `gameSessionService.start(gameId, gameType, participants, reservationId,
> tournamentMatchId)` directly. When `tournamentMatchId == null` the 5-arg
> overload behaves identically to the 4-arg. **New import:**
> `import com.gameplatform.shared.domain.model.TournamentMatchId;`

---

#### (M21) `GlobalExceptionHandler.java` (local) — add 4 handlers

```java
    @ExceptionHandler(TournamentMatchNotFoundException.class)
    public ResponseEntity<Void> handleTournamentMatchNotFound(TournamentMatchNotFoundException ex) {
        log.warn("Tournament match not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(TournamentMatchNotScheduledException.class)
    public ResponseEntity<Void> handleTournamentMatchNotScheduled(TournamentMatchNotScheduledException ex) {
        log.warn("Tournament match not scheduled: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(TournamentMatchBuildingMismatchException.class)
    public ResponseEntity<Void> handleTournamentMatchBuildingMismatch(TournamentMatchBuildingMismatchException ex) {
        log.warn("Tournament match building mismatch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(TournamentMatchValidationException.class)
    public ResponseEntity<Void> handleTournamentMatchValidation(TournamentMatchValidationException ex) {
        log.warn("Tournament match validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().build();
    }
```

> **New imports:**
> `import com.gameplatform.local.domain.exception.TournamentMatchNotFoundException;`
> `import com.gameplatform.local.domain.exception.TournamentMatchNotScheduledException;`
> `import com.gameplatform.local.domain.exception.TournamentMatchBuildingMismatchException;`
> `import com.gameplatform.local.domain.exception.TournamentMatchValidationException;`

---

## SECTION 4 — Test skeleton

### (17) `TournamentMatchLocalSyncServiceTest.java`

**Path:** `local-server/src/test/java/com/gameplatform/local/application/service/TournamentMatchLocalSyncServiceTest.java`

```java
package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TournamentMatchLocalSyncServiceTest {

    @Mock TournamentMatchLocalRepository tournamentMatchLocalRepository;
    @Mock Clock clock;
    @InjectMocks TournamentMatchLocalSyncService service;
```

**Test methods (intent + assertions):**

1. `applyEvents_upsertsSingleScheduledEvent()` — one `TournamentMatchScheduledDto` (status SCHEDULED) → verify `tournamentMatchLocalRepository.save(...)` called once with a `TournamentMatchLocal` having matching id/round/bracketPosition/participants/gameType/status.
2. `applyEvents_isIdempotentOnRedelivery()` — same event applied twice → `save` called twice with identical `TournamentMatchLocal` (upsert-by-PK; mapper produces equal domain).
3. `applyEvents_skipsNullEvents()` — list containing a `null` entry → no `save`, no exception.
4. `applyEvents_handlesEmptyList()` — `applyEvents(List.of())` → no interaction with repo.
5. `applyEvents_nullListIsNoOp()` — `applyEvents(null)` → no interaction.
6. `applyEvents_unknownEventTypeLogsAndSkips()` — dto with eventType `"FOO"` → no `save`.

---

### (18) `InternalTournamentControllerTest.java`

**Path:** `local-server/src/test/java/com/gameplatform/local/infrastructure/adapters/in/rest/InternalTournamentControllerTest.java`

```java
package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.TournamentMatchLocalSyncService;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InternalTournamentControllerTest {

    @Mock TournamentMatchLocalSyncService tournamentMatchLocalSyncService;
    MockMvc mvc;   // built in @BeforeEach via standaloneSetup(new InternalTournamentController(syncService))
```

**Test methods:**

1. `syncTournamentMatches_200_onValidBody()` — `PUT /internal/tournaments/matches/sync` with a JSON array of one `TournamentMatchScheduledDto` → status 200, verify `applyEvents` invoked once.
2. `syncTournamentMatches_200_onEmptyBody()` — empty array `[]` → 200, `applyEvents(emptyList)` invoked.
3. `syncTournamentMatches_delegatesEvenIfServiceThrows()` — service stubbed to throw → 500 (default advice); confirms the controller delegates (the `InternalApiKeyFilter` is bypassed in standaloneSetup).

---

### (19) `PlayerTournamentControllerTest.java`

**Path:** `local-server/src/test/java/com/gameplatform/local/infrastructure/adapters/in/rest/PlayerTournamentControllerTest.java`

```java
package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.domain.exception.TournamentMatchNotScheduledException;
import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.*;
import com.gameplatform.shared.dto.GameSessionDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class PlayerTournamentControllerTest {

    @Mock TournamentMatchLocalRepository tournamentMatchLocalRepository;
    @Mock CurrentUserService currentUserService;
    @Mock GameSessionService gameSessionService;
    MockMvc mvc;   // standaloneSetup(new PlayerTournamentController(...))
```

**Test methods:**

1. `myMatches_200_returnsScheduledMatchesForCurrentUser()` — `currentUserService.getCurrentUserId()` → `Optional.of(new UserId("u1"))`; repo returns two `TournamentMatchLocal` (one with participantA=u1, one with participantB=u1, both SCHEDULED) → `GET /api/players/tournaments/me/matches` → 200, body length 2.
2. `myMatches_200_emptyWhenNoUserId()` — `getCurrentUserId()` → empty → 200 with empty array.
3. `myMatches_200_emptyWhenNoScheduledMatches()` — repo returns empty → 200 empty array.
4. `startMatch_404_whenMatchNotFound()` — `findById` → empty → `POST /api/players/tournaments/matches/{id}/start` → 404.
5. `startMatch_409_whenMatchNotScheduled()` — repo returns a `TournamentMatchLocal` with status `COMPLETED`; `gameSessionService.start(...)` stubbed to throw `TournamentMatchNotScheduledException` → 409 (or the service throws before the controller returns; verify the exception is raised and mapped by the handler).
6. `startMatch_200_delegatesToGameSessionService()` — repo returns SCHEDULED match → `gameSessionService.start(...)` returns a `GameSession` → 200 with `GameSessionDto`; verify the 5-arg `start` overload is called with `new TournamentMatchId(matchId)`.

---

### (20) `GameSessionServiceTournamentTest.java`

**Path:** `local-server/src/test/java/com/gameplatform/local/application/service/GameSessionServiceTournamentTest.java`

```java
package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;

@ExtendWith(MockitoExtension.class)
class GameSessionServiceTournamentTest {

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock ReservationRepository reservationRepository;
    @Mock GameDefinitionLocalRepository gameDefinitionLocalRepository;
    @Mock TournamentMatchLocalRepository tournamentMatchLocalRepository;   // <-- NEW
    @Mock Clock clock;
    // ObjectMapper real; buildingId = "building-1"
    GameSessionService service;   // constructed in @BeforeEach with the 10-arg ctor
```

**Test methods:**

1. `start_withTournamentMatchId_loadsLocalMatchAndFlipsToInProgress()` — `tournamentMatchLocalRepository.findById` returns a SCHEDULED `TournamentMatchLocal` → call 5-arg `start(...)` → verify `tournamentMatchLocalRepository.save` called with a `TournamentMatchLocal` whose status is `IN_PROGRESS`, and the saved `GameSession` carries the `tournamentMatchId` + `tournamentId`.
2. `start_withTournamentMatchId_throwsWhenMatchNotScheduled()` — local match status `COMPLETED` → expect `TournamentMatchNotScheduledException`.
3. `start_withTournamentMatchId_throwsWhenMatchMissing()` — `findById` empty → expect `TournamentMatchNotFoundException`.
4. `start_teamAllowedMismatch_throwsValidationException()` — `GameDefinitionLocal.teamAllowed=false` but the tournament match is team-based (or vice versa) → `TournamentMatchValidationException`.
5. `end_withTournamentMatchId_writesTwoOutboxRowsAndCompletesLocalMatch()` — session with `tournamentMatchId != null` → call `end(...)` → verify `outboxEventRepository.save` invoked **twice** (once `GAME_SESSION_COMPLETED`, once `TOURNAMENT_MATCH_COMPLETED` with status `"COMPLETED"`) AND `tournamentMatchLocalRepository.save` called with status `COMPLETED`. All within the same `@Transactional` method (assert via `InOrder` or captors).
6. `end_withoutTournamentMatchId_writesOnlyOneOutboxRow()` — session with `tournamentMatchId == null` → verify exactly one outbox save (`GAME_SESSION_COMPLETED`), zero `TOURNAMENT_MATCH_COMPLETED`.

---

### (21) `SessionAbortHelperTournamentTest.java`

**Path:** `local-server/src/test/java/com/gameplatform/local/application/service/SessionAbortHelperTournamentTest.java`

```java
package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.out.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;

@ExtendWith(MockitoExtension.class)
class SessionAbortHelperTournamentTest {

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock Clock clock;
    @Mock TournamentMatchLocalRepository tournamentMatchLocalRepository;   // <-- NEW (7th)
    SessionAbortHelper helper;   // constructed in @BeforeEach with the 7-arg ctor
```

**Test methods:**

1. `abortAndEmit_withTournamentMatchId_writesAbandonedTournamentOutboxRow()` — session with `tournamentMatchId != null` → `abortAndEmit(...)` → verify `outboxEventRepository.save` invoked **twice** (`GAME_SESSION_ABORTED` + `TOURNAMENT_MATCH_COMPLETED` whose payload JSON contains `"status":"ABANDONED"` and `"winner":null`), and `tournamentMatchLocalRepository.save` called with status `ABANDONED`.
2. `abortAndEmit_withoutTournamentMatchId_writesOnlyAbortedRow()` — session with `tournamentMatchId == null` → exactly one outbox save (`GAME_SESSION_ABORTED`), zero `TOURNAMENT_MATCH_COMPLETED`, zero local-match save.

---

### (22) `TournamentFlowEndToEndIT.java` (central, H2)

**Path:** `central-system/src/test/java/com/gameplatform/central/application/service/TournamentFlowEndToEndIT.java`

```java
package com.gameplatform.central.application.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TournamentFlowEndToEndIT {
```

**Intent:** full bracket flow on H2 — schedule a 4-participant single-elimination tournament →
simulate 4× `TOURNAMENT_MATCH_COMPLETED` events (round-1 × 2 then round-2 final) fed to
`SyncEventProcessor.processOne` → assert `advanceWinner` populated round-2 parent slots,
emitted `TOURNAMENT_MATCH_SCHEDULED` outbox rows for round-2, the final match returns
`null` parent → `completeIfDone` transitions the `Tournament` to `COMPLETED` and
`assignFinalRanks` produces ranks `1..4`. Assert standings `wins/points` increments and
final `rank` ordering (`points desc, wins desc, participantId asc`).

**Test methods:**

1. `fullTournamentFlow_completesAndAssignsRanks()` — the end-to-end path above.
2. `abandonedFinalMatch_tournamentCompletesIfNoScheduledRemaining()` — final match COMPLETED
   with one earlier ABANDONED → `recomputeAfterCompletion` skipped for the abandoned match;
   `completeIfDone` still completes when no SCHEDULED/IN_PROGRESS remain. (Documents the
   ABANDONED advancement limitation — see SECTION 8 Q2.)

---

## SECTION 5 — Schema changes (3 local init.sql files)

> Apply **identically** to all three files:
> - `infrastructure/mysql-local/init.sql`
> - `infrastructure/mysql-local/init-building-2.sql`
> - `infrastructure/mysql-local/init-building-3.sql`

### 5.1 `game_sessions` — extend with 2 columns + 1 index

Inside the existing `CREATE TABLE game_sessions (...)` block, add the 2 columns
after `version` (and add the index after the existing indexes):

```sql
CREATE TABLE game_sessions (
    id            VARCHAR(36) PRIMARY KEY,
    game_id       VARCHAR(36) NOT NULL,
    game_type     VARCHAR(50) NOT NULL,
    building_id   VARCHAR(36) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    started_at    DATETIME(6) NOT NULL,
    ended_at      DATETIME(6),
    duration_s    INT,
    winner_id     VARCHAR(36),
    win_condition VARCHAR(30),
    result_data   JSON,
    version       BIGINT NOT NULL DEFAULT 0,
    tournament_match_id VARCHAR(36) NULL,          -- <-- NEW (FASE 6, PIANO §3.4 line 434)
    tournament_id       VARCHAR(36) NULL,          -- <-- NEW
    INDEX idx_game_type (game_type),
    INDEX idx_building (building_id),
    INDEX idx_status (status),
    INDEX idx_winner (winner_id),
    INDEX idx_game_sessions_tournament (tournament_match_id)   -- <-- NEW
);
```

### 5.2 New `tournament_matches_local` table

Append at the end of each init file (after the existing `game_definitions_local`
block), guarded with `IF NOT EXISTS`:

```sql
-- =============== FASE 6 — Replica tournament matches (read-only) ===============
-- Replica read-only dei match del torneo destinati a questo building, replicati
-- dal Central via outbox TOURNAMENT_MATCH_SCHEDULED. Usata da
-- PlayerTournamentController (GET /me/matches, POST /matches/{id}/start) e da
-- GameSessionService.start (validazione status / participant / team_allowed).
-- Nessun buildingId: la tabella contiene SOLO i match instradati a questo
-- building (ambiguity O). Aggiornata solo dal sync; nessun @Version.
CREATE TABLE IF NOT EXISTS tournament_matches_local (
    id              VARCHAR(36) PRIMARY KEY,
    tournament_id   VARCHAR(36) NOT NULL,
    round           INT NOT NULL,
    bracket_position INT NOT NULL,
    participant_a   VARCHAR(36) NOT NULL,
    participant_b   VARCHAR(36) NULL,
    game_type       VARCHAR(50) NOT NULL,
    game_id         VARCHAR(100) NULL,
    status          VARCHAR(30) NOT NULL,
    scheduled_at    TIMESTAMP NULL,
    INDEX idx_tml_tournament (tournament_id),
    INDEX idx_tml_status (status)
) ENGINE=InnoDB;
```

---

## SECTION 6 — Wiring / Spring auto-discovery summary

### New beans (auto-discovered by component scan, constructor-injected)

| Bean | Module | Stereotype | Injected into |
|------|--------|-----------|--------------|
| `TeamResult` (record) | shared-domain | none | (instantiated by game logic / serialised by `MqttPayloadSerializer` mixin) |
| `PushTournamentMatchToLocalServersPort` (interface) | central | — | implemented by `LocalTournamentMatchRestAdapter` |
| `LocalTournamentMatchRestAdapter` | central | `@Component` | `UserReplicationSchedulerService` (9th ctor param), `LateRegistrationCatchUpService` (8th ctor param) |
| `TournamentMatchLocal` (POJO) | local | none | (instantiated by `TournamentMatchLocalSyncService` / `GameSessionService`) |
| `TournamentMatchLocalRepository` (interface) | local | — | implemented by `TournamentMatchLocalRepositoryAdapter` |
| `TournamentMatchLocalJpaEntity` | local | `@Entity` | Spring Data JPA |
| `TournamentMatchLocalJpaRepository` | local | `@Repository` | `TournamentMatchLocalRepositoryAdapter` |
| `TournamentMatchLocalMapper` | local | `@Component` | `TournamentMatchLocalRepositoryAdapter` |
| `TournamentMatchLocalRepositoryAdapter` | local | `@Component` | `TournamentMatchLocalSyncService`, `GameSessionService` (9th param), `SessionAbortHelper` (7th param), `PlayerTournamentController` |
| `TournamentMatchLocalSyncService` | local | `@Service` | `InternalTournamentController` |
| `InternalTournamentController` | local | `@RestController` | (HTTP endpoint) |
| `PlayerTournamentController` | local | `@RestController` | (HTTP endpoint) |
| 4 exception classes | local | none | thrown by services; handled by `GlobalExceptionHandler` |

### Existing beans that GAIN new dependencies (Spring re-wires automatically)

- `SyncEventProcessor` — new `@Autowired` 11-arg ctor pulls `TournamentBracketService`, `TournamentStandingsService`, `TournamentRepository`, `TournamentMatchRepository` (all already `@Service`/`@Component` beans).
- `UserReplicationSchedulerService` — 9th ctor param `PushTournamentMatchToLocalServersPort` (+ `TournamentBuildingRepository`, `TournamentMatchRepository` if added per Q1).
- `LateRegistrationCatchUpService` — 8th ctor param `PushTournamentMatchToLocalServersPort` (+ `TournamentMatchRepository` per Q1).
- `TournamentBracketService` — no new ctor params (uses existing repos + new `findByIdForUpdate`/`findByTournamentIdAndRoundAndBracketPositionForUpdate` methods).
- `TournamentStandingsService` — **+1 ctor param** `TournamentMatchRepository` (Q3) for `recomputeAfterCompletion`.
- `GameSessionService` (local) — 9th `TournamentMatchLocalRepository`, 10th `@Value("${app.building-id}") String buildingId`.
- `SessionAbortHelper` (local) — 7th `TournamentMatchLocalRepository`.
- `GameSessionController` (local) — add concrete `GameSessionService` field to call the 5-arg `start` overload.
- `GameSessionMapper` (local) — no new deps (uses `ObjectMapper` already injected); maps 2 new fields.

### Discovery paths
- `central-system` component scan root: `com.gameplatform.central` → picks up `LocalTournamentMatchRestAdapter`.
- `local-server` component scan root: `com.gameplatform.local` → picks up `TournamentMatchLocalSyncService`, `TournamentMatchLocalRepositoryAdapter`, `TournamentMatchLocalMapper`, `InternalTournamentController`, `PlayerTournamentController`.
- JPA repos auto-discovered via `@EnableJpaRepository` (existing config) — `TournamentMatchLocalJpaRepository` is picked up by the local-server JPA scan.

---

## SECTION 7 — Module-isolation rule verification checklist

| File | Rule | Verified |
|------|------|----------|
| `TeamResult` (shared-domain) | PURE Java record, no annotations | ✅ no annotations; only `implements GameResult` |
| `WinCondition` (shared-domain) | PURE enum | ✅ additive constant only |
| `MqttPayloadSerializer` (shared-mqtt) | Jackson annotations ONLY in mixin (allowed exception) | ✅ `@JsonSubTypes.Type` added inside the existing `GameResultMixIn` |
| `CreateSessionRequestDto` / `TournamentMatchScheduledDto` (shared-dto) | PURE records, no annotations | ✅ additive components only |
| `PushTournamentMatchToLocalServersPort` (central domain) | PURE Java, no framework annotations | ✅ plain interface |
| `LocalTournamentMatchRestAdapter` (central infra) | `@Component` + `@Value` + `RestTemplate` (infra) | ✅ implements a domain port; no domain types leaked into JPA |
| `SyncEventProcessor` (central application) | `@Service`; constructor injection of ports; no direct JPA | ✅ new params are ports/services, not JPA repos (the `TournamentMatchRepository` is a domain port, not a JPA repo) |
| `TournamentBracketService` / `TournamentStandingsService` (central application) | `@Service` + `@Transactional`; constructor injection | ✅ new methods only; new `TournamentMatchRepository` port param (Q3) is a domain port |
| `Tournament*Repository` ports + JPA + adapters (central infra) | `@Component` adapters; `@Lock` only on JPA repo (infra); mappers separate domain↔JPA | ✅ pessimistic-lock queries live in the JPA repo (infra), not on the domain port |
| `TournamentMatchOutboxAdapter` (central infra) | `@Component`; `ObjectMapper` serialisation | ✅ only the DTO ctor arity changes |
| `EventTypeContractTest` (central test) | n/a (test) | ✅ additive literal |
| `TournamentMatchLocal` (local domain) | PURE Java, no framework annotations | ✅ POJO with final fields, equals/hashCode on id |
| `TournamentMatchLocalRepository` (local domain port) | PURE Java | ✅ |
| `TournamentMatchLocalJpaEntity` (local infra) | `@Entity` (infra only) | ✅ no `@Version`, no `@OneToMany` (read-only replica) |
| `TournamentMatchLocalJpaRepository` (local infra) | `@Repository` (Spring Data) | ✅ |
| `TournamentMatchLocalMapper` (local infra) | `@Component` | ✅ maps domain↔entity, `valueOf` for enums |
| `TournamentMatchLocalRepositoryAdapter` (local infra) | `@Component` implementing domain port; upsert by PK | ✅ no JPA annotations on domain |
| `TournamentMatchLocalSyncService` (local application) | `@Service @Transactional`; constructor injection; no direct JPA | ✅ uses the domain port |
| `InternalTournamentController` (local infra in) | `@RestController`; no `@PreAuthorize` (security via `InternalApiKeyFilter`) | ✅ |
| `PlayerTournamentController` (local infra in) | `@RestController`; `@PreAuthorize("hasRole('PLAYER')")` | ✅ |
| 4 exception classes (local domain) | PURE Java, extend `RuntimeException`, no annotations | ✅ |
| `GameSession` (local domain) | PURE Java; new fields `final`; no annotations | ✅ ctors delegate for backward-compat |
| `GameSessionJpaEntity` (local infra) | `@Column` additions (infra only) | ✅ nullable columns |
| `GameSessionMapper` (local infra) | `@Component`; maps 2 new fields | ✅ |
| `GameSessionService` (local application) | `@Service @Transactional`; ctor injection of ports; atomic outbox writes in same tx | ✅ new `TournamentMatchLocalRepository` port param; `end` writes 2 outbox rows atomically |
| `SessionAbortHelper` (local application) | `@Component`; `@Transactional(REQUIRES_NEW)`; atomic outbox + local-match update | ✅ |
| `GameSessionController` (local infra in) | `@RestController`; extracts DTO field | ✅ |
| `GlobalExceptionHandler` (local infra in) | `@RestControllerAdvice` | ✅ 4 new `@ExceptionHandler`s |
| init.sql ×3 (infrastructure) | DDL only | ✅ additive, `IF NOT EXISTS` for the new table |
| **IDs application-assigned** | no `@GeneratedValue` anywhere | ✅ all new entities use `@Id` only (PK = UUID string) |
| **Temporal via `Clock`** | new services inject `Clock` | ✅ `TournamentMatchLocalSyncService` ctor takes `Clock`; existing services already do |
| **Atomic outbox writes** | local `TOURNAMENT_MATCH_COMPLETED` + session + match row in same `@Transactional`; central `advanceWinner` next-round outbox + match save + standings in same `@Transactional(REQUIRES_NEW)` | ✅ documented in M18/M19/M7/M8 |

---

## SECTION 8 — Open contract questions

> These remain after the design pass. Implementation subagents MUST resolve them
> with the architect before (or during) STEP 3; they are flagged so the contract
> is unambiguous when handed off.

**Q1 — Extra repository deps for the drain / catch-up branches.**
`UserReplicationSchedulerService.replicateTournamentMatchEvent` and
`LateRegistrationCatchUpService`'s catch-up branch need to (a) load the involved
buildings (`TournamentBuildingRepository.findByTournament`) and (b) load+patch the
central `TournamentMatch` row (`TournamentMatchRepository.findById` / `save`) to
assign the round-robin `buildingId`. The STEP 1 scope listed only the push port
as the new ctor param. **Decision needed:** add `TournamentBuildingRepository` +
`TournamentMatchRepository` as additional ctor params to BOTH services (10th/11th
for the scheduler, 9th for catch-up), OR encapsulate the building-assignment
logic in a small new `TournamentMatchBuildingAssignmentService` application
service that both callers inject. **Recommended:** add the two repository ports
as extra ctor params (fewest new files; mirrors existing direct-port injection in
these services). The contract above assumes this; adjust ctor param counts if the
architect prefers the encapsulated service.

**Q2 — `advanceWinner` semantics for ABANDONED matches (winnerId == null).**
`SyncEventProcessor.handleTournamentMatchCompleted` calls
`advanceWinner(matchId, winnerId)` for BOTH COMPLETED and ABANDONED, but for
ABANDONED `winnerId` is null. **Decision needed:** does `advanceWinner` (a) skip
patching the parent slot and skip emitting `TOURNAMENT_MATCH_SCHEDULED` when
`winnerId == null` (current contract assumption), leaving the parent match
permanently SCHEDULED with one empty slot — which means `completeIfDone` will NOT
complete the tournament (a SCHEDULED match remains); or (b) advance the
non-abandoning participant as a walkover? **This is the primary design risk for
single-elimination + abandon.** The contract currently specifies (a) and flags
the resulting "stuck tournament" in `TournamentFlowEndToEndIT` test #2. The
architect must confirm whether FASE 6 accepts an abandoned match leaving the
tournament in `IN_PROGRESS` indefinitely, or whether a walkover rule is required.
If walkover is approved, `advanceWinner` must accept the `TournamentMatch` (to
read `participantA`/`participantB`) and the ABANDONED-flag so it can pick the
non-null participant as the de-facto winner.

**Q3 — `TournamentStandingsService.recomputeAfterCompletion` needs to load the match.**
The method needs `TournamentMatchRepository` to load the completed match (winner/loser).
This is a NEW dependency for a service that today only depends on
`TournamentStandingRepository` + `TournamentParticipantRepository` + `Clock`. The
contract specifies adding `TournamentMatchRepository` as a 4th ctor param.
**Confirm** this is acceptable (it keeps the service in the application layer,
injecting a domain port — compliant with the isolation rules).

**Q4 — `GameSessionController.start` calling the 5-arg overload.**
The in-port `StartGameSessionUseCase` exposes only the 4-arg `start`. To call the
5-arg tournament-aware overload, the controller must depend on the concrete
`GameSessionService` (not just the in-port). **Confirm** this is acceptable, or
alternatively extend `StartGameSessionUseCase` with a 5-arg method (would be a
purely additive default-override). The contract recommends depending on the
concrete service (matches the existing `GameSessionController` which already
injects `ObjectMapper` directly).

**Q5 — Local `end`/`abort` payload shape for `TOURNAMENT_MATCH_COMPLETED`.**
The central `SyncEventProcessor` deserialises the outbox payload as
`TournamentMatchResultDto` via `objectMapper.readValue(payload, TournamentMatchResultDto.class)`.
The local emitters (`GameSessionService.end`, `SessionAbortHelper.abortAndEmit`)
must therefore serialise the payload as a `TournamentMatchResultDto` record JSON
(`objectMapper.writeValueAsString(new TournamentMatchResultDto(matchId, winner, resultData, status))`),
NOT as the free-form `Map<String,Object>` used by `GAME_SESSION_COMPLETED`.
**Confirm** this direct-record payload is the intended contract (it diverges from
the existing `Map`-based payloads but is cleaner and matches the central
deserialiser). Note: the central `processed_events` idempotency key is the outbox
row `id` (the `OutboxEventDto.eventId()`), so the `TournamentMatchResultDto` does
NOT need an embedded `eventId` field.

**Q6 — `PlayerTournamentController.startMatch` `gameId` resolution.**
`TournamentMatchLocal.gameId` is nullable (assigned when the match is pushed to a
building). `startMatch` needs a `GameId` to start a session. **Decision needed:**
is `gameId` always populated on the local replica by the time a player starts the
match (the central drain branch sets it), or must the player supply it via a
query param? The contract exposes an optional `@RequestParam gameId` fallback.
**Confirm** whether the drain branch (M5) must also set `gameId` on the
`TournamentMatchScheduledDto` (and hence on the central `TournamentMatch` row +
the local replica) before pushing — currently M5 only assigns `buildingId`.

---

_END OF FASE 6 CONTRACT._