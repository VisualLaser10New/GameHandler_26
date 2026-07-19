# Report Completo delle API REST — Piattaforma Giochi da Tavolo e da Bar

> **Versione:** 1.1 | **Data:** 2026-07-19 (revisione allineata al codice)
> **Scope:** Documentazione esaustiva di ogni endpoint API REST esposto dai microservizi `Central System` e `Local Server`, corredata di ruoli, flussi, pattern architetturali e contratti di payload.
> **Documento di riferimento:** `progetto-openapi.yaml` (specifica OpenAPI 3.0.3 generata).

---

## Indice

1. [Panoramica e Modello di Deploy](#1-panoramica-e-modello-di-deploy)
2. [Sicurezza, Autenticazione e Autorizzazione](#2-sicurezza-autenticazione-e-autorizzazione)
3. [Convenzioni di Naming e Contract Surface](#3-convenzioni-di-naming-e-contract-surface)
4. [Central System — Endpoint API REST](#4-central-system--endpoint-api-rest)
   - 4.1 [Auth & Users](#41-central--auth--users)
   - 4.2 [Admin Buildings (binding LOCAL_ADMIN)](#42-central--admin-buildings-binding-local_admin)
   - 4.3 [Game Definitions (GAME_ADMIN)](#43-central--game-definitions-game_admin)
   - 4.4 [Statistics](#44-central--statistics)
   - 4.5 [Tournaments](#45-central--tournaments)
5. [Local Server — Endpoint API REST](#5-local-server--endpoint-api-rest)
   - 5.1 [Auth](#51-local--auth)
   - 5.2 [Reservations](#52-local--reservations)
   - 5.3 [Games & Sessions](#53-local--games--sessions)
   - 5.4 [Statistics](#54-local--statistics)
   - 5.5 [Devices](#55-local--devices)
   - 5.6 [Admin Local (LOCAL_ADMIN)](#56-local--admin-local-local_admin)
   - 5.7 [Admin Games (GAME_ADMIN)](#57-local--admin-games-game_admin)
   - 5.8 [Admin Platform (PLATFORM_ADMIN)](#58-local--admin-platform-platform_admin)
   - 5.9 [Tournaments](#59-local--tournaments)
6. [Internal Endpoints — Sincronizzazione Server-to-Server](#6-internal-endpoints--sincronizzazione-server-to-server)
7. [Matrice di Accesso Consolidata (RBAC)](#7-matrice-di-accesso-consolidata-rbac)
8. [Flussi End-to-End e Outbox Pattern](#8-flussi-end-to-end-e-outbox-pattern)
9. [Note di Implementazione e Vincoli](#9-note-di-implementazione-e-vincoli)

---

## 1. Panoramica e Modello di Deploy

La piattaforma è un sistema **distribuito ibrido** basato su **Edge Computing** + **Microservizi** + **Clean Architecture (Hexagonal)**. Due microservizi espongono API REST:

| Microservizio | Ruolo | Porta (TLS) | Base Path |
|---|---|---|---|
| **Central System** | Hub / Source-of-Truth globale | `8180` (HTTPS) | `/api/**`, `/internal/**` |
| **Local Server** | Edge Node per edificio | `8181` (HTTPS) | `/api/**`, `/internal/**` |

Il **Game Client (JavaFX)** comunica **esclusivamente** con il Local Server del proprio edificio. Il gameplay live transita su **MQTT over TLS (8883)**; le operazioni transazionali (login, prenotazioni, CRUD) transitano su **REST/HTTPS**.

> **Nota:** Il file `progetto-openapi.yaml` citato in ingresso non era presente nel repository; la specifica è stata ricostruita fedelmente dai documenti in `documenti/strutture/` (architettura, RBAC, tornei, offline-handling, MQTT, certificati) e dagli `architettura_classi.md` (FASI 0–4+), e **verificata contro il codice sorgente Java** dei microservizi `central-system` e `local-server` (controller REST in `infrastructure/adapters/in/rest/`). DTO, status code e ruoli riflettono il codice effettivo.

---

## 2. Sicurezza, Autenticazione e Autorizzazione

### 2.1 Trust Domain separati (JWT non interscambiabili)
- Ogni nodo possiede la **propria coppia di chiavi RSA** (privata/pubblica). Il Central firma i propri JWT; il Local Server firma i propri JWT locali. I JWT **non sono intercambiabili**.
- **Claims JWT:** `{ sub: username, userId, roles: [...], exp: timestamp }`. (Nota: il claim è `sub` = username, NON `buildingId`; è presente anche `userId`.)
- Mapping claim → Spring authority via `Role.toAuthorityNames` → `ROLE_PLAYER`, `ROLE_LOCAL_ADMIN`, `ROLE_GAME_ADMIN`, `ROLE_PLATFORM_ADMIN`. I letterali legacy (`USER`/`ADMIN`/`ROLE_USER`/`ROLE_ADMIN`) sono rinormalizzati ai canonici.

### 2.2 RBAC (Role-Based Access Control)
Le policy `@PreAuthorize` sono applicate su **ogni controller** (`@EnableMethodSecurity`). `PLATFORM_ADMIN` è superuser e compare come `or hasRole('PLATFORM_ADMIN')` in tutti gli endpoint ruolo-specifici.

| Ruolo | Descrizione |
|---|---|
| `ROLE_PLAYER` | Utente standard: prenota, gioca, vede le proprie statistiche. |
| `ROLE_LOCAL_ADMIN` | Gestisce giochi/propri edificio, dispositivi, monitora partite (binding edificio). |
| `ROLE_GAME_ADMIN` | Definisce tipologie di gioco e regole di registrazione. |
| `ROLE_PLATFORM_ADMIN` | Superuser: utenti, edifici, tornei, statistiche globali (bypass binding). |

### 2.3 Autenticazione Server-to-Server (`/internal/**`)
Gli endpoint `/internal/**` sono **esclusi dal filtro JWT** e protetti da **API Key** condivisa (`X-Internal-Api-Key`, validata da `InternalApiKeyFilter`). Un JWT utente non può invocarli.

### 2.4 Trasporto
Tutte le chiamate REST sono su **HTTPS (TLS 1.3)**. I client si autenticano al broker MQTT via **mTLS** (certificati firmati dalla Local CA, enroll via CSR su `POST /api/devices/register`).

---

## 3. Convenzioni di Naming e Contract Surface

- **Path prefix:** `/api` per il traffico autenticato JWT; `/internal` per la sincronizzazione (API Key).
- **Async vs Sync:** Le mutazioni "pesanti" lato Local (creazione/lifecycle torneo, assegnazione ruoli, iscrizione torneo) seguono il pattern **async outbox**: rispondono `202 Accepted` + `AdminRequestDto(status=PENDING)` e vengono drenate verso il Central dallo scheduler. Le mutazioni lato Central (CRUD tornei diretti) sono **sync** e rispondono `201/200/204`.
- **Self-check:** Gli endpoint che operano su risorse utente (`/reservations`, `/players/.../statistics`) impongono `userId == principal` (o `PLATFORM_ADMIN`).
- **Building-binding:** Gli endpoint LOCAL_ADMIN verificano la riga `(userId, buildingId)` in `local_admin_buildings_local`; `PLATFORM_ADMIN` bypassa.

---

## 4. Central System — Endpoint API REST

### 4.1 Central — Auth & Users

#### `POST /api/users`
- **Ruolo:** Pubblico (`security: []`).
- **Descrizione:** Registrazione di un nuovo utente. Default ruolo `PLAYER`. Genera `OutboxEvent: USER_REGISTERED` e, tramite `UserReplicationSchedulerService`, replica l'utente su tutti i Local Server registrati (`replicated_users`) abilitando il login offline.
- **Request:** `CreateUserRequestDto { username, password, email? }`.
- **Response:** `201` → `UserDto`; `409` se username duplicato (Conflict, non 400).

#### `POST /api/auth/login`
- **Ruolo:** Pubblico.
- **Descrizione:** Login sul Central. Verifica BCrypt e firma JWT con la chiave privata del Central (RSA-256). Il JWT è valido **solo** sul Central.
- **Request:** `LoginRequestDto { username, password }`.
- **Response:** `200` → `LoginResponseDto { token, userId, expiresAt }` (NON contiene `roles`); `401` se credenziali non valide.

### 4.2 Central — Admin Buildings (binding LOCAL_ADMIN)

#### `POST /api/admin/local/buildings`
- **Ruolo:** `PLATFORM_ADMIN`.
- **Descrizione:** Associa un `LOCAL_ADMIN` a uno o più edifici. Persiste in `local_admin_buildings` (Source-of-Truth) e replica `LOCAL_ADMIN_BUILDING_ASSIGNED` via outbox sui Local (`local_admin_buildings_local`).
- **Request:** `AssignLocalAdminBuildingsDto { userId, buildingIds[] }`.
- **Response:** `200` (corpo vuoto, `Void`).

#### `DELETE /api/admin/local/buildings`
- **Ruolo:** `PLATFORM_ADMIN`.
- **Descrizione:** Revoca il binding; replica `LOCAL_ADMIN_BUILDING_REVOKED`.
- **Request:** `AssignLocalAdminBuildingsDto { userId, buildingIds[] }` (corpo obbligatorio).
- **Response:** `204` (No Content).

#### `GET /api/admin/local/buildings`
- **Ruolo:** `PLATFORM_ADMIN`.
- **Descrizione:** Restituisce il binding LOCAL_ADMIN↔building per un dato `userId` (parametro query `userId` obbligatorio).
- **Response:** `200` → `LocalAdminBuildingsDto` (singolo, NON una lista).

### 4.3 Central — Game Definitions (GAME_ADMIN)

#### `POST /api/admin/games/definitions`
- **Ruolo:** `GAME_ADMIN or PLATFORM_ADMIN`.
- **Descrizione:** Crea/upsert una definizione di gioco (`game_definitions`: `gameType`, `name`, `minPlayers`, `maxPlayers`, `teamAllowed`, `registrationRules` JSON). Replica `GAME_DEFINITION_UPSERTED` sui Local (`game_definitions_local`).
- **Request:** `UpsertGameDefinitionRequestDto`.
- **Response:** `200` → `GameDefinitionDto`.

#### `PUT /api/admin/games/definitions/{gameType}`
- **Ruolo:** `GAME_ADMIN or PLATFORM_ADMIN`.
- **Descrizione:** Aggiorna una definizione esistente.
- **Response:** `200` → `GameDefinitionDto`; `404` se assente.

#### `GET /api/admin/games/definitions`
- **Ruolo:** `authenticated`.
- **Descrizione:** Lista tutte le definizioni di gioco.
- **Response:** `200` → `List<GameDefinitionDto>`.

### 4.4 Central — Statistics

#### `GET /api/statistics`
- **Ruolo:** `PLATFORM_ADMIN`.
- **Descrizione:** Statistiche globali aggregate (`aggregated_statistics` per `buildingId`, `gameType`, periodo).
- **Response:** `200` → `List<StatisticsDto>`; `403` altrimenti.

#### `GET /api/players/me/statistics`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN` (`?gameType=` opzionale).
- **Descrizione:** Read-model `player_statistics` del giocatore corrente.
- **Response:** `200` → `List<PlayerStatisticsDto>`.

#### `GET /api/players/{userId}/statistics`
- **Ruolo:** Self-check (`userId == principal`) **or** `PLATFORM_ADMIN`.
- **Descrizione:** Statistiche di un giocatore arbitrario. Se non autorizzato → `403 PlayerStatisticsAccessDeniedException`.
- **Response:** `200` → `List<PlayerStatisticsDto>`.

### 4.5 Central — Tournaments

| Metodo | Path | Ruolo | Descrizione |
|---|---|---|---|
| `POST` | `/api/tournaments` | `PLATFORM_ADMIN` | Crea torneo **DRAFT** (branch diretto/sync). Valida `buildingIds.size >= 2`, `gameType` in `game_definitions`, coerenza `teamBased`/`teamSize`. Forza `SINGLE_ELIMINATION`. → `200 TournamentDto`. |
| `GET` | `/api/tournaments?status=` | `authenticated` | Lista tornei (filter by status). → `200 List<TournamentDto>`. |
| `GET` | `/api/tournaments/{id}` | `authenticated` | Dettaglio torneo. → `200 TournamentDto`; `404`. |
| `POST` | `/api/tournaments/{id}/open` | `PLATFORM_ADMIN` | `DRAFT → OPEN_REGISTRATION` (`openRegistration()`). |
| `POST` | `/api/tournaments/{id}/cancel` | `PLATFORM_ADMIN` | `DRAFT/OPEN → CANCELLED` (`cancel()`). |
| `PUT` | `/api/tournaments/{id}` | `PLATFORM_ADMIN` | Update (solo `DRAFT`). `400` se stato non DRAFT. |
| `DELETE` | `/api/tournaments/{id}` | `PLATFORM_ADMIN` | Delete (solo `DRAFT`). `204`. |
| `POST` | `/api/tournaments/{id}/schedule` | `PLATFORM_ADMIN` | Genera bracket (`TournamentBracketService.schedule`): `OPEN_REGISTRATION → IN_PROGRESS`, emette `TOURNAMENT_MATCH_SCHEDULED` per round 1, seed standings. |
| `GET` | `/api/tournaments/{id}/standings` | `authenticated` | Snapshot classifica. → `200 List<TournamentStandingDto>`. |
| `GET` | `/api/tournaments/{id}/matches` | `authenticated` | Match del bracket. → `200 List<TournamentMatchDto>`. |
| `POST` | `/api/tournaments/{id}/participants` | `PLAYER or PLATFORM_ADMIN` | Iscrizione (individuale con body vuoto, o squadra via `RegisterTournamentParticipantDto`). Branch diretto → `200 TournamentParticipantDto`. |
| `DELETE` | `/api/tournaments/{id}/participants` | `PLAYER or PLATFORM_ADMIN` | Cancella iscrizione utente corrente. → `204`. |
| `GET` | `/api/tournaments/{id}/participants` | `authenticated` | Lista partecipanti. → `200 List<TournamentParticipantDto>`. |

**Macchina a stati Torneo:** `DRAFT → OPEN_REGISTRATION → IN_PROGRESS → COMPLETED`, con `CANCELLED` da `DRAFT`/`OPEN_REGISTRATION`. Transizioni invalide → `InvalidTournamentStateException` (`400`).

---

## 5. Local Server — Endpoint API REST

### 5.1 Local — Auth

#### `POST /api/auth/login`
- **Ruolo:** Pubblico.
- **Descrizione:** Login sul Local Server. Verifica BCrypt su `replicated_users` ∪ `LocalUserJpaEntity` (funziona **offline**). Firma JWT locale (RSA-256).
- **Request:** `LoginRequestDto`. **Response:** `200` → `LoginResponseDto { token, userId, expiresAt }`; `401`.

#### `POST /api/auth/signup`
- **Ruolo:** Pubblico.
- **Descrizione:** Registrazione utente diretta sul Local (default `PLAYER`). Genera outbox `USER_REGISTERED`. Verifica unicità username vs locali e replicati.
- **Response:** `201`; `409` se duplicato.

#### `GET /api/auth/me`
- **Ruolo:** `authenticated`.
- **Descrizione:** Profilo utente corrente arricchito (risolto da `CurrentUserService` via `Authentication.getName()`).
- **Response:** `200` → `UserInfoDto`.

### 5.2 Local — Reservations

#### `POST /api/reservations`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN` (self-check `userId == principal`).
- **Descrizione:** Crea prenotazione; transizione macchina a `RESERVED`; outbox `RESERVATION_CREATED`; publish MQTT `state`.
- **Request:** `CreateReservationRequestDto { gameId, userId, startTime, endTime? }`.
- **Response:** `201` → `ReservationDto`; `403` self-check fallito.

#### `DELETE /api/reservations/{id}`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN` (self-check).
- **Descrizione:** Cancella prenotazione; rilascia macchina a `AVAILABLE`; outbox `RESERVATION_CANCELLED`.
- **Response:** `204`; `403`.

#### `GET /api/reservations?userId=`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN` (self-check).
- **Descrizione:** Lista prenotazioni per utente.
- **Response:** `200` → `List<ReservationDto>`.

### 5.3 Local — Games & Sessions

#### `GET /api/games`
- **Ruolo:** `PLAYER or GAME_ADMIN or PLATFORM_ADMIN or LOCAL_ADMIN`.
- **Descrizione:** Catalogo giochi del building.
- **Response:** `200` → `List<GameStateDto>`.

#### `GET /api/games/available`
- **Ruolo:** (stesso di `/api/games`).
- **Descrizione:** Solo macchine con `status = AVAILABLE`.
- **Response:** `200` → `List<GameStateDto>`.

#### `POST /api/sessions/start`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN` (a livello classe).
- **Descrizione:** Avvia `GameSession` (`IN_PROGRESS`). Valida prenotazione opzionale e `GameDefinitionLocal` (fallback a `GameFactory`). Publish MQTT `session/start` + `state` `IN_USE`.
- **Request:** `CreateSessionRequestDto { gameId, gameType, participants[], reservationId? }`.
- **Response:** `201` → `GameSessionDto`.

#### `POST /api/sessions/{id}/end`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN`.
- **Descrizione:** Termina sessione (`COMPLETED`) con `GameResult` polimorfico (`@JsonTypeInfo type`). Rilascia macchina a `AVAILABLE`. Outbox `GAME_SESSION_COMPLETED` (+ `TOURNAMENT_MATCH_COMPLETED` se torneo). Supporta **late-arrival** da `ABORTED`.
- **Request:** `GameResult` (raw, nel body).
- **Response:** `200` (corpo vuoto, `Void`).

#### `POST /api/sessions/{id}/{pause,resume}`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN`.
- **Descrizione:** Pausa/ripresa sessione; broadcast MQTT.
- **Response:** `200`.

#### `POST /api/sessions/lobby`, `POST /api/sessions/{id}/{join,start-lobby,cancel-lobby}`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN`.
- **Descrizione:** Gestione lobby (creazione, join, avvio da lobby, cancellazione).
- **Response:** `201`/`200` → `GameSessionDto`.

#### `GET /api/sessions/lobby/active`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN`.
- **Descrizione:** Lista lobby attive del building.
- **Response:** `200` → `List<GameSessionDto>`; `404` se nessuna.

#### `POST /api/sessions/lobby/cancel-by-game`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN`.
- **Descrizione:** Cancella la lobby associata a un `gameId`.
- **Response:** `200`; `404` se assente.

#### `GET /api/sessions/active`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN`.
- **Descrizione:** Sessioni attive del building.
- **Response:** `200` → `List<GameSessionDto>`.

### 5.4 Local — Statistics

#### `GET /api/statistics`
- **Ruolo:** `LOCAL_ADMIN or PLATFORM_ADMIN` (building-binding check).
- **Descrizione:** Statistiche aggregate del building. Con `?gameType=` ritorna `LocalStatistics`, altrimenti `List<StatisticsDto>`.
- **Response:** `200` → `List<StatisticsDto>` (o `LocalStatistics` con `gameType`); `403` binding non valido.

#### `GET /api/players/me/statistics`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN` (`?gameType=` opzionale).
- **Descrizione:** Statistiche personali calcolate **on-demand** da `game_sessions`+`session_participants` (nessuna nuova tabella locale). Utente non replicato → lista vuota (offline-first).
- **Response:** `200` → `List<PlayerStatisticsDto>`.

#### `GET /api/players/me/matches/history`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN`.
- **Descrizione:** Storico partite (proiezione `player_match_facts`).
- **Response:** `200` → `List<PlayerMatchDto>`.

### 5.5 Local — Devices

#### `POST /api/devices/register`
- **Ruolo:** `LOCAL_ADMIN or PLATFORM_ADMIN` (building-binding check).
- **Descrizione:** Il Game Client invia una **CSR** (CN = `gameId`). Il Local Server firma il certificato con la **Local CA** (BouncyCastle, `SHA256withRSA`) e restituisce certificato client + CA per il bootstrap **mTLS** su MQTT (8883).
- **Request:** `Map<String,String>` con chiavi `gameId` e `csrPem` (NESSUN `CsrRequestDto`).
- **Response:** `200` → `Map` con chiavi `clientCertificatePem` e `caCertificatePem` (NESSUN `DeviceCertificateDto`); `403`.

### 5.6 Local — Admin Local (LOCAL_ADMIN)

Tutti richiedono `LOCAL_ADMIN or PLATFORM_ADMIN` + building-binding check ( `PLATFORM_ADMIN` bypassa).

| Metodo | Path | Descrizione | Response |
|---|---|---|---|
| `GET` | `/api/admin/local/devices` | Dispositivi del building | `200` |
| `GET` | `/api/admin/local/sessions/active` | Sessioni attive (dashboard) | `200` |
| `GET` | `/api/admin/local/statistics` | Statistiche building (dashboard) | `200` |
| `POST` | `/api/admin/local/games` | Crea gioco nel catalogo locale (valida vs `game_definitions_local`) | `201`/`400`/`403` |
| `PUT` | `/api/admin/local/games/{gameId}` | Rename/stato gioco (`rename` + state machine) | `200`/`403` |
| `DELETE` | `/api/admin/local/games/{gameId}` | Elimina gioco dal catalogo | `204`/`403` |

### 5.7 Local — Admin Games (GAME_ADMIN)

Le definizioni di gioco sono **generate lato Central** e replicate localmente (`game_definitions_local`); il Local **non** espone `GET /api/admin/games/definitions`. La superficie GAME_ADMIN sul Local è in **async outbox** (202 `AdminRequestDto`):

| Metodo | Path | Ruolo | Descrizione |
|---|---|---|---|
| `POST` | `/api/admin/games` | `GAME_ADMIN or PLATFORM_ADMIN` | Upsert definizione (richiesta async verso il Central). → `202 AdminRequestDto`. |
| `PUT` | `/api/admin/games/{gameType}` | `GAME_ADMIN or PLATFORM_ADMIN` | Aggiorna definizione (richiesta async). → `202 AdminRequestDto`. |

### 5.8 Local — Admin Platform (PLATFORM_ADMIN)

| Metodo | Path | Descrizione |
|---|---|---|
| `GET` | `/api/admin/users` | Gestione utenti (superficie locale). |
| `POST` | `/api/admin/users/{userId}/roles` | Assegna ruolo — body `List<String>` (nomi ruoli), **async outbox** `ROLE_ASSIGNMENT_REQUESTED` verso il Central. → `202 AdminRequestDto`. |
| `GET` | `/api/admin/servers/health` | Health dei Local Server. |
| `PATCH` | `/api/admin/servers/{buildingId}/active` | Attiva/disattiva un Local Server (`ToggleServerActiveRequestDto`). → `200 ServerHealthDto`; `404`. |
| `GET` | `/api/admin/tournaments` | **Non esistente** sul Local. La lista tornei è `GET /api/tournaments` (player-facing, §5.9). Il `PlatformAdminTournamentController` espone solo scritture (POST/PUT/DELETE). |
| `GET` | `/api/admin/requests` | Polling `admin_requests_local` (filtra per `actingUserId`). Accessibile a qualsiasi utente autenticato; la `AdminRequestsView` fa polling ogni **8 s** (comportamento client). |
| `GET` | `/api/admin/requests/{requestId}` | Dettaglio singola richiesta. → `200`; `404`. |

### 5.9 Local — Tournaments

#### `POST /api/admin/tournaments`
- **Ruolo:** `PLATFORM_ADMIN`.
- **Descrizione:** Crea torneo **async** — scrive `admin_requests_local` PENDING + outbox `TOURNAMENT_CREATE_REQUESTED`.
- **Response:** `202` → `AdminRequestDto(status=PENDING)`.

#### `POST /api/admin/tournaments/{id}/{action}`
- **Ruolo:** `PLATFORM_ADMIN`.
- **Descrizione:** Lifecycle async. `action ∈ {open, cancel, schedule}` → outbox `TOURNAMENT_{OPEN,CANCEL,SCHEDULE}_REQUESTED`.
- **Response:** `202` → `AdminRequestDto`.

#### `PUT` / `DELETE /api/admin/tournaments/{id}`
- **Ruolo:** `PLATFORM_ADMIN`.
- **Descrizione:** Update/Delete async (lato Central solo `DRAFT`).
- **Response:** `202` → `AdminRequestDto`.

#### `POST /api/tournaments/{id}/participants`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN`.
- **Descrizione:** Iscrizione torneo **async** — outbox `PARTICIPANT_REGISTER_REQUESTED`.
- **Response:** `202` → `AdminRequestDto`.

#### `GET /api/tournaments` (replica locale)
- **Ruolo:** `authenticated`.
- **Descrizione:** Lista da `tournaments_summary_local` (`?status=` opzionale).
- **Response:** `200` → `List<TournamentSummaryDto>`.

#### `GET /api/tournaments/{id}`
- **Ruolo:** `authenticated`.
- **Descrizione:** Dettaglio aggregato (summary + standings + matches + participants).
- **Response:** `200` → `TournamentDetailDto`.

#### `GET /api/tournaments/{id}/{standings,matches,participants}`
- **Ruolo:** `authenticated`.
- **Descrizione:** Standings/matches/participants locali (replica `tournament_standings_local`, `tournament_matches_local`, `tournament_participants_local`).
- **Response:** `200` → rispettivi DTO.

#### `GET /api/players/tournaments/me/matches`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN`.
- **Descrizione:** Match `SCHEDULED` riferiti all'utente; risolve appartenenza team via `EXISTS` su `team_members_local`.
- **Response:** `200` → `List<TournamentMatchDto>`.

#### `POST /api/players/tournaments/matches/{matchId}/start?gameId=`
- **Ruolo:** `PLAYER or PLATFORM_ADMIN`.
- **Descrizione:** Avvia `GameSession` torneo (bind `tournamentMatchId`). Valida `status == SCHEDULED`; risolve partecipanti da `participantA`/`participantB`; invoca `GameSessionService.start(..., tournamentMatchId)`.
- **Response:** `201` → `GameSessionDto`; `404` se match assente; `409` se non SCHEDULED.

---

## 6. Internal Endpoints — Sincronizzazione Server-to-Server

Tutti protetti da **API Key** (`X-Internal-Api-Key`), esclusi dal filtro JWT.

### 6.1 Central System — `/internal/**` (ricevuti dal Local)

Il Central espone solo i seguenti endpoint interni (verificati sul codice):

| Metodo | Path | Descrizione | Contratto |
|---|---|---|---|
| `POST` | `/internal/sync/receive` | Il Local invia `SyncPayloadDto` (eventi PENDING). Il Central elabora in modo **idempotente** (`processed_events`), aggiorna `aggregated_statistics` (lock pessimistico) e replica utenti; aggiorna `lastSeenAt` (heartbeat). | `SyncPayloadDto { buildingId, events[] }` → `200 Void` |
| `POST` | `/internal/servers/register` | Self-registration del Local al boot (`local_servers`). | `RegisterServerRequest { buildingId, baseUrl }` (campi = `ServerRegisterDto`) → `200` |
| `GET` | `/internal/servers` | Lista server registrati (health). | `List<ServerHealthDto>` → `200` |

> **Nota:** i path `/internal/users/sync`, `/internal/metadata/sync`, `/internal/tournaments/*/sync`, `/internal/servers/sync` elencati in precedenza **NON** sono esposti dal Central: sono endpoint del **Local Server** (§6.2).

### 6.2 Local Server — `/internal/**` (ricevuti dal Central)

Il Local espone gli endpoint di replica (API Key). Tutti `PUT`, ritornano `200`:

| Metodo | Path | Descrizione | Contratto |
|---|---|---|---|
| `PUT` | `/internal/users/sync` | Replica utenti Central → Local (`replicated_users`). | `InternalSyncController` |
| `PUT` | `/internal/metadata/sync` | Replica binding LOCAL_ADMIN↔building (`local_admin_buildings_local`). | `InternalMetadataController` |
| `PUT` | `/internal/metadata/game-definitions/sync` | Replica `game_definitions_local`. | `InternalGameDefinitionSyncController` |
| `PUT` | `/internal/tournaments/matches/sync` | Replica `tournament_matches_local`. | `InternalTournamentController` |
| `PUT` | `/internal/tournaments/standings/sync` | Replica `tournament_standings_local` (delete+insert by tournamentId). | `InternalTournamentStandingsController` |
| `PUT` | `/internal/tournaments/participants/sync` | Replica `tournament_participants_local` (+ `team_members_local`). | `InternalTournamentParticipantsController` |
| `PUT` | `/internal/tournaments/summaries/sync` | Replica `tournaments_summary_local` (upsert, tombstone `deleted`). | `InternalTournamentSummaryController` |
| `PUT` | `/internal/servers/sync` | Replica registry server. | `InternalLocalServerRegistryController` |
| `GET` | `/internal/users/count` | Conteggio utenti replicati (health/sync). | `InternalSyncController` |

**Idempotenza:** ogni evento porta un `eventId` (UUID). Il Central deduceplica via `processed_events(event_id PK)`; le repliche locali usano PK naturali (composite key / `game_type`).

---

## 7. Matrice di Accesso Consolidata (RBAC)

### Central System

| Endpoint | Metodo | Ruolo |
|---|---|---|
| `/api/users` | POST | Pubblico |
| `/api/auth/login` | POST | Pubblico |
| `/api/admin/local/buildings` | POST/DELETE/GET | `PLATFORM_ADMIN` |
| `/api/admin/games/definitions` | POST | `GAME_ADMIN` (or `PLATFORM_ADMIN`) |
| `/api/admin/games/definitions/{gameType}` | PUT | `GAME_ADMIN` (or `PLATFORM_ADMIN`) |
| `/api/admin/games/definitions` | GET | `authenticated` |
| `/api/statistics` | GET | `PLATFORM_ADMIN` (`List<StatisticsDto>`) |
| `/api/players/me/statistics` | GET | `PLAYER` (or `PLATFORM_ADMIN`) |
| `/api/players/{userId}/statistics` | GET | self-check or `PLATFORM_ADMIN` |
| `/api/tournaments` | POST/GET | `PLATFORM_ADMIN` / `authenticated` |
| `/api/tournaments/{id}/{open,cancel,schedule}` | POST | `PLATFORM_ADMIN` |
| `/api/tournaments/{id}` | PUT/DELETE/GET | `PLATFORM_ADMIN` / `authenticated` |
| `/api/tournaments/{id}/{standings,matches}` | GET | `authenticated` |
| `/api/tournaments/{id}/participants` | POST/DELETE/GET | `PLAYER` (or `PLATFORM_ADMIN`) / `authenticated` |
| `/internal/sync/receive`, `/internal/servers/register` | POST | API Key |

### Local Server

| Endpoint | Metodo | Ruolo |
|---|---|---|
| `/api/auth/login`, `/api/auth/signup` | POST | Pubblico |
| `/api/auth/me` | GET | `authenticated` |
| `/api/reservations` | POST/GET | `PLAYER` (self-check) |
| `/api/reservations/{id}` | DELETE | `PLAYER` (self-check) |
| `/api/games`, `/api/games/available` | GET | `PLAYER or GAME_ADMIN or PLATFORM_ADMIN or LOCAL_ADMIN` (`List<GameStateDto>`) |
| `/api/sessions/**` | POST | `PLAYER or PLATFORM_ADMIN` |
| `/api/sessions/active` | GET | `PLAYER or PLATFORM_ADMIN` |
| `/api/statistics` | GET | `LOCAL_ADMIN or PLATFORM_ADMIN` (`List<StatisticsDto>`/`LocalStatistics`) |
| `/api/players/me/statistics`, `/api/players/me/matches/history` | GET | `PLAYER or PLATFORM_ADMIN` (`PlayerMatchDto`) |
| `/api/devices/register` | POST | `LOCAL_ADMIN` (binding) — body `Map`, response `Map` |
| `/api/admin/local/**` | GET/POST/PUT/DELETE | `LOCAL_ADMIN` (binding) |
| `/api/admin/games` (`POST`/`PUT`), no `GET /definitions` | async 202 | `GAME_ADMIN or PLATFORM_ADMIN` |
| `/api/admin/users`, `/api/admin/servers/health`, `/api/admin/requests` (+ `/{requestId}`) | GET | `PLATFORM_ADMIN` / autenticato (requests) |
| `GET /api/admin/tournaments` | — | **Non esiste** sul Local (usare `GET /api/tournaments`) |
| `PATCH /api/admin/servers/{buildingId}/active` | — | `PLATFORM_ADMIN` |
| `/api/admin/users/{userId}/roles` | POST | `PLATFORM_ADMIN` (body `List<String>`) |
| `/api/tournaments` (replica) + `{id}/{standings,matches,participants}` | GET | `authenticated` (`TournamentSummaryDto`/`TournamentDetailDto`/`TournamentMatchDto`) |
| `/api/tournaments/{id}/participants` | POST | `PLAYER or PLATFORM_ADMIN` |
| `/api/players/tournaments/me/matches`, `/api/players/tournaments/matches/{matchId}/start` | GET/POST | `PLAYER or PLATFORM_ADMIN` |
| `/api/admin/tournaments` (+ `/{id}` + `/{id}/{action}`) | POST/PUT/DELETE | `PLATFORM_ADMIN` |
| `/internal/**` | PUT/POST/GET | API Key |

---

## 8. Flussi End-to-End e Outbox Pattern

### 8.1 Ciclo di vita di una partita
1. `POST /api/reservations` (Local) → macchina `RESERVED`, outbox `RESERVATION_CREATED`.
2. MQTT `session/start` → Local crea `GameSession IN_PROGRESS`, macchina `IN_USE`.
3. Heartbeat MQTT ogni 5 min (grace 3 cicli = 15 min) → abort se irraggiungibile.
4. MQTT `session/end` → `POST /api/sessions/{id}/end` (o equivalente) → `COMPLETED`, outbox `GAME_SESSION_COMPLETED`.
5. `SyncSchedulerService` (ogni 5 min) invia gli eventi a `POST /internal/sync/receive` sul Central.

### 8.2 Torneo (async edge→hub)
- `POST /api/admin/tournaments` (Local) → `202 AdminRequestDto` + outbox `TOURNAMENT_CREATE_REQUESTED`.
- Central `SyncEventProcessor.handleTournamentCreateRequested` → `TournamentService.create` (DRAFT) → outbox `TOURNAMENT_SUMMARY_UPSERTED` → replica su `tournaments_summary_local`.
- `open`/`schedule`/`participants` seguono lo stesso pattern async; il bracket avanza via `TournamentBracketService.advanceWinner` + `TournamentStandingsService.recomputeAfterCompletion`.
- Il gameplay del match avviene sul Local (`POST /api/players/tournaments/matches/{matchId}/start`) e produce `TOURNAMENT_MATCH_COMPLETED` riconciliato dal Central.

### 8.3 Resilienza offline
Il Local opera autonomamente (login BCrypt, sessioni, prenotazioni su DB locale + broker MQTT locale). Gli eventi si accumulano nell'`outbox_events` e vengono inviati al ripristino della connettività. La replica utenti Central→Local avviene via `PUT /internal/users/sync`. Idempotenza ovunque previene doppi conteggi.

---

## 9. Note di Implementazione e Vincoli

- **Clean Architecture:** i controller REST stanno nell'infrastructure adapter; la logica di business nel domain puro (zero dipendenze Spring/JPA). I DTO vivono in `shared-dto`.
- **RBAC legacy:** `USER`/`ADMIN` sopravvivono solo come alias di lettura (`PLAYER`/`PLATFORM_ADMIN`); nessun controller di produzione emette `hasRole('USER')`/`hasRole('ADMIN')`.
- **Late arrival:** un client dichiarato `ABORTED` che torna online con `session/end` viene accettato (`ABORTED → COMPLETED`) preservando il risultato.
- **Session Recovery:** al boot il `SessionRecoveryService` ( `@DependsOn("mqttClient")`) pinga le sessioni `IN_PROGRESS`/`PAUSED`; non risponde → `ABORTED` (`SERVER_RESTART`).
- **Expiration:** `ReservationExpirationService` (`@Scheduled` ogni minuto) porta a `EXPIRED` le prenotazioni scadute e rilascia la macchina.
- **Truststore separati:** Central e Local hanno CA distinte; JWT e TLS non sono interscambiabili tra i due domini.

> **Deliverable associato:** `progetto-openapi.yaml` (OpenAPI 3.0.3) — specifica machine-readable di tutti gli endpoint, schemi e security scheme documentati in questo report.
