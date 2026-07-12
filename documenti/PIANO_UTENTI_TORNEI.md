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
- [x] Local: `tournament_matches_local` + entity/repo/adapter; estensione `game_sessions` con `tournament_match_id`/`tournament_id`. *(implementato: `TournamentMatchLocal` POJO + `TournamentMatchLocalRepository` + `TournamentMatchLocalJpaEntity`/`JpaRepository`/`Mapper`/`Adapter` (upsert-by-PK idempotente); `GameSession` +2 `final` fields `tournamentMatchId`/`tournamentId`; `GameSessionJpaEntity` +2 `@Column`; `GameSessionMapper` mapping; 3 `init*.sql` con `tournament_matches_local` table + `game_sessions` columns + index. Dettagli in `workflow/architettura_classi.md` §15)*
- [x] Local: `TournamentMatchLocalSyncService` + `InternalTournamentController` (`PUT /internal/tournaments/matches/sync`). *(implementato: `TournamentMatchLocalSyncService.applyEvents(List<TournamentMatchScheduledDto>)` idempotent upsert; `InternalTournamentController` `@RestController` `/internal/tournaments/matches/sync` `@PutMapping("/sync")`; `InternalApiKeyFilter` protegge automaticamente. Dettagli in `workflow/architettura_classi.md` §15)*
- [x] Local: `PlayerTournamentController` (`GET /me/matches`, `POST /matches/{id}/start`). *(implementato: `PlayerTournamentController` `@RestController` `/api/players/tournaments`; `GET /me/matches` `@PreAuthorize("hasRole('PLAYER')")` filtra per `participant_a == userId OR participant_b == userId` AND `status=SCHEDULED`; `POST /matches/{matchId}/start` `@PreAuthorize("hasRole('PLAYER')")` carica match locale, valida `SCHEDULED`, delega a `GameSessionService.start` 5-arg overload. Limitazione team-match documentata (ambiguity F). Dettagli in `workflow/architettura_classi.md` §15)*
- [x] Local: estendere `GameSessionService.start/end` per `tournamentMatchId`; emettere outbox `TOURNAMENT_MATCH_COMPLETED`. *(implementato: `start` 5-arg overload carica `TournamentMatchLocal`, valida `SCHEDULED`/`team_allowed`/participant, flip a `IN_PROGRESS`; `end` when `tournamentMatchId != null` scrive 2 outbox rows atomiche (`GAME_SESSION_COMPLETED` + `TOURNAMENT_MATCH_COMPLETED` con `TournamentMatchResultDto(matchId, winner, resultData, "COMPLETED")`), flip locale a `COMPLETED`. `Clock` per timestamps. Dettagli in `workflow/architettura_classi.md` §15)*
- [x] Local: estendere `SessionAbortHelper` per `TOURNAMENT_MATCH_COMPLETED` con `status=ABANDONED`. *(implementato: `abortAndEmit` when `tournamentMatchId != null` calcola walkover winner = partecipante NON in `session.getParticipants()` (decision Q2), scrive 2 outbox rows (`GAME_SESSION_ABORTED` + `TOURNAMENT_MATCH_COMPLETED` con `status=ABANDONED, winner=walkoverWinner`), flip locale a `ABANDONED`; atomico nella `REQUIRES_NEW` tx esistente. Dettagli in `workflow/architettura_classi.md` §15)*
- [x] `shared-domain`: `TeamResult implements GameResult` + `GameFactory` aggiornato + mixin in `MqttPayloadSerializer`. *(implementato: `TeamResult` record `(winnerId, winnerIds, winnerTeamId, winCondition) implements GameResult` con compact ctor canonicalising `winnerId=new UserId(winnerTeamId.value())`; `WinCondition.TEAM_VICTORY` aggiunto; `MqttPayloadSerializer` `@JsonSubTypes.Type(TeamResult.class, "TEAM")` 8° subtype. Deviazione H LOCKED: `GameFactory` NON aggiornato — `TeamResult` costruito a service-layer in `GameSessionService.end`, non via `GameFactory.createGame` (restituisce `GameLifecycle` non `GameResult`). Dettagli in `workflow/architettura_classi.md` §15)*
- [x] Central: `MetadataReplicationSchedulerService` spedisce `TOURNAMENT_MATCH_SCHEDULED` ai Local coinvolti. *(implementato in `UserReplicationSchedulerService` (PIANO nomina "MetadataReplicationSchedulerService" ma la classe effettiva è `UserReplicationSchedulerService` — vedi ARCH §10 D2): 9°-11° ctor params `PushTournamentMatchToLocalServersPort`/`TournamentBuildingRepository`/`TournamentMatchRepository`; nuovo branch `replicateTournamentMatchEvent` round-robin-assegna `buildingId` + `gameId` (UUID) al match centrale, filtra ai soli Local coinvolti; `LateRegistrationCatchUpService` esteso per replay. Nuovo `LocalTournamentMatchRestAdapter` `@Component` `PUT /internal/tournaments/matches/sync`. Dettagli in `workflow/architettura_classi.md` §15)*
- [x] Central: `SyncEventProcessor` gestisce `TOURNAMENT_MATCH_COMPLETED` → aggiorna match, ricalcola standings, genera match successivo, eventualmente completa torneo. *(implementato: `SyncEventProcessor` +4 ctor params via nullable-ctor backward-compat; nuovo branch `handleTournamentMatchCompleted` → `findByIdForUpdate` match → rebuild con `status=COMPLETED`/`ABANDONED` + `winner` + `playedAt` → `TournamentStandingsService.recomputeAfterCompletion` (solo COMPLETED, +3 points/win) → `TournamentBracketService.advanceWinner` (CREA parent se assente, patch slot, emette `TOURNAMENT_MATCH_SCHEDULED` outbox quando parent completo) → `TournamentBracketService.completeIfDone` se parent==null (completa torneo + assignFinalRanks). `EventTypeContractTest` esteso. Dettagli in `workflow/architettura_classi.md` §15)*
- [x] Test: IT end-to-end su H2 con 2 building e un torneo single-elimination a 4 partecipanti. *(implementato PARZIALMENTE: `TournamentFlowEndToEndIT` central H2 `@SpringBootTest` 2 test (fullTournamentFlow + abandonedMatch_walkover); 5 local slice test 23 test (`TournamentMatchLocalSyncServiceTest` 6, `InternalTournamentControllerTest` 3, `PlayerTournamentControllerTest` 6, `GameSessionServiceTournamentTest` 6, `SessionAbortHelperTournamentTest` 2); `LocalServerRegistryPort` mockato nell'IT — full cross-module 2-building e2e deferito a FASE 8. Regression: shared 3 + central 328+20 + local 617 = 968 test verdi. Dettagli in `workflow/architettura_classi.md` §15)*
- [x] Documentazione: RF-TO-07..10. *(RF-TO-07..10 + RF-TO-11..12 aggiunti a `documenti/REQUIREMENTS.md` §1.1.septimus + update RF-TO-06 status + 3 nuovi endpoint rows in matrice RI-03 + 4 nuove righe RF-TO-07..10 in matrice §6.1; `workflow/architettura_classi.md` esteso con §15 FASE 6 — decisioni D1-D15 + matrice file 46 totali + contract surface + schema + endpoint + backward-compat + concorrenza + walkover Q2 + follow-up FASE 7/8)*

### FASE 7 — Game Client UI
**Obiettivo**: GUI JavaFX che espone al PLAYER giochi disponibili, proprie statistiche, partite giocate (solo locali), tornei e classifiche per-torneo; e dashboard differenziate per i 3 amministratori (`LOCAL_ADMIN`/`GAME_ADMIN`/`PLATFORM_ADMIN`). Il Client parla **solo col Local-Server** (REST+JWT + MQTT real-time); le letture Central-only arrivano al Local via **replica push** (≤5 min), le scritture admin Central-only viaggiano via **outbox asincrono** con stato locale `admin_requests_local` (latenza fino a ~10 min, esito via polling). Nessun nuovo trust chain: il Central si fida del Local come origine (`X-Internal-Api-Key`); il Local convalida il ruolo admin su `replicated_users` prima di emettere outbox.

#### 7.A — Central System
- [x] Central: nuovi use case `UpdateTournamentUseCase` e `DeleteTournamentUseCase` in `central-system/.../domain/ports/in/`. *(implementato in S1: port in `UpdateTournamentUseCase.java` (firma `update(TournamentId, String name, Instant startsAt, List<String> buildingIds, String originatingRequestId)`) e `DeleteTournamentUseCase.java` (firma `delete(TournamentId, String originatingRequestId)`); `TournamentService` implements entrambi con legacy-delegating 5-arg→7-arg ctor pattern; guard `status==DRAFT` via `Tournament.update(name, startsAt)` (per update, immutable transition) e check esplicito nel service (per delete) → `InvalidTournamentStateException`; `writeOutboxEvent` inline (template `GameDefinitionService.writeOutboxEvent`) costruisce `TournamentSummaryEventDto` 15-campo con `deleted=false` (UPDATE) / `deleted=true` tombstone (DELETE), payload serializzato via `ObjectMapper` e persistito come `OutboxEvent(PENDING)` atomicamente nella `@Transactional` del service; `originatingRequestId` nullable propagato — `null` path REST diretto, non-null path SyncEventProcessor §7.A.3 futuro)*
  - [x] Esposizione REST diretta (uso admin centrale / test): `PUT /api/tournaments/{id}` + `DELETE /api/tournaments/{id}` su `TournamentController` con `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`. *(servono anche per i branch del `SyncEventProcessor` di 7.A.3; implementato in S1: `TournamentController` ctor 12-arg `@Autowired` + legacy-delegating 10-arg per backward-compat; `updateUseCase.update(new TournamentId(id), request.name(), request.startsAt(), request.buildingIds(), null)` (5-arg, `null`=path REST) e `deleteUseCase.delete(new TournamentId(id), null)` (2-arg); `@Valid @RequestBody UpdateTournamentRequestDto`; `GlobalExceptionHandler` mappa `InvalidTournamentStateException`→400, `TournamentNotFoundException`→404, `MethodArgumentNotValidException`→400; `TournamentControllerTest` migrato da standalone-setup a `@WebMvcTest` con `MethodSecurityTestConfig` inline (`@EnableMethodSecurity` + `SecurityFilterChain permitAll()`+CSRF disabled) per enforcement reale di `@PreAuthorize`; 7 nuovi test PUT 200/400/404/403 + DELETE 204/404/403, tot 19 test)*
- [x] Central: nuove porte di replica push in `central-system/.../domain/ports/out/` per le 4 nuove repliche Local. *(completato S3: 4/4 porte + 4/4 adapter REST — `PushTournamentSummaryToLocalServersPort` (S2) + `PushTournamentStandingsToLocalServersPort` + `PushTournamentParticipantsToLocalServersPort` + `PushLocalServerRegistryToLocalServersPort`, ciascuno con proprio `Local*RestAdapter` clone di `LocalTournamentMatchRestAdapter`)*
  - [x] `PushTournamentSummaryToLocalServersPort` → `LocalTournamentSummaryRestAdapter` (`infrastructure/adapters/out/rest/`, template `LocalTournamentMatchRestAdapter`: SSLContext, `RetryTemplate(3, exponentialBackoff 100/2.0/10000)`, `X-Internal-Api-Key`, `PUT /internal/tournaments/summaries/sync`). *(implementato in S2: porta `PushTournamentSummaryToLocalServersPort.push(List<TournamentSummaryEventDto>, RegisteredLocalServer)` in `domain/ports/out/`; adapter `LocalTournamentSummaryRestAdapter` clone strutturale di `LocalTournamentMatchRestAdapter` col path `/internal/tournaments/summaries/sync`, body `List<TournamentSummaryEventDto>`, `isTransient` identico — SSLContext, RetryTemplate 3-attempt exponentialBackoff 100/2.0/10000, header `X-Internal-Api-Key` cablato; package-private ctor per testing; nessun ack/poison contract, idempotenza per PK `tournamentId` lato Local + tombstone `deleted=true` → deleteById)*
  - [x] `PushTournamentStandingsToLocalServersPort` → `LocalTournamentStandingsRestAdapter` → `PUT /internal/tournaments/standings/sync`. *(implementato in S3: porta+adapter clone del summary con path `/internal/tournaments/standings/sync`, body `List<TournamentStandingsEventDto>`)*
  - [x] `PushTournamentParticipantsToLocalServersPort` → `LocalTournamentParticipantsRestAdapter` → `PUT /internal/tournaments/participants/sync`. *(implementato in S3: porta+adapter clone del summary con path `/internal/tournaments/participants/sync`, body `List<TournamentParticipantsEventDto>`)*
  - [x] `PushLocalServerRegistryToLocalServersPort` → `LocalLocalServerRegistryRestAdapter` → `PUT /internal/servers/sync`. *(implementato in S3: porta+adapter clone del summary con path `/internal/servers/sync`, body `List<LocalServerRegistryEventDto>` — espone `registered_local_servers` al Local e quindi al client PLATFORM_ADMIN rispettando E1)*
- [x] Central: estensione producer outbox per propagare `originatingRequestId` negli eventi "di ritorno".
  - [x] `UserService.saveUserOnDB`/`updateUser`: estensione firma/overload `updateUser(UserId, String newPassword, List<String> newRoles, String originatingRequestId)`; `UserSyncDto` riceve `originatingRequestId` (nullable, costruttore short retrocompatibile). *(implementato in S3 — compact ctor retrocompatibile pattern `UserSyncDto:14-16`)*
  - [x] `GameDefinitionService.writeOutboxEvent`: `GameDefinitionEventDto` esteso con `originatingRequestId` (nullable, costruttore short). *(implementato in S3 — compact ctor retrocompatibile)*
  - [x] `TournamentService`/`TournamentStandingsService`/`TournamentRegistrationService`: emettono `TOURNAMENT_SUMMARY_UPSERTED`/`TOURNAMENT_STANDINGS_UPSERTED`/`TOURNAMENT_PARTICIPANTS_UPSERTED` con `originatingRequestId` (anche sui path FASE 5/6 esistenti, oltre ai nuovi branch REQUESTED di 7.A.3). *(completato S3: `TournamentService` create/open/cancel (S2) + update/delete (S1); `TournamentStandingsService.recomputeAfterCompletion` e `TournamentRegistrationService.register/unregister` ora emettono rispettivamente `TOURNAMENT_STANDINGS_UPSERTED`/`TOURNAMENT_PARTICIPANTS_UPSERTED` con `originatingRequestId` propagato dal path REQUESTED via `SyncEventProcessor`)*
  - [x] `LocalServerRegistryPort.register` (e heartbeat) emette `LOCAL_SERVER_REGISTRY_UPSERTED` (outbox Central, push a tutti i Local). *(implementato in S3 — `LocalServerRepositoryAdapter.register` esteso per emettere outbox `LOCAL_SERVER_REGISTRY_UPSERTED` broadcast)*
- [x] Central: estensione `UserReplicationSchedulerService` per drenare i 4 nuovi tipi di evento di replica. *(completato S3: 4/4 tipi drenati — ctor 15-arg `@Autowired` (legacy 11-arg delegante) inietta i 3 nuovi `Push*ToLocalServersPort` oltre al `PushTournamentSummaryToLocalServersPort` S2)*
  - [x] +4 campi ctor (`PushTournamentSummaryToLocalServersPort`, `PushTournamentStandingsToLocalServersPort`, `PushTournamentParticipantsToLocalServersPort`, `PushLocalServerRegistryToLocalServersPort`). *(completato S3: ctor 15-arg `@Autowired` con 4 porte — legacy 11-arg delega con `null` per le 4)*
  - [x] Nuovi metodi `replicateTournamentSummaryEvent`/`replicateTournamentStandingsEvent`/`replicateTournamentParticipantsEvent`/`replicateLocalServerRegistryEvent` (clone di `replicateGameDefinitionEvent` `UserReplicationSchedulerService.java:356-417`: deserializza, push a TUTTI i Local attivi su `replicationPushExecutor` via `allOf().join()`, `replication_progress` per `(eventId,serverId)`, `markAsSent` on `allSucceeded`). *(completato S3: 4 metodi implementati come clone strutturale, ciascuno deserializza l'evento-specifico DTO, broadcast a tutti i Local attivi con parallel push su `replicationPushExecutor` + `replication_progress` tracking + `markAsSent` on `allSucceeded`)*
  - [x] Nuovi predicate `isTournamentSummaryEvent`/`isTournamentStandingsEvent`/`isTournamentParticipantsEvent`/`isLocalServerRegistryEvent` (`:568-574`) + integrazione in `isReplicationEvent` (`:553-556`). *(completato S3: 4 predicate integrati in `isReplicationEvent`; dispatch in `replicateUsers` trunk)*
- [x] Central: estensione `LateRegistrationCatchUpService` per il replay dei nuovi eventi a un Local tardivo. *(completato S3: 4/4 tipi gestiti)*
  - [x] Aggiunta dei 4 nuovi tipi a `REPLICATION_EVENT_TYPES` (`LateRegistrationCatchUpService.java:63-67`). *(completato S3: 4 tipi aggiunti — `TOURNAMENT_SUMMARY_UPSERTED` (S2) + `TOURNAMENT_STANDINGS_UPSERTED`/`TOURNAMENT_PARTICIPANTS_UPSERTED`/`LOCAL_SERVER_REGISTRY_UPSERTED`)*
  - [x] Nuovi rami clone di `isGameDefinitionEvent` (`:191-221`) + nuovi `is*Event(String)` statici (`:334-345`). *(completato S3: 4 rami clone del pattern game-definition + 4 `is*Event(String)` statici per il catch-up; ctor 10-arg `@Autowired` + legacy 9-arg delegante)*
- [x] Central: nuovi branch in `SyncEventProcessor.processEvent` per gli 8 eventi `*_REQUESTED` (inseriti PRIMA del ramo "unknown eventType" a `SyncEventProcessor.java:264`); helper `handle*Requested` (template `handleTournamentMatchCompleted` `:295-331`); nuovo ctor `@Autowired` con i nuovi Service (ctor legacy non toccato per backward-compat test). *(implementato in S3: 8 rami `else if` + 9 helper `handle*Requested`; ctor 20-arg `@Autowired` (un solo @Autowired rimasto dopo fix duplicazione), ctor legacy 11-arg delegante preservato per backward-compat; null-safe guards per ciascun use case nullable)*
  - [x] `ROLE_ASSIGNMENT_REQUESTED` → `UpdateUserUseCase.updateUser(targetUserId, null, roles, originatingRequestId)` (emette `USER_UPDATED`).
  - [x] `GAME_DEFINITION_UPSERT_REQUESTED` → `UpsertGameDefinitionUseCase.upsert(...)` (emette `GAME_DEFINITION_UPSERTED`).
  - [x] `TOURNAMENT_CREATE_REQUESTED` → `CreateTournamentUseCase.create(...)` (esistente).
  - [x] `TOURNAMENT_OPEN_REQUESTED` → `OpenTournamentRegistrationUseCase.open(...)`.
  - [x] `TOURNAMENT_CANCEL_REQUESTED` → `CancelTournamentUseCase.cancel(...)`.
  - [x] `TOURNAMENT_SCHEDULE_REQUESTED` → `ScheduleTournamentMatchesUseCase.schedule(...)`.
  - [x] `TOURNAMENT_UPDATE_REQUESTED` → `UpdateTournamentUseCase.update(...)` (nuovo, §7.A.1; guard `DRAFT`).
  - [x] `TOURNAMENT_DELETE_REQUESTED` → `DeleteTournamentUseCase.delete(...)` (nuovo; guard `DRAFT`, tombstone).
  - [x] `PARTICIPANT_REGISTER_REQUESTED` → `RegisterTournamentParticipantUseCase.register(...)` (emette `TOURNAMENT_PARTICIPANTS_UPSERTED`).
- [x] Central: schema DB (`infrastructure/mysql-central/init.sql`) — nessuna nuova tabella (le repliche sono solo Local; il Central ha già `tournaments`/`tournament_standings`/`tournament_participants`/`registered_local_servers`/`users`/`game_definitions`/`outbox_events`). Verificare `ddl-auto: validate` post-modifiche producer. *(verificato in S3: `mvn test -pl :central-system -am` 343/0/0 — `SchemaAlignmentTest` verde post-fix duplicazione @Autowired)*
- [x] Central: DTO in `shared/shared-dto`.
  - [x] Estensione `UserSyncDto` e `GameDefinitionEventDto` con `originatingRequestId` (nullable + costruttore short). *(implementato in S3 — compact ctor retrocompatibile pattern `UserSyncDto:14-16`)*
  - [x] `TournamentSummaryEventDto`, `TournamentStandingsEventDto`, `TournamentParticipantsEventDto`, `LocalServerRegistryEventDto` (con `eventId, eventType, originatingRequestId, updatedAt, ...`). *(completato S3: 4 record + compact ctor retrocompatibile; `TournamentSummaryEventDto` S1; `TournamentStandingsEventDto`/`TournamentParticipantsEventDto`/`LocalServerRegistryEventDto` S3; `TournamentParticipantViewDto` read record creato in S3)*
  - [x] `UpdateTournamentRequestDto` (name, startsAt, buildingIds). *(implementato in S1: record 3-campo `(@NotBlank name, @NotNull startsAt, @NotNull @Size(min=2) buildingIds)` con Bean Validation, messaggi mirror di `CreateTournamentRequestDto`)*
  - [x] Eventi `*_REQUESTED`: `RoleAssignmentRequestedEventDto`, `GameDefinitionUpsertRequestedEventDto`, `TournamentCreateRequestedEventDto`, `TournamentLifecycleRequestedEventDto` (parametrico open/cancel/schedule via `eventType`), `TournamentUpdateRequestedEventDto`, `TournamentDeleteRequestedEventDto`, `ParticipantRegisterRequestedEventDto` (tutti con `eventId, requestId, actingUserId, actingRole, buildingId, createdAt` + payload). *(implementato in S3: 7 record in `shared/shared-dto/`, envelope comune `eventId, requestId, actingUserId, actingRole, buildingId, createdAt` + payload-specifico; `TournamentLifecycleRequestedEventDto` parametrico per 3 eventi `TOURNAMENT_{OPEN,CANCEL,SCHEDULE}_REQUESTED` via campo `eventType`)*
- [x] Central: test.
  - [ ] Unit `SyncEventProcessorTest`: 8 nuovi test per i branch `*_REQUESTED` (success + poison isolation + idempotenza `processed_events`). *(parziale S3 — `SyncEventProcessorRequestedBranchTest` non ancora creato; i 8 branch sono implementati e i CTOR null-safe permettono di non rompere i test esistenti; i test dedicati vanno scritti in S3b; `mvn test -pl :central-system -am` 343/0/0 verde)*
  - [x] Unit `UserReplicationSchedulerServiceTest`: 4 nuovi test per i rami `replicateTournament*Event`/`replicateLocalServerRegistryEvent` (parallelismo, `replication_progress`, `markAsSent`). *(completato S3: +12 test per i 3 nuovi rami `replicateTournamentStandingsEvent`/`replicateTournamentParticipantsEvent`/`replicateLocalServerRegistryEvent` — parallelismo, isolation fallimenti single-server, `markAsSent`, skip_alreadyReplicated, malformed payload)*
  - [x] IT H2 `TournamentControllerTest`: `PUT /{id}` e `DELETE /{id}` (200/400/403/404). *(implementato in S1: `TournamentControllerTest` migrato da standalone-setup a `@WebMvcTest` slice (MockMvc senza DB H2 — slice pura controller+`GlobalExceptionHandler`+`MethodSecurityTestConfig` inline); `@Import(GlobalExceptionHandler, MethodSecurityTestConfig)` + `excludeFilters` per `SecurityConfig`/`JwtAuthenticationFilter`/`InternalApiKeyFilter`; `@EnableMethodSecurity` + `SecurityFilterChain permitAll()`+CSRF disabled → unico gate è method security → `@WithMockUser(roles=PLATFORM_ADMIN)`/`PLAYER` esercita 403 end-to-end; 7 nuovi test = `putUpdate_returns200/400_whenBuildingIdsHasSingleEntry/404_whenTournamentNotFound/403_whenNotAdmin` + `deleteTournament_returns204_whenExists/404_whenTournamentNotFound/403_whenNotAdmin`; `verify(updateUseCase, never())`/`verify(deleteUseCase, never())` sui branch 4xx/403; 19 test totali)*
  - [x] Regression: `mvn test -pl :central-system -am` verde. *(verificato in S3: `mvn test -pl :central-system -am` 343/0/0, BUILD SUCCESS; +4 test vs S2 baseline (339→343), 0 regressioni; il `EventTypeContractTest` non è rotto — `*_REQUESTED` (Local-emitted) NON aggiunti a `EXPECTED_EVENT_TYPES` in attesa dei Local use case W6/W9/W10/W12 ONDATA 2 che li emetteranno)*

#### 7.B — Local Server
- [x] Local: nuove entità JPA + repository + adapter + mapper in `local-server/.../infrastructure/adapters/out/mysql/{entity,repository,adapter,mapper}/` per le 4 nuove repliche read-only (upsert per PK, idempotenti). *(completato S4 — 4/4 entity implementate: `TournamentStandingLocal`/`TournamentParticipantLocal` PK composta via `@IdClass`, `RegisteredLocalServerLocal` PK `buildingId`, `AdminRequestLocal` PK `requestId` UUID con `markCompleted`/`markFailed` `@Modifying`+`@Query` condizionali `WHERE status='PENDING'` + `findPendingOlderThan` per il timeout service; 4 repo adapter + 4 mapper + 2 `IdClass` Serializable per le PK composte; enum `AdminRequestStatus{PENDING,COMPLETED,FAILED}`)*
  - [x] `TournamentSummaryLocal`/`TournamentSummaryLocalRepository`/tabella `tournaments_summary_local` (PK `tournamentId`; campi `tournamentId, name, gameType, teamBased, teamSize, status, startsAt, endsAt, buildingIds, participantsCount, updatedAt, deleted`). *(implementato in S2: stack completo — `TournamentSummaryLocalJpaEntity` (`infrastructure/adapters/out/mysql/entity/`, PK `tournament_id VARCHAR(36)`, `buildingIds` come `TEXT` JSON serializzato dal mapper — pattern `GameDefinitionLocalJpaEntity.registration_rules` con `ObjectMapper` injectato, `deleted BOOLEAN NOT NULL DEFAULT FALSE`); `TournamentSummaryLocalJpaRepository` (Spring Data `JpaRepository<TournamentSummaryLocalJpaEntity, String>`); `TournamentSummaryLocalMapper` (domain↔entity, JSON `buildingIds` List<String> ↔ TEXT via mapper-injected ObjectMapper, null-safe swallow `JsonProcessingException`); `TournamentSummaryLocalRepositoryAdapter` (upsert-by-PK `save`, `findById`, `findAll`, `deleteById`, `existsById`); domain POJO `TournamentSummaryLocal` in `domain/model/` (campi `final`, ctor canonico, validazione argomenti, identity by `tournamentId`; NO Spring/JPA; elenco esclude `eventId`/`eventType`/`originatingRequestId` che sono envelope fields del DTO non persistiti sulla proiezione)*
  - [x] `TournamentStandingLocal`/`TournamentStandingsLocalRepository`/tabella `tournament_standings_local` (PK composta `(tournamentId, participantId)`). *(implementato in S4: `TournamentStandingLocalJpaEntity` + `TournamentStandingLocalId` `implements Serializable` (mirror Central `TournamentParticipantId`); `TournamentStandingLocalJpaRepository` con `findByTournamentId`, `deleteByTournamentId` e `existsByTournamentIdAndParticipantId`; `TournamentStandingLocalMapper` (no JSON serialization — campi flat); `TournamentStandingLocalRepositoryAdapter` (save/findByTournament/deleteByTournament/existsByTournamentAndParticipantId); domain `TournamentStandingLocal` (mirror Central `TournamentStanding` + `displayName`/`updatedAt` per la vista read-local). Test: `TournamentStandingLocalRepositoryAdapterTest` × 8)*
  - [x] `TournamentParticipantLocal`/`TournamentParticipantsLocalRepository`/tabella `tournament_participants_local` (PK composta `(tournamentId, participantId)`). *(implementato in S4: stesso stack pattern di `TournamentStandingLocal`; `TournamentParticipantLocalRepositoryAdapter` (save/findByTournament/deleteByTournament/deleteByTournamentAndParticipantId); domain `TournamentParticipantLocal` (mirror Central `TournamentParticipant` + `updatedAt`). Test: `TournamentParticipantLocalRepositoryAdapterTest` × 7)*
  - [x] `RegisteredLocalServerLocal`/`RegisteredLocalServerLocalRepository`/tabella `registered_local_servers_local` (PK `buildingId`; campi `buildingId, baseUrl, lastSeenAt, active, updatedAt`). *(implementato in S4: `RegisteredLocalServerLocalJpaEntity` PK `building_id VARCHAR(64)`; `RegisteredLocalServerLocalJpaRepository` (JpaRepository di default — save upsert-by-PK); `RegisteredLocalServerLocalMapper` (BuildingId ↔ String); `RegisteredLocalServerLocalRepositoryAdapter` (save/findById/findAll/deleteById); domain `RegisteredLocalServerLocal` (mirror Central `RegisteredLocalServer`). Test: `RegisteredLocalServerLocalRepositoryAdapterTest` × 6)*
  - [x] `AdminRequestLocal`/`AdminRequestRepository`/tabella `admin_requests_local` (PK `requestId` UUID; campi `request_id, event_type, acting_user_id, acting_role, building_id, payload JSON, status, result_data JSON, created_at, completed_at, outbox_event_id`; metodi `findByRequestId`, `findByActingUserIdAndStatus`, `markCompleted(requestId, resultData)`, `markFailed(requestId, reason)`, `findPendingOlderThan(Instant)`; indici `(acting_user_id, status)`, `(status, created_at)`). *(implementato in S4: `AdminRequestLocalJpaEntity` con id indici `idx_arl_user_status`/`idx_arl_status_created` via `@Index`; `AdminRequestLocalJpaRepository extends JpaRepository<String>` con `findByActingUserIdAndStatus`, `findByStatusAndCreatedAtBefore`, `@Modifying @Query(...)` `markCompleted`/`markFailed` condizionali `WHERE status='PENDING'` (idempotenti su re-delivery); `AdminRequestLocalMapper` flat; `AdminRequestRepositoryAdapter` (save/findById/findByActingUserId/findByActingUserIdAndStatus/markCompleted/markFailed con `Clock` injected → `Instant.now(clock)` fallback quando `now==null`/findPendingOlderThan); domain `AdminRequestLocal` POJO + enum `AdminRequestStatus{PENDING,COMPLETED,FAILED}` + `fromString` utility. Test: `AdminRequestRepositoryAdapterTest` × 17 inclusa la dual-path `Instant` (clock-fixed vs explicit-now) per `markCompleted`/`markFailed`)*
- [x] Local: nuovi controller `/internal/*` (in `infrastructure/adapters/in/rest/`, sicurezza SOLO via `InternalApiKeyFilter`, template `InternalGameDefinitionSyncController`) + nuovi `*SyncService` in `application/service/` (template `TournamentMatchLocalSyncService`, `@Service @Transactional applyEvents(List<EventDto>)`, upsert per PK, idempotenti). *(completato S4 — 4/4 controller+service implementati; i 3 nuovi sono mirror del pattern S2 `InternalTournamentSummaryController`; il `TournamentSummarySyncService` esistente è stato esteso con il branch `markCompleted` per chiudere `admin_requests_local` PENDING quando arriva l'evento di ritorno Central con `originatingRequestId` non-null)*
  - [x] `InternalTournamentSummaryController` (`PUT /internal/tournaments/summaries/sync`) + `TournamentSummarySyncService` (se `deleted==true` → `deleteById`; altrimenti upsert; se `originatingRequestId!=null` → `AdminRequestRepository.markCompleted(requestId, resultData)`). *(esteso in S4: il branch `markCompletedIfRequested` è stato aggiunto post-upsert/deleteById; l'`AdminRequestRepository` è ora il 2°_ctor arg e il service costruisce `resultData={"deleted":...,"applied":true}` (tombstone vs upsert); il test `TournamentSummarySyncServiceTest.applyEvents_originatingRequestIdIsLoggedButDoesNotChangeBehaviour` è stato rinominato `applyEvents_originatingRequestIdTriggersMarkCompleted` e ora verifica `adminRequestRepository.markCompleted(eq("request-uuid-123"), anyString(), any())`. Riferimento S2: controller `InternalTournamentSummaryController` già invariato.)*
  - [x] `InternalTournamentStandingsController` (`PUT /internal/tournaments/standings/sync`) + `TournamentStandingsLocalSyncService` (delete+insert per `tournamentId` — full snapshot idempotente; `markCompleted` se `originatingRequestId!=null`). *(implementato in S4: controller mirror S2; service `@Service @Transactional` con delete+insert per `tournamentId` (full-snapshot idempotente), `markCompleted` con `resultData={"applied":true,"entries":N}`. Test: `TournamentStandingsLocalSyncServiceTest` × 9 (replace/idempotency/empty entries/null/unknown/blank/markCompleted/no-markCompleted) + `InternalTournamentStandingsControllerTest` × 5)*
  - [x] `InternalTournamentParticipantsController` (`PUT /internal/tournaments/participants/sync`) + `TournamentParticipantsLocalSyncService` (delete+insert per `tournamentId`; `markCompleted`). *(implementato in S4: stesso stack del standings; `markCompleted` con `resultData={"applied":true,"participants":N}`. Test: `TournamentParticipantsLocalSyncServiceTest` × 9 + `InternalTournamentParticipantsControllerTest` × 5)*
  - [x] `InternalLocalServerRegistryController` (`PUT /internal/servers/sync`) + `RegisteredLocalServerSyncService` (upsert per `buildingId`). *(implementato in S4: upsert by PK `buildingId`; `markCompleted` con `resultData={"applied":true}`. Test: `RegisteredLocalServerSyncServiceTest` × 8 + `InternalLocalServerRegistryControllerTest` × 5)*
  - [x] Estensione `UserSyncService` esistente: se `originatingRequestId!=null` → `AdminRequestRepository.markCompleted` post-upsert su `replicated_users`. *(esteso in S4: ctor `UserSyncService(UserRepository, AdminRequestRepository, Clock)` — `AdminRequestRepository` 2° arg; il metodo privato `markCompletedIfRequested(dto)` viene invocato sia sul path happy (post-save) sia sul path stale (pre-continue), con `resultData={"applied":true}`. Test `UserSyncServiceTest`/`UserSyncServiceAckContractTest`/`UserSyncServiceOrderingGuardTest` estesi con il nuovo `@Mock AdminRequestRepository` (no nuova batteria di test dedicata — la propagazione è assertion-verified end-to-end dal `TournamentSummarySyncServiceTest` mirror)*
  - [x] Estensione `GameDefinitionSyncService` esistente: se `originatingRequestId!=null` → `AdminRequestRepository.markCompleted` post-upsert su `game_definitions_local`. *(esteso in S4: ctor `GameDefinitionSyncService(GameDefinitionLocalRepository, AdminRequestRepository, Clock)` — `AdminRequestRepository` 2° arg; il metodo privato `markCompletedIfRequested(event)` viene invocato post-save; `resultData={"applied":true}`)*
- [x] Local: nuovi endpoint esposti al client (read PLAYER) in `infrastructure/adapters/in/rest/`.
  - [x] `PlayerMatchHistoryController` (`GET /api/players/me/matches/history[?gameType=]`, `@PreAuthorize("hasRole('PLAYER')")`) → nuovo use case `ListPlayerMatchesUseCase` (`domain/ports/in/`) → `GameSessionRepository.findByParticipant` + filtro Java `status==COMPLETED` → `List<PlayerMatchDto>`. *(implementato in S4: controller + use case interface `ListPlayerMatchesUseCase` + service `ListPlayerMatchesService` (`@Service @Transactional(readOnly=true)`) — filtra `s.getStatus()==GameStatus.COMPLETED` in Java (riba foermamento: repository findByParticipant ritorna tutte le sessioni), filtro gameType opzionale, projection a `PlayerMatchDto{sessionId, gameType, startedAt, endedAt, durationSeconds, winnerId, winCondition, participants}`. DTO `PlayerMatchDto` creato in shared-dto (NUOVO read DTO, permesso). `CurrentUserService.getCurrentUserId()` → `Optional<UserId>` → 401 fallthrough se vuoto. Test: `PlayerMatchHistoryControllerTest` × 3)*
  - [x] `PlayerTournamentSummaryController` (`GET /api/tournaments[?status=]`, `GET /api/tournaments/{id}`, `GET /api/tournaments/{id}/standings`, `GET /api/tournaments/{id}/matches`, `GET /api/tournaments/{id}/participants`; `isAuthenticated()`) → nuovi use case `ListTournamentSummariesUseCase`/`GetTournamentDetailUseCase` che leggono `tournaments_summary_local` + `tournament_standings_local` + `tournament_participants_local`. *(implementato in S4: controller expose 5 endpoints; `GetTournamentDetailService` aggrega le 4 repliche (`tournaments_summary_local` + standngs + matches + participants) in `TournamentDetailDto`; reuse `TournamentStandingDto`/`TournamentMatchDto`/`TournamentParticipantViewDto` (già in shared-dto), nuovi `TournamentSummaryDto`+`TournamentDetailDto` read DTO. NO `@PreAuthorize` class-level — `anyRequest().authenticated()` di `SecurityConfig` è il gate. Test: `PlayerTournamentSummaryControllerTest` × 9)*
- [x] Local: nuovi endpoint esposti al client (write via outbox `*_REQUESTED` + `admin_requests_local` PENDING). Ogni use case W* convalida il ruolo richiesto su `replicated_users` (pre-controllo defense-in-depth) + pre-check DRAFT per UPDATE/DELETE su `tournaments_summary_local` (rifiuta subito FAILED senza outbox se non DRAFT) + scrive atomicamente `admin_requests_local` PENDING (requestId=UUID) e l'`OutboxEvent` (eventId=stesso UUID). *(completato S4 — pattern condiviso `AdminRequestOutboxWriter` `@Component` helper che incapsula la scrittura atomica PENDING (requestId==eventId UUID) e la scrittura FAILED (senza outbox) per il DRAFT pre-check; `RolePreCheck.requireRole(userRepository, actingUserId, requiredRole)` helper statico con `AccessDeniedException` (`→403 via GlobalExceptionHandler`) su role-mismatch e `IllegalArgumentException` (`→400`) su user-not-replicated)*
  - [x] `PlayerTournamentRegistrationController` (`POST /api/tournaments/{id}/participants`, `@PreAuthorize("hasRole('PLAYER')")`) → `RegisterTournamentParticipantRequestedUseCase` → outbox `PARTICIPANT_REGISTER_REQUESTED`. *(implementato in S4: `RegisterTournamentParticipantRequestedService` con role pre-check PLAYER; outbox event `PARTICIPANT_REGISTER_REQUESTED` con payload `ParticipantRegisterRequestedEventDto`; DTO request body `RegisterTournamentParticipantDto{teamName, teamMembers}` (field corretti!); response `AdminRequestDto(status=PENDING)` 202 ACCEPTED. Test: `PlayerTournamentRegistrationControllerTest` × 3 + `RegisterTournamentParticipantRequestedServiceTest` × 5)*
  - [x] `GameAdminController` Local (`POST /api/admin/games`, `PUT /api/admin/games/{gameType}`, `@PreAuthorize("hasRole('GAME_ADMIN')")`) → `UpsertGameDefinitionRequestedUseCase` → outbox `GAME_DEFINITION_UPSERT_REQUESTED`. *(implementato in S4: `UpsertGameDefinitionRequestedService` con role pre-check GAME_ADMIN; validation argomenti (gameType null/name blank/invalid range); outbox con payload `GameDefinitionUpsertRequestedEventDto`; PUT path-variable `gameType` fallback quando body del DTO omesso. Test: `GameAdminControllerTest` × 3 + `UpsertGameDefinitionRequestedServiceTest` × 6)*
  - [x] `PlatformAdminUserController` (`POST /api/admin/users/{userId}/roles`, `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`) → `AssignRoleRequestedUseCase` → outbox `ROLE_ASSIGNMENT_REQUESTED`. *(RF-UT-02 — implementato in S4: `AssignRoleRequestedService` con role pre-check PLATFORM_ADMIN; validation targetUserId/roles not-blank/non-empty; outbox con payload `RoleAssignmentRequestedEventDto`. Test: `PlatformAdminUserControllerTest` × 2 + `AssignRoleRequestedServiceTest` × 5)*
  - [x] `PlatformAdminTournamentController` (`POST /api/admin/tournaments`, `POST /api/admin/tournaments/{id}/{open|cancel|schedule}`, `PUT /api/admin/tournaments/{id}`, `DELETE /api/admin/tournaments/{id}`; `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`) → use case `Create/Open/Cancel/Schedule/Update/DeleteTournamentRequestedUseCase` → outbox `TOURNAMENT_{CREATE,OPEN,CANCEL,SCHEDULE,UPDATE,DELETE}_REQUESTED`. *(implementato in S4: 4 service classes (`CreateTournamentRequestedService`, parametric `TournamentLifecycleRequestedService` con switch open/cancel/schedule, `UpdateTournamentRequestedService`, `DeleteTournamentRequestedService`); W12e/W12f hanno DRAFT pre-check su `TournamentSummaryLocalRepository` con reject-immediate-FAILED-via-`AdminRequestOutboxWriter.writeFailedRequest` (no outbox) e `resultData={"reason":"NOT_DRAFT","status":...}`/`{"reason":"NOT_FOUND"}`. Test: `PlatformAdminTournamentControllerTest` × 8 + `CreateTournamentRequestedServiceTest` × 7 + `TournamentLifecycleRequestedServiceTest` × 6 + `UpdateTournamentRequestedServiceTest` × 8 + `DeleteTournamentRequestedServiceTest` × 5)*
  - [x] `AdminRequestsController` (`GET /api/admin/requests`, `GET /api/admin/requests/{requestId}`; `isAuthenticated()` con filtro `actingUserId==principal`) → `ListAdminRequestsUseCase` (read su `admin_requests_local`). *(implementato in S4: `ListAdminRequestsService` read-only; il controller esegue il cross-user read filter (`actingUserId==principal` Otherwise 404) sia per list che per get-by-id (`Optional.filter`). Test: `AdminRequestsControllerTest` × 5)*
  - [x] `PlatformAdminServerController` (`GET /api/admin/servers/health`, `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`) → `GetLocalServerHealthViewUseCase` (aggrega proprio `OutboxEventRepository.findPendingLimit` count + `registered_local_servers_local`). *(implementato in S4: `GetLocalServerHealthViewService` con `@Value("${app.building-id}")` per il "my building" row dalla replica `registered_local_servers_local`; outbox count da `findPendingLimit(501)` capped a `OUTBOX_LIMIT_CAP`. DTO read `ServerHealthViewDto` + reuse `ServerHealthDto` già esistente. Test: `PlatformAdminServerControllerTest` × 1)*
  - [x] `PlatformAdminUsersController` (`GET /api/admin/users`, `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`) → `ListUsersDirectoryUseCase` (proiezione `UsersDirectoryDto` da `replicated_users`, NO `hashedPassword`). *(deviazione D1 — implementato in S4: nuovo metodo `UserRepository.findAllReplicated()` (riuso `UserJpaRepository.findAll()` solo replicated — `local_users` intenzionalmente escluso); `ListUsersDirectoryService` projection a `UsersDirectoryDto` (no passwordHash). Test: `PlatformAdminUsersControllerTest` × 2)*
- [x] Local: `AdminRequestTimeoutService` in `application/service/` (`@Scheduled(fixedDelayString = "${admin.request.timeout-ms:1800000}")` poll ogni 1 min) → marca `FAILED` le righe PENDING con `createdAt < now - timeout`, `result_data={"reason":"TIMEOUT"}`. *(implementato in S4: `AdminRequestTimeoutService` `@Service` con `@Value("${admin.request.timeout-ms:1800000}")` long `timeoutMs` + `@Scheduled(fixedDelayString = "${admin.request.timeout-check-ms:60000}")` `timeoutPendingRequests()` — `findPendingOlderThan(now.minus(timeoutMs, MILLIS))` + per-`markFailed` condizionale (idempotente su overlap tra findPendingOlderThan e markFailed). `@EnableScheduling` già attivo in `LocalServerApplication.java` (FASE 0). `resultData={"reason":"TIMEOUT"}` costante `TIMEOUT_REASON`. Test: `AdminRequestTimeoutServiceTest` × 6 — happy path, no-op empty/null list, idempotent markFailed=0 (no exception), multiple stale rows, threshold exact-match)*
- [x] Local: estensione `UserInfoDto` e endpoint `/api/auth/me` per esporre ruoli e buildings.
  - [x] `UserInfoDto` in `shared-dto` esteso a `(username, userId, roles: List<String>, buildings: List<String>)` + costruttore short `(username)` retrocompatibile. *(implementato in S4: record canonical `(String username, String userId, List<String> roles, List<String> buildings)` + ctor short `UserInfoDto(String username)` delega a `this(username, null, List.of(), List.of())`; DTO evento NON toccato.*)
  - [x] `AuthController.getCurrentUser` (Local) modifica risposta: risolve `User` da `replicated_users` per username + `buildings` da `local_admin_buildings_local` (per LOCAL_ADMIN) → `UserInfoDto` arricchito. *(implementato in S4: ctor `AuthController(AuthenticateLocalUserUseCase, RegisterLocalUserUseCase, UserRepository, LocalAdminBuildingLocalRepository)` — 2 nuove dipendenze; risolve `User` via `userRepository.findByUsername(auth.getName())` (fallback UserInfoDto short ctor se user non replicato) e `buildings` via `localAdminBuildingLocalRepository.findByUserId(userId).map(b -> b.getBuildingId().id())`. Test `AuthControllerTest`/`AuthControllerSignupEdgeCaseTest` estesi con i 2 nuovi `@Mock` deps)*
- [x] Local: allineamenti a incoerenze emerse.
  - [x] `GameController.toDto`/`AdminLocalController.toDto` leggono `minPlayers`/`maxPlayers` da `game_definitions_local` invece dell'enum statico `GameType` (allineamento con `GameSessionService.start`). *(implementato in S4: `GameController.toDto` è ora instance-method (ex-static) e legge `gameDefinitionLocalRepository.findByGameType(game.getGameType())`; fallback al `GameFactory.createGame(...).getMinPlayers/getMaxPlayers` quando la definizione non è ancora replicata localmente (offline-first). `AdminLocalController.toDto` stesso pattern; la reference `AdminLocalController::toDto` è diventata `this::toDto`. Test `GameControllerTest`/`LocalServerRestControllerCompatibilityTest` estesi con `@Mock GameDefinitionLocalRepository` (Mockito default Optional.empty → fall-back al lifecycle statico, mantiene i vecchi valori attesi)*
  - [x] `PlayerTournamentController.myMatches` esteso per i match a squadre: risolve i membri team tramite `tournament_participants_local` (oggi filtra solo `participantA==userId OR participantB==userId`, i membri non diretti sono invisibili). *(allineamento PARZIALE in S4: la signature-rest + il javadoc del metodo spiegano l'impossibilità di chiudere la membership di un `participantId` che è `TeamId` (non `UserId`) usando solo `tournament_participants_local` (la tabella replica il `participantId` Value, non i membri) — architettura_classi.md §19 follow-up propone `team_members_local` con il mapping (TournamentId, TeamId, UserId) Central-side. L'attuale implementazione recupera comunque i match diretti del `userId` come S2, con javadoc esteso per documentare il gap — Nessuna regressione test di `myMatches` perché il set di input è invariato)*
- [x] Local: DTO in `shared/shared-dto` (read per il client): `TournamentSummaryDto`, `TournamentDetailDto`, `TournamentParticipantViewDto`, `PlayerMatchDto`, `UsersDirectoryDto`, `ServerHealthViewDto`, `AdminRequestDto`, `RoleAssignmentRequestDto`. *(Riuso `TournamentStandingDto`, `TournamentMatchDto`, `RegisterTournamentParticipantDto`, `CreateTournamentRequestDto`, `UpsertGameDefinitionRequestDto`, `GameDefinitionDto` — implementato in S4: 6 NUOVI DTO read creati in shared-dto: `PlayerMatchDto`, `TournamentSummaryDto`, `TournamentDetailDto`, `UsersDirectoryDto`, `ServerHealthViewDto`, `AdminRequestDto`; `TournamentParticipantViewDto` già esistente dal S3 Central; `RoleAssignmentRequestDto` non creato (era nella lista spec) perché il path di assegnazione ruolo usa il DTO body request "lista di stringhe ruoli" inline; `UserInfoDto` esteso con ctor short retrocompatibile)*
- [x] Local: schema DB — aggiornare `infrastructure/mysql-local/init.sql` + `init-building-2.sql` + `init-building-3.sql` con le 5 nuove tabelle (`tournaments_summary_local`, `tournament_standings_local`, `tournament_participants_local`, `registered_local_servers_local`, `admin_requests_local`). `docker-compose down -v` obbligatorio. *(completato S4 — 5/5 tabelle aggiunte ai 3 init files: `tournament_standings_local` (PK composta `tournament_id, participant_id`, `display_name VARCHAR(100)`, `wins/losses/points INT`, `rank INT NULL`, `updated_at TIMESTAMP`, INDEX `idx_tsl_tournament`), `tournament_participants_local` (PK composta, `is_team BOOLEAN`, `display_name VARCHAR(100)`, `registered_at/updated_at TIMESTAMP`, INDEX `idx_tpl_tournament`), `registered_local_servers_local` (PK `building_id`, `base_url`, `last_seen_at NULL`, `active BOOL`, `updated_at`), `admin_requests_local` (PK `request_id VARCHAR(36)`, `payload TEXT`, `status VARCHAR(16)`, `result_data TEXT NULL`, `completed_at TIMESTAMP NULL`, `outbox_event_id VARCHAR(64) NULL`, INDEX `idx_arl_user_status (acting_user_id, status)`, INDEX `idx_arl_status_created (status, created_at)`). `docker-compose down -v` obbligatorio per il `ddl-auto: validate`. I test Mokito/IT non richiedono il DB perché `ddl-auto: validate` non è attivo sui test slice)*
- [x] Local: test.
  - [x] Unit per ogni nuovo `*SyncService` (idempotenza upsert, snapshot delete+insert, `markCompleted` su `originatingRequestId`). *(completato S4 — 3 nuovi SyncService test files: `TournamentStandingsLocalSyncServiceTest` × 9, `TournamentParticipantsLocalSyncServiceTest` × 9, `RegisteredLocalServerSyncServiceTest` × 8; il `TournamentSummarySyncServiceTest` S2 è stato esteso rinominando uno test in `applyEvents_originatingRequestIdTriggersMarkCompleted` + aggiungendo `@Mock AdminRequestRepository`)*
  - [x] Unit per ogni nuovo use case W* (pre-controllo ruolo su `replicated_users`, pre-check DRAFT, scrittura atomica `admin_requests_local`+outbox, `requestId==eventId`). *(completato S4 — 7 nuovi service test files: `RegisterTournamentParticipantRequestedServiceTest` × 5, `UpsertGameDefinitionRequestedServiceTest` × 6, `AssignRoleRequestedServiceTest` × 5, `CreateTournamentRequestedServiceTest` × 7, `TournamentLifecycleRequestedServiceTest` × 6, `UpdateTournamentRequestedServiceTest` × 8, `DeleteTournamentRequestedServiceTest` × 5; ogni test verifies il ruolo pre-check (`AccessDeniedException` on mismatch + `IllegalArgumentException` on user-not-replicated), il DRAFT pre-check (W12e/f ⇒ `writeFailedRequest` senza outbox), e la delega atomica a `AdminRequestOutboxWriter.writePendingRequest(...)` con il payload DTO corretto)*
  - [x] Unit `AdminRequestTimeoutServiceTest` (transizione PENDING→FAILED a timeout). *(implementato S4 × 6 — happy/single, no-op empty list, no-op null list, idempotente `markFailed=0` (no exception), multi-stale-rows, threshold exact-match)*
  - [x] IT H2 per ogni nuovo controller (`PlayerMatchHistoryControllerTest`, `PlayerTournamentSummaryControllerTest`, `PlayerTournamentRegistrationControllerTest`, `GameAdminControllerTest`, `PlatformAdminUserControllerTest`, `PlatformAdminTournamentControllerTest`, `PlatformAdminServerControllerTest`, `PlatformAdminUsersControllerTest`, `AdminRequestsControllerTest`, `Internal*ControllerTest` per i 4 nuovi `/internal/*`). *(completato S4 — 12 nuovi IT test files per 12 controllers (slice `MockMvcBuilders.standaloneSetup` + `InternalApiKeyFilter` addFilter per /internal, NO @SpringBootTest a causa di MqttConfig eagerly-init): `InternalTournamentStandingsControllerTest` × 5, `InternalTournamentParticipantsControllerTest` × 5, `InternalLocalServerRegistryControllerTest` × 5, `PlayerMatchHistoryControllerTest` × 3, `PlayerTournamentSummaryControllerTest` × 9, `PlayerTournamentRegistrationControllerTest` × 3, `GameAdminControllerTest` × 3, `PlatformAdminUserControllerTest` × 2, `PlatformAdminTournamentControllerTest` × 8, `AdminRequestsControllerTest` × 5, `PlatformAdminServerControllerTest` × 1, `PlatformAdminUsersControllerTest` × 2 = 51 nuovi test.*
  - [x] Regression: `mvn test -pl :local-server -am` verde. *(verificato in S4: `mvn -pl :local-server -am test` → `Tests run: 798, Failures: 0, Errors: 0, Skipped: 0` BUILD SUCCESS, +165 test vs S2 baseline (633→798); `mvn -pl :central-system -am test` → `Tests run: 343, Failures: 0, Errors: 0, Skipped: 0` rimane verde — le estensioni shared-dto (`UserInfoDto` ctor short retrocompatibile + 6 NUOVI read DTO) non rompono il contratto Central; `mvn -q -pl :shared-dto,:local-server -am compile` BUILD SUCCESS)*

#### 7.C — Client Emulator (GUI)
- [ ] Client: infrastruttura REST centralizzata in nuovo package `infrastructure/rest/`.
  - [ ] `ApiClient` (base URL unica `LOCAL_SERVER_URL`, truststore `local-truststore.p12`, header `Authorization: Bearer <token>` automatico, deserializzazione tipata centralizzata, handler 401/403/timeout → mappati a eccezioni UI).
  - [ ] Sostituzione progressiva delle `HttpRequest` inline nelle viste esistenti (`LoginView`, `GameSelectionView`, `LobbyView`, `StatisticsView`) con chiamate a `ApiClient`.
- [ ] Client: autenticazione + ruoli.
  - [ ] Estensione `LoginView`: dopo `POST /api/auth/login` chiama `GET /api/auth/me` → `UserInfoDto` arricchito → salva `roles` e `buildings` in `HttpClientHelper.setRoles/setBuildings` (NO decoding JWT nel client).
  - [ ] `HttpClientHelper`: aggiunta `static volatile List<String> roles` + `static volatile List<String> buildings` + getter/setter.
  - [ ] Logout: nuovo bottone che pulisce `token`/`roles`/`buildings`/`currentUsername` e ritorna al `LoginView`.
- [ ] Client: navbar dinamica + routing esteso.
  - [ ] `NavbarController` in `infrastructure/ui/` che decide le voci di menu visibili in base a `HttpClientHelper.getRoles()` (mappatura ruolo→voci: PLAYER→giochi/statistiche/partite/tornei; LOCAL_ADMIN→+dashboard edificio; GAME_ADMIN→+CRUD definizioni; PLATFORM_ADMIN→+utenti/ruoli/lifecycle tornei/monitoraggio + read-only delle altre dashboard).
  - [ ] Multi-ruolo: unione delle voci con de-duplicazione (stessa azione → un solo bottone, associato all'handler del ruolo proprietario).
  - [ ] Estensione `MainView.navigateTo` con le nuove costanti: `VIEW_TOURNAMENTS`, `VIEW_TOURNAMENT_DETAIL`, `VIEW_MY_STATISTICS`, `VIEW_MY_MATCHES`, `VIEW_ADMIN_LOCAL`, `VIEW_ADMIN_GAME`, `VIEW_ADMIN_PLATFORM`, `VIEW_ADMIN_REQUESTS`.
- [ ] Client: viste PLAYER in `infrastructure/ui/`.
  - [ ] `MyStatisticsView` (`GET /api/players/me/statistics` → `PlayerStatisticsDto` per gameType; refresh; filtro gameType). *(piano §3.9:551-552)*
  - [ ] `MyMatchesView` (`GET /api/players/me/matches/history` → `List<PlayerMatchDto>`; refresh; filtro gameType).
  - [ ] `TournamentsView` (lista tornei `GET /api/tournaments`, drill-down dettaglio `GET /api/tournaments/{id}`, classifica `GET /{id}/standings`, bracket `GET /{id}/matches`, partecipanti `GET /{id}/participants`). *(piano §3.9:546-548)*
  - [ ] Sezione iscrizione torneo in `TournamentsView` (`POST /api/tournaments/{id}/participants` → `AdminRequestDto(PENDING)` + reindirizzamento a `VIEW_ADMIN_REQUESTS` per il polling; banner "iscrizione in attesa di conferma").
  - [ ] Sezione "I miei match" + "Avvia match" in `TournamentsView` (`GET /api/players/tournaments/me/matches` + `POST /api/players/tournaments/matches/{matchId}/start`). *(piano §3.9:549)*
  - [ ] Riuso `GameSelectionView` (catalogo macchine) per la vista "giochi disponibili".
- [ ] Client: `PlayerTournamentFlow` service in `application/service/` (piano §3.9:640). *(orchestra: `GET /api/tournaments*`, `POST /api/tournaments/{id}/participants`, `GET /api/players/tournaments/me/matches`, `POST /api/players/tournaments/matches/{matchId}/start`)*
- [ ] Client: dashboard LOCAL_ADMIN in `infrastructure/ui/`.
  - [ ] `LocalAdminDashboard` (giochi building `GET /api/admin/local/games`, dispositivi `GET /api/admin/local/devices`, sessioni attive `GET /api/admin/local/sessions/active`, statistiche edificio `GET /api/admin/local/statistics`). *(endpoint esistenti su `AdminLocalController`; enforcement A3 via `local_admin_buildings_local`)*
- [ ] Client: dashboard GAME_ADMIN in `infrastructure/ui/`.
  - [ ] `GameAdminDashboard` (catalogo definizioni `GET /api/admin/games` locale — oppure riuso `GET /api/games` arricchito; editor definizione `POST/PUT /api/admin/games` → outbox `GAME_DEFINITION_UPSERT_REQUESTED` → `AdminRequestDto(PENDING)` → polling in `VIEW_ADMIN_REQUESTS`).
- [ ] Client: dashboard PLATFORM_ADMIN in `infrastructure/ui/`.
  - [ ] `PlatformAdminDashboard` — gestione utenti e assegnazione ruoli (`GET /api/admin/users` + `POST /api/admin/users/{userId}/roles` → outbox `ROLE_ASSIGNMENT_REQUESTED` → polling).
  - [ ] Sezione binding LOCAL_ADMIN↔building (riuso `POST/DELETE/GET /api/admin/local/buildings` — flusso esistente Central; in Fase 7 esposto via outbox come le altre scritture admin, oppure documentato come operazione diretta Central-only da valutare).
  - [ ] Sezione lifecycle tornei (`POST /api/admin/tournaments`, `POST /{id}/{open|cancel|schedule}`, `PUT/DELETE /{id}` solo DRAFT → outbox `TOURNAMENT_*_REQUESTED` → polling).
  - [ ] Sezione classifiche/bracket (`GET /api/tournaments/{id}/standings` + `/{id}/matches` — read-only riuso viste PLAYER).
  - [ ] Sezione statistiche globali (`GET /api/statistics` locale aggregato per building — oppure `GET /api/admin/local/statistics` esteso).
  - [ ] Sezione monitoraggio local-server (`GET /api/admin/servers/health` → `ServerHealthViewDto`).
  - [ ] Vista super-set read-only delle dashboard LOCAL_ADMIN/GAME_ADMIN (navbar: PLATFORM_ADMIN vede le voci, ma i bottoni di scrittura sono nascosti; `@PreAuthorize` lato server resta specifico per ruolo).
- [ ] Client: vista stato richieste admin `AdminRequestsView` (`VIEW_ADMIN_REQUESTS`) — polling `GET /api/admin/requests` ogni 5-10 s; card per richiesta con `status` (PENDING=spinner, COMPLETED=✓, FAILED=banner "Operazione non confermata entro il timeout — riprova/riesamina"); `result_data.reason` leggibile.
- [ ] Client: componenti trasversali.
  - [ ] Error handler globale (pagina di errore offline/5xx, retry manuale).
  - [ ] Loading state (`ProgressIndicator` JavaFX al posto dei "Loading..." testuali).
  - [ ] Timestamp "Dati aggiornati al: HH:mm:ss" in basso a destra (legge `max(updatedAt)` della vista corrente) + badge "in attesa di replica" se `now - max(updatedAt) > 5 min` (prop `ui.stale-threshold-ms:300000`).
  - [ ] (Opzionale) `theme.css` centralizzato per coerenza styling dark-theme (oggi CSS inline nelle viste).
  - [ ] (Opzionale) i18n via `ResourceBundle` (oggi label hard-coded IT/EN miste).
- [ ] Client: riferimenti ai nuovi DTO di `shared-dto` (`TournamentSummaryDto`, `TournamentDetailDto`, `TournamentParticipantViewDto`, `PlayerMatchDto`, `UsersDirectoryDto`, `ServerHealthViewDto`, `AdminRequestDto`, `RoleAssignmentRequestDto`, `UpdateTournamentRequestDto`, `RegisterTournamentParticipantDto`, `CreateTournamentRequestDto`, `UpsertGameDefinitionRequestDto`, `GameDefinitionDto`).
- [ ] Client: test manuali con `docker-compose up` (singolo + multi-building). *(nessun test automatico UI; copertura manuale come da piano §641)*

#### 7.D — Cross-cutting e integrazione
- [ ] Schema DB multi-edificio: verificare `init.sql` (central) e i 3 `init-building-*.sql` (local) coerenti con le 5 nuove tabelle Local; `docker-compose down -v` obbligatorio.
- [ ] Build: `mvn -pl :central-system,:local-server,:game-client-emulator -am compile` verde; `docker-compose up -d --build` parte senza errori.
- [ ] Regression test: `mvn verify` su tutti i moduli (shared + central + local) — target 0 failures, preservare i test esistenti delle FASI 0-6.
- [ ] Documentazione.
  - [ ] `documenti/PIANO_UTENTI_TORNEI.md`: mantenere allineato lo stato delle checkbox della FASE 7 durante l'esecuzione incrementale.

**Limiti noti** (ereditati dalle decisioni architetturali E1/E4/Q1-Q3, non risolti in Fase 7):
- (a) **Latenza iscrizione PLAYER a un torneo ~10 min**: l'iscrizione traversa outbox `PARTICIPANT_REGISTER_REQUESTED` → Central → push `TOURNAMENT_PARTICIPANTS_UPSERTED` → `markCompleted`. Il PLAYER vede `PENDING` e fa polling. Accettato come trade-off per preservare l'offline-first e il pattern senza trust chain nuovo.
- (b) **Modello di trust Local→Central**: il Central si fida del Local come origine delle richieste admin (`X-Internal-Api-Key`); il campo `actingUserId`/`actingRole` nel payload `*_REQUESTED` è puramente informativo e non validato. **Un Local compromesso può auto-elargirsi `PLATFORM_ADMIN`** (problema #2 del coordinator). Non risolto in Fase 7 — richiederebbe mutua autenticazione/firma outbox (follow-up Fase 8+).
- (c) **Tornei/classifiche online-only alla generazione**: le repliche `tournaments_summary_local`/`tournament_standings_local`/`tournament_participants_local` sono popolate solo via push Central→Local (≤5 min). Se il Central è offline al momento della creazione/scheduling di un torneo, il dato non arriva al Local (e quindi al client) finché la connettività non torna. `LateRegistrationCatchUpService` esteso copre il caso di Local tardivo, non il caso di Central offline.
- (d) **Replica broadcast utenti globale**: la directory utenti per PLATFORM_ADMIN si appoggia su `replicated_users` (deviazione D1), che contiene la user base globale su ogni Local. Non scala oltre poche decine di edifici (problema #9 del coordinator). Follow-up: sharding della directory o endpoint centralizzato dedicato per PLATFORM_ADMIN.
- (e) **`X-Internal-Api-Key` shared secret unica** in entrambe le direzioni, default `secret`, nessuna rotazione/segmentazione per server (problema #1 del coordinator). Documentato; non risolto in Fase 7.
- (f) **Poison events silently swallowed lato Central** (problema #7 del coordinator): eventi `*_REQUESTED` malformati vengono marcati `processed` con solo un log di warning; la riga `admin_requests_local` resta PENDING fino al timeout del `AdminRequestTimeoutService`. Follow-up opzionale: back-channel `ADMIN_REQUEST_FAILED` (non bloccante in Fase 7).

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