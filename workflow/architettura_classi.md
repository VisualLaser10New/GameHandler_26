# architettura_classi.md — Modello RBAC a 4 ruoli (FASE 0)

> **Documento:** Architettura delle classi — modello dei ruoli utente
> **Versione:** 1.0
> **Data:** 2026-07-12
> **Stato:** Implementato (FASE 0 di `documenti/PIANO_UTENTI_TORNEI.md`)
> **Pubblico:** Development Team
> **Storicamente referenziato in:** `workflow/analisi/problemi_noti.md` (POF-3), ora materiale.

---

## 1. Modello RBAC a 4 ruoli

La piattaforma passa dal modello legacy a due ruoli stringa (`USER`, `ADMIN`) a un modello a **quattro ruoli canonici**, codificati come `enum` di dominio condiviso.

| Ruolo canonico | Letterale |Spring authority | Responsabilità (obiettivo PIANO) |
|---|---|---|---|
| `PLAYER` | `PLAYER` | `ROLE_PLAYER` | Partecipa alle partite, consulta le proprie statistiche, visualizza i giochi disponibili nei locali, partecipa ai tornei. |
| `LOCAL_ADMIN` | `LOCAL_ADMIN` | `ROLE_LOCAL_ADMIN` | Gestisce i giochi del proprio locale, configura i dispositivi, monitora le partite, consulta le statistiche del locale. |
| `GAME_ADMIN` | `GAME_ADMIN` | `ROLE_GAME_ADMIN` | Definisce nuove tipologie di gioco, configura le regole di registrazione delle partite. |
| `PLATFORM_ADMIN` | `PLATFORM_ADMIN` | `ROLE_PLATFORM_ADMIN` | Gestisce utenti e locali, monitora il sistema, accede alle statistiche globali. |

I ruoli legacy `USER` e `ADMIN` **non vengono più emessi** dal codice applicativo (la registrazione assegna `PLAYER`), ma continuano a essere **riconosciuti in lettura** per compatibilità con token e righe DB preesistenti (§3).

---

## 2. Tipo di dominio `Role`

**Modulo:** `shared/shared-domain`
**Package:** `com.gameplatform.shared.domain.security`
**File:** `shared/shared-domain/src/main/java/com/gameplatform/shared/domain/security/Role.java`
**Natura:** Java puro, nessuna annotazione framework (conforme alle *isolation rules* del `domain/`, RNF-08).

```
public enum Role { PLAYER, LOCAL_ADMIN, GAME_ADMIN, PLATFORM_ADMIN }

// Strict — per validazione assignment (fasi successive). Rifiuta legacy e prefisso ROLE_.
public static Role of(String name)  // throws IllegalArgumentException se blank/sconosciuto/legacy

// Tollerante — per parsing claim JWT e righe DB. Mappa legacy, ignora null/blank/sconosciuto.
public static Set<Role> parse(String csv)
public static Set<Role> parse(Iterable<String> roles)

// Formattazione CSV canonica (senza legacy).
public static String format(Collection<Role> roles)

// Autorità Spring Security "ROLE_" + name(), con mappatura legacy. Usato dai filtri.
public static List<String> toAuthorityNames(String csv)
public static List<String> toAuthorityNames(Iterable<String> roles)
```

### Rationale: tollerante vs strict
- **`parse` tollerante** (salta `null`/blank/sconosciuto, mappa `USER`→`PLAYER` e `ADMIN`→`PLATFORM_ADMIN`, rimuove il prefisso `ROLE_`): è入口 del *compatibility window* al boundary JWT. Un token legacy con `roles=["USER"]` non deve mai 401 un utente valido durante la migrazione.
- **`of` strict** (rifiuta legacy e `ROLE_`): per la **validazione in scrittura** quando un `PLATFORM_ADMIN` assegnerà ruoli (FASE 1+). Garantisce che a DB non vengano mai scritti letterali legacy, mantenendo il vincolo dogmatico di dominio senza rompere la lettura dei token storici.

---

## 3. Mappatura claim → authority (finestra di compatibilità)

Il claim JWT `roles` rimane una `List<String>` (CSV lato DB `users.roles` / `replicated_users.roles`). I **filtri** convertono i letterali claim in autorità Spring attraverso `Role.toAuthorityNames`:

| Claim/token `roles` (input) | `Role.parse` → | Authority Spring |
|---|---|---|
| `USER` | `PLAYER` | `ROLE_PLAYER` |
| `ADMIN` | `PLATFORM_ADMIN` | `ROLE_PLATFORM_ADMIN` |
| `ROLE_USER` | `PLAYER` | `ROLE_PLAYER` |
| `ROLE_ADMIN` | `PLATFORM_ADMIN` | `ROLE_PLATFORM_ADMIN` |
| `PLAYER` | `PLAYER` | `ROLE_PLAYER` |
| `PLATFORM_ADMIN` | `PLATFORM_ADMIN` | `ROLE_PLATFORM_ADMIN` |
| `LOCAL_ADMIN` | `LOCAL_ADMIN` | `ROLE_LOCAL_ADMIN` |
| `GAME_ADMIN` | `GAME_ADMIN` | `ROLE_GAME_ADMIN` |
| `MODERATOR` (o altro sconosciuto) | — (saltato) | — (nessuna authority) |

---

## 4. Decisione architetturale — finestra di compatibilità al filtro, `JwtTokenProvider` invariato

Il `PIANO_UTENTI_TORNEI.md` FASE 0 elencava: *"Aggiornare `JwtTokenProvider` per emettere `roles` da `Set<Role>`"*. Durante l'implementazione (protocollo §5 — *Gestione di ambiguità*) si è rilevato che:

- `User` (`central` e `local`) resta `List<String>` per i ruoli (POJO di dominio); migrarlo a `Set<Role>` avrebbe rotto decine di test e contratto `UserSyncDto`.
- Normalizzare i claim *al momento dell'emissione* (`Role.format(Role.parse(user.getRoles()))` nel provider) avrebbe trasformato il claim `["USER"]` → `["PLAYER"]`, rompendo le asserzioni di round-trip esistenti (`JwtTokenProviderTest:138`, `JwtTokenProviderValidatorTest:48`) oltre ad aggiungere carico di test.
- La nota di rischio §7 del piano indicava esplicitamente la *"finestra di compatibilità"* in `JwtAuthenticationFilter`.

**Decisione presa (approvata dal richiedente):**
1. `JwtTokenProvider` (entrambi i moduli) ** rimane pass-through**: emette `user.getRoles()` così com'è. Invariato.
2. Le nuove registrazioni producono `["PLAYER"]` (default cambiato in `UserService.register` e `LocalSignupService.register`), quindi i token **nuovi** portano già il letterale canonico.
3. La **mappatura legacy avviene esclusivamente al boundary di lettura** (`JwtAuthenticationFilter` centrale e `JwtTokenValidator` locale) via `Role.toAuthorityNames`. I token storici con `USER`/`ADMIN` continuano così ad autorizzare correttamente (`ROLE_PLAYER` / `ROLE_PLATFORM_ADMIN`).
4. `@PreAuthorize` aggiornati: `StatisticsController` (central) → `hasRole('PLATFORM_ADMIN')`; `GameController`, `GameSessionController`, `ReservationController`, `StatisticsController` (local) → `hasRole('PLAYER')`.

**Consequenza desiderata:** zero rotture al *contratto del token*, score di test impattato minimo (solo autorità attese, non round-trip del claim), e vecchi token ancora validi. Il follow-up "emissione canonica dal provider" resta opzionale (riallineamento futuro del claim, non necessario).

---

## 5. Modifiche per modulo

### `shared/shared-domain`
- **New** `security/Role.java` — enum + `of`/`parse`/`format`/`toAuthorityNames`.

### `central-system`
| File | Modifica |
|---|---|
| `application/service/UserService.java:69` | `List.of("USER")` → `List.of(Role.PLAYER.name())`; import `Role`. |
| `infrastructure/security/JwtAuthenticationFilter.java` | mapping autorità sostituito con `Role.toAuthorityNames(roles)`; import `Role`. |
| `infrastructure/adapters/in/rest/StatisticsController.java:24` | `hasRole('ADMIN')` → `hasRole('PLATFORM_ADMIN')`. |
| `infrastructure/security/JwtTokenProvider.java` | **invariato** (pass-through). |
| `domain/model/User.java` | **invariato** (`List<String>` roles). |

### `local-server`
| File | Modifica |
|---|---|
| `infrastructure/security/JwtTokenValidator.java` | `getAuthorities` delega a `Role.toAuthorityNames(roles)`; import `Role`. |
| `application/service/LocalSignupService.java:80` | `List.of("USER")` → `List.of(Role.PLAYER.name())`; import `Role`. |
| `infrastructure/adapters/in/rest/GameController.java:18` | `hasRole('USER')` → `hasRole('PLAYER')`. |
| `infrastructure/adapters/in/rest/GameSessionController.java:32` | `hasRole('USER')` → `hasRole('PLAYER')`. |
| `infrastructure/adapters/in/rest/ReservationController.java:21` | `hasRole('USER')` → `hasRole('PLAYER')`. |
| `infrastructure/adapters/in/rest/StatisticsController.java:25` | `hasRole('USER')` → `hasRole('PLAYER')`. |
| `infrastructure/security/JwtTokenProvider.java` | **invariato** (pass-through). |
| `domain/model/User.java`, `domain/model/LocalSignupUser.java` | **invariati** (`List<String>` roles). |

### `infrastructure/mysql-*` (schema source of truth, init Docker)
Vedi §6.

---

## 6. Migrazione schema DB

Niente Flyway/Liquibase; lo schema è inizializzato da Docker `docker-entrypoint-initdb.d`. Hibernate `ddl-auto: validate`. I 4 file `init*.sql` sono stati estesi con:

1. **`infrastructure/mysql-central/init.sql`** — blocco `UPDATE users` idempotente in append.
2. **`infrastructure/mysql-local/init.sql`** — `DEFAULT 'USER'` → `DEFAULT 'PLAYER'` su `users.roles` + blocco `UPDATE` idempotente su `users` e `replicated_users`.
3. **`infrastructure/mysql-local/init-building-2.sql`** — come (2).
4. **`infrastructure/mysql-local/init-building-3.sql`** — come (2).

### Bug evitato: doppia sostituzione di `PLATFORM_ADMIN`
Poiché `"PLATFORM_ADMIN"` contiene la sottostringa `"ADMIN"`, un `REPLACE(REPLACE(roles,'USER','PLAYER'),'ADMIN','PLATFORM_ADMIN')` trasformerebbe `"PLATFORM_ADMIN"` in `"PLATFORM_PLATFORM_ADMIN"`. La migrazione usa pertanto **`UPDATE` exact-match** per ogni combinazione legacy nota:

```sql
UPDATE users SET roles = 'PLAYER'                   WHERE roles = 'USER';
UPDATE users SET roles = 'PLATFORM_ADMIN'           WHERE roles = 'ADMIN';
UPDATE users SET roles = 'PLAYER,PLATFORM_ADMIN'    WHERE roles = 'USER,ADMIN';
UPDATE users SET roles = 'PLAYER,PLATFORM_ADMIN'    WHERE roles = 'ADMIN,USER';
UPDATE users SET roles = 'ROLE_PLAYER'              WHERE roles = 'ROLE_USER';
UPDATE users SET roles = 'ROLE_PLATFORM_ADMIN'      WHERE roles = 'ROLE_ADMIN';
```

Idempotenza verificata (double-pass produce output identico; 0 occorrenze di `%PLATFORM_PLATFORM_ADMIN%`) su container throwaway `mysql:8.0`. No-op su DB vergini. Per DB di dev già popolati con combinazioni non elencate: comando obbligatorio `docker-compose down -v` e reinit.

---

## 7. Modifiche ai test

**Aggiornate solo le asserzioni attese (i dati di input restano i letterali legacy, per esercitare il *compatibility window*):**

| File | Asserzione | Da → A |
|---|---|---|
| `central/.../UserServiceTest.java:66` | ruolo registrato | `containsExactly("USER")` → `"PLAYER"` |
| `central/.../JwtAuthenticationFilterTest.java:198` | authority claim `USER` | `"ROLE_USER"` → `"ROLE_PLAYER"` |
| `central/.../JwtAuthenticationFilterTest.java:217` | authority claim `ROLE_ADMIN` | `"ROLE_ADMIN"` → `"ROLE_PLATFORM_ADMIN"` |
| `local/.../LocalSignupServiceTest.java:64` | ruolo registrato | `"USER"` → `"PLAYER"` |
| `local/.../LocalSignupServiceTest.java:83` | `dto.roles()` replicato | `"USER"` → `"PLAYER"` |
| `local/.../LocalSignupServiceEdgeCaseTest.java:105` | ruolo registrato | `"USER"` → `"PLAYER"` |
| `local/.../JwtTokenProviderValidatorTest.java:94` | `getAuthorities` | `contains("ROLE_USER","ROLE_ADMIN")` → `contains("ROLE_PLAYER","ROLE_PLATFORM_ADMIN")` |

**Aggiunto (new test):**
- `central/.../AuthServiceTest.java::authenticate_shouldPropagatePlayerRoleIntoToken` — verifica via `ArgumentCaptor` che il `User` passato a `JwtTokenProvider.generateTokenWithExpiry` rechi `roles = ["PLAYER"]` dopo `authenticate`. Import aggiunti: `Role`, `ArgumentCaptor`.

**Invariate (verificate verdi):** `UserTest` (central/local), `LocalSignupUserTest`, mapper tests, `UserServiceBugTest` (updateUser passthrough), `UserServiceFromSyncTest`/`EdgeCase`, `JwtTokenProviderTest:138` (claim round-trip), `JwtTokenProviderValidatorTest:48` (claim round-trip), `JwtAuthenticationFilterTest` (local, mocka `getAuthorities`), `UserSyncDtoSerializationTest`.

---

## 8. Verifica FASE 0 (§4 — compilazione e test)

Eseguita da `gamehandler-platform/`:

| Comando | Esito |
|---|---|
| `mvn clean compile -pl :shared-domain` | EXIT 0 |
| `mvn clean compile -pl :central-system` | EXIT 0 |
| `mvn clean compile -pl :local-server` | EXIT 0 |
| `mvn clean test -pl :shared-domain,:central-system,:local-server -am` | EXIT 0 (tutti i test verdi) |

`MultiBuildingEndToEndIT` non eseguita in F0 (modulo `e2e-tests` separato, Docker-gated; non asserisce ruoli quindi non impattata dalla migrazione).

---

## 9. Follow-up noti (fuori scope FASE 0)

- **`RoleTest` in `shared-domain`:** non aggiunto. `shared-domain` non ha oggi né test né infrastruttura di test (junit-jupiter/assertj/surefire); introdurla è scope di una fase a sé. Nel frattempo il comportamento di `Role` è coperto indirettamente da `JwtAuthenticationFilterTest` (central), `JwtTokenProviderValidatorTest` (local) e dal nuovo caso `AuthServiceTest`. **Raccomandato** aggiungere `RoleTest` quando `shared-domain` acquisirà dipendenze test.
- **e2e B13/B16** (`B13CentralUserUpdateReplicatesRoleReplacementToLocalTest`, `B16DoubleUpdateReplicatedUserPreservesVersionTest`) asseriscono `"USER"` su utenti replicati. Non inclusi nel comando di verifica F0 (`mvn -pl shared-domain,central-system,local-server test`); al runtime Docker si romperebbero per via del nuovo default `PLAYER`. Da aggiornare nella fase che riguarderà il modulo `e2e-tests` (o al重启 dello smoke multi-building).
- **POF-3 residuo Central** (purge outbox) — preesistente, non toccato da FASE 0.
- **Claim-canonicalizzazione lato provider** — opzionale (vedi §4 punto 4); non necessaria ai fini RBAC.

---

## 10. FASE 1 — Amministratore del Locale (binding + enforcement)

> **Stato:** Implementato (FASE 1 di `documenti/PIANO_UTENTI_TORNEI.md`).
> **Verifica:** `mvn clean test -pl :shared-dto,:central-system,:local-server -am` → EXIT 0 (central 249 + local 594 test, 0 failures). Verifica preliminare §3.4: 9/9 controlli contract-surface PASS.

### 10.1 Modello di dominio — binding LOCAL_ADMIN ↔ building

Il binding è un'aggregato separato (POJO Java puro, `domain/model/LocalAdminBuilding.java` in entrambi i moduli) con identità composita `(userId, buildingId)` e campo `assignedAt: Instant`. Non modifica l'entità `User` (che resta senza campi building, come in FASE 0).

### 10.2 Decisioni architetturali prese (protocollo §5)

Quattro decisioni sono state approvate prima dell'implementazione (vedi `PIANO_UTENTI_TORNEI.md` checkbox FASE 1 per le note inline):

| Decisione | Scelta | Motivazione |
|---|---|---|
| **A3** — Claim JWT `buildings` | **NON aggiunto** (né central né local) | Coerente con la cautela di FASE 0 (§4: `TokenProviderPort`/`JwtTokenProvider` pass-through, firme invariate). L'enforcement è delegato al `LocalAdminBuildingAuthorizationManager` che consulta la tabella replicata `local_admin_buildings_local`. Il claim `buildings` si aggiungerà in una fase futura quando un `LOCAL_ADMIN` dovrà chiamare endpoint centrali scoped. La FASE 1 ha `LocalAdminController` (central) solo `PLATFORM_ADMIN`. |
| **B1** — `PUT /games/{gameId}` | `Game.name` reso **non-`final`** + metodo domain `rename(String)` (valida non-blank) | Piena aderenza al PIANO "modifica nome/stato". `PUT` applica `rename` + transizione di stato via macchina esistente (`setMaintenance()`/`release()`). Piccola evoluzione domain pulita. |
| **C1** — `POST /games` validazione | Valida contro enum `GameType` (`GameType.valueOf`) | FASE 2 rafforzerà la validazione contro `game_definitions_local` (min/max players, team_allowed). FASE 1 self-contained. |
| **D2** — Replica metadata | **Esteso** `UserReplicationSchedulerService` (+ `LateRegistrationCatchUpService`) | Singolo scheduler drena USER_REGISTERED/USER_UPDATED + LOCAL_ADMIN_BUILDING_ASSIGNED/REVOKED; ramifica per eventType. Tracciamento `replication_progress` invariato (usa sempre l'id outbox). Nuovo `PushMetadataToLocalServersPort` + `LocalMetadataRestAdapter` (`PUT /internal/metadata/sync`). |

### 10.3 Deviazioni accessorie emerse durante l'implementazione

- **`game_definitions` / `game_definitions_local`** (PIANO §1.3/§1.4) — **differiti a FASE 2**. La FASE 1 = solo binding LOCAL_ADMIN↔building. Lo schema `init.sql` aggiunge solo `local_admin_buildings` (central) / `local_admin_buildings_local` (local ×3 building).
- **`UserRoleService` / `UserRoleController` / `RoleNotFoundException`** (PIANO §1.5) — **esclusi** dalla FASE 1 (la checklist FASE 1 non li menziona; l'assegnazione ruoli è uno scope separato).
- **Locale `ErrorResponseDto`** non esiste nel `local-server` → il nuovo handler 403 per `BuildingNotRegisteredToAdminException` usa `ResponseEntity<Void>` (status-only), in stile con gli handler esistenti del `GlobalExceptionHandler` locale. Non è stato introdotto un nuovo DTO.
- **IT `AdminLocalControllerTest`** come **slice test** (`MockMvcBuilders.standaloneSetup` + `@Mock` use cases) invece di `@SpringBootTest` (H2 mode): il contesto applicativo locale eagerly istanzia il client MQTT (`MqttConfig.mqttClient`) durante il refresh, fallendo senza broker. È il pattern già adottato da tutti i test di controller locali esistenti (`StatisticsControllerTest`, `GameOptimisticLockGuardTest`, ecc.). Il test copre: GET /devices 200 + seeded games, 403 senza binding, POST 201, gameType invalido 400, DELETE 204, statistiche, sessioni attive, + delegazione InternalMetadataController.
- **`@IdClass` composita** per `LocalAdminBuildingJpaEntity` (entrambi i moduli): scelta per allinearsi al PIANO SQL `PRIMARY KEY (user_id, building_id)` e alla precedenza di `SessionParticipantJpaEntity` (local). Si discosta dal pattern del central `ReplicationProgressJpaEntity` (synthetic id + unique constraint) ma è più naturale per una tabella di binding e mantiene Hibernate `validate` allineato allo schema.

### 10.4 Matrice file — FASE 1

| Modulo | Nuovi file | File modificati |
|---|---|---|
| `shared/shared-dto` | `AssignLocalAdminBuildingsDto`, `LocalAdminBuildingsDto`, `LocalAdminBuildingEventDto`, `CreateGameRequestDto`, `UpdateGameRequestDto` (5 DTO) | — |
| `central-system` | `domain/model/LocalAdminBuilding`; `domain/ports/in/{AssignLocalAdminBuildingsUseCase, GetLocalAdminBuildingsUseCase}`; `domain/ports/out/{LocalAdminBuildingRepository, PushMetadataToLocalServersPort}`; `infrastructure/adapters/out/mysql/entity/{LocalAdminBuildingJpaEntity, LocalAdminBuildingId}`; `.../repository/LocalAdminBuildingJpaRepository`; `.../mapper/LocalAdminBuildingMapper`; `.../adapter/LocalAdminBuildingRepositoryAdapter`; `infrastructure/adapters/out/rest/LocalMetadataRestAdapter`; `application/service/LocalAdminBuildingService`; `infrastructure/adapters/in/rest/LocalAdminController`; test `LocalAdminBuildingServiceTest` (14 nuovi) | `infrastructure/mysql-central/init.sql` (append `local_admin_buildings`); `UserReplicationSchedulerService` (+metadata branch, +`PushMetadataToLocalServersPort` ctor); `LateRegistrationCatchUpService` (+`REPLICATION_EVENT_TYPES`, +metadata branch); 6 test untero scheduler/catch-up (ctor) |
| `local-server` | `domain/model/LocalAdminBuilding`; `domain/exception/BuildingNotRegisteredToAdminException`; `domain/ports/in/{ManageGameCatalogUseCase, ListBuildingGamesUseCase, ListBuildingActiveSessionsUseCase, GetBuildingStatisticsUseCase}`; `domain/ports/out/LocalAdminBuildingLocalRepository`; `infrastructure/adapters/out/mysql/entity/{LocalAdminBuildingJpaEntity, LocalAdminBuildingId}`; `.../repository/LocalAdminBuildingJpaRepository`; `.../mapper/LocalAdminBuildingMapper`; `.../adapter/LocalAdminBuildingLocalRepositoryAdapter`; `application/service/{LocalAdminBuildingSyncService, GameCatalogService}`; `infrastructure/security/LocalAdminBuildingAuthorizationManager`; `infrastructure/adapters/in/rest/{AdminLocalController, InternalMetadataController}`; test `AdminLocalControllerTest` (18 nuovi) | `infrastructure/mysql-local/init.sql` + `init-building-2.sql` + `init-building-3.sql` (append `local_admin_buildings_local`); `domain/model/Game` (name non-`final` + `rename`); `domain/ports/out/GameRepository` (+`deleteById`); `infrastructure/adapters/out/mysql/adapter/GameRepositoryAdapter` (+`deleteById`); `application/service/GameStateService` (+`getByBuilding`, +`implements ListBuildingGamesUseCase`); `application/service/StatisticsService` (+`getActiveSessionsByBuilding`, +`getStatisticsForBuilding`, +`implements ListBuildingActiveSessionsUseCase, GetBuildingStatisticsUseCase`); `infrastructure/adapters/in/rest/GlobalExceptionHandler` (+403 `BuildingNotRegisteredToAdminException`) |

### 10.5 Contract surface Central → Local (metadata replication)

- **Event type literals** (byte-identical entrambi i lati): `LOCAL_ADMIN_BUILDING_ASSIGNED`, `LOCAL_ADMIN_BUILDING_REVOKED`.
- **DTO outbox payload**: `LocalAdminBuildingEventDto(eventId, eventType, userId, buildingId, assignedAt)`. `eventId` == id outbox (UUID condiviso in `LocalAdminBuildingService.writeOutboxEvent`); `assignedAt` null per REVOKED.
- **REST**: `PUT /internal/metadata/sync`; header `X-Internal-Api-Key`. Il `LocalMetadataRestAdapter` (central)_PUSH; `InternalMetadataController` (local) riceve `List<LocalAdminBuildingEventDto>`.
- **Idempotenza locale**: upsert per PK composita (ASSIGNED) / delete per PK (REVOKED) su `local_admin_buildings_local`; nessun `processed_events` (l'idempotenza è garantita dalla PK composita).
- **Tracciamento**: `replication_progress` (id outbox + serverId) invariato; `markAsSent` se tutti i Local attivi hanno acked.

### 10.6 Schema DB — FASE 1

- **Central** (`infrastructure/mysql-central/init.sql`): append di `local_admin_buildings (user_id VARCHAR(36), building_id VARCHAR(100), assigned_at TIMESTAMP, PRIMARY KEY (user_id, building_id), FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)` — Source of Truth.
- **Local ×3** (`init.sql`, `init-building-2.sql`, `init-building-3.sql`): append di `local_admin_buildings_local (user_id VARCHAR(36), building_id VARCHAR(100), assigned_at TIMESTAMP, PRIMARY KEY (user_id, building_id))` — replica read-only, nessuna FK.

### 10.7 Endpoint `@PreAuthorize` — FASE 1

| Endpoint | Modulo | Ruolo richiesto |
|---|---|---|
| `POST/DELETE/GET /api/admin/local/buildings` | central | `PLATFORM_ADMIN` |
| `GET /api/admin/local/devices` | local | `LOCAL_ADMIN` (+ building-binding check) |
| `GET /api/admin/local/sessions/active` | local | `LOCAL_ADMIN` (+ building-binding check) |
| `GET /api/admin/local/statistics` | local | `LOCAL_ADMIN` (+ building-binding check) |
| `POST /api/admin/local/games` | local | `LOCAL_ADMIN` (+ building-binding check) |
| `PUT/DELETE /api/admin/local/games/{gameId}` | local | `LOCAL_ADMIN` (+ building-binding check) |
| `PUT /internal/metadata/sync` | local | API Key (`InternalApiKeyFilter`) |

### 10.8 Follow-up noti (fuori scope FASE 1)

- **Claim `buildings` nel JWT** — differito a fase futura (vedi A3). Quando un `LOCAL_ADMIN` dovrà chiamare endpoint centrali scoped, si aggiungerà l'overload `TokenProviderPort.generateTokenWithExpiry(User, Instant, List<String> buildings)` + popolamento da `LocalAdminBuildingRepository.findByUserId`.
- **`UserRoleService` / `UserRoleController` / assegnazione ruoli (RF-UT-02)** — non ancora implementati; la FASE 1 li ha esclusi. Saranno oggetto di una fase dedicata (PIANO §1.5).
- **`game_definitions_local`** (FASE 2) — la validazione di `POST /games` contro le definizioni di gioco configurabili (min/max players, team_allowed) è demandata a FASE 2 (PIANO FASE 2 checklist).
- **`processed_events` lato Local per metadata** — non introdotta (idempotenza via PK composita). Se in futuro servirà tracciare eventi metadata non idempotenti-by-PK, si valuterà allora.
- **`@SpringBootTest` IT completo su local-server** — bloccato dal client MQTT eagerly istanziato a context-refresh. Sblocco futuro: estrarre la configurazione MQTT in un `@Configuration` `@ConditionalOnProperty` o fornire un broker embedded (mosquitto-testcontainer) per gli IT.

---

## 11. FASE 2 — Amministratore del Gioco (game definitions)

> **Stato:** Implementato (FASE 2 di `documenti/PIANO_UTENTI_TORNEI.md`).
> **Requisiti:** RF-UT-GA-01..03 (vedi `documenti/REQUIREMENTS.md` §1.1.ter).
> **Convenzione:** specchia la FASE 1 (`LocalAdminBuilding`) per struttura di package, entità `@IdClass`, adapter, mapper, outbox con UUID condiviso.

### 11.1 Modello di dominio — `GameDefinition` / `GameDefinitionLocal`

POJO Java puro in `domain/model/` (entrambi i moduli). Identity = `gameType` (PK). Campi: `gameType: GameType`, `name: String`, `minPlayers: int`, `maxPlayers: int`, `teamAllowed: boolean`, `registrationRules: Map<String,Object>` (defensive copy, nullable), `createdAt`/`updatedAt: Instant` (solo `updatedAt` nel modello locale, che è replica read-only). Invarianti nel costruttore: `gameType != null`, `name` non-blank, `minPlayers >= 1`, `maxPlayers >= 1`, `minPlayers <= maxPlayers`, timestamp non-null. `equals/hashCode` su `gameType` solo.

### 11.2 Decisioni architetturali prese (protocollo §5)

| Decisione | Scelta | Motivazione |
|---|---|---|
| **E1** — Replica metadata `GAME_DEFINITION_UPSERTED` | **Flow parallelo isolato**: nuovo `PushGameDefinitionToLocalServersPort` + `LocalGameDefinitionRestAdapter` + endpoint dedicato `PUT /internal/metadata/game-definitions/sync`, invece di widenare `PushMetadataToLocalServersPort` | Preserva la firma preesistente di `PushMetadataToLocalServersPort.pushMetadata(List<LocalAdminBuildingEventDto>, ...)` (vincolo backward-compat: "non rompa alcuna firma"). Il `InternalApiKeyFilter` protegge automaticamente ogni path `/internal/**`. |
| **E2** — `GameSessionService.start` validazione | Legge `GameDefinitionLocalRepository.findByGameType`; se assente → **fallback a `GameFactory.createGame(...)`** per offline-first resilience | Preserva il comportamento FASE 1 quando la replica central non è ancora pervenuta al Local. Signature di `start(...)` invariata. `team_allowed` rinviata al contesto torneo (FASE 6). |
| **E3** — `AdminLocalController POST /games` | Rafforza la validazione FASE 1 (decisione §10.2 C1): dopo `GameType.valueOf(...)` (enum) chiama `existsByGameType`; assente → `GameDefinitionNotAvailableLocallyException` → 400 | Sostituisce la validazione puramente enum-based di FASE 1 con il check contro la replica `game_definitions_local`. |
| **E4** — Outbox `GAME_DEFINITION_UPSERTED` | UUID condiviso `OutboxEvent.id == GameDefinitionEventDto.eventId`; scrittura atomica `@Transactional` class-level su `GameDefinitionService` | Coerente con la regola §10.5 (FASE 1): tracciamento `replication_progress` via id outbox; idempotenza locale per PK `game_type`. |
| **E5** — Estensione scheduler/catch-up | `UserReplicationSchedulerService` + `LateRegistrationCatchUpService` estesi con nuovo branch `isGameDefinitionEvent`/`replicateGameDefinitionEvent` (7° parametro ctor; ctor 5/6-arg esistenti delegano con `null`) | Zero churn dei ~14 siti test esistenti; behavior byte-identico quando la proiezione è `null`. |

### 11.3 Matrice file — FASE 2

**Nuovi (central):** `domain/model/GameDefinition.java`; `domain/exception/{GameDefinitionNotFound,InvalidGameDefinition}Exception.java`; `domain/ports/in/{Upsert,List}GameDefinitionUseCase.java`; `domain/ports/out/{GameDefinitionRepository,PushGameDefinitionToLocalServersPort}.java`; `application/service/GameDefinitionService.java`; `infrastructure/adapters/out/mysql/entity/{GameDefinitionJpaEntity}.java`; `.../repository/GameDefinitionJpaRepository.java`; `.../mapper/GameDefinitionMapper.java`; `.../adapter/GameDefinitionRepositoryAdapter.java`; `infrastructure/adapters/out/rest/LocalGameDefinitionRestAdapter.java`; `infrastructure/adapters/in/rest/GameAdminController.java`.
**Nuovi (local):** `domain/model/GameDefinitionLocal.java`; `domain/ports/out/GameDefinitionLocalRepository.java`; `domain/exception/GameDefinitionNotAvailableLocallyException.java`; `application/service/GameDefinitionSyncService.java`; `infrastructure/adapters/in/rest/InternalGameDefinitionSyncController.java`; `infrastructure/adapters/out/mysql/entity/GameDefinitionLocalJpaEntity.java` + `.../repository/...JpaRepository.java` + `.../mapper/...Mapper.java` + `.../adapter/...RepositoryAdapter.java`.
**Nuovi (shared-dto):** `GameDefinitionDto.java`, `UpsertGameDefinitionRequestDto.java`, `GameDefinitionEventDto.java`.
**Modificati (central):** `application/service/{UserReplicationSchedulerService,LateRegistrationCatchUpService}.java`; `infrastructure/adapters/in/rest/GlobalExceptionHandler.java`.
**Modificati (local):** `application/service/GameSessionService.java` (blocco validazione `start`); `infrastructure/adapters/in/rest/{AdminLocalController,GlobalExceptionHandler}.java`.
**Modificati (schema):** `infrastructure/mysql-central/init.sql` (tabella `game_definitions` + seed 7 righe); `infrastructure/mysql-local/{init,init-building-2,init-building-3}.sql` (tabella `game_definitions_local` ×3).
**Test:** `GameDefinitionServiceTest` (6 unit), `GameAdminControllerTest` (4 slice IT); retrofit 14 test FASE 1 (7 central scheduler/catchup + 7 local session/admin).

### 11.4 Contract surface Central → Local (game definitions replication)

- **Event type literal**: `GAME_DEFINITION_UPSERTED` (byte-identical entrambi i lati).
- **DTO payload**: `GameDefinitionEventDto(eventId, eventType, gameType, name, minPlayers, maxPlayers, teamAllowed, registrationRules, updatedAt)`. `eventId` == outbox id (UUID condiviso).
- **REST**: `PUT /internal/metadata/game-definitions/sync` (header `X-Internal-Api-Key`). Body: `List<GameDefinitionEventDto>`. Endpoint dedicato separato da `PUT /internal/metadata/sync` (FASE 1) per preservare firme.
- **Idempotenza locale**: upsert per PK `game_type` su `game_definitions_local`; re-delivery idempotente.

### 11.5 Schema DB — FASE 2

- **Central**: `game_definitions (game_type VARCHAR(50) PK, name VARCHAR(200), min_players INT, max_players INT, team_allowed BOOLEAN, registration_rules JSON NULL, created_at TIMESTAMP, updated_at TIMESTAMP)` + seed 7 righe (CHESS/FOOSBALL/DARTS/MONOPOLY/RISK/SLOT_MACHINE/ROULETTE) con `ON DUPLICATE KEY UPDATE`.
- **Local ×3**: `game_definitions_local (game_type VARCHAR(50) PK, name VARCHAR(200), min_players INT, max_players INT, team_allowed BOOLEAN, registration_rules JSON NULL, updated_at TIMESTAMP)` — replica read-only, no `created_at`, no FK.

### 11.6 Endpoint `@PreAuthorize` — FASE 2

| Endpoint | Modulo | Ruolo richiesto |
|---|---|---|
| `POST /api/admin/games/definitions` | central | `GAME_ADMIN` |
| `PUT /api/admin/games/definitions/{gameType}` | central | `GAME_ADMIN` |
| `GET /api/admin/games/definitions` | central | `authenticated` |
| `PUT /internal/metadata/game-definitions/sync` | local | API Key (`InternalApiKeyFilter`) |

### 11.7 Follow-up noti (fuori scope FASE 2)

- **`game_definitions` DELETE**: non implementata (PIANO §1.5 non la richiede per FASE 2). `GameDefinitionRepository.deleteByGameType` esiste come port ma non è esposto via REST.
- **`registration_rules` JSON**: mappato come `String` JSON sull'entità + conversione `Map<String,Object>` nel mapper via `ObjectMapper`. Nessun `AttributeConverter` dedicato (scelta di semplicità).
- **`UserRoleService`/`UserRoleController` (RF-UT-02)**: tuttora non implementati; la FASE 2 non li ha introdotti. Saranno oggetto di fase dedicata.

---

## 12. FASE 3 — Statistiche del Giocatore

> **Stato:** Implementato (FASE 3 di `documenti/PIANO_UTENTI_TORNEI.md`).
> **Requisiti:** RF-UT-PL-01..02 (vedi `documenti/REQUIREMENTS.md` §1.1.quater).
> **Convenzione:** read-model per-giocatore popolato dal `SyncEventProcessor` centrale consumando `GAME_SESSION_COMPLETED` arricchito; replica locale on-demand da `game_sessions`+`session_participants` esistenti (nessuna nuova tabella locale).

### 12.1 Modello di dominio — read-model `PlayerMatchFact` / `PlayerStatistics`

POJO Java puro in `central domain/model/`. `PlayerMatchFact` identity = composita `(sessionId, userId)`; campi: `sessionId: String`, `userId: UserId`, `buildingId: BuildingId`, `gameType: GameType`, `tournamentId: String` (nullable, popolato in FASE 6), `won: boolean`, `winCondition: WinCondition` (nullable), `endedAt: Instant`. `PlayerStatistics` identity = composita `(userId, gameType)`; campi: `matchesPlayed: int`, `matchesWon: int`, `lastPlayedAt: Instant` (nullable). Metodo domain `mergeIncrement(boolean won, Instant endedAt)` per l'incremento atomico.

### 12.2 Decisioni architetturali prese (protocollo §5)

| Decisione | Scelta | Motivazione |
|---|---|---|
| **D1** — Recupero `userId` per `/me` | `Authentication.getName()` (username) → `UserRepository.findByUsername` via `CurrentUserService` (`@Component` di security) | Mirroring decisione A3 di FASE 1 (`LocalAdminBuildingAuthorizationManager`). Il filtro JWT memorizza lo username come principal; non modifica il filtro. |
| **D2** — Race first-bucket su `player_statistics` | `@Lock(PESSIMISTIC_WRITE)` su `findByUserIdAndGameTypeForUpdate` + same-tx EM-retry: intercetta `PersistenceException` di PK duplicate **prima** che attraversi il proxy `@Transactional` (marcatore rollback-only) → `em.clear()` + re-find-locked + merge | Più pulito del `RetryHelper` usato da `aggregated_statistics` (che opera in tx `REQUIRES_NEW` separata): il retry same-tx non avvelena la tx del chiamante. Errori di flush genuini sono rilanciati (poison-isolation). |
| **D3** — `SyncEventProcessor` backward-compat | 7° parametro ctor nullable (`PlayerStatisticsProjectionService`); ctor a 6/5 arg esistenti delegano con `null`; behavior byte-identico quando proiezione è `null` (skip guardato) | Zero churn dei ~14 siti `new SyncEventProcessor(...)`/`new SyncReceiverService(...)` nei test FASE 0/1/2. La proiezione gira solo in production (Spring inietta il bean). |
| **D4** — Payload `GAME_SESSION_COMPLETED` superset | Aggiunti `participants` + `winnerId` (§2.2) **e** `winCondition` (pragmatico: serve per `player_match_facts.win_condition`) | Purely additive → i test esistenti con asserzioni substring restano verdi. Evita il re-parsing del JSON polimorfico di `GameResult`. |
| **D5** — `matches_played` locale = solo `COMPLETED` | `StatisticsService.getPlayerStatistics` filtra `session.getStatus() == COMPLETED` | Coerente con il read-model centrale (popolato solo da `GAME_SESSION_COMPLETED`, emesso solo per sessioni non abortite). Sessioni `ABORTED` non conteggiate. |
| **D6** — `/me` locale con utente non replicato | Restituisce lista vuota ("nessun match locale"), non errore | Offline-first: l'utente autenticato ma non ancora replicato sul building locale → nessun match locale possibile. |
| **D7** — Fix test `GameSessionServiceTest` (retrofit FASE 2) | Aggiunto `@Mock GameDefinitionLocalRepository` + stub lenient `Optional.empty()` (fallback a `GameFactory`) | Il working-tree ereditava le modifiche FASE 2 a `GameSessionService` (parametro ctor + blocco validazione) senza retrofit del test → `@InjectMocks` iniettava `null` → NPE. Test-only, completa il retrofit FASE 2. |

### 12.3 Matrice file — FASE 3

**Nuovi (shared-dto):** `PlayerStatisticsDto.java` (record: `userId, gameType, matchesPlayed, matchesWon, lastPlayedAt`) — condiviso central/local per shape parity.
**Nuovi (central domain):** `domain/model/{PlayerMatchFact,PlayerStatistics}.java`; `domain/ports/out/{PlayerMatchFact,PlayerStatistics}Repository.java`; `domain/ports/in/GetPlayerStatisticsUseCase.java`; `domain/exception/PlayerStatisticsAccessDeniedException.java`.
**Nuovi (central infra):** `entity/{PlayerMatchFact,PlayerStatistics}{JpaEntity,Id}.java` (`@IdClass` composite); `repository/{PlayerMatchFact,PlayerStatistics}JpaRepository.java` (后者 con `@Lock(PESSIMISTIC_WRITE) findByUserIdAndGameTypeForUpdate`); `mapper/{PlayerMatchFact,PlayerStatistics}Mapper.java`; `adapter/{PlayerMatchFact,PlayerStatistics}RepositoryAdapter.java`; `infrastructure/adapters/in/rest/PlayerStatisticsController.java`; `infrastructure/security/CurrentUserService.java`.
**Nuovi (central app):** `application/service/PlayerStatisticsService.java` (read); `application/service/PlayerStatisticsProjectionService.java` (write projection).
**Nuovi (local):** `domain/ports/in/GetPlayerStatisticsUseCase.java`; `infrastructure/security/CurrentUserService.java`; `infrastructure/adapters/in/rest/PlayerStatisticsController.java`.
**Modificati (central):** `application/service/SyncEventProcessor.java` (7° param + helper `projectPlayerStatistics`/`parseParticipants`/`parseWinCondition`); `infrastructure/adapters/in/rest/GlobalExceptionHandler.java` (handler 403).
**Modificati (local):** `application/service/GameSessionService.end` (payload arricchito); `application/service/StatisticsService.java` (`implements GetPlayerStatisticsUseCase` + `getPlayerStatistics`); `domain/ports/out/GameSessionRepository.java` + `GameSessionJpaRepository.java` + `GameSessionRepositoryAdapter.java` (`findByParticipant`); `GameSessionServiceTest.java` (retrofit D7).
**Modificati (schema):** `infrastructure/mysql-central/init.sql` (tabelle `player_match_facts` + `player_statistics`). **Nessun cambio locale** (§2.1: on-demand).
**Test:** `PlayerStatisticsServiceTest` (5 unit), `PlayerStatisticsControllerTest` (7 slice IT).

### 12.4 Contract surface — payload `GAME_SESSION_COMPLETED` arricchito + `PlayerStatisticsDto`

- **Payload outbox `GAME_SESSION_COMPLETED`** (emesso da `GameSessionService.end` Local): campi esistenti + `participants: List<String>` (user id values), `winnerId: String` (null per draw), `winCondition: String` (null se assente). Il payload resta JSON String; campi purely additive.
- **`PlayerStatisticsDto`** (`shared-dto`): `record(String userId, GameType gameType, int matchesPlayed, int matchesWon, Instant lastPlayedAt)` — restituito da entrambi i lati (Central read-model + Local on-demand).
- **Projection**: `PlayerStatisticsProjectionService.onGameSessionCompleted(buildingId, gameType, sessionId, participants, winnerId, winCondition, endedAt)` — per ogni partecipante scrive `PlayerMatchFact` (idempotente via PK composita + `saveIfAbsent`); solo se newly-inserted → incrementa `PlayerStatistics`.

### 12.5 Schema DB — FASE 3

- **Central**: `player_match_facts (session_id VARCHAR(36), user_id VARCHAR(36), building_id VARCHAR(100), game_type VARCHAR(50), tournament_id VARCHAR(36) NULL, won BOOLEAN, win_condition VARCHAR(30) NULL, ended_at TIMESTAMP, PRIMARY KEY (session_id, user_id), INDEX idx_user (user_id, ended_at))` — fatto per singola partita.
- **Central**: `player_statistics (user_id VARCHAR(36), game_type VARCHAR(50), matches_played INT DEFAULT 0, matches_won INT DEFAULT 0, last_played_at TIMESTAMP NULL, PRIMARY KEY (user_id, game_type))` — proiezione aggregata.
- **Local**: nessuna nuova tabella (§2.1: computazione on-demand da `game_sessions`+`session_participants` esistenti).

### 12.6 Endpoint `@PreAuthorize` — FASE 3

| Endpoint | Modulo | Ruolo richiesto |
|---|---|---|
| `GET /api/players/me/statistics` | central | `PLAYER` (`?gameType=` opzionale) |
| `GET /api/players/{userId}/statistics` | central | `PLATFORM_ADMIN` o self-check (`userId == current`) → 403 `PlayerStatisticsAccessDeniedException` se non autorizzato |
| `GET /api/players/me/statistics` | local | `PLAYER` (`?gameType=` opzionale) |

### 12.7 Concorrenza e atomicità

- `PlayerStatisticsJpaRepository.findByUserIdAndGameTypeForUpdate` con `@Lock(LockModeType.PESSIMISTIC_WRITE)` — race protection sull'incremento.
- `PlayerMatchFactRepository.saveIfAbsent` — idempotente per PK composita `(session_id, user_id)`; ritorna `true` solo se newly-inserted → condiziona l'incremento del contatore (no double-count su reprocessing).
- `PlayerStatisticsProjectionService` è **non** `@Transactional`: opera dentro la tx `REQUIRES_NEW` di `SyncEventProcessor.processOne`; i lock e i flush richiedono una tx attiva (programming error fuori tx).
- Same-tx EM-retry: `em.clear()` su `PersistenceException` di PK duplicate + re-find-locked + merge → evita il marcatore rollback-only senza avvelenare la tx chiamante.

### 12.8 Follow-up noti (fuori scope FASE 3)

- **`tournament_id` su `player_match_facts`**: colonna nullable, popolata in FASE 6 (integrazione torneo ↔ local). Attualmente sempre `null`.
- **Same-tx EM-retry vs `RetryHelper`**: la semplificazione adottata (D2) è documentata; se futuri eventi richiedono retry cross-tx, si valuterà l'estrazione di un helper condiviso con `aggregated_statistics`.
- **`PlayerStatisticsService` ricalcolo**: il PIANO §2.4 menziona "lettura + ricalcolo"; l'implementazione attuale è read-only (legge `player_statistics`). Un eventuale ricalcolo da `player_match_facts` è addizionale e non richiesto dai test.

---

## 13. FASE 4 — Dominio Torneo (CRUD + registrazione)

> **Stato:** Implementato (FASE 4 di `documenti/PIANO_UTENTI_TORNEI.md`).
> **Requisiti:** RF-TO-01..04 (vedi `documenti/REQUIREMENTS.md` §1.1.quinquies).
> **Convenzione:** dominio torneo greenfield central-only. Specchia i pattern FASE 0/1/2/3 per POJO/porte/JPA `@IdClass`/mapper/adapter/service/controller/`GlobalExceptionHandler`. **Nessuna modifica a `local-server`** (FASE 4 è central-only; componenti Local sono FASE 6 — `tournament_matches_local` table, `TournamentMatchLocalSyncService`, `InternalTournamentController`, `PlayerTournamentController`, `GameSessionService` extension, `TeamResult`).

### 13.1 Modello di dominio — Torneo (POJO centrali)

- **`Tournament`** — identity = `TournamentId`. Campi: `name, gameType, teamBased, teamSize, format, status, startsAt, endsAt (nullable), createdBy, createdAt`. **Transizioni immutabili** (mirror `PlayerStatistics.mergeIncrement`): `openRegistration()`, `cancel()`, `startProgress()`, `complete(endedAt)` ritornano NUOVA istanza via `new Tournament(...)`; le guardie `openRegistration`/`cancel` sono usate in FASE 4, `startProgress`/`complete` sono forward-declared per FASE 5/6.
- **`Team`** — identity = `TeamId`. Campi: `tournamentId, name, members: List<UserId> (defensive List.copyOf), createdAt`. NO mutation methods. Member-count validation è responsabilità del service (non del costruttore, che deve ricostruire Team carichi da DB con qualunque count).
- **`TournamentParticipant`** — identity = `(tournamentId, participantId)`. Campi: `isTeam, displayName, registeredAt`. Per individual: `participantId = UserId.value()`, `displayName = user.username` (risolto via `UserRepository.findById`). Per team: `participantId = TeamId.value()` UUID fresco, `displayName = teamName`.
- **`TournamentMatch`** — identity = `TournamentMatchId`. 14 campi (8 nullable). Scaffolding per FASE 5/6 (nessun service FASE 4 lo scrive).
- **`TournamentStanding`** — identity = `(tournamentId, participantId)`. Campi `wins/losses/points >= 0`, `rank: Integer` boxed nullable. Scaffolding per FASE 5/6.

### 13.2 Decisioni architetturali prese (protocollo §5)

Quindici decisioni sono state approvate nel STEP 1 prima della implementazione. Identificatori D1–D15 (anziché A/B/C/D/E per allineare con la notazione §12 D1-D7):

| Decisione | Scelta | Motivazione |
|---|---|---|
| **D1** — Accessori ID record | `.value()` per `TournamentId`, `TeamId`, `TournamentMatchId` | Maggioranza del codebase (3/5 record esistenti: `UserId`/`GameSessionId`/`ReservationId`); analoghi più vicini. |
| **D2** — `tournament_team_members` modelling | **Option B**: standalone `TournamentTeamMemberJpaEntity @IdClass(TournamentTeamMemberId.class)` (mirror `local-server SessionParticipantJpaEntity`), NO `@OneToMany` su `TournamentTeamJpaEntity`. Singola porta `TournamentTeamRepository`; adapter inietta 2 JpaRepos, scrive atomic delete-all-then-insert in `@Transactional`. | Preserva RNF-08 ( unico `@OneToMany` consentito è `local-server GameSessionJpaEntity.participants`). Specchia il precedente FASE 0. |
| **D3** — `TournamentMatchOutboxPort` | DEFER a FASE 5 | FASE 4 crea solo le 6 porte di persistenza; il port outbox è usato da `ScheduleTournamentMatchesUseCase` (FASE 5). |
| **D4** — Captain | Principal via `CurrentUserService.getCurrentUserId()` (NESSUN body field). `teamMembers` contiene TUTTI i `teamSize` userId COMPRESO il capitano; service valida `teamMembers.contains(captainId)`. | Coerente con §3.6 line 491 (notazione informale) e §5.2 RF-TO-04. |
| **D5** — Typo §3.6 line 472 | Interpretato come TYPO: "valida teamBased vs `game_definitions.team_allowed`, e teamSize vs il teamSize del torneo stesso". NO read di `game_definitions.team_size` (non esiste). | `game_definitions` ha solo `team_allowed BOOLEAN`; `teamSize` è campo di `tournaments`. |
| **D6** — TournamentStatus state machine | Transizioni FASE 4: `DRAFT→OPEN_REGISTRATION`, `DRAFT/OPEN_REGISTRATION→CANCELLED`. `IN_PROGRESS`/`COMPLETED`/`CANCELLED` terminali. Transizioni invalide → `InvalidTournamentStateException` → HTTP 400. | `startProgress`/`complete` forward-declared per FASE 5/6. |
| **D7** — TournamentParticipant identity | Individual: `participant_id = UserId.value()`, `displayName` risolto via `UserRepository.findById` (throw `UserNotFoundException` se non trova, popola con `user.getUsername()`). Team: `participant_id = TeamId.value()`, `displayName = teamName`. Member existence NON validato (rinviato a FASE 6). | Coerente con §3.7 line 524 + §7 risk-mitigation line 724. |
| **D8** — Repo scaffolding | Create in FASE 4 TUTTE 6 porte di persistenza (incluse `TournamentMatchRepository`/`TournamentStandingRepository` NON invocate da service FASE 4) | Scaffolding-first/behavior-later; le tabelle `tournament_matches`/`tournament_standings` sono create nel init.sql FASE 4 block. |
| **D9** — `tournament_buildings` persistenza | `TournamentService.create` scrive atomic `tournaments` row + N `tournament_buildings` righe nello stesso `@Transactional`. | Evita data-loss: `buildingIds` del creator è salvato a creazione anche se consumato solo in FASE 6 dal replication targeting. |
| **D10** — Scope DTO | Creati in FASE 4 TUTTI 10 DTO di §3.5 (anche quelli consumati solo in FASE 5/6). | Coerente con testo checklist "tutti i DTO tornei (§3.5)"; dipendenze da `TournamentId/TournamentStatus` introdotti nello stesso step minimizzano churn cross-fase. |
| **D11** — `TournamentMatchScheduledDto` campi | `(eventId, eventType, matchId, tournamentId, round, bracketPosition, participantA, participantB nullable, gameType, gameId nullable, status, scheduledAt nullable)`. | Specchia colonne `tournament_matches_local` §3.4; pattern `(eventId, eventType, ...)` di `LocalAdminBuildingEventDto`/`GameDefinitionEventDto` per idempotency lato Local. |
| **D12** — `TournamentMatchResultDto` 4 campi | `(matchId, winner nullable, resultData nullable, status)`. **Deviazione da §3.5 line 450** (che elenca 3 campi). | §3.7 line 524 richiede `status=ABANDONED` per abort; `status` disambigua ABANDONED vs COMPLETED-with-null-winner. Deviazione documentata. |
| **D13** — Outbox emission FASE 4 | **NO outbox emission**. I 5 event record vivono come PURE declarations in `shared-domain/events` per uso FASE 5/6. | YAGNI: nessun consumer (Local sync, scheduler, MQTT) esiste in FASE 4. Le emissioni sono attivate in FASE 5 (`TOURNAMENT_MATCH_SCHEDULED` via `TournamentBracketService`) e FASE 6 (`TOURNAMENT_MATCH_COMPLETED`/`TOURNAMENT_COMPLETED`). |
| **D14** — Endpoint set FASE 4 | Solo 5 su `TournamentController` (`POST /`, `POST /{id}/open`, `POST /{id}/cancel`, `GET /`, `GET /{id}`) + 3 su `TournamentRegistrationController` (`POST /`, `DELETE /`, `GET /`). DEFER a FASE 5: `POST /{id}/schedule`, `GET /{id}/standings`, `GET /{id}/matches`. | Servizi bracket/standings sono FASE 5. |
| **D15** — Use-case port scheduling | Create in FASE 4: 8 use cases (Create, OpenRegistration, Cancel, RegisterParticipant, UnregisterParticipant, ListParticipants, Get, List). DEFER a FASE 5: `ScheduleTournamentMatchesUseCase`, `GetTournamentStandingsUseCase`. | Allineato con D14. Il `ListTournamentParticipantsUseCase` è additive (forced by C.14 GET /participants). |

### 13.3 Matrice file — FASE 4 (83 totali)

**Nuovi (81):**
- `shared-domain` (11): `model/TournamentId|TeamId|TournamentMatchId|TournamentStatus|TournamentMatchStatus|TournamentFormat` (6); `events/TournamentCreatedEvent|TournamentRegistrationOpenedEvent|TournamentMatchScheduledEvent|TournamentMatchCompletedEvent|TournamentCompletedEvent` (5).
- `shared-dto` (10): `CreateTournamentRequestDto|TournamentDto|RegisterTournamentParticipantDto|TeamDto|TournamentParticipantDto|TournamentMatchDto|ScheduleTournamentMatchesDto|TournamentStandingDto|TournamentMatchScheduledDto|TournamentMatchResultDto`.
- Central — `domain/model` (5): `Tournament|Team|TournamentParticipant|TournamentMatch|TournamentStanding`. `domain/exception` (5): `InvalidTournamentException|TournamentNotFoundException|InvalidTournamentStateException|TournamentRegistrationClosedException|DuplicateTournamentParticipantException`. `domain/ports/in` (8): use cases (vedi D15). `domain/ports/out` (6): Repository ports (vedi D3/D8).
- Central — `infrastructure/adapters/out/mysql/entity` (7+4=11): `TournamentJpaEntity|TournamentBuildingJpaEntity+TournamentBuildingId|TournamentTeamJpaEntity|TournamentTeamMemberJpaEntity+TournamentTeamMemberId|TournamentParticipantJpaEntity+TournamentParticipantId|TournamentMatchJpaEntity|TournamentStandingJpaEntity+TournamentStandingId`.
- Central — `infrastructure/adapters/out/mysql/repository` (7): `TournamentJpaRepository|TournamentBuildingJpaRepository|TournamentTeamJpaRepository|TournamentTeamMemberJpaRepository|TournamentParticipantJpaRepository|TournamentMatchJpaRepository|TournamentStandingJpaRepository`.
- Central — `infrastructure/adapters/out/mysql/mapper` (6): `TournamentMapper|TournamentBuildingMapper|TeamMapper|TournamentParticipantMapper|TournamentMatchMapper|TournamentStandingMapper` (TeamMapper absorbs members ↔ `List<UserId>` mapping).
- Central — `infrastructure/adapters/out/mysql/adapter` (6): `TournamentRepositoryAdapter|TournamentBuildingRepositoryAdapter|TournamentTeamRepositoryAdapter|TournamentParticipantRepositoryAdapter|TournamentMatchRepositoryAdapter|TournamentStandingRepositoryAdapter`.
- Central — `application/service` (2): `TournamentService|TournamentRegistrationService`.
- Central — `infrastructure/adapters/in/rest` (2): `TournamentController|TournamentRegistrationController`.
- Central — tests (4): `TournamentServiceTest|TournamentRegistrationServiceTest|TournamentControllerTest|TournamentRegistrationControllerTest` (27 test totali: 9+7+7+4).

**Modificati (2, additive only):**
- Central `infrastructure/adapters/in/rest/GlobalExceptionHandler.java` — 5 nuovi `@ExceptionHandler` (400/400/404/409/409), 9 originali intatti.
- `infrastructure/mysql-central/init.sql` — block **FASE 4** righe 167-248: header `-- =============== FASE 4 — Dominio Torneo (CRUD + registrazione) ===============`, 7 `CREATE TABLE IF NOT EXISTS` (`tournaments`, `tournament_buildings`, `tournament_teams`, `tournament_team_members`, `tournament_participants`, `tournament_matches`, `tournament_standings`), `FK game_type REFERENCES game_definitions(game_type)` su `tournaments` (valido: `game_definitions` creata in FASE 2).

**NON modificati:** `local-server` (zero cambiamenti; i componenti Local sono FASE 6: `tournament_matches_local`, `GameSessionService.start/end` extension, `TournamentMatchLocalSyncService`, `InternalTournamentController`, `PlayerTournamentController`, `TeamResult`/`GameFactory`/`MqttPayloadSerializer`).

### 13.4 Contract surface — eventi + DTO

- **5 eventi** (`shared-domain/events`): `record XEvent(String eventId, Instant occurredAt, <payload>) implements DomainEvent`, letterale inline `getEventType()` return. **PURE declarations — no outbox emission in FASE 4** (D13). Emission sites: FASE 5 `TournamentBracketService.schedule` per `TOURNAMENT_MATCH_SCHEDULED`; FASE 6 `GameSessionService.end`/`SessionAbortHelper.abort` per `TOURNAMENT_MATCH_COMPLETED`; FASE 6 `TournamentService.completeIfDone` per `TOURNAMENT_COMPLETED`. `TournamentCreatedEvent`/`TournamentRegistrationOpenedEvent` non hanno emission sites pianificati (forward-declared per eventuale audit/replica futura).
- **DTO `TournamentDto`** (risposta assembled): `String id, String name, GameType gameType, boolean teamBased, int teamSize, TournamentStatus status, Instant startsAt, Instant endsAt nullable, List<String> buildings, int participantsCount`. Assemblato dal service (NOT controller) cross-tabella `tournaments` + `tournament_buildings` + `countByTournament`.
- **DTO `TournamentParticipantDto`** (risposta register/list): `String participantId, boolean isTeam, String displayName`.
- **Outbox DTOs** (`TournamentMatchScheduledDto` per Central→Local in FASE 6; `TournamentMatchResultDto` 4 campi per Local→Central in FASE 6): creati in FASE 4 (D10), NON emessi in FASE 4.

### 13.5 Schema DB — FASE 4 (centrale)

7 tabelle in `infrastructure/mysql-central/init.sql` (righe 167-248):
- `tournaments` (PK `id` VARCHAR(36); `FK game_type REFERENCES game_definitions(game_type)`)
- `tournament_buildings` (PK composita `(tournament_id, building_id)`, `FK tournament_id → tournaments ON DELETE CASCADE`)
- `tournament_teams` (PK `id`; UNIQUE `(tournament_id, name)`)
- `tournament_team_members` (PK composita `(team_id, user_id)`, `FK team_id → tournament_teams ON DELETE CASCADE`)
- `tournament_participants` (PK composita `(tournament_id, participant_id)`, `FK tournament_id → tournaments ON DELETE CASCADE`)
- `tournament_matches` (PK `id`; nullable: `participant_b`, `building_id`, `game_id`, `session_id`, `winner`, `scheduled_at`, `played_at`, `result_data TEXT`)
- `tournament_standings` (PK composita `(tournament_id, participant_id)`; `rank INT NULL`)

Tutte `CREATE TABLE IF NOT EXISTS ... ENGINE=InnoDB` coerenti con convenzione FASE 1/2/3. Nessuna tabella Locale aggiunta (D2/D8 — `tournament_matches_local` è FASE 6).

### 13.6 Endpoint `@PreAuthorize` — FASE 4

| Endpoint | Modulo | Ruolo richiesto |
|---|---|---|
| `POST /api/tournaments` | central | `PLATFORM_ADMIN` |
| `POST /api/tournaments/{id}/open` | central | `PLATFORM_ADMIN` |
| `POST /api/tournaments/{id}/cancel` | central | `PLATFORM_ADMIN` |
| `GET /api/tournaments` | central | `authenticated` (default `SecurityConfig.anyRequest().authenticated()`) |
| `GET /api/tournaments/{id}` | central | `authenticated` (404 via `TournamentNotFoundException`) |
| `GET /api/tournaments?status=...` | central | `authenticated` (filtro opzionale via `TournamentStatus.valueOf`) |
| `POST /api/tournaments/{id}/participants` | central | `PLAYER` |
| `DELETE /api/tournaments/{id}/participants` | central | `PLAYER` (idempotent no-op se non trovato → 204) |
| `GET /api/tournaments/{id}/participants` | central | `authenticated` |

**POST / ritorna 200** (mirror `GameAdminController` upsert convention, non 201).

### 13.7 Backward-compat — FASE 4 incrementi

- Solo 2 file central modificati (`GlobalExceptionHandler` additive + `init.sql` additive). **Zero signature preesistenti rotte.** Zero test FASE 0/1/2/3 toccati. Regression suite: **298 central + 594 local verdi**.
- `currentUserService` (FASE 3 bean) riusato dal controller per il captain resolution (NESSUNA modifica a `CurrentUserService`).
- `GameDefinitionRepository` (FASE 2 port) riusato dal `TournamentService` per la validazione `team_allowed` (NESSUNA modifica a `GameDefinitionRepository`).
- `UserRepository` + `User.getUsername()` (FASE 1 accessori) riusato dal `TournamentRegistrationService` per `displayName` risoluzione.

### 13.8 Concorrenza e atomicità

- `TournamentService.create` atomica: `tournaments` row + N `tournament_buildings` righe nello stesso `@Transactional` class-level.
- `TournamentTeamRepositoryAdapter.save(Team)` atomica: delete-all-then-insert di team_members + team row nello stesso `@Transactional`. Pattern delete-then-insert è safe per window invisibile (team piccoli, teamSize ≤ ~6 per `game_definitions`).
- `TournamentRegistrationService.register` NON usa `@Lock` pessimistico (no race condition tra registration concorrenti nella stessa FASE 4: `existsByTournamentAndParticipantId`/`existsByTournamentAndName` check + insert ha narrow TOCTOU window ma è acceptable per FASE 4 alpha; FASE 5/6 possono introdurre `@Lock(PESSIMISTIC_WRITE)` se serve). Documentato come follow-up.

### 13.9 Follow-up noti (fuori scope FASE 4)

- **`TournamentMatchOutboxPort` + `ScheduleTournamentMatchesUseCase` + `GetTournamentStandingsUseCase`** → FASE 5.
- **`TournamentBracketService` + `TournamentStandingsService` + endpoint `POST /{id}/schedule` + `GET /{id}/standings` + `GET /{id}/matches`** → FASE 5.
- **Componenti Local** (`tournament_matches_local` table, `InternalTournamentController`, `PlayerTournamentController`, `GameSessionService.start/end` extension per `tournamentMatchId`, `SessionAbortHelper` extension, `TeamResult`/`GameFactory`/`MqttPayloadSerializer`) → FASE 6.
- **Outbox emission** per i 5 eventi → FASE 5 (`TOURNAMENT_MATCH_SCHEDULED`) + FASE 6 (`TOURNAMENT_MATCH_COMPLETED`, `TOURNAMENT_COMPLETED`).
- **Member existence validation** alla registration → rinviata a FASE 6 session start (D7).
- **Race-condition guard su register concorrenti** → eventuale `@Lock(PESSIMISTIC_WRITE)` su `TournamentParticipantRepository` se l'analisi di FASE 5/6 lo richiede.
- **Emendare typo PIANO §3.6 line 472** ("valida team_size coerente con `game_definitions.team_allowed` e team_size" → chiarire che `game_definitions.team_size` NON esiste; validazione è solo `tournament.teamBased` vs `game_definitions.team_allowed` + `tournament.teamSize` uguaglianza con `teamMembers.size()` alla registrazione).

---

*Fine `architettura_classi.md`.*
*Cross-riferimenti: `documenti/PIANO_UTENTI_TORNEI.md` FASE 0 + FASE 1 + FASE 2 + FASE 3 + FASE 4; `documenti/REQUIREMENTS.md` RF-AU-05, RF-UT-LA-01..04, RF-UT-GA-01..03, RF-UT-PL-01..02, RF-TO-01..04; `workflow/analisi/problemi_noti.md`.*