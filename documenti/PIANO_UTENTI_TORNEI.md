# Piano di Implementazione — Utenti (RBAC a 4 ruoli) e Gestione Tornei

> **Documento:** Piano di implementazione delle funzionalità "Utenti" e "Gestione Tornei"
> **Versione:** 1.0
> **Data:** 2026-07-12
> **Stato:** Bozza in revisione
> **Pubblico:** Development Team
> **Si riveda:** `documenti/REQUIREMENTS.md`, `documenti/DESIGN.md`, `workflow/workflow.md`

---

## 0. Stato di partenza e vincoli architetturali

Lo stato attuale del repository (ricavato da `REQUIREMENTS.md`, `workflow.md` e dall'analisi del codice) è:

- **Moduli Maven** (`gamehandler-platform/pom.xml`): monorepo con `shared/{shared-domain,shared-dto,shared-mqtt}`, `central-system`, `local-server`, `game-client-emulator`, `e2e-tests`.
- **Architettura**: Hub-and-Spoke + Hexagonal (Ports & Adapters). Central = Source of Truth globale (MySQL); Local = Edge Node offline-first (MySQL); Client = endpoint MQTT-only (JavaFX).
- **Convenzioni** (RNF-08, MUST):
  - Layout pacchetti per modulo: `domain/model`, `domain/ports/in`, `domain/ports/out`, `application/service`, `infrastructure/adapters/in|out`, `infrastructure/config`, `infrastructure/security`.
  - Nessuna dipendenza del `domain` da Spring o JPA.
  - **ID sempre application-assigned** (UUID v4 o chiavi di business); niente `@GeneratedValue`.
  - **Niente relazioni JPA** tranne l'attuale `@OneToMany` su `GameSessionJpaEntity.participants`; le associazioni sono String FK.
  - **Schema source of truth**: `infrastructure/mysql-central/init.sql` e `infrastructure/mysql-local/init.sql`; Hibernate `ddl-auto: validate`. Nessun Flyway/Liquibase. Ogni modifica allo schema → modifica degli `init.sql` + `docker-compose down -v` per reinizializzare.
  - **Replica Central→Local**: outbox su Central + `UserReplicationSchedulerService`; ricezione su Local via `InternalSyncController`/`UserSyncService`.
  - **Sync Local→Central**: outbox su Local + `SyncSchedulerService`; ricezione su Central via `SyncController`/`SyncReceiverService`/`SyncEventProcessor` con idempotenza via `processed_events`.
  - **JWT**: RS256, ruoli come claim `roles` (Stringa separata da virgole); `JwtAuthenticationFilter` normalizza prefisso `ROLE_`.
  - **Sicurezza**: `@EnableMethodSecurity` già attivo in entrambi i moduli; `@PreAuthorize("hasRole(...)")` per guardare ruolo; `/internal/**` protetti da `InternalApiKeyFilter`.
  - **DTO e enum condivisi** devono stare in `shared-dto` / `shared-domain`.
  - **GameResult** polimorfo gestito da `@JsonTypeInfo` in `MqttPayloadSerializer` (shared-mqtt).

### Gap rispetto ai requisiti del task

| Concept | Stato attuale | Gap |
|---|---|---|
| Ruoli utente | `users.roles` String separata da virgole; usati solo `"USER"` e `"ADMIN"` (`UserService.java:69`, `StatisticsController.java:24`). Nessun `enum Role`. | Servono 4 ruoli: `PLAYER`, `LOCAL_ADMIN`, `GAME_ADMIN`, `PLATFORM_ADMIN`. |
| Profilo Giocatore | Inesistente. I giocatori sono `UserId` in `session_participants`. | Servono statistiche personali consultabili (`GET /api/players/me/statistics`). |
| Admin del Locale | Inesistente. `RegisteredLocalServer` rappresenta il building, non l'utente. | Servono binding `local_admin ↔ building` e enforcement su Local. |
| Admin del Gioco | Inesistente. `GameType` è enum statico; `GameFactory` statico. `game_catalog` centrale esiste ma non è mappato. | Servono definizioni di gioco configurabili (regole registrazione partita). |
| Admin della Piattaforma | ≈ ruolo `ADMIN`. Da rinominare/arricchire. | Già coperto in sostanza; necessita solo renaming + UI di gestione globale. |
| Tornei | Inesistente. | Intero dominio da introdurre. |
| Partite a squadre | Inesistente. Le `GameSession.participants` sono `List<UserId>`. | Serve concetto di `Team` e registrazione risultato per squadra. |

### Criteri di accettazione comuni (RNF)

- Le modifiche **non rompono** i test esistenti (es. `AuthServiceTest`, `SyncReceiverServiceTest`, `MultiBuildingEndToEndIT`).
- Ogni nuovo service ha almeno un test unitario con mock dei port (RNF-09).
- I nuovi endpoint sono coperti da test di integrazione H2 (`application-test.yml` già disponibile in entrambi i moduli).
- I nuovi eventi outbox sono gestiti in modo idempotente (lato Central) e con `@Version`/guarda-stale (lato Local).
- I messaggi MQTT per le nuove funzionalità seguono la convenzione `building/{buildingId}/...`.

---

## 1. Modello RBAC a 4 ruoli

### 1.1 Decisioni di design

1. **Enum `Role` in `shared-domain`** (non tabella normalizzata): si mantiene lo storage come Stringa separata da virgole su `users.roles` e `replicated_users.roles` per retro-compatibilità con `UserSyncDto` e `UserRegisteredEventDto`. L'enum fa da parser/validatore.
   - Costanti: `PLAYER`, `LOCAL_ADMIN`, `GAME_ADMIN`, `PLATFORM_ADMIN`.
   - Metodi: `static Set<Role> parse(String csv)`, `static String format(Collection<Role>)`.
2. **Migrazione one-shot** degli `init.sql`: `USER` → `PLAYER`, `ADMIN` → `PLATFORM_ADMIN`. Per i DB esistenti: `docker-compose down -v` e reinit.
3. **`UserService.register`** assegna `Set.of(Role.PLAYER)` invece di `"USER"` (idem `LocalSignupService.java:117`).
4. **Token JWT**: il claim `roles` continua a essere CSV String. L'autorità Spring deriva da `ROLE_<Role.name()>`. I token emessi prima della migrazione vanno rigenerati (login di nuovo richiesto, documentato).
5. **Enforcement**: `@PreAuthorize` a livello di metodo per semplicità. Per l'`LOCAL_ADMIN` serve un guard supplementare "JWT `buildings` claim contains `app.building-id`" (vedi §1.4).
6. **Mapping utenti→building** per `LOCAL_ADMIN`: nuova tabella centrale `local_admin_buildings(user_id, building_id)`, replicata ai Local via outbox. Il `JwtTokenProvider` centrale arricchisce i claim con `buildings: ["building-1", ...]` solo per `LOCAL_ADMIN`.

### 1.2 Modifica del enum `Role`

`shared/shared-domain/src/main/java/com/gameplatform/shared/domain/security/Role.java`

```java
public enum Role {
    PLAYER, LOCAL_ADMIN, GAME_ADMIN, PLATFORM_ADMIN;
    public static Set<Role> parse(String csv) { /* tolerant parse */ }
    public static String format(Collection<Role> roles) { /* join "," */ }
}
```

Aggiornare `JwtTokenProvider` (entrambi i moduli) per emettere `roles` claim da `Set<Role>` invece che da `String`.

### 1.3 Schema centrale — `infrastructure/mysql-central/init.sql`

Aggiungere:
```sql
-- Bind amministratore locale <-> edificio
CREATE TABLE IF NOT EXISTS local_admin_buildings (
    user_id     VARCHAR(36)  NOT NULL,
    building_id VARCHAR(100) NOT NULL,
    assigned_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, building_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Definizioni di gioco configurabili (gestite da GAME_ADMIN)
CREATE TABLE IF NOT EXISTS game_definitions (
    game_type           VARCHAR(50)  NOT NULL,
    name                VARCHAR(200) NOT NULL,
    min_players         INT          NOT NULL DEFAULT 1,
    max_players         INT          NOT NULL DEFAULT 1,
    team_allowed        BOOLEAN      NOT NULL DEFAULT FALSE,
    registration_rules  JSON         NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (game_type)
) ENGINE=InnoDB;

-- Seed iniziale allineato al GameType enum esistente
INSERT INTO game_definitions (game_type, name, min_players, max_players, team_allowed) VALUES
  ('CHESS','Scacchi',2,2,FALSE),
  ('FOOSBALL','Calciobalilla',2,4,TRUE),
  ('DARTS','Freccette',1,4,TRUE),
  ('MONOPOLY','Monopoli',2,6,TRUE),
  ('RISK','Rischio',2,6,TRUE),
  ('SLOT_MACHINE','Slot Machine',1,1,FALSE),
  ('ROULETTE','Roulette',1,20,TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Migrazione ruoli (idempotente)
UPDATE users SET roles = REPLACE(REPLACE(roles, 'USER', 'PLAYER'), 'ADMIN', 'PLATFORM_ADMIN')
  WHERE roles LIKE '%USER%' OR roles LIKE '%ADMIN%';
```

### 1.4 Schema locale — `infrastructure/mysql-local/init.sql`

Aggiungere (anche su `init-building-2.sql`/`init-building-3.sql`):
```sql
-- Replica read-only dei binding admin/building (per enforcement offline)
CREATE TABLE IF NOT EXISTS local_admin_buildings_local (
    user_id     VARCHAR(36)  NOT NULL,
    building_id VARCHAR(100) NOT NULL,
    assigned_at TIMESTAMP    NOT NULL,
    PRIMARY KEY (user_id, building_id)
) ENGINE=InnoDB;

-- Replica read-only delle definizioni di gioco
CREATE TABLE IF NOT EXISTS game_definitions_local (
    game_type          VARCHAR(50)  NOT NULL,
    name               VARCHAR(200) NOT NULL,
    min_players        INT          NOT NULL,
    max_players        INT          NOT NULL,
    team_allowed       BOOLEAN      NOT NULL,
    registration_rules JSON        NULL,
    updated_at         TIMESTAMP    NOT NULL,
    PRIMARY KEY (game_type)
) ENGINE=InnoDB;
```

### 1.5 Central System — nuovi componenti

#### `shared-domain`
- `Role` enum (vedi §1.2).

#### `shared-dto`
- `AssignRoleRequestDto(userId, roles: List<String>)`
- `AssignLocalAdminBuildingsDto(userId, buildingIds: List<String>)`
- `LocalAdminBuildingsDto(userId, buildingIds)`
- `GameDefinitionDto(gameType, name, minPlayers, maxPlayers, teamAllowed, registrationRules: Map<String,Object>)`
- `UpsertGameDefinitionRequestDto(...)` (con `@Valid` su min/max)

#### Central `domain`
- `domain/model/LocalAdminBuilding.java` (UserId, BuildingId, assignedAt)
- `domain/model/GameDefinition.java` (GameType, name, minPlayers, maxPlayers, teamAllowed, rules)
- `domain/exception/RoleNotFoundException`, `BuildingNotRegisteredToAdminException`

#### Central `domain/ports/in`
- `AssignRoleUseCase.assignRole(userId, Set<Role>)`
- `AssignLocalAdminBuildingsUseCase.assign(userId, Set<BuildingId>)` / `.revoke(...)`
- `GetLocalAdminBuildingsUseCase`
- `UpsertGameDefinitionUseCase`, `ListGameDefinitionsUseCase`

#### Central `domain/ports/out`
- `LocalAdminBuildingRepository`
- `GameDefinitionRepository`

#### Central `application/service`
- `UserRoleService` — assegna/revoca ruoli; scrive `USER_ROLE_UPDATED` outbox.
- `LocalAdminBuildingService` — CRUD binding; scrive outbox `LOCAL_ADMIN_BUILDING_ASSIGNED`/`_REVOKED`.
- `GameDefinitionService` — CRUD; scrive outbox `GAME_DEFINITION_UPSERTED`.

#### Central `infrastructure/adapters/out/mysql`
- `LocalAdminBuildingJpaEntity` (tabella `local_admin_buildings`), `LocalAdminBuildingJpaRepository`, `LocalAdminBuildingRepositoryAdapter`.
- `GameDefinitionJpaEntity` (tabella `game_definitions`), `GameDefinitionJpaRepository`, `GameDefinitionRepositoryAdapter`.
- Rispettivi `*Mapper`.

#### Central `infrastructure/adapters/in/rest`
- `UserRoleController` (`/api/admin/roles`)
  - `POST /assign` → `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`.
- `LocalAdminController` (`/api/admin/local`)
  - `POST /buildings`, `DELETE /buildings`, `GET /buildings?userId=` → `PLATFORM_ADMIN` (GET per `LOCAL_ADMIN` solo se stesso).
- `GameAdminController` (`/api/admin/games`)
  - `POST /definitions`, `PUT /definitions/{gameType}`, `GET /definitions` → `GAME_ADMIN` per scrittura, `authenticated` per GET.

#### Central `infrastructure/security`
- Aggiornare `JwtTokenProvider.generateTokenWithExpiry` per includere claim `buildings` (List<String>) quando l'utente ha ruolo `LOCAL_ADMIN` (popolato da `LocalAdminBuildingRepository.findByUserId`).
- Aggiornare `JwtTokenProvider.getAuthorities` e `JwtAuthenticationFilter` per usare `Role.parse(roles)` invece di split diretto.

#### Central — estensione sync
- Estendere `UserReplicationSchedulerService` (o nuovo `MetadataReplicationSchedulerService`) per propagare anche `LOCAL_ADMIN_BUILDING_ASSIGNED/REVOKED` e `GAME_DEFINITION_UPSERTED` a tutti i Local attivi.
- Estendere `SyncEventProcessor` (lato ricezione, ma per questi eventi il flusso è Central→Local, quindi il ricevente è il Local) — vedi §1.6.

### 1.6 Local Server — nuovi componenti

#### Local `domain/model`
- `LocalAdminBuilding` (UserId, BuildingId, assignedAt)
- `GameDefinitionLocal` (copia in sola lettura)

#### Local `domain/ports/out`
- `LocalAdminBuildingLocalRepository`
- `GameDefinitionLocalRepository`

#### Local `application/service`
- `LocalAdminBuildingSyncService` — applica gli eventi `LOCAL_ADMIN_BUILDING_ASSIGNED`/`_REVOKED` ricevuti dal Central (upsert/delete su `local_admin_buildings_local`).
- `GameDefinitionSyncService` — applica `GAME_DEFINITION_UPSERTED` (upsert su `game_definitions_local`).
- Estendere `LocalAuthService.login` per arricchire il JWT locale con claim `buildings` (relativo al solo building del Local se l'utente è admin di questo edificio). Alternativa: confrontare con `app.building-id` lato `JwtAuthenticationFilter`/filtro custom.

#### Local `infrastructure/config`
- `LocalAdminBuildingAuthorizationManager` (bean) — metodo `boolean canManageBuilding(Authentication, String buildingId)` usato dai controller per fine-grained check.

#### Local `infrastructure/adapters/in/rest`
- `AdminLocalController` (`/api/admin/local`)
  - `POST /games` (aggiunge un gioco a `game_catalog` del building a partire da una `game_definitions_local`) → `LOCAL_ADMIN`.
  - `PUT /games/{gameId}` (modifica nome/stato) → `LOCAL_ADMIN`.
  - `DELETE /games/{gameId}` (rimuove dal catalogo, non permette se IN_USE) → `LOCAL_ADMIN`.
  - `GET /devices` (lista dispositivi con stato) → `LOCAL_ADMIN`.
  - `GET /sessions/active` (monitoraggio match in corso nel building) → `LOCAL_ADMIN`.
  - `GET /statistics` (statistiche aggregate del building) → `LOCAL_ADMIN`. Estende `/api/statistics` esistente con vista admin (include anche sessioni abortite, top player locali, ecc.).
  - Tutti i metodi controllano `LocalAdminBuildingAuthorizationManager.canManageBuilding(auth, app.building-id)`.

#### Local `infrastructure/adapters/in/rest` (ricezione sync)
- Estendere `InternalSyncController` con nuovo endpoint `PUT /internal/metadata/sync` che riceve batch di eventi `{type, payload}` e dispatcha a `LocalAdminBuildingSyncService` / `GameDefinitionSyncService`.

### 1.7 Sostituzioni / refactor

- Sostituire `@PreAuthorize("hasRole('ADMIN')")` in `StatisticsController` (Central) con `hasRole('PLATFORM_ADMIN')`.
- Sostituire `@PreAuthorize("hasRole('USER')")` in `GameController`/`GameSessionController`/`ReservationController`/`StatisticsController` (Local) con `hasRole('PLAYER')` OR `hasAnyRole('PLAYER','LOCAL_ADMIN')` dove serve (un admin locale deve potersi muovere per monitorare).
- Aggiornare `GlobalExceptionHandler` (entrambi) per `RoleNotFoundException` → 404 e `BuildingNotRegisteredToAdminException` → 403.

---

## 2. Statistiche del Giocatore

### 2.1 Decisioni di design

- Per le **statistiche personali** servono dati per-giocatore che non esistono: l'attuale `aggregated_statistics` è chiave `(building, gameType, period)`. Si introduce un **read-model per-giocatore** popolato dal `SyncEventProcessor` del Central consumando `GAME_SESSION_COMPLETED` con payload arricchito (vedi §2.2).
- Le statistiche del giocatore sono consultabili **sul Central** (source of truth globale) — `GET /api/players/me/statistics` e `GET /api/players/{userId}/statistics` (autorizzato: se stesso o `PLATFORM_ADMIN`).
- Replica per consultazione offline (Could-Have): `player_statistics_local` derivata da `game_sessions`+`session_participants` già presenti nel DB locale — calcolabile on-demand da `StatisticsService.getPlayerStatistics(userId)`. **Nessun sync aggiuntivo richiesto**.

### 2.2 Estensione payload `GAME_SESSION_COMPLETED`

Aggiornare l'evento outbox emesso da `GameSessionService.java:250` (Local) per includere `participants: List<String>` e `winnerId: String` (oltre a `resultData` che già li contiene, ma espliciti per facilitare il processing Central-side). Modificare `OutboxEventDto`/`SyncPayloadDto`? No — il payload resta JSON String; basta aggiungere campi al JSON. Aggiornare la documentazione in `REQUIREMENTS.md` (RF-SE-02) per indicare i nuovi campi.

### 2.3 Schema centrale — `infrastructure/mysql-central/init.sql`

```sql
-- Fatto per singola partita giocata da un utente
CREATE TABLE IF NOT EXISTS player_match_facts (
    session_id   VARCHAR(36)  NOT NULL,
    user_id      VARCHAR(36)  NOT NULL,
    building_id  VARCHAR(100) NOT NULL,
    game_type   VARCHAR(50)  NOT NULL,
    tournament_id VARCHAR(36) NULL,
    won          BOOLEAN      NOT NULL,
    win_condition VARCHAR(30) NULL,
    ended_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (session_id, user_id),
    INDEX idx_user (user_id, ended_at)
) ENGINE=InnoDB;

-- Proiezione aggregata per giocatore e tipo di gioco
CREATE TABLE IF NOT EXISTS player_statistics (
    user_id         VARCHAR(36) NOT NULL,
    game_type       VARCHAR(50) NOT NULL,
    matches_played INT NOT NULL DEFAULT 0,
    matches_won     INT NOT NULL DEFAULT 0,
    last_played_at  TIMESTAMP NULL,
    PRIMARY KEY (user_id, game_type)
) ENGINE=InnoDB;
```

### 2.4 Central — nuovi componenti

- `domain/model/PlayerMatchFact.java`, `PlayerStatistics.java`.
- `domain/ports/out/PlayerMatchFactRepository`, `PlayerStatisticsRepository`.
- `application/service/PlayerStatisticsService` (lettura + ricalcolo).
- Estensione `SyncEventProcessor.handleGameSessionCompleted`: parsare `resultData`/`participants`/`winnerId` e fare upsert in `player_match_facts` + `player_statistics` (incremento atomico).
- `infrastructure/adapters/in/rest/PlayerStatisticsController` (`/api/players`):
  - `GET /me/statistics` → `@PreAuthorize("hasRole('PLAYER')")` (claim `userId` dal JWT).
  - `GET /{userId}/statistics` → `hasAnyRole('PLATFORM_ADMIN')` o self-check (se `userId == claim.sub`).
  - `GET /me/statistics?gameType=CHESS` opzionale.

### 2.5 Local — statistiche locali del giocatore

- Estendere `StatisticsService` (Local) con `getPlayerStatistics(userId)` che aggrega da `game_sessions`+`session_participants` locali.
- `GET /api/players/me/statistics` (Local) → `hasRole('PLAYER')`.

---

## 3. Gestione Tornei

### 3.1 Modello di dominio

**Identificatori** (in `shared-domain/model`):
- `TournamentId(String)`, `TeamId(String)`, `TournamentMatchId(String)`.

**Enum** (in `shared-domain/model`):
- `TournamentStatus { DRAFT, OPEN_REGISTRATION, IN_PROGRESS, COMPLETED, CANCELLED }`
- `TournamentMatchStatus { SCHEDULED, IN_PROGRESS, COMPLETED, ABANDONED, BYE }`
- `TournamentFormat { SINGLE_ELIMINATION, ROUND_ROBIN }` (Could-Have: solo `SINGLE_ELIMINATION` in fase 1).

**Modelli di dominio** (POJO):
- `Tournament(tournamentId, name, gameType, teamBased, teamSize, status, startsAt, endsAt, createdBy, createdAt)`.
- `Team(teamId, tournamentId, name, members: List<UserId>, createdAt)`.
- `TournamentParticipant(tournamentId, participantId, isTeam, displayName, registeredAt)`.
- `TournamentMatch(matchId, tournamentId, round, bracketPosition, participantA, participantB, buildingId, gameId, sessionId, winner, status, scheduledAt, playedAt, resultData)`.
- `TournamentStanding(tournamentId, participantId, wins, losses, points, rank)`.

**Eventi** (in `shared-domain/events`):
- `TournamentCreatedEvent("TOURNAMENT_CREATED")`
- `TournamentRegistrationOpenedEvent("TOURNAMENT_REGISTRATION_OPENED")`
- `TournamentMatchScheduledEvent("TOURNAMENT_MATCH_SCHEDULED")`
- `TournamentMatchCompletedEvent("TOURNAMENT_MATCH_COMPLETED")`
- `TournamentCompletedEvent("TOURNAMENT_COMPLETED")`

### 3.2 Regole chiave (derive dai punti)

1. Un torneo **coinvolge un insieme di edifici** → tabella `tournament_buildings(tournament_id, building_id)`.
2. Un torneo riguarda **un solo tipo di gioco** → campo `game_type` (FK al `game_definitions`).
3. Un torneo è composto da un **insieme di partite** → tabella `tournament_matches`.
4. **Produce una classifica finale** → tabella `tournament_standings` derivata dai risultati.
5. Le partite possono essere **individuali** o **a squadre**. Nel caso a squadre **si registra la squadra e non i singoli giocatori**: `tournament_matches.participant_a/b` contiene `team_id`; `tournament_teams` raccoglie i membri; il `GameSessionService` locale, in caso di match di torneo a squadre, registra comunque il `winner_id` a livello di **team** nel `GameResult` (serve nuovo `TeamResult` in shared-domain che implementi `GameResult`).

### 3.3 Schema centrale — `infrastructure/mysql-central/init.sql`

```sql
CREATE TABLE IF NOT EXISTS tournaments (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    game_type   VARCHAR(50)  NOT NULL,
    team_based  BOOLEAN      NOT NULL DEFAULT FALSE,
    team_size   INT          NOT NULL DEFAULT 1,
    format      VARCHAR(30)  NOT NULL DEFAULT 'SINGLE_ELIMINATION',
    status      VARCHAR(30)  NOT NULL,
    starts_at   TIMESTAMP    NOT NULL,
    ends_at     TIMESTAMP    NULL,
    created_by  VARCHAR(36)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    FOREIGN KEY (game_type) REFERENCES game_definitions(game_type)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_buildings (
    tournament_id VARCHAR(36) NOT NULL,
    building_id   VARCHAR(100) NOT NULL,
    PRIMARY KEY (tournament_id, building_id),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_teams (
    id           VARCHAR(36) PRIMARY KEY,
    tournament_id VARCHAR(36) NOT NULL,
    name         VARCHAR(200) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    UNIQUE(tournament_id, name),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_team_members (
    team_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (team_id, user_id),
    FOREIGN KEY (team_id) REFERENCES tournament_teams(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_participants (
    tournament_id VARCHAR(36) NOT NULL,
    participant_id VARCHAR(36) NOT NULL,
    is_team       BOOLEAN     NOT NULL,
    display_name  VARCHAR(200) NOT NULL,
    registered_at TIMESTAMP   NOT NULL,
    PRIMARY KEY (tournament_id, participant_id),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_matches (
    id               VARCHAR(36) PRIMARY KEY,
    tournament_id    VARCHAR(36) NOT NULL,
    round            INT NOT NULL,
    bracket_position INT NOT NULL,
    participant_a    VARCHAR(36) NOT NULL,
    participant_b    VARCHAR(36) NULL,
    building_id      VARCHAR(100) NULL,
    game_id          VARCHAR(100) NULL,
    session_id       VARCHAR(36) NULL,
    winner           VARCHAR(36) NULL,
    status           VARCHAR(30) NOT NULL,
    scheduled_at     TIMESTAMP NULL,
    played_at        TIMESTAMP NULL,
    result_data      TEXT NULL,
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_standings (
    tournament_id  VARCHAR(36) NOT NULL,
    participant_id VARCHAR(36) NOT NULL,
    wins           INT NOT NULL DEFAULT 0,
    losses         INT NOT NULL DEFAULT 0,
    points         INT NOT NULL DEFAULT 0,
    rank           INT NULL,
    PRIMARY KEY (tournament_id, participant_id),
    FOREIGN KEY (tournament_id) REFERENCES tournaments(id) ON DELETE CASCADE
) ENGINE=InnoDB;
```

### 3.4 Schema locale — `infrastructure/mysql-local/init.sql` (e varianti building-2/3)

```sql
-- Replica dei match del torneo programmati su questo edificio
CREATE TABLE IF NOT EXISTS tournament_matches_local (
    id               VARCHAR(36) PRIMARY KEY,
    tournament_id    VARCHAR(36) NOT NULL,
    round            INT NOT NULL,
    bracket_position INT NOT NULL,
    participant_a    VARCHAR(36) NOT NULL,
    participant_b    VARCHAR(36) NULL,
    game_type        VARCHAR(50) NOT NULL,
    game_id          VARCHAR(100) NULL,
    status           VARCHAR(30) NOT NULL,
    scheduled_at     TIMESTAMP NULL
) ENGINE=InnoDB;

-- Estensione del game_sessions esistente per legare la sessione al match torneo
-- (la ALTER TABLE in init.sql viene eseguita solo su DB vergine; vedi nota)
ALTER TABLE game_sessions
    ADD COLUMN IF NOT EXISTS tournament_match_id VARCHAR(36) NULL,
    ADD COLUMN IF NOT EXISTS tournament_id        VARCHAR(36) NULL;
CREATE INDEX idx_game_sessions_tournament ON game_sessions(tournament_match_id);
```

> Nota operativa: poiché l'`init.sql` è eseguito da `docker-entrypoint-initdb.d` solo su DB vergine, per ambiente di sviluppo già popolato è necessario `docker-compose down -v` (come già fatto per le migrazioni utente §1.3).

### 3.5 `shared-dto` — nuovi DTO

- `CreateTournamentRequestDto(name, gameType, teamBased, teamSize, startsAt, buildingIds: List<String>)`
- `TournamentDto(id, name, gameType, teamBased, teamSize, status, startsAt, endsAt, buildings, participantsCount)`
- `RegisterTournamentParticipantDto(teamName?, teamMembers?: List<String>)` — per squadra o singolo.
- `TeamDto(id, name, members: List<String>)`
- `TournamentParticipantDto(participantId, isTeam, displayName)`
- `TournamentMatchDto(id, round, bracketPosition, participantA, participantB, buildingId, gameId, status, scheduledAt, winner)`
- `ScheduleTournamentMatchesDto(tournamentId)`
- `TournamentStandingDto(participantId, displayName, wins, losses, points, rank)`
- `TournamentMatchScheduledDto` (per outbox sync Central→Local)
- `TournamentMatchResultDto` (per outbox Local→Central: matchId, winner, resultData)

### 3.6 Central System — nuovi componenti

#### `domain/model`
- Le 5 classi POJO elencate in §3.1 + `TournamentFormat` enum.

#### `domain/ports/in`
- `CreateTournamentUseCase`, `OpenTournamentRegistrationUseCase`, `CancelTournamentUseCase`
- `RegisterTournamentParticipantUseCase` (gestisce registrazione singolo o squadra)
- `ScheduleTournamentMatchesUseCase` (genera bracket single-elimination)
- `GetTournamentUseCase`, `ListTournamentsUseCase`
- `GetTournamentStandingsUseCase`

#### `domain/ports/out`
- `TournamentRepository`, `TournamentBuildingRepository`
- `TournamentTeamRepository`, `TournamentParticipantRepository`
- `TournamentMatchRepository`, `TournamentStandingRepository`
- `TournamentMatchOutboxPort` (pubblica `TOURNAMENT_MATCH_SCHEDULED` ai Local)

#### `application/service`
- `TournamentService` (CRUD, lifecycle)
- `TournamentRegistrationService` (singolo/squadra; valida team_size coerente con `game_definitions.team_allowed` e `team_size`)
- `TournamentBracketService` (algoritmo single-elimination: byes se numero potenza di 2 non perfetto, generazione match round-by-round man mano che i vincitori sono noti)
- `TournamentStandingsService` (ricalcolo `tournament_standings` dopo ogni match completato; final rank quando `status=COMPLETED`)

#### `infrastructure/adapters/out/mysql`
- 6 JPA entities + 6 repositories + 6 adapters + 6 mappers.
- Tutte senza `@GeneratedValue` né relazioni JPA (String FK).

#### `infrastructure/adapters/in/rest`
- `TournamentController` (`/api/tournaments`)
  - `POST /` → `PLATFORM_ADMIN` (crea torneo)
  - `POST /{id}/open` → `PLATFORM_ADMIN` (apre registrazione)
  - `POST /{id}/cancel` → `PLATFORM_ADMIN`
  - `POST /{id}/schedule` → `PLATFORM_ADMIN` (genera bracket)
  - `GET /` → `authenticated` (lista)
  - `GET /{id}` → `authenticated`
  - `GET /{id}/standings` → `authenticated`
  - `GET /{id}/matches` → `authenticated`
- `TournamentRegistrationController` (`/api/tournaments/{id}/participants`)
  - `POST /` → `PLAYER` (singolo) o `captain=userId` in body (squadra)
  - `DELETE /` → `PLAYER` o `captain`
  - `GET /` → `authenticated`

#### `infrastructure/adapters/out/rest`
- Estendere `LocalRestAdapter` (o nuovo `LocalTournamentPushAdapter`) per `POST {local}/internal/tournaments/matches` con `X-Internal-Api-Key`.

#### Estensione sync Central→Local
- `MetadataReplicationSchedulerService` (nuovo servizio schedulato `fixedDelay=300_000`) drena outbox degli eventi `TOURNAMENT_MATCH_SCHEDULED`, `LOCAL_ADMIN_BUILDING_*`, `GAME_DEFINITION_UPSERTED` e li spedisce ai soli Local coinvolti (per i match tornei) o a tutti (per metadati). Tracciamento via `replication_progress` esistente.

#### Estensione `SyncEventProcessor` (ricezione Local→Central)
- Aggiungere handler `TOURNAMENT_MATCH_COMPLETED`: aggiorna `tournament_matches.winner/played_at/result_data/status=COMPLETED`, ricalcola `tournament_standings`, determina il match successivo del bracket e scrive nuovo evento outbox `TOURNAMENT_MATCH_SCHEDULED`.

### 3.7 Local Server — nuovi componenti

#### `domain/model`
- `TournamentMatchLocal` (replica read-only di `tournament_matches_local`).
- Estensione di `GameSession` con `tournamentMatchId: TournamentMatchId?` e `tournamentId: TournamentId?`.

#### `domain/ports/out`
- `TournamentMatchLocalRepository`

#### `application/service`
- `TournamentMatchLocalSyncService` — applica gli eventi `TOURNAMENT_MATCH_SCHEDULED` ricevuti dal Central (upsert su `tournament_matches_local`).
- Estensione di `GameSessionService.start` per accettare `tournamentMatchId` (opzionale):
  - Valida che il match sia `SCHEDULED` e appartenga a questo building.
  - Valida che il richiedente sia tra i partecipanti (singolo o membro della squadra).
  - Marca `tournament_matches_local.status = IN_PROGRESS`.
  - Se match a squadre → i partecipanti della `GameSession` sono i membri della squadra (leggendo `tournament_teams` replicato; oppure semplificazione: registra solo il `team_id` come winner nel `TeamResult`).
- Estensione di `GameSessionService.end`:
  - Se `tournamentMatchId != null` → oltre a `GAME_SESSION_COMPLETED`, emette nuovo outbox `TOURNAMENT_MATCH_COMPLETED` con `{matchId, winner, resultData}`.
  - Aggiorna `tournament_matches_local.status = COMPLETED`.
- Estensione di `SessionAbortHelper.abort`:
  - Se sessione legata a match torneo → emette `TOURNAMENT_MATCH_COMPLETED` con `winner=null, status=ABANDONED`.

#### `infrastructure/adapters/out/mysql`
- `TournamentMatchLocalJpaEntity`, `TournamentMatchLocalJpaRepository`, `TournamentMatchLocalRepositoryAdapter`, `*Mapper`.
- Estensione `GameSessionJpaEntity` con due nuovi campi `tournament_match_id`, `tournament_id` (String, niente FK).

#### `infrastructure/adapters/in/rest`
- Estensione `CreateSessionRequestDto` (in `shared-dto`) con campo opzionale `tournamentMatchId`.
- Estensione `GameSessionController.start` per accettare il nuovo campo.
- `InternalTournamentController` (`/internal/tournaments`)
  - `PUT /matches/sync` (riceve batch da Central) → `InternalApiKeyFilter`.
- `PlayerTournamentController` (`/api/players/tournaments`)
  - `GET /me/matches` → `PLAYER`: elenco dei match torneo assegnati all'utente (singolo o come membro squadra) su questo edificio.
  - `POST /matches/{matchId}/start` → `PLAYER`: scorciatoia per avviare la sessione legata al match.

### 3.8 `shared-domain/game` — nuovo GameResult per squadre

- Aggiungere `TeamResult implements GameResult` con `winnerTeamId: TeamId`, `getWinnerIds()` ritorna i membri della squadra (opzionale). Aggiornare `GameFactory` per scegliere `TeamResult` quando `GameSession.tournamentMatchId != null && match.teamBased`.
- Aggiornare il mixin `@JsonSubTypes` in `MqttPayloadSerializer` per includere `TeamResult`.

### 3.9 Game Client Emulator — UI

- `infrastructure/ui/TournamentsView`:
  - Lista tornei aperti (`GET /api/tournaments?status=OPEN_REGISTRATION`).
  - Dettaglio + registrazione (`POST /api/tournaments/{id}/participants`).
  - "I miei match" (`GET /api/players/tournaments/me/matches`).
  - "Avvia match torneo" → chiama `POST /api/players/tournaments/matches/{matchId}/start` (avvia sessione legata).
- `infrastructure/ui/MyStatisticsView`:
  - Chiama `GET /api/players/me/statistics` (Central o Local).
- Estendere `MainView` con voci di menu "Tornei" e "Le mie statistiche".

---

## 4. Fasi di implementazione

Le fasi sono ordinate per minimizzare dipendenze. Ogni fase è una PR reviewabile indipendentemente.

### FASE 0 — Setup, enum `Role`, migrazione ruoli
**Obiettivo**: introdurre `Role` enum e migrare USER/ADMIN legacy senza rompere i test.
- [ ] `shared-domain`: nuovo `Role` enum con `parse`/`format`.
- [ ] `central-system`:
  - [ ] Aggiornare `UserService.register` (`:69`) → `Role.PLAYER`.
  - [ ] Aggiornare `JwtTokenProvider` per emettere `roles` da `Set<Role>`.
  - [ ] Aggiornare `JwtAuthenticationFilter` per usare `Role.parse`.
  - [ ] Sostituire `hasRole('ADMIN')` → `hasRole('PLATFORM_ADMIN')` in `StatisticsController`.
- [ ] `local-server`: stesso aggiornamento JWT + sostituire `hasRole('USER')` → `hasRole('PLAYER')` nei controller.
- [ ] `infrastructure/mysql-central/init.sql` + `init-building-{2,3}.sql`: aggiungere migrazione `USER`→`PLAYER`, `ADMIN`→`PLATFORM_ADMIN`.
- [ ] Test: estendere `AuthServiceTest` per verificare `PLAYER` nel token.
- [ ] Verifica: `mvn -pl shared-domain,central-system,local-server test` verde; `MultiBuildingEndToEndIT` verde.

### FASE 1 — Amministratore del Locale (binding + enforcement)
**Obiettivo**: admin locale può gestire i giochi/dispositivi solo del proprio building.
- [x] Central: `local_admin_buildings` table + entity/repo/adapter/mapper.
- [x] Central: `LocalAdminBuildingService`, `LocalAdminController`, use-cases.
- [x] Central: outbox `LOCAL_ADMIN_BUILDING_ASSIGNED`/`_REVOKED`; estendere `MetadataReplicationSchedulerService` (o `UserReplicationSchedulerService`). *(decisione §5 D2: esteso `UserReplicationSchedulerService` + nuovo `PushMetadataToLocalServersPort`/`LocalMetadataRestAdapter`; `LateRegistrationCatchUpService` esteso per replay metadata. Dettagli in `workflow/architettura_classi.md` §10)*
- [x] Central: `JwtTokenProvider` arricchisce claim `buildings` per `LOCAL_ADMIN`. *(decisione §5 A3: claim `buildings` NON aggiunto al JWT (né central né local); enforcement delegata a `LocalAdminBuildingAuthorizationManager` che consulta la tabella replicata. `TokenProviderPort`/`JwtTokenProvider` invariati. Il claim `buildings` si aggiungerà in una fase futura quando un LOCAL_ADMIN dovrà chiamare endpoint centrali scoped. Dettagli in `workflow/architettura_classi.md` §10)*
- [x] Local: `local_admin_buildings_local` + entity/repo/adapter.
- [x] Local: `LocalAdminBuildingSyncService` + endpoint `PUT /internal/metadata/sync` (o riusare `/internal/users/sync` con payload esteso).
- [x] Local: `LocalAdminBuildingAuthorizationManager` bean.
- [x] Local: `AdminLocalController` con `GET /devices`, `GET /sessions/active`, `GET /statistics` (admin view), `POST /games`, `PUT /games/{gameId}`, `DELETE /games/{gameId}`. *(POST /games valida contro enum `GameType` in FASE 1 — decisione §5 C1; FASE 2 rafforzerà con `game_definitions_local`. PUT /games modifica nome+status — decisione §5 B1: `Game.name` reso non-final + metodo domain `rename(String)`)*
- [x] Test: unit per `LocalAdminBuildingService`; IT per `LocalAdminController` (H2 mode). *(IT come slice test `MockMvcBuilders.standaloneSetup` — il `@SpringBootTest` completo non è viable per il local: il contesto eagerly istanzia il client MQTT che si connette al broker; pattern già adottato dai test locali esistenti)*
- [x] Documentazione: aggiornare `REQUIREMENTS.md` con RF-UT-LA-01..04. *(RF-AU-05 aggiornato a 4 ruoli + nuova sezione 1.1.bis con RF-UT-LA-01..04 + matrice §6.1 + endpoint RI-02/RI-03 + schema §4.1/§4.2; `workflow/architettura_classi.md` esteso con sezione 10 FASE 1 — decisioni A3/B1/C1/D2 + deviazioni accessorie + matrice file + contract surface + schema + endpoint + follow-up)*

### FASE 2 — Amministratore del Gioco (game definitions)
**Obiettivo**: game admin può definire nuove tipologie e regole di registrazione.
- [x] Central: `game_definitions` table + entity/repo/adapter/mapper + seed iniziale. *(implementato: `GameDefinitionJpaEntity`/`JpaRepository`/`RepositoryAdapter`/`Mapper`; seed 7 riga per CHESS/FOOSBALL/DARTS/MONOPOLY/RISK/SLOT_MACHINE/ROULETTE in `infrastructure/mysql-central/init.sql`; `registration_rules` JSON mappato via ObjectMapper nel mapper)*
- [x] Central: `GameDefinitionService`, `GameAdminController`, use-cases. *(implementati `UpsertGameDefinitionUseCase`/`ListGameDefinitionsUseCase`; `GameAdminController` `/api/admin/games/definitions` con `POST/PUT` `@PreAuthorize("hasRole('GAME_ADMIN')")` e `GET` authenticated; `GameDefinition` POJO + `GameDefinitionNotFoundException`/`InvalidGameDefinitionException`; mapping central `GlobalExceptionHandler` → 404/400)*
- [x] Central: outbox `GAME_DEFINITION_UPSERTED`. *(scritto atomicamente da `GameDefinitionService.writeOutboxEvent` con eventId UUID condiviso tra `OutboxEvent.id` e `GameDefinitionEventDto.eventId`; `UserReplicationSchedulerService` esteso con nuovo branch `replicateGameDefinitionEvent` + `isGameDefinitionEvent`; `LateRegistrationCatchUpService` esteso con aggiunta di `GAME_DEFINITION_UPSERTED` a `REPLICATION_EVENT_TYPES` e nuovo branch)*
- [x] Local: `game_definitions_local` + sync service. *(implementati `GameDefinitionLocalJpaEntity`/`JpaRepository`/`RepositoryAdapter`/`Mapper` + `GameDefinitionLocal` POJO + `GameDefinitionLocalRepository`; `GameDefinitionSyncService.applyEvents(List<GameDefinitionEventDto>)` upsert idempotente per PK `game_type`; endpoint receiver `InternalGameDefinitionController` `PUT /internal/metadata/game-definitions/sync` — endpoint dedicato separato da FASE 1 `/internal/metadata/sync` per preservare firme preesistenti; push via nuovo `PushGameDefinitionToLocalServersPort` + `LocalGameDefinitionRestAdapter` parallelo a `LocalMetadataRestAdapter`)*
- [x] Local: `GameSessionService.start` valida `participants.size()` e `teamBased` contro `game_definitions_local`. *(implementato: il blocco di validazione legge `gameDefinitionLocalRepository.findByGameType(gameType)`; se presente usa `getMinPlayers/getMaxPlayers`; assente (offline-first / replica non ancora pervenuta) fallback a `GameFactory.createGame(...)` preservando il comportamento FASE 1; `team_allowed` rinviato al contesto torneo FASE 6; signature di `start(...)` invariata; `AdminLocalController POST /games` rafforzato (sostituzione C1) con `existsByGameType` → 400 `GameDefinitionNotAvailableLocallyException`)*
- [x] Test: unit per `GameDefinitionService`; IT per `GameAdminController`. *(GameDefinitionServiceTest 6 test; GameAdminControllerTest 4 test slice MockMvc; 14 test FASE 1 retrofit (7 central scheduler/catchup + 7 local session/admin) aggiornati con mock `PushGameDefinitionToLocalServersPort`/`GameDefinitionLocalRepository` — build verde su shared-domain/dto/mqtt + central-system + local-server)*
- [x] Documentazione: RF-UT-GA-01..03. *(RF-UT-GA-01..03 aggiunti a `documenti/REQUIREMENTS.md` §1.1.ter + matrice §6.1 + schema §4.1/§4.2 + endpoint RI-02/RI-03; `workflow/architettura_classi.md` esteso con §11 FASE 2 — decisioni E1-E5 + matrice file + contract surface + schema + endpoint + follow-up)*

### FASE 3 — Statistiche del Giocatore
**Obiettivo**: il giocatore consulta le proprie statistiche (globali e locali).
- [x] Central: `player_match_facts` + `player_statistics` tables.
- [x] Central: estendere payload `GAME_SESSION_COMPLETED` per includere `participants` + `winnerId` espliciti.
- [x] Central: estendere `SyncEventProcessor.handleGameSessionCompleted` per popolare i nuovi read-model.
- [x] Central: `PlayerStatisticsService`, `PlayerStatisticsController` (`/api/players/me/statistics`, `/api/players/{userId}/statistics`).
- [x] Local: estendere `StatisticsService.getPlayerStatistics(userId)`; endpoint `GET /api/players/me/statistics`.
- [x] Test: unit per `PlayerStatisticsService`; IT per `PlayerStatisticsController`.
- [x] Documentazione: RF-UT-PL-01..02. *(RF-UT-PL-01..02 aggiunti a `documenti/REQUIREMENTS.md` §1.1.quater + update RF-SE-02 (payload arricchito) + matrice §6.1 + schema §4.1 central + endpoint RI-02/RI-03; `workflow/architettura_classi.md` esteso con §12 FASE 3 — decisioni D1-D7 + matrice file + contract surface + schema + endpoint + concorrenza + follow-up)*

### FASE 4 — Dominio Torneo (CRUD + registrazione)
**Obiettivo**: amministratore crea tornei; giocatori/squadre si iscrivono.
- [x] `shared-domain`: `TournamentId`, `TeamId`, `TournamentMatchId`, `TournamentStatus`, `TournamentMatchStatus`, `TournamentFormat`, 5 eventi. *(11 file: 3 ID record accessore `.value()` (C.1); 3 enum puri `model/`; 5 event record `implements DomainEvent` con letterale inline — PURE declarations, NO outbox emission in FASE 4 (C.13))*
- [x] `shared-dto`: tutti i DTO tornei (§3.5). *(10 record: 3 validati con Jakarta, 7 vanilla; `TournamentMatchResultDto` esteso a 4 campi con `status` per disambiguare ABANDONED vs COMPLETED-null-winner per §3.7 line 524 — deviazione C.12 LOCKED)*
- [x] Central: 6 entities + 6 repos + 6 adapters + 6 mappers + 6 tabelle `init.sql`. *(Deviazione C.2 LOCKED: 7 JPA entities + 4 IdClass + 7 JpaRepos per modellare `tournament_team_members` come entità standalone `@IdClass` mirror di `SessionParticipantJpaEntity` — NO `@OneToMany` preserva RNF-08; adapter `TournamentTeamRepositoryAdapter` inietta 2 JpaRepos e scrive atomic delete-all-then-insert in `@Transactional`. Conti effettivi: 6 porte dominio, 6 adapter, 6 mapper, 7 entities, 4 IdClass, 7 JpaRepos, 7 tabelle in `init.sql` linee 167-248. `TournamentMatchRepository`/`TournamentStandingRepository` create come scaffolding (C.8) NON invocate da service FASE 4)*
- [x] Central: `TournamentService`, `TournamentRegistrationService`, controller. *(2 service `@Service @Transactional` + Clock; 2 controller `TournamentController` 5 endpoint + `TournamentRegistrationController` 3 endpoint; `GlobalExceptionHandler` esteso con 5 handler 400/400/404/409/409. NO outbox (C.13). Captain via `CurrentUserService` (C.4) incluso in `teamMembers.size()==teamSize`. Validate `teamBased` vs `game_definitions.team_allowed` (C.5 typo §3.6 line 472 interpretato come: typo, validazione only `team_allowed`). Tournament transition methods sul POJO `openRegistration()`/`cancel()` ritornano NUOVA istanza immutabile (C.6))*
- [x] Test: unit per `TournamentService`/`TournamentRegistrationService`; IT per i controller. *(TournamentServiceTest 9 unit, TournamentRegistrationServiceTest 7 unit, TournamentControllerTest 7 slice MockMvc, TournamentRegistrationControllerTest 4 slice MockMvc — 27 test verdi; regression 298 central + 594 local verdi)*
- [x] Documentazione: RF-TO-01..04. *(RF-TO-01..04 aggiunti a `documenti/REQUIREMENTS.md` §1.1.quinquies + matrice §6.1 + schema §4.1 (7 tabelle) + endpoint RI-02; `workflow/architettura_classi.md` esteso con §13 FASE 4 — decisioni D1-D15 + matrice file 83 totali + contract surface + schema + endpoint + backward-compat + concorrenza + follow-up)*

### FASE 5 — Bracket e classifiche
**Obiettivo**: generazione match e calcolo classifica.
- [x] Central: `TournamentBracketService` (single-elimination con byes). *(implementato: `TournamentBracketService.java` `@Service @Transactional` implements `ScheduleTournamentMatchesUseCase` + `ListTournamentMatchesUseCase`; algoritmo single-elimination con `bracketSize = nextPow2(N)`, `byes = bracketSize - N`; convenzione top-seeds-get-byes (partecipanti sort-ati per `registeredAt` ASC; seed 1..byes ricevono righe `BYE` con `participantB=null, status=BYE, winner=participantA`; restanti accoppiati lowest-remaining vs highest-remaining); `publishScheduled` emesso SOLO per match `SCHEDULED` (mai `BYE`); atomicità outbox `@Transactional` class-level; transition `OPEN_REGISTRATION → IN_PROGRESS` via `Tournament.startProgress()`; guard `SINGLE_ELIMINATION`-only con `InvalidTournamentStateException`; guard minimo 2 partecipanti. Dettagli in `workflow/architettura_classi.md` §14)*
- [x] Central: `TournamentStandingsService`. *(implementato: `TournamentStandingsService.java` `@Service @Transactional` implements `GetTournamentStandingsUseCase`; `getStandings(tournamentId)` read+sort (`points desc, wins desc, participantId asc`) con `displayName` risolto via `TournamentParticipantRepository.findByTournament`; package-visible `seedStandings(tournamentId, participantIds)` zero-init idempotente (skip se `findByTournamentAndParticipantId` presente); `@Transactional(readOnly = true)` method-level su `getStandings`. FASE 6 aggiungerà `recomputeAfterCompletion(matchId)` + final rank. Dettagli in `workflow/architettura_classi.md` §14)*
- [x] Central: `POST /api/tournaments/{id}/schedule`, `GET /api/tournaments/{id}/standings`. *(implementati su `TournamentController.java`: `POST /{id}/schedule` `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` body vuoto → 200 + `List<TournamentMatchDto>` (righe BYE + SCHEDULED ordinate per `bracketPosition`); `GET /{id}/standings` authenticated → 200 + `List<TournamentStandingDto>`. AGGIUNTIVO per decisione §13.2 D14 + ambiguity A (locked): `GET /{id}/matches` authenticated → 200 + `List<TournamentMatchDto>` read-only delegation a `TournamentMatchRepository.findByTournament`; mappature eccezioni `InvalidTournamentStateException`→400, `TournamentNotFoundException`→404 già presenti in `GlobalExceptionHandler`. Dettagli in `workflow/architettura_classi.md` §14)*
- [x] Test: unit copertura completa del bracket (8, 7, 6, 5, 4, 3, 2 partecipanti → byes corretti). *(30 nuovi test: `TournamentBracketServiceTest` 15 test (uno per N∈{2,3,4,5,6,7,8} con asserzioni su #BYE/#SCHEDULED/accoppiamenti + transizione `IN_PROGRESS` + guard `ROUND_ROBIN` + guard minimo 2 partecipanti + convenzione top-seeds-get-byes con input shuffled + outbox discipline + seed standings per tutti N + listing); `TournamentStandingsServiceTest` 8 test (sort multi-key, displayName resolution, empty cases, seed idempotency, null safety); `TournamentMatchOutboxAdapterTest` 2 test (shared UUID outbox-id ↔ dto.eventId + payload JSON round-trip con JavaTimeModule); `TournamentControllerTest` esteso con 5 nuovi test slice MockMvc (200/200/200/400/404). Regression: `mvn test -pl :central-system -am` → 328 test verdi, 0 failures)*
- [x] Documentazione: RF-TO-05..06. *(RF-TO-05..06 aggiunti a `documenti/REQUIREMENTS.md` §1.1.sextus + update lifecycle addendum (endpoint "DEFERRED a FASE 5" → "Implementati in FASE 5") + 3 nuovi endpoint rows in matrice RI-02 + 2 nuove righe RF-TO-05/RF-TO-06 in matrice §6.1; `workflow/architettura_classi.md` esteso con §14 FASE 5 — decisioni D1-D12 + matrice file 12 totali + contract surface + schema + endpoint + backward-compat + concorrenza + follow-up FASE 6)*

### FASE 6 — Integrazione Torneo ↔ Local Server
**Obiettivo**: i match di torneo sono giocati come sessioni locali e il risultato torna al Central.
- [ ] Local: `tournament_matches_local` + entity/repo/adapter; estensione `game_sessions` con `tournament_match_id`/`tournament_id`.
- [ ] Local: `TournamentMatchLocalSyncService` + `InternalTournamentController` (`PUT /internal/tournaments/matches/sync`).
- [ ] Local: `PlayerTournamentController` (`GET /me/matches`, `POST /matches/{id}/start`).
- [ ] Local: estendere `GameSessionService.start/end` per `tournamentMatchId`; emettere outbox `TOURNAMENT_MATCH_COMPLETED`.
- [ ] Local: estendere `SessionAbortHelper` per `TOURNAMENT_MATCH_COMPLETED` con `status=ABANDONED`.
- [ ] `shared-domain`: `TeamResult implements GameResult` + `GameFactory` aggiornato + mixin in `MqttPayloadSerializer`.
- [ ] Central: `MetadataReplicationSchedulerService` spedisce `TOURNAMENT_MATCH_SCHEDULED` ai Local coinvolti.
- [ ] Central: `SyncEventProcessor` gestisce `TOURNAMENT_MATCH_COMPLETED` → aggiorna match, ricalcola standings, genera match successivo, eventualmente completa torneo.
- [ ] Test: IT end-to-end su H2 con 2 building e un torneo single-elimination a 4 partecipanti.
- [ ] Documentazione: RF-TO-07..10.

### FASE 7 — Game Client UI
**Obiettivo**: il client espone le nuove funzionalità.
- [ ] `TournamentsView`, `MyStatisticsView`, voci di menu in `MainView`.
- [ ] `PlayerTournamentFlow` service sul client (chiama local REST).
- [ ] Test manuali con `docker-compose up` (singolo + multi-building).
- [ ] Documentazione: aggiorare `IMPLEMENTATION.md` con la nuova UI.

### FASE 8 — Docs, smoke test, requirements
- [ ] Estendere il `README.md` "Smoke test" con uno scenario torneo end-to-end.
- [ ] Estendere `e2e-tests` con uno smoke torneo (simile a `MultiBuildingEndToEndIT`).

---

## 5. Requisiti funzionali proposti (bozza per `REQUIREMENTS.md`)

### 5.1 Modulo: Utenti e Ruoli (RF-UT)

| ID | Priorità | Descrizione |
|---|---|---|
| RF-UT-01 | M | 4 ruoli: PLAYER, LOCAL_ADMIN, GAME_ADMIN, PLATFORM_ADMIN. |
| RF-UT-02 | M | PLATFORM_ADMIN assegna/revoca ruoli (`POST /api/admin/roles/assign`). |
| RF-UT-03 | M | PLATFORM_ADMIN assegna building a un LOCAL_ADMIN; claim JWT `buildings`. |
| RF-UT-LA-01 | M | LOCAL_ADMIN gestisce i giochi nel `game_catalog` del proprio building. |
| RF-UT-LA-02 | M | LOCAL_ADMIN monitora le sessioni in corso del proprio building. |
| RF-UT-LA-03 | S | LOCAL_ADMIN consulta le statistiche aggregate del proprio building. |
| RF-UT-LA-04 | M | Enforcement offline: il binding admin/building è replicato e disponibile offline. |
| RF-UT-GA-01 | M | GAME_ADMIN definisce nuove tipologie di gioco (`game_definitions`). |
| RF-UT-GA-02 | M | GAME_ADMIN configura le regole di registrazione delle partite (min/max players, team_allowed). |
| RF-UT-GA-03 | S | Le `game_definitions` sono replicate ai Local per validazione offline. |
| RF-UT-PL-01 | M | PLAYER consulta le proprie statistiche globali (`/api/players/me/statistics`). |
| RF-UT-PL-02 | S | PLAYER consulta le proprie statistiche locali. |
| RF-UT-PA-01 | M | PLATFORM_ADMIN gestisce utenti e locali (esistente, da rifattorizzare). |
| RF-UT-PA-02 | M | PLATFORM_ADMIN consulta statistiche globali (esistente, renaming). |

### 5.2 Modulo: Gestione Tornei (RF-TO)

| ID | Priorità | Descrizione |
|---|---|---|
| RF-TO-01 | M | PLATFORM_ADMIN crea un torneo specificando nome, gameType, teamBased, teamSize, edifici coinvolti, finestra temporale. |
| RF-TO-02 | M | Un torneo coinvolge ≥2 edifici e riguarda un solo `gameType`. |
| RF-TO-03 | M | PLAYER si iscrive a un torneo individuale. |
| RF-TO-04 | M | PLAYER (capitano) iscrive una squadra di `teamSize` membri a un torneo a squadre; il sistema registra la squadra, non i singoli. |
| RF-TO-05 | M | PLATFORM_ADMIN genera il bracket single-elimination con byes. |
| RF-TO-06 | M | Il sistema espone la classifica (`tournament_standings`) aggiornata dopo ogni match. |
| RF-TO-07 | M | Il Central replica i match programmati ai Local coinvolti via outbox `TOURNAMENT_MATCH_SCHEDULED`. |
| RF-TO-08 | M | Il Local avvia una sessione legata a un match (`tournamentMatchId`); valida partecipante. |
| RF-TO-09 | M | Al termine della sessione, il Local emette outbox `TOURNAMENT_MATCH_COMPLETED` con `winner`. |
| RF-TO-10 | M | Il Central consuma `TOURNAMENT_MATCH_COMPLETED`, aggiorna match/standings, programma il match successivo del bracket. |
| RF-TO-11 | S | Quando tutti i match si concludono, `Tournament.status=COMPLETED` e il rank finale è calcolato. |
| RF-TO-12 | S | Partita a squadre: il `GameResult` è `TeamResult` con `winnerTeamId`; i singoli membri non sono registrati come vincitori. |
| RF-TO-13 | C | Formati alternativi: `ROUND_ROBIN` (Could-Have). |

---

## 6. Matrice di tracciabilità estesa (proposta)

| Requisito | Modulo | File chiave (previsti) | Fase |
|---|---|---|---|
| RF-UT-01 | shared, central, local | `Role.java`, `JwtTokenProvider.java`, `init.sql` | F0 |
| RF-UT-02 | central | `UserRoleController.java`, `UserRoleService.java` | F0 |
| RF-UT-03 | central, local | `LocalAdminController.java`, `LocalAdminBuildingSyncService.java` | F1 |
| RF-UT-LA-01..04 | local | `AdminLocalController.java`, `LocalAdminBuildingAuthorizationManager.java` | F1 |
| RF-UT-GA-01..03 | central, local | `GameAdminController.java`, `GameDefinitionSyncService.java` | F2 |
| RF-UT-PL-01..02 | central, local | `PlayerStatisticsController.java`, `StatisticsService.getPlayerStatistics` | F3 |
| RF-UT-PA-01..02 | central | rifattorizzazione esistenti | F0–F1 |
| RF-TO-01..04 | central | `TournamentController.java`, `TournamentRegistrationController.java` | F4 |
| RF-TO-05..06 | central | `TournamentBracketService.java`, `TournamentStandingsService.java` | F5 |
| RF-TO-07..10 | central, local | `MetadataReplicationSchedulerService.java`, `InternalTournamentController.java`, `GameSessionService.end` | F6 |
| RF-TO-11 | central | `TournamentService.completeIfDone` | F6 |
| RF-TO-12 | shared, local | `TeamResult.java`, `GameFactory.java`, `MqttPayloadSerializer` | F6 |
| RF-TO-13 | central | `TournamentBracketService` (strategia pluggabile) | F8+ |

---

## 7. Rischi e mitigazioni

| Rischio | Impatto | Mitigazione |
|---|---|---|
| Token JWT emessi prima della migrazione ruoli non più validi. | Medio | Documentare la necessità di re-login; considerare una finestra "compatibilità" che mappa `USER`→`PLAYER` e `ADMIN`→`PLATFORM_ADMIN` in `JwtAuthenticationFilter` durante la fase 0. |
| Schema drift su DB di dev già popolati (le `ALTER TABLE` su `game_sessions`). | Medio | `docker-compose down -v` obbligatorio; documentato nel README. |
| Race sul bracket quando più match dello stesso round terminano contemporaneamente. | Alto | Il `SyncEventProcessor` usa già `@Lock(PESSIMISTIC_WRITE)` per le statistiche; applicare lo stesso pattern al `TournamentRepository` per il match successivo del bracket. |
| `game_definitions.team_allowed` non rispettato in `GameSessionService.start` legacy. | Medio | Aggiungere validazione in FASE 2 con test dedicato. |
| Crescita outbox Central senza purge (POF-3 residuo). | Medio | Estendere `OutboxPurgeService` (attualmente solo Local) anche al Central per gli eventi SENT > retention. |
| Replicazione `TOURNAMENT_MATCH_SCHEDULED` verso Local offline al momento della pubblicazione. | Basso | `LateRegistrationCatchUpService` (già esistente per USER_REGISTERED) viene esteso ai nuovi eventi. |
| Partecipante di un match torneo non presente in `replicated_users` del Local scelto. | Medio | Garantire che la registrazione al torneo avvenga solo se l'utente è già noto al Central (lo è); per il Local, il match porta `participant_id` (UUID) e l'identità è verificata dal JWT del giocatore che avvia la sessione. |

---

## 8. Criteri di "Done" per fase

Una fase è considerata completata quando:

1. Codice compilato (`mvn -pl <module> compile`) e test unitari verdi.
2. Test di integrazione (H2) verdi per ogni nuovo controller/service.
3. `init.sql` aggiornato per central e per `init-building-2.sql`/`init-building-3.sql` se rilevante.
4. `docker-compose up -d --build` parte senza errori (almeno per le fasi che toccano infrastruttura).
5. `REQUIREMENTS.md` aggiornato con i nuovi RF corrispondenti.
6. `workflow/workflow.md` aggiornato con le nuove checkbox in stile esistente.

---

*Fine del piano.*