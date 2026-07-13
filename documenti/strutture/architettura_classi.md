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
| `application/service/UserService.java:70` | `List.of("USER")` → `List.of(Role.PLAYER.name())`; import `Role`. |
| `infrastructure/security/JwtAuthenticationFilter.java` | mapping autorità sostituito con `Role.toAuthorityNames(roles)`; import `Role`. |
| `infrastructure/adapters/in/rest/StatisticsController.java:24` | `hasRole('ADMIN')` → `hasRole('PLATFORM_ADMIN')`. |
| `infrastructure/security/JwtTokenProvider.java` | **invariato** (pass-through). |
| `domain/model/User.java` | **invariato** (`List<String>` roles). |

### `local-server`
| File | Modifica |
|---|---|
| `infrastructure/security/JwtTokenValidator.java` | `getAuthorities` delega a `Role.toAuthorityNames(roles)`; import `Role`. |
| `application/service/LocalSignupService.java:82` | `List.of("USER")` → `List.of(Role.PLAYER.name())`; import `Role`. |
| `infrastructure/adapters/in/rest/GameController.java:21` | `hasRole('USER')` → `hasRole('PLAYER')` (poi esteso in FASI successive a `PLAYER or GAME_ADMIN or PLATFORM_ADMIN or LOCAL_ADMIN`). |
| `infrastructure/adapters/in/rest/GameSessionController.java:34` | `hasRole('USER')` → `hasRole('PLAYER')` (oggi `PLAYER or PLATFORM_ADMIN` a livello classe). |
| `infrastructure/adapters/in/rest/ReservationController.java:23` | `hasRole('USER')` → `hasRole('PLAYER')` (oggi `PLAYER or PLATFORM_ADMIN` a livello classe, con self-check `userId`). |
| `infrastructure/adapters/in/rest/StatisticsController.java:41` | `hasRole('USER')` → `hasRole('PLAYER')` (oggi `LOCAL_ADMIN or PLATFORM_ADMIN` su `/api/statistics`; `PLAYER or PLATFORM_ADMIN` su `/api/sessions/active` a riga 73). |
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
| `POST /api/admin/games/definitions` | central | `GAME_ADMIN or PLATFORM_ADMIN` |
| `PUT /api/admin/games/definitions/{gameType}` | central | `GAME_ADMIN or PLATFORM_ADMIN` |
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
| `GET /api/players/me/statistics` | central | `PLAYER or PLATFORM_ADMIN` (`?gameType=` opzionale) |
| `GET /api/players/{userId}/statistics` | central | `PLATFORM_ADMIN` o self-check (`userId == current`) → 403 `PlayerStatisticsAccessDeniedException` se non autorizzato |
| `GET /api/players/me/statistics` | local | `PLAYER or PLATFORM_ADMIN` (`?gameType=` opzionale) |

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
| `POST /api/tournaments/{id}/participants` | central | `PLAYER or PLATFORM_ADMIN` |
| `DELETE /api/tournaments/{id}/participants` | central | `PLAYER or PLATFORM_ADMIN` (idempotent no-op se non trovato → 204) |
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

## 14. FASE 5 — Bracket e classifiche

> **Stato:** Implementato (FASE 5 di `documenti/PIANO_UTENTI_TORNEI.md`).
> **Requisiti:** RF-TO-05..06 (vedi `documenti/REQUIREMENTS.md` §1.1.sextus).
> **Verifica:** `mvn clean compile -pl :central-system -am` → EXIT 0; `mvn test -pl :central-system -am` → 328 test verdi (298 baseline FASE 0-4 + 30 nuovi FASE 5), 0 failures.
> **Convenzione:** specchia i pattern FASE 1/2 (outbox + adapter), FASE 3 (read-model + projection), FASE 4 (POJO + ports + JPA `@IdClass` + mapper + adapter). **Nessuna modifica a `local-server`** (FASE 5 è central-only; componenti Local sono FASE 6).

### 14.1 Modello di dominio — riutilizzo FASE 4

FASE 5 non crea nuovi tipi di dominio. Riutilizza integralmente i POJO FASE 4:
- `Tournament.startProgress()` (`Tournament.java:124-130` — forward-declared per FASE 5) è ora invocato dal suo primo caller effettivo (`TournamentBracketService.schedule`): transizione `OPEN_REGISTRATION → IN_PROGRESS` con ritorno di NUOVA istanza immutabile.
- `TournamentMatch` (14 campi, immutabile) è ora popolato da `TournamentBracketService.schedule` per round-1 (righe `BYE` + `SCHEDULED`).
- `TournamentStanding` (6 campi, immutabile) è ora inizializzato (zero-init `wins=0, losses=0, points=0, rank=null`) da `TournamentStandingsService.seedStandings`.
- `TournamentMatchScheduledEvent` (shared-domain `events/`, letterale `"TOURNAMENT_MATCH_SCHEDULED"`) è l'evento logico di dominio; il payload over-the-wire è il `TournamentMatchScheduledDto` di `shared-dto` (già creato in FASE 4 per decisione D10) — separazione clean tra evento di dominio e DTO di replica, conforme a FASE 1/2 (`LocalAdminBuildingEventDto`/`GameDefinitionEventDto`).
- `TournamentMatchRepository` / `TournamentStandingRepository` (scaffolding FASE 4 D8) sono ora invocati effettivamente da `TournamentBracketService` / `TournamentStandingsService`.

### 14.2 Decisioni architetturali prese (protocollo §5)

Dodici decisioni sono state approvate nel STEP 1 prima della implementazione. Identificatori D1–D12 (allineati con la notazione §12/§13 Dxx):

| Decisione | Scelta | Motivazione |
|---|---|---|
| **D1** — Scope `GET /{id}/matches` | **Incluso in FASE 5** (oltre a `/schedule` + `/standings`) | Tre sorgenti architetturali lo elencano come FASE 5 (`architettura_classi.md` §13.2 D14, §13.9, `TournamentController.java:40-42` javadoc "Deferred to FASE 5"); il checkbox PIANO FASE 5 è un summary minimo. Delega in modo banale a `TournamentMatchRepository.findByTournament` (già scaffolding FASE 4). |
| **D2** — `TournamentMatchOutboxPort` tipizzato | Port con signature `publishScheduled(TournamentMatch, Tournament)` — adapter possiede costruzione DTO + UUID condiviso | Chiude D3 di FASE 4 (`architettura_classi.md` §13.2 D3: "TournamentMatchOutboxPort DEFER a FASE 5"). Port dipende solo da tipi di dominio (NO `shared-dto`) — il dominio resta pulito. L'adapter genera il UUID condiviso outbox-id ↔ `dto.eventId()` ed evita il round-trip del record immutabile. |
| **D3** — `SINGLE_ELIMINATION`-only | Guard `tournament.getFormat() != SINGLE_ELIMINATION` → `InvalidTournamentStateException` (riuso, NO nuova exception) | RF-TO-13 `ROUND_ROBIN` è Could-Have; `TournamentService.create` hard-coda `SINGLE_ELIMINATION` (FASE 4). Signature del port resta format-agnostic per pluggabilità futura (FASE 8+). |
| **D4** — Transizione di stato post-schedule | `OPEN_REGISTRATION → IN_PROGRESS` via `Tournament.startProgress()` | Realizza la state-machine forward-declared in FASE 4 (`Tournament.java:124` Javadoc: "bracket generation ... will be its first caller"). La transizione è atomica con il resto del `schedule` (stesso `@Transactional`). |
| **D5** — Split `TournamentStandingsService` | FASE 5: `getStandings` (read+sort) + package-visible `seedStandings` (zero-init idempotente). FASE 6: `recomputeAfterCompletion(matchId)` + final rank. | RF-TO-06 pieno ("classifica aggiornata dopo ogni match") è FASE 6 (i match completano in FASE 6); in FASE 5 la classifica è la seed zero-init. Il contratto "esponi classifica" è già soddisfatto in FASE 5. |
| **D6** — Persistenza standings seed | `TournamentStandingsService.seedStandings(tournamentId, allParticipantIds)` invocato da `TournamentBracketService.schedule` nella stessa `@Transactional` | Localizza invarianti standings nel service; riga `TournamentStanding(0,0,0,null)` per ogni partecipante (inclusi BYE auto-advancers). |
| **D7** — Convenzione byes | **Top-seeds-get-byes**: partecipanti sortati per `registeredAt` ASC; seed 1..byes ricevono BYE; restanti accoppiati lowest-remaining (seed più basso) vs highest-remaining (seed più alto) | Convenzione standard single-elim. **CRITICO**: `TournamentParticipantRepository.findByTournament` NON ha `ORDER BY` esplicito (`TournamentParticipantJpaRepository:19`); il service sorta internamente per determinismo riproducibile. |
| **D8** — Semantica BYE row | `participantB=null, status=BYE, winner=participantA`; BYE rows NON emettono outbox | BYE è auto-avanzamento, non partita da giocare in un building; FASE 6 replica solo `SCHEDULED` matches ai Local coinvolti. |
| **D9** — Idempotency-by-rejection | `Tournament.startProgress()` lancia `InvalidTournamentStateException` se status ≠ `OPEN_REGISTRATION` → 400 (via handler esistente `GlobalExceptionHandler:114`) | No idempotency-key check esplicito; la state-machine fa da guard. Una seconda chiamata `/schedule` su torneo `IN_PROGRESS` fallisce con 400. |
| **D10** — Body richiesta `/schedule` | Body vuoto; path `id` è authoritative; ritorna `200` + `List<TournamentMatchDto>` (BYE + SCHEDULED) | Path id è la source of truth; `ScheduleTournamentMatchesDto` record (FASE 4 D10) resta in `shared-dto` invariato (churn-free). Returns 200 mirroring upsert convention (§13.6 line 484). |
| **D11** — FASE 5 NON tocca `shared-domain/game` | `participantA/B` sono opaque UUIDs (UserId OR TeamId); `TeamResult`/`GameFactory`/`MqttPayloadSerializer` sono FASE 6 | Semantica team-winner rileva solo al session play (FASE 6). FASE 5 aggiunge **0 file** a `shared-domain` e **0 file** a `shared-dto` (tutti i tipi torneo sono stati creati in FASE 4). |
| **D12** — `TournamentMatchOutboxPort` interface-only in FASE 5 | Port + adapter che scrive la riga `outbox_events` `PENDING`; REST push a Local + scheduler drain branch sono FASE 6 | Mirrors esattamente FASE 1/2 pattern: `LocalAdminBuildingService.writeOutboxEvent` scrive riga NOW (`:130-145`); `UserReplicationSchedulerService` drena LATER (`:121` `findPendingLimit`). La riga outbox è la FASE 5 deliverable; il drain è FASE 6. |

### 14.3 Matrice file — FASE 5 (12 totali)

**Nuovi (11):**

| Modulo | Nuovi file |
|---|---|
| `central-system` (domain `ports/in`) | `ScheduleTournamentMatchesUseCase.java`, `GetTournamentStandingsUseCase.java`, `ListTournamentMatchesUseCase.java` (3 interfacce pure Java, zero annotazioni framework) |
| `central-system` (domain `ports/out`) | `TournamentMatchOutboxPort.java` (1 interfaccia pura; signature domain-only `(TournamentMatch, Tournament)`, NO dipendenza da `shared-dto`) |
| `central-system` (infra `adapters/out/mysql/adapter`) | `TournamentMatchOutboxAdapter.java` (1 `@Component`; solleva il body di `LocalAdminBuildingService.writeOutboxEvent:130-145` in un adapter; UUID condiviso outbox-id ↔ `TournamentMatchScheduledDto.eventId`; JSON via `ObjectMapper`; `OutboxEventRepository.save`) |
| `central-system` (application `service`) | `TournamentBracketService.java`, `TournamentStandingsService.java` (2 `@Service @Transactional`) |
| `central-system` (test) | `TournamentBracketServiceTest.java` (15 test puri JUnit5+Mockito), `TournamentStandingsServiceTest.java` (8 test), `TournamentMatchOutboxAdapterTest.java` (2 test) — 25 nuovi test totali |

**Modificati (1, additive only):**

| Modulo | File modificati |
|---|---|
| `central-system` | `infrastructure/adapters/in/rest/TournamentController.java` — 3 nuovi parametri ctor (`ScheduleTournamentMatchesUseCase`, `GetTournamentStandingsUseCase`, `ListTournamentMatchesUseCase`) appesi dopo `clock`; 3 nuovi campi `final`; 3 nuovi endpoint (`POST /{id}/schedule` `@PreAuthorize("hasRole('PLATFORM_ADMIN')")`, `GET /{id}/standings` authenticated, `GET /{id}/matches` authenticated); Javadoc `:40-42` aggiornato da "Deferred to FASE 5" a "Implemented in FASE 5". I 5 endpoint FASE 4 restano byte-identici. |

**Test modificati (1):**

| Modulo | File modificati |
|---|---|
| `central-system` (test) | `TournamentControllerTest.java` — 3 nuovi `@Mock` fields, constructor call aggiornato con 3 nuovi args, 5 nuovi `@Test` (200 matches list / 200 standings list / 200 matches list / 400 invalid state / 404 not found). I 7 test FASE 4 esistenti restano byte-identici (totale 12 test). |

**NON modificati:**
- `shared-domain`, `shared-dto`, `shared-mqtt`, `local-server`, `game-client-emulator`, `e2e-tests` (zero cambiamenti; i componenti Local sono FASE 6).
- `init.sql` (zero nuove tabelle — `tournament_matches`, `tournament_standings`, `outbox_events` già create in FASE 4 / FASE 0). Hibernate `ddl-auto: validate` resta allineato.
- `GlobalExceptionHandler` (handler per `InvalidTournamentStateException` 400 e `TournamentNotFoundException` 404 già presenti in FASE 4).
- `OutboxEvent` / `OutboxEventRepository` / `OutboxEventMapper` / `OutboxEventJpaEntity` (riutilizzo totale del modello outbox FASE 0).
- `TournamentMatchMapper` / `TournamentStandingMapper` / `TournamentMatchRepositoryAdapter` / `TournamentStandingRepositoryAdapter` (riutilizzo totale dello scaffolding FASE 4).

### 14.4 Contract surface

- **Outbox event literal**: `"TOURNAMENT_MATCH_SCHEDULED"` (byte-identical al `TournamentMatchScheduledEvent.getEventType()` di `shared-domain`).
- **Outbox payload DTO**: `TournamentMatchScheduledDto(eventId, eventType, matchId, tournamentId, round, bracketPosition, participantA, participantB nullable, gameType, gameId nullable, status, scheduledAt nullable)`. `eventId` == `OutboxEvent.id` (UUID condiviso generato dall'adapter); `gameId=null` e `scheduledAt=null` in FASE 5 (assegnati in FASE 6 dal push ai Local); `status="SCHEDULED"` (i BYE non raggiungono mai l'adapter).
- **In-port `ScheduleTournamentMatchesUseCase.schedule(TournamentId) -> List<TournamentMatchDto>`**: ritorna righe BYE + SCHEDULED ordinate per `bracketPosition` ASC; transizione atomica `OPEN_REGISTRATION → IN_PROGRESS`; outbox emission per ogni SCHEDULED.
- **In-port `GetTournamentStandingsUseCase.getStandings(TournamentId) -> List<TournamentStandingDto>`**: read-only, sort `points desc, wins desc, participantId asc`; `displayName` risolto via `TournamentParticipantRepository.findByTournament`; `rank=null` per la FASE 5 seed.
- **In-port `ListTournamentMatchesUseCase.findByTournament(TournamentId) -> List<TournamentMatchDto>`**: read-only delegation a `TournamentMatchRepository.findByTournament`; ritorna BYE + SCHEDULED senza filtri.
- **Out-port `TournamentMatchOutboxPort.publishScheduled(TournamentMatch, Tournament)`**: domain-only signature (NO `shared-dto` dep); adapter adapter-owned UUID + DTO construction.

### 14.5 Schema DB — FASE 5

Nessuna modifica. Riutilizzo delle 7 tabelle torneo create in FASE 4 (`tournaments`, `tournament_buildings`, `tournament_teams`, `tournament_team_members`, `tournament_participants`, `tournament_matches`, `tournament_standings`) + `outbox_events` (FASE 0). Hibernate `ddl-auto: validate` resta allineato.

### 14.6 Endpoint `@PreAuthorize` — FASE 5

| Endpoint | Modulo | Ruolo richiesto |
|---|---|---|
| `POST /api/tournaments/{id}/schedule` | central | `PLATFORM_ADMIN` |
| `GET /api/tournaments/{id}/standings` | central | `authenticated` (default `SecurityConfig.anyRequest().authenticated()`) |
| `GET /api/tournaments/{id}/matches` | central | `authenticated` |

### 14.7 Backward-compat — FASE 5 incrementi

- Solo 1 file central modificato in production (`TournamentController` additive: 3 campi/params ctor + 3 endpoint; nessuna signature rotta). I 5 endpoint FASE 4 restano byte-identici.
- 1 file test modificato (`TournamentControllerTest` additive: 3 nuovi `@Mock` + 3 args ctor + 5 nuovi `@Test`; i 7 test FASE 4 esistenti restano byte-identici).
- Zero test FASE 0/1/2/3/4 toccati. Regression suite: `mvn test -pl :central-system -am` → **328 test verdi, 0 failures** (298 baseline FASE 0-4 + 30 nuovi FASE 5: 15 bracket + 8 standings + 2 outbox-adapter + 5 controller).
- `Tournament.startProgress()` (forward-declared in FASE 4) è ora invocato dal primo caller (`TournamentBracketService.schedule`).
- `TournamentMatchRepository` / `TournamentStandingRepository` (scaffolding FASE 4 D8) sono ora invocati effettivamente da `TournamentBracketService` / `TournamentStandingsService`.
- `TournamentMatchScheduledDto` (creato in FASE 4 per D10) è ora popolato dal `TournamentMatchOutboxAdapter`.

### 14.8 Concorrenza e atomicità

- `TournamentBracketService` è `@Service @Transactional` class-level: la transizione torneo (`startProgress()` + `tournamentRepository.save`), ogni `tournamentMatchRepository.save(match)`, ogni `tournamentMatchOutboxPort.publishScheduled` (→ adapter → `outboxEventRepository.save`), e `tournamentStandingsService.seedStandings` (→ `tournamentStandingRepository.save` per ogni partecipante) avvengono nella **stessa transazione** (Outbox Pattern, mirrors `LocalAdminBuildingService.writeOutboxEvent:130-145`). Se una qualsiasi scrittura fallisce, l'intera operazione è roll-backed → nessuno stato inconsistente (match salvati senza outbox, o standings seeded senza matches, ecc.).
- `TournamentStandingsService.getStandings` è `@Transactional(readOnly = true)` method-level (ottimizzazione per il path read; mirrors `TournamentService.getById/findAll/findByStatus`).
- `TournamentMatchOutboxAdapter` NON ha `@Transactional` class-level: partecipa nella tx del caller (`TournamentBracketService.schedule`). Mirrors l'inline outbox write di `LocalAdminBuildingService.assignBuildings` (che è `@Transactional` class-level sul service, NON sul singolo helper).
- **Thread-safety**: la state-machine immutabile di `Tournament` (transizioni restituiscono NUOVA istanza via `new Tournament(...)` — `Tournament.java:124-130`) rende `startProgress()` intrinsecamente thread-safe; `TournamentBracketService` non mantiene stato mutabile fra chiamate. La race-condition su `/schedule` concorrenti (PIANO §7 line 720) è mitigata da `Tournament.startProgress()` che throws se il torneo è già `IN_PROGRESS` (idempotency-by-rejection D9). La race su `advanceWinner` in FASE 6 (PIANO §7) richiederà `@Lock(PESSIMISTIC_WRITE)` su `TournamentRepository` — follow-up FASE 6 §14.9.

### 14.9 Follow-up noti (fuori scope FASE 5)

- **`MetadataReplicationSchedulerService` / `LateRegistrationCatchUpService` extension per `TOURNAMENT_MATCH_SCHEDULED`** → FASE 6 (drena le righe outbox `PENDING` scritte in FASE 5 e le spedisce ai Local coinvolti; targeting derivato da `tournament_buildings × tournament_matches`; parallelo a `replicateMetadataEvent`/`replicateGameDefinitionEvent` FASE 1/2).
- **`LocalTournamentPushAdapter` (REST push adapter) + `PushTournamentMatchToLocalServersPort`** → FASE 6 (parallelo a `LocalMetadataRestAdapter` / `LocalGameDefinitionRestAdapter`; `PUT /internal/tournaments/matches/sync` sul Local).
- **`SyncEventProcessor.handleTournamentMatchCompleted`** → FASE 6 (consuma `TOURNAMENT_MATCH_COMPLETED` da Local, aggiorna match, ricalcola standings, genera match round successivo via `TournamentBracketService.advanceWinner`).
- **`TournamentBracketService.advanceWinner(matchId, winnerId)`** → FASE 6 (generazione round 2+ via vincitori noti; non forward-declared in FASE 5 — YAGNI/D13-aligned).
- **`TournamentStandingsService.recomputeAfterCompletion(matchId)` + final rank assignment** → FASE 6.
- **`Tournament.completeIfDone()` + RF-TO-11** (quando tutti i match si concludono, `status=COMPLETED` + rank finale) → FASE 6.
- **Componenti Local** (`tournament_matches_local` table, `GameSessionJpaEntity` extension con `tournament_match_id`/`tournament_id`, `TournamentMatchLocalSyncService`, `InternalTournamentController`, `PlayerTournamentController` (`GET /me/matches`, `POST /matches/{id}/start`), `GameSessionService.start/end` extension per `tournamentMatchId`, `SessionAbortHelper` extension, `TeamResult`/`GameFactory`/`MqttPayloadSerializer`) → FASE 6.
- **`ROUND_ROBIN` format** (RF-TO-13 Could-Have) → FASE 8+ (strategia pluggabile via port signature format-agnostic `schedule(TournamentId)`).
- **`@Lock(PESSIMISTIC_WRITE)` su `TournamentRepository` per race bracket in FASE 6** → FASE 6 (i match completano in FASE 6; race su advanceWinner concorrenti).

---

## 15. FASE 6 — Integrazione Torneo ↔ Local Server

> **Stato:** Implementato (FASE 6 di `documenti/PIANO_UTENTI_TORNEI.md`).
> **Requisiti:** RF-TO-07..12 (vedi `documenti/REQUIREMENTS.md` §1.1.septimus).
> **Verifica:** `mvn clean compile -pl :shared-domain,:shared-dto,:shared-mqtt` → EXIT 0; `mvn clean compile -pl :central-system -am` → EXIT 0; `mvn clean compile -pl :local-server -am` → EXIT 0; `mvn test` shared 3 + central 328+20 (surefire+failsafe) + local 617 = **968 test verdi, 0 failures**.
> **Moduli toccati:** `shared-domain` (1 new + 1 mod), `shared-mqtt` (1 mod), `shared-dto` (2 mod), `central-system` (2 new + ~16 mod), `local-server` (13 new + ~8 mod + 3 SQL).

### 15.1 Decisioni architetturali prese (protocollo §5)

Quindici decisioni approvate nello STEP 1:

| Decisione | Scelta | Motivazione |
|---|---|---|
| **D1** — Building assignment | Round-robin al drain time: `buildingId = buildingIds[matchIndex % buildingIds.size()]` | Deterministico, semplice, nessun intervento admin. Il drain branch dello scheduler assegna `buildingId` + `gameId` (UUID) al match centrale prima del push. |
| **D2** — DTO `buildingId` | Aggiunto come 13° campo a `TournamentMatchScheduledDto` | Il Local valida "appartenga a questo building" confrontando `dto.buildingId() == app.building-id`. Additive al FASE 4 record. |
| **D3** — `advanceWinner` approach | Costruisce nuove istanze immutabili `TournamentMatch` direttamente (no transition methods) | Coerente con FASE 5 precedent. **CREA il parent se assente** quando `parentRound <= totalRounds`. |
| **D4** — Race protection (PIANO §7 line 717) | 3 query `@Lock(PESSIMISTIC_WRITE)` `*ForUpdate` su `TournamentRepository`/`TournamentMatchRepository`/`TournamentStandingRepository` | Race su `advanceWinner` concorrenti + `completeIfDone` race. Pattern `PlayerStatisticsJpaRepository.findByUserIdAndGameTypeForUpdate` FASE 3. |
| **D5** — `TournamentMatchOutboxPort` reuse | `advanceWinner` chiama `tournamentMatchOutboxPort.publishScheduled(parent, tournament)` per il match round-2+ | PIANO §3.6 line 502: "scrive nuovo evento outbox". Reuse del port FASE 5. Il drain scheduler picka il nuovo row PENDING al prossimo tick. |
| **D6** — Parent CREATO se assente | `advanceWinner` computa `totalRounds = log2(nextPow2(N))`; se `parentRound > totalRounds` → null (finale); else CREA parent con `participantA=winner, participantB=null, status=SCHEDULED` | FASE 5 `schedule` persiste solo round-1. Senza parent creation, completare round-1 signalerebbe "tournament complete" senza il round-2 finale. Fix critico: il parent viene creato al primo completamento, patchato al secondo. |
| **D7** — Slot assignment | First completion → `participantA`; second → `participantB` (no parity convention) | Evita il vincolo `participantA` non-blank del `TournamentMatch` ctor. Per single-elim, lo slot non affecta la correttezza — il match è tra i due vincitori. |
| **D8** — Q2 Walkover | `SessionAbortHelper` calcola walkover winner = partecipante NON in `session.getParticipants()`; DTO `winner=walkoverWinner` (non null) | Mantiene il torneo scorrevole anche su ABANDONED. Il central `advanceWinner` riceve sempre winnerId non-null. `status=ABANDONED` è per bookkeeping centrale. |
| **D9** — Q4 Controller 5-arg | `GameSessionController` dipende dal concreto `GameSessionService` (non solo l'in-port) per chiamare il 5-arg overload | Matches existing pattern (il controller già inietta `ObjectMapper`). |
| **D10** — Q5 Payload shape | Local emitters serializzano `TournamentMatchResultDto` via `objectMapper.writeValueAsString(dto)` — NON `Map` | Matches il central deserialiser `objectMapper.readValue(payload, TournamentMatchResultDto.class)`. Più pulito degli existing `Map`-based payloads. |
| **D11** — Q6 `gameId` drain | Il drain branch assegna `gameId = UUID.randomUUID().toString()` al match centrale + DTO | `PlayerTournamentController.startMatch` risolve `gameId` da `TournamentMatchLocal.getGameId()`. Fallback `@RequestParam gameId` per sicurezza. |
| **D12** — Q3 `TournamentStandingsService` +4° ctor | `TournamentMatchRepository` come 4° parametro | `recomputeAfterCompletion(matchId)` carica il match per identificare winner/loser. Domain port — isolation-compliant. |
| **D13** — Team simplification | `GameSession.participants` = `[team_id_as_UserId]` (single pseudo-participant); `TeamResult.winnerId = new UserId(winnerTeamId.value())` | Local non replica `tournament_teams`/`tournament_team_members`. Semplificazione PIANO §3.7 line 519. `GET /me/matches` limitato a match individuali. |
| **D14** — Deviation H: `GameFactory` not updated | `TeamResult` costruito a service-layer in `GameSessionService.end`, non via `GameFactory.createGame` | `GameFactory` restituisce `GameLifecycle` non `GameResult`. Il PIANO §3.8 phrasing was misleading. `MqttPayloadSerializer` mixin WAS updated. |
| **D15** — IT scope | Central H2 `@SpringBootTest` IT + local slice tests; full cross-module 2-building e2e deferred to FASE 8 | Local `@SpringBootTest` non viable (MQTT eagerly instantiates). Full Docker e2e è FASE 8. |

### 15.2 Matrice file — FASE 6 (46 totali)

**Nuovi (22):** `shared-domain` (1): `TeamResult`. `central-system` (2): `PushTournamentMatchToLocalServersPort`, `LocalTournamentMatchRestAdapter`. `central-system` test (1): `TournamentFlowEndToEndIT`. `local-server` (13 main): `TournamentMatchLocal`, `TournamentMatchLocalRepository`, `TournamentMatchLocalJpaEntity`/`JpaRepository`/`Mapper`/`Adapter` (4), `TournamentMatchLocalSyncService`, `InternalTournamentController`, `PlayerTournamentController`, 4 `TournamentMatch*Exception`. `local-server` test (5): `TournamentMatchLocalSyncServiceTest`, `InternalTournamentControllerTest`, `PlayerTournamentControllerTest`, `GameSessionServiceTournamentTest`, `SessionAbortHelperTournamentTest`.

**Modificati (24):** `shared-domain` (1): `WinCondition` (+`TEAM_VICTORY`). `shared-mqtt` (1): `MqttPayloadSerializer` (+8° subtype). `shared-dto` (2): `CreateSessionRequestDto` (+`tournamentMatchId`), `TournamentMatchScheduledDto` (+`buildingId`). `central-system` (10): `UserReplicationSchedulerService` (+3 ctor, +branch), `LateRegistrationCatchUpService` (+2 ctor, +branch), `SyncEventProcessor` (+4 ctor via nullable pattern, +branch), `TournamentBracketService` (+`advanceWinner`, +`completeIfDone`), `TournamentStandingsService` (+1 ctor, +`recomputeAfterCompletion`, +`assignFinalRanks`), 3 repo trios (`+ForUpdate`), `TournamentMatchOutboxAdapter` (+`buildingId=null`), `EventTypeContractTest` (+literal). `local-server` (7): `GameSession` (+2 fields), `GameSessionJpaEntity` (+2 columns), `GameSessionMapper` (+2 fields mapping), `GameSessionService` (+2 ctor, +5-arg `start`, +`end` extension), `SessionAbortHelper` (+1 ctor, +walkover outbox), `GameSessionController` (+concrete `GameSessionService`), `GlobalExceptionHandler` (+4 handlers). `infrastructure/mysql-local` (3): `init.sql`/`init-building-2.sql`/`init-building-3.sql` (+`tournament_matches_local` table + `game_sessions` columns).

### 15.3 Contract surface

- **Outbox event literals (byte-identical):** `"TOURNAMENT_MATCH_SCHEDULED"` (Central→Local drain, FASE 5+6 emission), `"TOURNAMENT_MATCH_COMPLETED"` (Local→Central drain, FASE 6 emission).
- **Local→Central payload:** `TournamentMatchResultDto(matchId, winner nullable→non-null per walkover, resultData nullable, status)` — serialized as JSON record via `objectMapper.writeValueAsString(dto)` (NOT `Map`).
- **Central→Local payload:** `TournamentMatchScheduledDto(13 fields incl. buildingId)` — enriched at drain time with `buildingId` + `gameId`.
- **REST endpoints:** Central push `PUT /internal/tournaments/matches/sync` (Local, `X-Internal-Api-Key`); Local→Central via existing `POST /internal/sync/receive`; Local player `GET /api/players/tournaments/me/matches` + `POST /api/players/tournaments/matches/{matchId}/start`.

### 15.4 Schema DB — FASE 6

- **Local ×3** (`init.sql`/`init-building-2.sql`/`init-building-3.sql`): `game_sessions` +2 nullable columns `tournament_match_id`/`tournament_id` + index `idx_game_sessions_tournament`; nuova `tournament_matches_local (id, tournament_id, round, bracket_position, participant_a, participant_b nullable, game_type, game_id nullable, status, scheduled_at nullable)` + indexes.
- **Central**: NESSUNA modifica schema (7 tournament tables + `outbox_events` + `processed_events` + `replication_progress` già esistenti FASE 0/4).

### 15.5 Concorrenza e atomicità

- **Local `GameSessionService.end`**: 2 outbox rows (`GAME_SESSION_COMPLETED` + `TOURNAMENT_MATCH_COMPLETED`) + local match flip a `COMPLETED` atomicamente nella stessa `@Transactional` class-level.
- **Local `SessionAbortHelper.abortAndEmit`**: 2 outbox rows (`GAME_SESSION_ABORTED` + `TOURNAMENT_MATCH_COMPLETED` ABANDONED) + local match flip atomicamente nella `@Transactional(REQUIRES_NEW)`.
- **Central `SyncEventProcessor.handleTournamentMatchCompleted`**: match update + standings recompute + bracket advance + (optional) next-round outbox emission + (optional) tournament completion — tutti atomicamente nella `@Transactional(REQUIRES_NEW)` di `processOne`.
- **Central `TournamentBracketService.advanceWinner`**: parent CREA/patcha + (optional) outbox emission atomicamente nella `@Transactional` class-level.
- **Central `TournamentBracketService.completeIfDone`**: `@Lock(PESSIMISTIC_WRITE)` su `Tournament` via `findByIdForUpdate`; `Tournament.complete()` + save + `assignFinalRanks` atomicamente.
- **Race protection**: 3 `@Lock(PESSIMISTIC_WRITE)` `ForUpdate` queries (PIANO §7 line 717).

### 15.6 Follow-up noti (fuori scope FASE 6)

- **Full cross-module 2-building Docker e2e** → FASE 8 (`e2e-tests` module, `MultiBuildingEndToEndIT` extension).
- **`GameFactory` aggiornato** (PIANO §3.8) — deviazione H LOCKED: `TeamResult` costruito a service-layer.
- **Team match `GET /me/matches`** — limitato a match individuali; team membership non replicata a Local.
- **`ROUND_ROBIN` format** (RF-TO-13 Could-Have) → FASE 8+.
- **CI: gate on `mvn verify`** — il `TournamentFlowEndToEndIT` (`*IT` convention) è skipped da `mvn test` surefire; runs via failsafe.

---

## 16. FASE 7-A1 — UpdateTournamentUseCase + DeleteTournamentUseCase (Central)

> **Stato:** Implementato (batch S1 di FASE 7 §7.A.1 di `documenti/PIANO_UTENTI_TORNEI.md`).
> **Data:** 2026-07-12
> **Scope:** Primo batch atomico della FASE 7 — use case di mutazione di un torneo `DRAFT` (`UPDATE` name/startsAt/buildingIds + `DELETE` hard + tombstone) + esposizione REST diretta `PUT/DELETE /api/tournaments/{id}` + DTO `UpdateTournamentRequestDto` + DTO outbox `TournamentSummaryEventDto`.
> **Scope NON incluso (deferito a S2/S3):** porte di replica push (`PushTournamentSummaryToLocalServersPort` + `LocalTournamentSummaryRestAdapter` + `InternalTournamentSummaryController` + `TournamentSummarySyncService` Local), estensione del producer outbox `originatingRequestId` sugli altri service (`UserService`/`GameDefinitionService`/`TournamentStandingsService`/`TournamentRegistrationService`/`LocalServerRegistryPort`), rami `SyncEventProcessor` per `*_REQUESTED`, estensione `UserReplicationSchedulerService`/`LateRegistrationCatchUpService`, DTO `UserSyncDto`/`GameDefinitionEventDto` estensione + 7 DTO `*_REQUESTED`, test `SyncEventProcessorTest`/`UserReplicationSchedulerServiceTest`.
> **Scelte d'implementazione concordate con l'utente:**
> - **S1 minimo**: consegna isolata di §7.A.1 (compila + test verdi + outbox written, ma NESSUN effetto end-to-end — la riga outbox `TOURNAMENT_SUMMARY_UPSERTED` resta `PENDING` nel DB Central perché `UserReplicationSchedulerService.isReplicationEvent` non la filtra. Lo attiva il batch S2: §7.A.3 + §7.A.5 + §7.A.6 + mini-§7.B).
> - **Outbox producer pattern INLINE** (template `GameDefinitionService.writeOutboxEvent:114-137`): `OutboxEventRepository` + `ObjectMapper` iniettati in `TournamentService`; helper `writeOutboxEvent` privato. Conforme alla convenzione già in uso in `UserService`/`GameDefinitionService`/`LocalAdminBuildingService`.
> - **DELETE = hard delete centrale + tombstone `deleted=true`** nella replica `TournamentSummaryEventDto` verso i Local. Coerente con `TournamentRepository.deleteById`/`TournamentBuildingRepository.deleteByTournament` esistenti (no campo `deleted` sul model `Tournament`); safe perché in `DRAFT` non esistono partecipanti, standings, né match (la registrazione richiede `OPEN_REGISTRATION`).
> - **Test `@PreAuthorize` 403 via migrazione `TournamentControllerTest` a `@WebMvcTest`** con `MethodSecurityTestConfig` inner class (`@EnableMethodSecurity` + `permitAll()` filter chain) + `@WithMockUser`.

### 16.1 Matrice dei file (10 totali)

| # | File (relativo `gamehandler-platform/`) | Tipo | Layer | Scope |
|---|---|---|---|---|
| 1 | `central-system/src/main/java/com/gameplatform/central/domain/ports/in/UpdateTournamentUseCase.java` | NUOVO | domain/ports-in | Port in `TournamentDto update(TournamentId, String, Instant, List<String>, String originatingRequestId)` |
| 2 | `central-system/src/main/java/com/gameplatform/central/domain/ports/in/DeleteTournamentUseCase.java` | NUOVO | domain/ports-in | Port in `void delete(TournamentId, String originatingRequestId)` |
| 3 | `central-system/src/main/java/com/gameplatform/central/domain/model/Tournament.java` | ESTESO (+21 righe) | domain/model | Metodo `update(String, Instant)` con guard `status==DRAFT` → `InvalidTournamentStateException`; ritorna `new Tournament(..., status, startsAt, null, createdBy, createdAt)` |
| 4 | `shared/shared-dto/src/main/java/com/gameplatform/shared/dto/UpdateTournamentRequestDto.java` | NUOVO | shared-dto | Record `(name, startsAt, buildingIds)` con `@NotBlank`/`@NotNull`/`@Size(min=2)` (message string identici a `CreateTournamentRequestDto`) |
| 5 | `shared/shared-dto/src/main/java/com/gameplatform/shared/dto/TournamentSummaryEventDto.java` | NUOVO | shared-dto | Record 15 campi (eventId, eventType, tournamentId, name, gameType, teamBased, teamSize, status, startsAt, endsAt, buildingIds, participantsCount, updatedAt, deleted, originatingRequestId) + **compact ctor** retrocompatibile senza `deleted`/`originatingRequestId` (default `false`/`null`, pattern `UserSyncDto.java:14-16`) |
| 6 | `central-system/src/main/java/com/gameplatform/central/application/service/TournamentService.java` | ESTESO | application/service | `implements UpdateTournamentUseCase, DeleteTournamentUseCase`; ctor 7-arg `@Autowired` + legacy 5-arg delegante; metodi `update`/`delete` `@Transactional`; helper `writeOutboxEvent` inline null-safe |
| 7 | `central-system/src/main/java/com/gameplatform/central/infrastructure/adapters/in/rest/TournamentController.java` | ESTESO | infrastructure/adapters/in/rest | ctor 12-arg `@Autowired` + legacy 10-arg delegante; `@PutMapping("/{id}")` + `@DeleteMapping("/{id}")` con `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` + `@Valid` |
| 8 | `central-system/src/test/java/com/gameplatform/central/application/service/TournamentServiceTest.java` | ESTESO (+4 test, ctor 7-arg) | test/application | +4 test: `update` DRAFT 200 + reject OPEN_REGISTRATION, `delete` DRAFT 200 + reject IN_PROGRESS; `ArgumentCaptor<OutboxEvent>` round-trip JSON → assert `eventType=TOURNAMENT_SUMMARY_UPSERTED` + `deleted` tombstone; `objectMapper` reale (non mock) |
| 9 | `central-system/src/test/java/com/gameplatform/central/infrastructure/adapters/in/rest/TournamentControllerTest.java` | MIGRATO a `@WebMvcTest` (+7 test) | test/infrastructure | `standaloneSetup` → `@WebMvcTest(TournamentController.class)` con `excludeFilters` (`SecurityConfig`/`JwtAuthenticationFilter`/`InternalApiKeyFilter`, template `UserControllerTest:36-47`) + `@Import({GlobalExceptionHandler.class, MethodSecurityTestConfig.class})`; inner static `@TestConfiguration @EnableMethodSecurity` `MethodSecurityTestConfig` con `@Bean SecurityFilterChain` `permitAll()` + `csrf(disable)` + `@Bean Clock` fixed; 11 `@MockBean` use cases + `CurrentUserService` + `Clock`; 12 test esistenti migrati con `@WithMockUser` + 7 nuovi: PUT 200/400/404/403 + DELETE 204/404/403 |
| 10 | `central-system/pom.xml` | ESTESO (+5 righe) | build | +`spring-security-test` test scope (per `@WithMockUser`) |

### 16.2 Contract surface

- **Nuove porte in** (`central-system/.../domain/ports/in/`):
  - `UpdateTournamentUseCase.update(TournamentId tournamentId, String name, Instant startsAt, List<String> buildingIds, String originatingRequestId) → TournamentDto`
  - `DeleteTournamentUseCase.delete(TournamentId tournamentId, String originatingRequestId) → void`
  - `originatingRequestId` è **nullable** in firma: `null` sul path REST diretto (`TournamentController.update/delete`); non-null sul path `SyncEventProcessor` `TOURNAMENT_UPDATE_REQUESTED`/`TOURNAMENT_DELETE_REQUESTED` (§7.A.3/S3 — forward-declared signature, no overload futuro).
- **Nuovo metodo domain** (`Tournament.update(String name, Instant startsAt)`): guard `status != DRAFT` → `InvalidTournamentStateException`; ritorna `new Tournament(tournamentId, name, gameType, teamBased, teamSize, format, status, startsAt, null, createdBy, createdAt)` (endsAt=null per DRAFT). Stile immutabile coerente con `openRegistration()`/`cancel()`/`startProgress()`/`complete()`.
- **Nuovi endpoint REST** (`TournamentController`):
  - `PUT /api/tournaments/{id}` `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` `@Valid @RequestBody UpdateTournamentRequestDto` → 200 `TournamentDto` (o 400 `InvalidTournamentStateException`/`MethodArgumentNotValidException`, 404 `TournamentNotFoundException`, 403 method security).
  - `DELETE /api/tournaments/{id}` `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` → 204 No Content (o 400/404/403).
- **Nuovo evento outbox** (producer inline): `TOURNAMENT_SUMMARY_UPSERTED` con payload `TournamentSummaryEventDto`. `eventType` è un nuovo literal mai usato prima nel codebase (`grep` proof = 0 match pre-batch). `OutboxEvent` (`domain/model`) è generico su `eventType: String` → nessuna modifica al model.
- **Nessuna nuova porta out** creata: usa `OutboxEventRepository` preesistente (`domain/ports/out`, FASE 1). `TournamentRepository.deleteById` e `TournamentBuildingRepository.deleteByTournament` esistenti.

### 16.3 Decisioni di design (D1–D8)

- **D1 — Scope S1**: deciso con l'utente dopo 3 opzioni confrontate (S1 minimo / S2 slider end-to-end / S3 strict by piano). Rationale: batch atomico, feedback rapido, prepara il terreno per S2 senza doppi ritocchi sul DTO (che è nato già con `originatingRequestId` nullable + compact ctor). Trade-off accettato: NESSUN effetto end-to-end fino a S2.
- **D2 — Outbox producer pattern INLINE**: deciso dopo confronto con opzione port+adapter (`TournamentMatchOutboxPort`+`TournamentMatchOutboxAdapter`). Rationale: uniformità con i 3 service FASE 1/2 che usano già `writeOutboxEvent` inline (`UserService`, `GameDefinitionService`, `LocalAdminBuildingService`); diff minimo; `OutboxEventRepository` è già cablato in `SyncEventProcessor` e `UserReplicationSchedulerService`.
- **D3 — DELETE = hard delete + tombstone**: deciso dopo confronto con soft-delete (campo `deleted` sul model). Rationale: hard delete è coerente con `TournamentRepository.deleteById` esistente e non inquina il model con un campo non persistente (`Tournament` non ha `@Entity`, è POJO); il tombstone è puro payload della replica (costringe il Local a `deleteById` la sua `tournaments_summary_local`). Safe perché in `DRAFT` non esistono partecipanti (la registrazione richiede `OPEN_REGISTRATION`), né match (lo schedule richiede `OPEN_REGISTRATION`); per cui `tournament_participants`/`tournament_standings`/`tournament_matches` sono sempre vuoti — no cascade necessario.
- **D4 — Update ripristina `endsAt=null`**: il `Tournament.update` riazzera `endsAt` (un DRAFT non ha fine programmata). Non può essere diversamente perché in DRAFT `endsAt` è sempre null fin dall'inizio; mantenere un vecchio valore se fosse stato modificato in precedenza sarebbe incoerente.
- **D5 — Guard `DRAFT` nel domain per `update`, nel service per `delete`**: asimmetria motivata. `Tournament` ha già una state machine con guard per ogni transizione (`openRegistration`/`cancel`/`startProgress`/`complete`); il metodo domain `update` segue lo stesso pattern (guard + rebuilt immutable). `Delete` non ha un corrispettivo domain (è una rimozione, non una transizione di stato), quindi la guard resta nel service con esplicito `if (existing.getStatus() != TournamentStatus.DRAFT) throw new InvalidTournamentStateException`.
- **D6 — `buildingIds` replace = `deleteByTournament` + `saveAll`**: pattern già usato in `TournamentService.create:96-97` per il save iniziale. Per l'update, replica lo stesso (delete-all + insert-all atomicamente). Non esiste `replaceBuildings(tournamentId, List)` né è richiesto (la combo è idempotente).
- **D7 — Legacy-delegating ctor pattern**: applicato uniformemente a `TournamentService` (5-arg → 7-arg con `null, null`) e `TournamentController` (10-arg → 12-arg con `null, null`). Pattern già in uso in `SyncEventProcessor:91-146` (multi-level deleganti). Rationale: preserva i test esistenti (`TournamentServiceTest:66`, `TournamentControllerTest:88`) durante la transizione; il 5-arg e il 10-arg diventano safety-net transitori; il `@Autowired` N-arg è il ctor production.
- **D8 — Null-safe `writeOutboxEvent`**: guard `if (outboxEventRepository == null || objectMapper == null) return;`. Rationale: i test esistenti (ctor 5-arg legacy con deps null) non esercitano `update`/`delete`, quindi il path outbox non è mai invocato con deps null; ma la guard difensiva previene NPE in eventuali nuovi test rapidi che istanziano il service col ctor legacy. Pattern speculare a `SyncEventProcessor:297-302`.

### 16.4 Backward-compat

- **`TournamentServiceTest`:** 9 test pregressi FASE 4 (create/open/cancel/getById/findByStatus) passano indenni sul ctor 7-arg (deps non toccati dai loro path). `@ExtendWith(MockitoExtension.class)` strict-stubbing: i nuovi `@Mock OutboxEventRepository` non sono invocati dai 9 vecchi test (mock autoflush-only). L'`objectMapper` è reale `new ObjectMapper().registerModule(new JavaTimeModule())` per serializzare realmente il `TournamentSummaryEventDto` nel round-trip del test.
- **`TournamentControllerTest`:** 12 test pregressi FASE 4/5/6 migrati con `@WithMockUser` appropriati (PLATFORM_ADMIN sui POST admin, PLAYER su GET). La migrazione `standaloneSetup` → `@WebMvcTest` introduce `MethodSecurityTestConfig` per enforcement reale di `@PreAuthorize`. Il `Clock` è bean reale `Clock.fixed(FIXED_NOW, UTC)` (non `@MockBean` — un `@MockBean Clock` unstubbed restituirebbe null e romperebbe i test `create` che invocano `Instant.now(clock)`).
- **Ctor 10-arg `TournamentController` legacy** preservato come delegante `(null, null)`: il `TournamentControllerTest` migrato usa `@WebMvcTest` (iniezione Spring), ma il 10-arg resta disponibile per eventuali IT/debug futuri.
- **`EventTypeContractTest`:** NON rotto. `TOURNAMENT_SUMMARY_UPSERTED` è Central-emitted (non Local-emitted) → non aggiunto a `EXPECTED_EVENT_TYPES` (che vincola solo eventi Local→Central). `grep` confermato: 0 match del literal nel codice pre-batch, ora presente solo in `TournamentService.SUMMARY_EVENT_TYPE` + `TournamentSummaryEventDto.eventType` javadoc.
- **`TournamentBracketServiceTest`:** NON toccato (non dipende da `TournamentService.update`/`delete`).
- **Regression target:** `mvn test -pl :central-system -am` → **339 test, 0 failures, 0 errors** (delta vs FASE 6 baseline: 328 → 339 = +11 test, 4 service + 7 controller).

### 16.5 Schema DB e infrastruttura

- **Central**: NESSUNA nuova tabella (non è un evento replica in S1, anche se l'outbox è scritto — il `OutboxEvent` è già persistito su `outbox_events` FASE 1; la propagazione sarà attivata in S2 con `UserReplicationSchedulerService` extension + `tournaments_summary_local`Lato).
- **Local**: NESSUNA modifica (S2).
- **`ddl-auto: validate`**: invariato. Nessun campo nuovo su `Tournament` o altre entity JPA (la guard `DRAFT` è in Java POJO, non in colonna DB). La validazione post-modifiche producer (§7.A.8 del piano) è deferita a S2/S3.

### 16.6 Concorrenza e atomicità

- **`TournamentService.update`**: `tournamentRepository.save` + `tournamentBuildingRepository.deleteByTournament` + `saveAll` + `writeOutboxEvent` eseguiti nella **stessa transazione** `@Transactional` class-level (readWrite, dato che `TournamentService` è `@Service @Transactional` class-level); residue helper `writeOutboxEvent` è nella stessa tx, non apre sub-transazioni. Outbox Pattern atomico: se una scrittura fallisce, l'intera operazione è rolled-back (no entity saved senza outbox, no outbox senza entity).
- **`TournamentService.delete`**: `tournamentBuildingRepository.deleteByTournament` + `tournamentRepository.deleteById` + `writeOutboxEvent` (tombstone) atomicamente nella stessa `@Transactional`.
- **`writeOutboxEvent` null-safe guard**: esegue `outboxEventRepository.save` solo con deps non null; con deps null (test legacy 5-arg) è no-op — non interferisce coi 9 test esistenti.
- **Thread-safety**: `Tournament` immutable (tutti `final`), `Tournament.update` ritorna `new Tournament(...)` → intrinsecamente thread-safe, no stato mutabile in `TournamentService` fra chiamate. `OutboxEvent` è costruito per chiamata (UUID per call). Concorrenza su `update` concorrenti gestita a livello DB da `OutOfboxEventRepository.save` (insert isolata). Non sono richiesti `@Lock` per `update`/`delete` di DRAFT (il vincolo è getById che è read).

### 16.7 Follow-up noti (fuori scope S1)

- **S2 (prossimo batch atomico)**: §7.A.3 (estensione producer outbox `originatingRequestId` per `TournamentService` path update/delete — già fatto in D1/D2/D8, ma mancano gli altri service); §7.A.5 (estensione `UserReplicationSchedulerService` con nuovo ramo `replicateTournamentSummaryEvent` + predicate `isTournamentSummaryEvent` + integrazione in `isReplicationEvent`); §7.A.6 (estensione `LateRegistrationCatchUpService.REPLICATION_EVENT_TYPES`); mini-§7.B (tabella `tournaments_summary_local` su Local + `InternalTournamentSummaryController` + `TournamentSummarySyncService` per attivare il drenaggio end-to-end del `TOURNAMENT_SUMMARY_UPSERTED` PENDING).
- **S3**: §7.A.7 (8 branch `SyncEventProcessor` per `*_REQUESTED`) + §7.B W12e/W12f (Local use case `UpdateTournamentRequestedUseCase`/`DeleteTournamentRequestedUseCase` che emettono i `*_REQUESTED`). Devono essere consegnati nello stesso commit (o in commit adiacenti con `EventTypeContractTest` temporaneamente escluso) per via di `EventTypeContractTest.EXPECTED_EVENT_TYPES` che vincola ogni literal Local-emitted a un branch Central.
- **A5 (gap emerso in STEP 1)**: NON esiste test speculare a `EventTypeContractTest` per eventi Central-emitted drained. Consegnare S1 con `TOURNAMENT_SUMMARY_UPSERTED` senza 7.A.5 apre una finestra di silenzioso stallo (compila, test verdi, ma outbox resta PENDING). Raccomandazione: introdurre `ReplicationEventTypeContractTest` in §7.A.5 come salvaguardia — ogni evento Central-emitted deve apparire in `UserReplicationSchedulerService.isReplicationEvent`.
- **A7 (limite di trust)**: in S1 il path `TournamentController.update/delete` ha `originatingRequestId=null` (non c'è `admin_requests_local` quando il PLATFORM_ADMIN opera direttamente sul Central). Sul path S3, il `SyncEventProcessor` propaga il `requestId` UUID dal payload `*_REQUESTED` al `originatingRequestId` del `TournamentSummaryEventDto`; il Local `TournamentSummarySyncService` lo userà per `AdminRequestRepository.markCompleted(requestId, resultData)`.
- **DELETE guard allentata in futuro**: se si volesse permettere la DELETE in `OPEN_REGISTRATION` (non DRAFT), occorrerebbe emettere `TOURNAMENT_PARTICIPANTS_UPSERTED` con lista vuota per svuotare i partecipanti replicati + delete-cascade su `tournament_standings`/`tournament_matches` Local. Attualmente non necessario.
- **Test 403 esteso**: il 403 è ora coperto per `PUT`/`DELETE`. I 403 su `POST /open`/`/cancel`/`/schedule` e `POST /api/tournaments` create NON sono coperti explicit test ma sono enforced via `@EnableMethodSecurity` + `@PreAuthorize`. Follow-up opzionale.

### 16.8 Mappatura requisiti

| RF | Coverage in S1 | Nota |
|---|---|---|
| RF-UT-01 | n/a (RBAC 4 ruoli FASE 0) | — |
| RF-UT-02 | Parziale | `PLATFORM_ADMIN` assegna/revoca ruoli via §7.A.7/S3 (outbox `ROLE_ASSIGNMENT_REQUESTED`). S1 introduce `PLATFORM_ADMIN` enforcement su PUT/DELETE. |
| RF-TO-01 | +S1 | `PLATFORM_ADMIN` può ora anche aggiornare o cancellare un DRAFT torneo (oltre a creare FASE 4). |
| RF-TO-02 | n/a | Vincolo ≥2 edifici: invariante preservato da `@Size(min=2)` su `UpdateTournamentRequestDto.buildingIds`. |
| RF-TO-03/04 | Parziale | PLAYER iscrizione torneo via §7.A.7 + 7.B W6 (outbox `PARTICIPANT_REGISTER_REQUESTED`). S1 introduce la disponibilità di `Update`/`Delete` che `PLATFORM_ADMIN` può usare per preparare il torneo prima dell'`OPEN_REGISTRATION`. |

---

## 17. FASE 7-A2 - Replica end-to-end di `TOURNAMENT_SUMMARY_UPSERTED` (Central→Local)

> **Stato:** Implementato (batch S2 di FASE 7 §7.A.2-parziale/§7.A.3-parziale/§7.A.5/§7.A.6 + mini-§7.B di `documenti/PIANO_UTENTI_TORNEI.md`).
> **Data:** 2026-07-12
> **Scope:** Attivazione del flusso end-to-end dell'outbox `TOURNAMENT_SUMMARY_UPSERTED` (rimasto PENDING nel DB Central dopo S1 perché `UserReplicationSchedulerService.isReplicationEvent` non lo filtrava). Estensione dei 3 producer `TournamentService.create/open/cancel` (già implementati in S1 per `update`/`delete`) → nuova porta out `PushTournamentSummaryToLocalServersPort` + adapter REST `LocalTournamentSummaryRestAdapter` → drain broadcast in `UserReplicationSchedulerService.replicateTournamentSummaryEvent` (TUTTI i Local attivi, no routing per building) → catch-up `LateRegistrationCatchUpService` per Local tardivi → endpoint Local `PUT /internal/tournaments/summaries/sync` + `TournamentSummarySyncService` con upsert-by-PK + tombstone-delete + tabella `tournaments_summary_local` (su tutti i 3 init files Local).
> **Scope NON incluso (deferito a S3):** porte di replica push per le restanti 3 repliche (`TournamentStandings`/`TournamentParticipants`/`LocalServerRegistry`), rami `SyncEventProcessor` per gli 8 eventi `*_REQUESTED`, branch `markCompleted` su `originatingRequestId` lato Local (`AdminRequestLocal` non ancora implementato), DTO `UserSyncDto`/`GameDefinitionEventDto` estensione `originatingRequestId`, 7 DTO `*_REQUESTED`, test `SyncEventProcessorTest`, `TournamentStandingsService`/`TournamentRegistrationService` extension `originatingRequestId`, `LocalServerRegistryPort` outbox `LOCAL_SERVER_REGISTRY_UPSERTED`.
> **Scelte d'implementazione concordate con l'utente:**
> - **S2 attiva l'end-to-end**: consegna isolata della tripletta producer→scheduler→receiver per `TOURNAMENT_SUMMARY_UPSERTED`. Coerente col sequencing deciso in §16.7 ("S2 prossimo batch atomico"). Trade-off accettato: 1/4 tipi di evento drenati — gli altri 3 (Standings/Participants/LocalServerRegistry) deferiti a S3.
> - **Backward-compat ctor pattern `SyncEventProcessor:91-146`**: applicato uniformemente a `UserReplicationSchedulerService` (11-arg legacy → 12-arg `@Autowired` production con `PushTournamentSummaryToLocalServersPort` come 12° arg) e `LateRegistrationCatchUpService` (9-arg legacy → 10-arg `@Autowired` production con `PushTournamentSummaryToLocalServersPort` come 10° arg). I ctor legacy delegano al production ctor con `null` per il nuovo port. Quando il port è `null`, il drain branch short-circuita con un WARN log e lascia l'evento PENDING per un futuro tick (Scheduler) o lo salta (CatchUp) — pattern nullo-safe coerente con `SyncEventProcessor.playerStatisticsProjection=null`.
> - **`buildingIds` serializzato come JSON in `TEXT`**: ambiguità STEP 1 risolta allineandosi al pattern di `GameDefinitionLocalJpaEntity.registration_rules` (TEXT column + `ObjectMapper` injectato nel mapper `TournamentSummaryLocalMapper`). Alternative scartate: (a) colonna VARCHAR CSV — non allineata al pattern di serializzazione del codebase; (b) `@Convert` con `JsonStringUnwrappingConverter` — non esiste nel codebase e aggiungerebbe una nuova abstraction per un solo campo. La scelta TEXT+JSON è coerente con FASE 2.
> - **InternalTournamentSummaryControllerTest usa `standaloneSetup.addFilter(InternalApiKeyFilter)`**: l'IT H2 "puro" non è fattibile nel codebase perché il `@SpringBootApplication` del local-server eagerly instanzia `MqttConfig.mqttClient` (connect a `tcp://localhost:1883`) durante context refresh, fallendo in CI/dev senza broker — stessa limitazione documentata in `AdminLocalControllerIT` e `UserRepositoryAdapterOrderingGuardIT`. La soluzione adottata: `MockMvcBuilders.standaloneSetup(controller).addFilter(new InternalApiKeyFilter("test-key"))` verifica il 401-through-filter chain su header `X-Internal-Api-Key` mancante/errato senza richiedere un full Spring context. Documentato nel javadoc del test.
> - **`originatingRequestId` propagato ma `markCompleted` deferito**: il campo è già nel DTO `TournamentSummaryEventDto` (definito in S1, nullable). Il `TournamentSummarySyncService` lo logga a DEBUG ma NON esegue `AdminRequestRepository.markCompleted` (l'entità `AdminRequestLocal` non è ancora implementata, deferita a S3). Questo scelta garantisce che il contract upstream `TournamentSummaryEventDto` è già S3-ready: S3 dovrà solo aggiungere un branch `if (originatingRequestId != null) adminRequestRepository.markCompleted(...)` in `TournamentSummarySyncService` senza toccare DTO, adapter, controller, schedulatore o tabella.

### 17.1 Matrice dei file (18 totali)

| # | File (relativo `gamehandler-platform/`) | Tipo | Layer | Scope |
|---|---|---|---|---|
| 1 | `central-system/.../domain/ports/out/PushTournamentSummaryToLocalServersPort.java` | NUOVO | domain/ports-out | Porta out `void push(List<TournamentSummaryEventDto>, RegisteredLocalServer)` (strutturale twin di `PushTournamentMatchToLocalServersPort`); nessun ack/poison contract, idempotenza per PK lato Local |
| 2 | `central-system/.../infrastructure/adapters/out/rest/LocalTournamentSummaryRestAdapter.java` | NUOVO | infra/adapters-out-rest | `@Component implements PushTournamentSummaryToLocalServersPort`; clone di `LocalTournamentMatchRestAdapter` (SSLContext, `RetryTemplate(3, expBackoff 100/2.0/10000)`, `X-Internal-Api-Key`, `PUT /internal/tournaments/summaries/sync`); `isTransient` identico |
| 3 | `central-system/.../application/service/TournamentService.java` | ESTESO (+6 righe eff.) | application/service | `create`/`open`/`cancel` ora emettono `TOURNAMENT_SUMMARY_UPSERTED` via `writeOutboxEvent` S1 (deleted=false, originatingRequestId=null); atomico nella `@Transactional` class-level |
| 4 | `central-system/.../application/service/UserReplicationSchedulerService.java` | ESTESO (+85 righe) | application/service | +costante `TOURNAMENT_SUMMARY_UPSERTED_EVENT`; +campo `PushTournamentSummaryToLocalServersPort`; ctor 12-arg `@Autowired` (production) + ctor 11-arg legacy delegante (null port); +metodo `replicateTournamentSummaryEvent` (clone di `replicateGameDefinitionEvent`, broadcast a TUTTI i Local attivi, no building routing filter, allOf().join(), replication_progress, markAsSent); +predicato `isTournamentSummaryEvent` + integrazione in `isReplicationEvent` + ramo dispatch in `replicateUsers` |
| 5 | `central-system/.../application/service/LateRegistrationCatchUpService.java` | ESTESO (+45 righe) | application/service | +`TOURNAMENT_SUMMARY_UPSERTED` in `REPLICATION_EVENT_TYPES`; ctor 10-arg `@Autowired` (production) + ctor 9-arg legacy delegante; +ramo `isTournamentSummaryEvent(eventType)` (clone del ramo `isGameDefinitionEvent`, push best-effort al server appena registrato, replication_progress, swallowing DIVE, fail-soft); +static `isTournamentSummaryEvent(String)` |
| 6 | `local-server/.../domain/model/TournamentSummaryLocal.java` | NUOVO | domain/model | POJO puro (campi `final`, ctor canonico, validazione arg, identity by `tournamentId`, `equals/hashCode`); NO Spring/JPA/Jackson; elenco campi: `tournamentId (TournamentId), name, gameType (GameType), teamBased, teamSize, status (TournamentStatus), startsAt, endsAt, buildingIds (List<String>), participantsCount, deleted, updatedAt` — esclude `eventId`/`eventType`/`originatingRequestId` (envelope fields del DTO non persistiti sulla proiezione) |
| 7 | `local-server/.../domain/ports/out/TournamentSummaryLocalRepository.java` | NUOVO | domain/ports-out | Porta out `save` (upsert by PK), `findById(TournamentId)`, `findAll`, `deleteById(TournamentId)`, `existsById(TournamentId)` |
| 8 | `local-server/.../infrastructure/adapters/out/mysql/entity/TournamentSummaryLocalJpaEntity.java` | NUOVO | infra/adapters-out-mysql-entity | `@Entity @Table(name = "tournaments_summary_local")`; PK `tournament_id VARCHAR(36)`; `building_ids` TEXT JSON (serializzato dal mapper, mirror `registration_rules`); `deleted BOOLEAN NOT NULL`; no `@Version`, no `@OneToMany` |
| 9 | `local-server/.../infrastructure/adapters/out/mysql/repository/TournamentSummaryLocalJpaRepository.java` | NUOVO | infra/adapters-out-mysql-repo | Spring Data `JpaRepository<TournamentSummaryLocalJpaEntity, String>` |
| 10 | `local-server/.../infrastructure/adapters/out/mysql/mapper/TournamentSummaryLocalMapper.java` | NUOVO | infra/adapters-out-mysql-mapper | `@Component`; ObjectMapper injectato; serializza `buildingIds List<String>` ↔ JSON in TEXT (mirror `GameDefinitionLocalMapper.registration_rules`); null-safe, `JsonProcessingException` → `RuntimeException` |
| 11 | `local-server/.../infrastructure/adapters/out/mysql/adapter/TournamentSummaryLocalRepositoryAdapter.java` | NUOVO | infra/adapters-out-mysql-adapter | `@Component implements TournamentSummaryLocalRepository`; `save` upsert by PK; `findById`/`findAll`/`deleteById`/`existsById` con guard null; pattern `TournamentMatchLocalRepositoryAdapter` |
| 12 | `local-server/.../application/service/TournamentSummarySyncService.java` | NUOVO | application/service | `@Service @Transactional void applyEvents(List<TournamentSummaryEventDto>)`; per-evento: `deleted==true` → `deleteById(tournamentId)` (no-op safe su re-delivery); altrimenti build `TournamentSummaryLocal` + `save` (upsert idempotente PK); `originatingRequestId` loggato a DEBUG (markCompleted deferito a S3); skip su null/empty/unknown-eventType/blank-tournamentId |
| 13 | `local-server/.../infrastructure/adapters/in/rest/InternalTournamentSummaryController.java` | NUOVO | infra/adapters-in-rest | `@RestController @RequestMapping("/internal/tournaments/summaries")` + `@PutMapping("/sync")`; delega a `TournamentSummarySyncService.applyEvents`; NO `@PreAuthorize` (sicurezza SOLO via `InternalApiKeyFilter`, template `InternalGameDefinitionSyncController`/`InternalTournamentController`) |
| 14 | `central-system/src/test/.../application/service/UserReplicationSchedulerServiceTest.java` | ESTESO (+4 test) | test/application | +`@Mock PushTournamentSummaryToLocalServersPort`; setUp migrato al 12-arg ctor production; parallel-test ctor migrato al 12-arg; +`buildTournamentSummaryEvent(tournamentId, deleted)` helper (real `ObjectMapper` + `JavaTimeModule`); +4 test: `replicateTournamentSummaryEvent_pushesToAllActiveServersAndMarksSent_whenAllSucceed` (verify push broadcast a entrambi i server + `replication_progress` per `(eventId,serverId)` + `markAsSent`), `_doesNotMarkAsSent_andStillPushesOthers_whenOneServerFails` (verify isolamento fallimento single-server: server2 ancora pushato, markAsSent NOT called, markAsFailed NOT called — no poison isolation), `_skipsAlreadyReplicatedServer` (verify progress pre-check salta il push per il server già replicato), `_marksFailed_whenPayloadIsMalformed` (verify `markAsFailed` su payload JSON malformato, no push) |
| 15 | `local-server/src/test/.../application/service/TournamentSummarySyncServiceTest.java` | NUOVO | test/application | 11 test pure-Mockito: upsert su evento regolare (capture + assert 12 campi, verify never deleteById); idempotenza re-delivery upsert (save times(2) con equals sui domain object); tombstone `deleted==true` → `deleteById` + `never save`; tombstone re-delivery safe (`deleteById` times(2)); upsert-after-tombstone ripristina la proiezione (`deleteById` then `save`); skip null/empty/unknown-eventType/blank-tournamentId; `originatingRequestId` loggato ma non cambia il comportamento (verify save comunque invocato) |
| 16 | `local-server/src/test/.../infrastructure/adapters/in/rest/InternalTournamentSummaryControllerTest.java` | NUOVO | test/infrastructure | 5 test MockMvc `standaloneSetup.addFilter(InternalApiKeyFilter)`: 200 su body valido + X-Internal-Api-Key corretto (verify applyEvents called); 200 su body vuoto; 401 su X-Internal-Api-Key mancante (verifyNoInteractions syncService — il filter short-circuita prima del controller); 401 su X-Internal-Api-Key errato; idempotenza HTTP-layer (same body accepted twice, verify times(2)). NOTA: usa `addFilter` invece di `@SpringBootTest` H2 per via del vincolo MqttConfig documentato |
| 17 | `infrastructure/mysql-local/init.sql` | ESTESO (+24 righe) | infra/schema | +tabella `tournaments_summary_local` (PK `tournament_id VARCHAR(36)`, `building_ids TEXT NULL`, `deleted BOOLEAN NOT NULL DEFAULT FALSE`, INDEX `idx_tsl_status`) |
| 18 | `infrastructure/mysql-local/init-building-2.sql` + `init-building-3.sql` | ESTESO (+24 righe ciascuno) | infra/schema | +stessa tabella `tournaments_summary_local` identica (3 edifici Local hanno lo stesso schema replica) |

### 17.2 Contract surface

- **Nuova porta out** (`central-system/.../domain/ports/out/`): `PushTournamentSummaryToLocalServersPort.push(List<TournamentSummaryEventDto> events, RegisteredLocalServer server)`. Metodo denominato `push` (deviazione dal pattern `pushTournamentMatch`/`pushGameDefinitions`/`pushMetadata`, scelta motivata dal task scope §7.A.2 che esplicita `push(...)` come firma target). Nessun ack / poison contract (idempotenza per PK lato Local + tombstone `deleted=true` → deleteById).
- **Nuovo adapter REST** (`central-system/.../infrastructure/adapters/out/rest/`): `LocalTournamentSummaryRestAdapter` → `PUT /internal/tournaments/summaries/sync`, body `List<TournamentSummaryEventDto>`, header `X-Internal-Api-Key`. SSLContext + `RetryTemplate(3, exponentialBackoff 100/2.0/10000)` + `isTransient` identico a `LocalTournamentMatchRestAdapter`. `TransientPushException` su 5xx/429/408/ResourceAccessException (retry-able); altre propagate (non retry-able, propagated come `RuntimeException`).
- **Nuovo evento outbox producer (extension)**: `TournamentService.create/open/cancel` ora emettono `TOURNAMENT_SUMMARY_UPSERTED` con `deleted=false`, `originatingRequestId=null` via helper `writeOutboxEvent` S1 (atomico nella `@Transactional` class-level del service). `update`/`delete` già implementati in S1. `originatingRequestId=null` perché S2 non gestisce il path `admin_requests_local` (path REST diretto admin centrale — non c'è requestId UUID).
- **Nuovo ramo drain** (`UserReplicationSchedulerService.replicateTournamentSummaryEvent`): deserializza `TournamentSummaryEventDto`, push broadcast a TUTTI i Local attivi (no building routing filter, il sommario è globale), `replication_progress(eventId,serverId)` per server, `allOf().join()` sul `replicationPushExecutor`, `markAsSent` on `allSucceeded`. No poison isolation: una deserializzazione fallita → `markAsFailed`; un push fallito per un server → flip `allSucceeded=false` (no markAsSent, no markAsFailed, retry su futuro tick). Legacy 11-arg ctor (port=null) short-circuita con WARN log.
- **Nuovo ramo catch-up** (`LateRegistrationCatchUpService.catchUpNewlyRegisteredServer`): replay di `TOURNAMENT_SUMMARY_UPSERTED` SENT+PENDING al Local appena registrato. Push best-effort (failure swallowed non abortisce il batch). `replication_progress(eventId,buildingId)` con swallowing DIVE su insert duplicata. Legacy 9-arg ctor (port=null) short-circuita con WARN log e `continue`.
- **Nuovo endpoint Local** (`InternalTournamentSummaryController`): `PUT /internal/tournaments/summaries/sync` riceve `List<TournamentSummaryEventDto>`, delega a `TournamentSummarySyncService.applyEvents`. NO `@PreAuthorize` — sicurezza SOLO via `InternalApiKeyFilter` (filter short-circuita con 401 se `X-Internal-Api-Key` mancante/errato, prima del controller).
- **Nuovo service Local** (`TournamentSummarySyncService`): `@Service @Transactional void applyEvents(List<TournamentSummaryEventDto>)`. Per-evento: `deleted==true` → `tournamentSummaryLocalRepository.deleteById(tournamentId)` (no-op safe su re-delivery); altrimenti build `TournamentSummaryLocal` + `save` (upsert idempotente PK `tournamentId`). `originatingRequestId` loggato a DEBUG (markCompleted deferito a S3).
- **Nuova tabella Local** (`tournaments_summary_local`): PK `tournament_id VARCHAR(36)`; colonne matching l'entity JPA (`name VARCHAR(200) NOT NULL`, `game_type VARCHAR(50) NOT NULL`, `team_based BOOLEAN NOT NULL`, `team_size INT NOT NULL`, `status VARCHAR(50) NOT NULL`, `starts_at TIMESTAMP NULL`, `ends_at TIMESTAMP NULL`, `building_ids TEXT NULL`, `participants_count INT NOT NULL DEFAULT 0`, `deleted BOOLEAN NOT NULL DEFAULT FALSE`, `updated_at TIMESTAMP NOT NULL`); INDEX `idx_tsl_status (status)` per il filtro status nelle viste client (deferito a S3).

### 17.3 Decisioni di design (D1-D5)

- **D1 - Scope S2 = attivazione end-to-end di 1/4 eventi**: deciso con l'utente dopo S1 (sequencing §16.7). Rationale: batch atomico che risolve il "problema attuale" di S1 (outbox `TOURNAMENT_SUMMARY_UPSERTED` PENDING non drenato) toccando producer (3 metodi) + drain + push + catch-up + receiver + tabella. Trade-off accettato: 1/4 tipi drenati — gli altri 3 (Standings/Participants/LocalServerRegistry) deferiti a S3 perché richiedono porte/DTO/adapter dedicati non ancora implementati. Consegnare S2 con tutti e 4 insieme avrebbe violato il principio di "batch atomico consegnabile indipendentemente" e richiesto 4x la superficie di S2 (porte + DTO + adapter + service + entity + tabella + test per ognuno).
- **D2 - `buildingIds` serializzato come JSON in TEXT (risoluzione A1)**: ambiguità STEP 1 ("`String`/TEXT JSON o `,`-joined VARCHAR?"). Deciso dopo confronto di 3 opzioni:
  - (a) `VARCHAR` CSV `,`-joined: scartato — non allineato al pattern di serializzazione del codebase (le altre tabelle Local con campo strutturato usano JSON: `registration_rules` in `game_definitions_local`).
  - (b) `@Convert` con `JsonStringUnwrappingConverter`: scartato — il converter non esiste nel codebase (grep proof = 0 match `JsonStringUnwrapping` in `local-server`), aggiungerebbe una nuova abstraction per un solo campo.
  - (c) **`TEXT` JSON con ObjectMapper injectato nel mapper** (pattern `GameDefinitionLocalMapper`): scelto. Mirror esatto di `registration_rules` (che è `Map<String,Object>` ↔ JSON in TEXT via mapper-injected ObjectMapper). Null-safe: `null`/empty list → `null` column; deserializzazione fallita → `RuntimeException` non leaka past l'adapter boundary.
- **D3 - `originatingRequestId` propagato ma markCompleted deferito a S3**: il campo è già nel DTO `TournamentSummaryEventDto` (definito in S1, nullable). Il `TournamentSummarySyncService` lo logga a DEBUG ma NON esegue `AdminRequestRepository.markCompleted` perché l'entità `AdminRequestLocal` non è ancora implementata (deferita a S3 insieme ai W use case §7.B W12). Questa scelta garantisce che il contract upstream `TournamentSummaryEventDto` è già S3-ready: S3 dovrà solo aggiungere un branch `if (originatingRequestId != null) adminRequestRepository.markCompleted(requestId, resultData)` in `TournamentSummarySyncService` senza toccare DTO, adapter, controller, schedulatore, tabella o test esistenti. Coerente col principio "forward-declared signature, no overload futuro" già applicato in S1 per `UpdateTournamentUseCase`.
- **D4 - InternalTournamentSummaryControllerTest usa `standaloneSetup.addFilter` invece di `@SpringBootTest` H2**: l'IT H2 "puro" non è fattibile nel codebase perché il `@SpringBootApplication` del local-server eagerly instanzia `MqttConfig.mqttClient` (connect a `tcp://localhost:1883`) durante context refresh, fallendo in CI/dev senza broker — documentato in `AdminLocalControllerIT` e `UserRepositoryAdapterOrderingGuardIT` javadocs. La soluzione adottata: `MockMvcBuilders.standaloneSetup(controller).addFilter(new InternalApiKeyFilter("test-key"))` verifica il 401-through-filter chain su `X-Internal-Api-Key` mancante/errato senza richiedere un full Spring context. L'`addFilter` trick è la via più vicina al "vero IT H2" compatibile col vincolo MQTT. Documentato nel javadoc del test.
- **D5 - `replicateTournamentSummaryEvent` è broadcast, no building routing**: asimmetria motivata rispetto a `replicateTournamentMatchEvent` (che filtra per `buildingId` assegnato al match). Il sommario del torneo è una proiezione GLOBALE del torneo (ogni Local deve sapere dell'esistenza di ogni torneo per poter esporre le viste PLAYER e le dashboard admin); un match invece è instradato a un solo building. Coerente col piano §7.B "Replica read-only del sommario torneo" su ogni Local. Trade-off accettato: ogni Local riceve TUTTI i sommari, non solo quelli del suo building (per i quali esiste `tournament_buildings` Central — ma non viene replicato ai Local in FASE 7; il client usa `buildingIds` nel sommario per filtrare a vista). Follow-up: eventualmente filtrare client-side.

### 17.4 Backward-compat

- **`UserReplicationSchedulerServiceTest`**: 19 test pregressi FASE 1/2/6 passano indenni sul ctor 12-arg (deps non toccati dai loro path). `@ExtendWith(MockitoExtension.class)` strict-stubbing: il nuovo `@Mock PushTournamentSummaryToLocalServersPort` è `lenient` (unstubbed, mai invocato dai test pregressi → no `UnnecessaryStubbingException`). Il `ObjectMapper` è ora reale con `JavaTimeModule` (per supportare la serializzazione di `Instant` nei nuovi test `TournamentSummaryEventDto`); i 19 test pregressi usavano `new ObjectMapper()` plain senza `JavaTimeModule`, ma il `UserSyncDto` non ha campi `Instant`, quindi il cambio è invisible ai loro path. Il test parallelo `replicateUsers_pushesToAllServersInParallelAndDoesNotBlockOnSlowServer` è migrato al ctor 12-arg (passando il nuovo mock come 12° arg, invariato nel comportamento perché il test non esercita il ramo TournamentSummary).
- **`LateRegistrationCatchUpServiceTest`** + **`LateRegistrationCatchUpReplaysPendingAndRecordsProgressTest`** + **`LateRegistrationCatchUpProgressPersistenceIT`**: i 3 test file pregressi usano il legacy 9-arg ctor di `LateRegistrationCatchUpService`. Il ctor 9-arg delegante preserva la backward-compat (delega al 10-arg con `null` per il nuovo port); quando il test costruisce un evento `TOURNAMENT_SUMMARY_UPSERTED` (che nessuno dei 3 fa), il ramo catch-up short-circuiterebbe con WARN log. Tutti i 3 test file passano indenni senza modifiche.
- **`TournamentServiceTest`** (S1): 13 test pregressi passano indenni. Le estensioni `create`/`open`/`cancel` ora invocano `writeOutboxEvent` (path `outboxEventRepository.save`) — il `@Mock OutboxEventRepository` S1 accetta la call senza stubbing (ritorna `null` di default, non flip strict stubbing). I test `create_persistsDraftTournament_whenValidIndividualRequest`/`open_transitionsToOpenRegistration_whenStatusIsDraft`/`cancel_transitionsToCancelled_whenStatusIsDraft` verificano il save del `Tournament` e il `toDto` risultato — la chiamata aggiuntiva a `outboxEventRepository.save` non rompe gli assert esistenti. Stessa logica dei 4 test S1 su `update`/`delete` che già invocavano `writeOutboxEvent`.
- **`TournamentControllerTest`** (S1, `@WebMvcTest`): 19 test pregressi passano indenni. I test `create`/`open`/`cancel` mockano il `createUseCase`/`openUseCase`/`cancelUseCase` (che sono porte in), quindi non esercitano `TournamentService` — il nuovo outbox emission è internamente al service e non influenza il comportamento del controller.
- **`EventTypeContractTest`**: NON rotto. `TOURNAMENT_SUMMARY_UPSERTED` è Central-emitted (non Local-emitted) → non aggiunto a `EXPECTED_EVENT_TYPES` (che vincola solo eventi Local→Central gestiti da `SyncEventProcessor.processEvent`). Il `EventTypeContractTest` passa (verificato: 343 central tests, 0 failures).
- **`SchemaAlignmentTest`** (central, IT H2 `@SpringBootTest`): passa perché il `LateRegistrationCatchUpService` ha ora un ctor 10-arg `@Autowired` esplicito (Spring non ambiguo su quale ctor usare — fix STEP 4): il context refresh riesce a istanziare il bean. Il `UserReplicationSchedulerService` ha un ctor 12-arg `@Autowired` esplicito per la stessa ragione (S1 lo aveva già su 11-arg, S2 lo ha spostato al 12-arg).
- **Regression target S2**: `mvn test -pl :central-system -am` → **343 test, 0 failures, 0 errors** (+4 test vs S1 baseline di 339). `mvn test -pl :local-server -am` → **633 test, 0 failures, 0 errors** (+16 test file-level aggiunti: 11 di `TournamentSummarySyncServiceTest` + 5 di `InternalTournamentSummaryControllerTest`; il delta netto sulla barriera `Tests run:` può differire leggermente per via di stubbed/lenient mock adjustments nei test esistenti per compatibilità col nuovo ctor 12-arg di `UserReplicationSchedulerService`, ma il target 0 failures/0 errors è raggiunto).

### 17.5 Schema DB e infrastruttura

- **Central**: NESSUNA nuova tabella (il `OutboxEvent` è già persistito su `outbox_events` FASE 1; la propagazione è ora attiva via `UserReplicationSchedulerService` extension + `LocalTournamentSummaryRestAdapter` + `LateRegistrationCatchUpService` extension). `ddl-auto: validate` invariato.
- **Local**: NUOVA tabella `tournaments_summary_local` aggiunta ai 3 init files (`init.sql` + `init-building-2.sql` + `init-building-3.sql`) — identica in tutti e 3 (i 3 edifici Local hanno lo stesso schema replica). PK `tournament_id VARCHAR(36)`; `building_ids TEXT NULL` (JSON serializzato dal mapper); `deleted BOOLEAN NOT NULL DEFAULT FALSE`; INDEX `idx_tsl_status (status)` per il filtro status nelle viste client (deferito a S3). `ddl-auto: validate` invariato.
- **`docker-compose down -v` obbligatorio a regime** (per ricreare i container MySQL Local con la nuova tabella); non bloccante per i test Mockito/IT perché `ddl-auto: validate` non è attivo sui test slice H2 e i test Mokito non toccano il DB. Da eseguire prima del smoke test end-to-end `docker-compose up -d --build` (§7.D, S3+).

### 17.6 Concorrenza e atomicità

- **`TournamentService.create/open/cancel`**: le scritture del `Tournament` + i `tournamentBuildingRepository.saveAll`/`findByTournament`/`countByTournament` + il `writeOutboxEvent` eseguiti nella **stessa transazione** `@Transactional` class-level (readWrite). Outbox Pattern atomico: se una scrittura fallisce, l'intera operazione è rolled-back (no entity saved senza outbox, no outbox senza entity). Lo `writeOutboxEvent` è null-safe (S1 D8) — non interferisce coi test legacy 5-arg.
- **`UserReplicationSchedulerService.replicateUsers`** (`@Scheduled fixedDelayString = "${app.sync-interval-ms:300000}"`): non `@Transactional` (non vuole una long-tx con REST I/O esterne — C-R4/C-01). Per-evento: `replication_progress.save` committa autonomamente via l'adapter `@Transactional`; `markAsSent`/`markAsFailed` committano autonomamente. `allOf().join()` attende TUTTI i push paralleli sul `replicationPushExecutor` prima di decidere `markAsSent`. Il nuovo ramo `replicateTournamentSummaryEvent` segue lo stesso pattern: per-server push parallelo + per-server progress record + `allOf().join()` + `markAsSent` on `allSucceeded`.
- **`LateRegistrationCatchUpService.catchUpNewlyRegisteredServer`**: intenzionalmente NON `@Transactional` (stessa ragione del scheduler — evita long-tx con REST I/O esterne; vedi javadoc del metodo `:99-128`). Per-evento: ogni push + `replication_progress.save` committa autonomamente. Best-effort: un push fallito non abortisce il resto del batch (lo skip con `continue`). Il nuovo ramo `isTournamentSummaryEvent` segue lo stesso pattern.
- **`TournamentSummarySyncService.applyEvents`** (`@Service @Transactional` class-level): per-evento il `save` o il `deleteById` sono eseguiti nella stessa tx del metodo. Se un evento fallisce (eccezione), l'intera tx è rolled-back e NESSUNO degli eventi precedenti è applicato — trade-off accettato: un batch di 5 eventi in cui il 5° è malformato (es. `gameType` enum invalido → `IllegalArgumentException` nel `TournamentSummaryLocal` ctor) rollbacka tutti e 5. Rationale: il producer Central valida già i campi; un evento malformato è un bug di produzione (non di rete), quindi fail-fast è preferibile a fail-silent (no-ack isolation come nel path USER). Riconsiderabile in S3 se si volesse per-evento poison isolation (un evento malformato NON blocca gli altri del batch) — ma romperebbe l'atomicità `applyEvents` per un endpoint idempotente.
- **Thread-safety**: `TournamentSummaryLocal` immutable (tutti `final`); `TournamentSummaryLocalJpaEntity` mutabile (setter JPA) ma confinata al thread della tx del service; `TournamentSummarySyncService` stateless (nessuno stato mutabile fra chiamate); `TournamentSummaryLocalMapper` stateless (ObjectMapper thread-safe dopo warmup). `TournamentSummaryEventDto` record (immutable per costruzione). Concorrenza gestita a livello DB da `tournamentSummaryLocalRepository.save` (upsert isolata per PK).

### 17.7 Follow-up noti (fuori scope S2)

- **S3 (prossimo batch atomico)**: porte di replica push per le restanti 3 repliche (`PushTournamentStandingsToLocalServersPort`/`PushTournamentParticipantsToLocalServersPort`/`PushLocalServerRegistryToLocalServersPort`) + adapter REST + rami `replicateTournament*Event`/`replicateLocalServerRegistryEvent` + rami catch-up + DTO `TournamentStandingsEventDto`/`TournamentParticipantsEventDto`/`LocalServerRegistryEventDto` + tabella Local `tournament_standings_local`/`tournament_participants_local`/`registered_local_servers_local` + Internal controllers + Sync services.
- **S3 — `SyncEventProcessor` 8 branch `*_REQUESTED`**: i branch `ROLE_ASSIGNMENT_REQUESTED`/`GAME_DEFINITION_UPSERT_REQUESTED`/`TOURNAMENT_CREATE/OPEN/CANCEL/SCHEDULE/UPDATE/DELETE_REQUESTED`/`PARTICIPANT_REGISTER_REQUESTED` per il path admin async outbox Central. Devono essere consegnati nello stesso commit (o in commit adiacenti con `EventTypeContractTest` temporaneamente escluso) per via di `EventTypeContractTest.EXPECTED_EVENT_TYPES` che vincola ogni literal Local-emitted a un branch Central.
- **S3 — `AdminRequestLocal` + `AdminRequestRepository` + `markCompleted` branch in `TournamentSummarySyncService`**: l'entità `AdminRequestLocal` non è ancora implementata. Quando sarà disponibile, il `TournamentSummarySyncService.applyEvents` dovrà aggiungere un branch `if (event.originatingRequestId() != null) adminRequestRepository.markCompleted(event.originatingRequestId(), resultData)` post-upsert/delete. Il campo `originatingRequestId` è già nel DTO `TournamentSummaryEventDto` (S1) — forward-declared signature, no overload futuro.
- **S3 — `originatingRequestId` propagation negli altri producer**: `UserService.saveUserOnDB`/`updateUser`, `GameDefinitionService.writeOutboxEvent`, `TournamentStandingsService`, `TournamentRegistrationService`, `LocalServerRegistryPort.register`/heartbeat. S2 ha esteso solo `TournamentService.create/open/cancel` (oltre a `update`/`delete` di S1). Gli altri producer propagano `originatingRequestId=null` (path REST diretto admin centrale — non c'è `admin_requests_local` in S2). S3 dovrà propagare il `requestId` UUID dal payload `*_REQUESTED` del `SyncEventProcessor` al `originatingRequestId` del DTO di ritorno.
- **S3 — `ReplicationEventTypeContractTest` (gap emerso in S1 §16.7 A5)**: NON esiste ancora un test speculare a `EventTypeContractTest` per eventi Central-emitted drained. S2 ha attivato il drain di `TOURNAMENT_SUMMARY_UPSERTED` ma non c'è salvaguardia architetturale che impedisca a un futuro evento Central-emitted di essere silenziosamente ignorato da `UserReplicationSchedulerService.isReplicationEvent`. Raccomandazione confermata: introdurre `ReplicationEventTypeContractTest` in S3 — ogni evento Central-emitted deve apparire in `UserReplicationSchedulerService.isReplicationEvent` OR in un altro drain scheduler dedicato.
- **S3 — `AdminRequestTimeoutService`**: `@Scheduled` poll (1 min) che marca `FAILED` le righe `admin_requests_local` PENDING con `createdAt < now - timeout` (default 30 min). Poison rejection Central: l'evento di ritorno `TOURNAMENT_SUMMARY_UPSERTED` non arriva (perché il Central non riesce a processare il `*_REQUESTED` originale) → timeout chiude il cerchio lato Local. Richiede `AdminRequestLocal` (S3).
- **A7 (limite di trust, ereditato da S1)**: in S2 il path `TournamentService.create/open/cancel` ha `originatingRequestId=null` (non c'è `admin_requests_local` quando il PLATFORM_ADMIN opera direttamente sul Central — S2 non gestisce i W use case §7.B W12). Sul path S3, il `SyncEventProcessor` propaga il `requestId` UUID dal payload `*_REQUESTED` al `originatingRequestId` del `TournamentSummaryEventDto`; il `TournamentSummarySyncService` lo userà per `AdminRequestRepository.markCompleted(requestId, resultData)`.
- **A6 (gap emerso in S2)**: il `TournamentSummarySyncService.applyEvents` è `@Transactional` class-level con per-evento processing INSIDE la tx. Se un evento del batch è malformato (es. `gameType` enum invalido → `IllegalArgumentException` nel `TournamentSummaryLocal` ctor), l'intero batch è rolled-back (atomicità fail-fast). Riconsiderabile in S3 se si volesse per-evento poison isolation (pattern `SyncEventProcessor.processOne` con `Propagation.REQUIRES_NEW` per evento) — ma rompe l'atomicità `applyEvents` per un endpoint idempotente. Trade-off da valutare in S3.

### 17.8 Mappatura requisiti

| RF | Coverage in S2 | Nota |
|---|---|---|
| RF-UT-01 | n/a (RBAC 4 ruoli FASE 0) | - |
| RF-UT-02 | n/a | `PLATFORM_ADMIN` assegna/revoca ruoli via §7.A.7/S3 (outbox `ROLE_ASSIGNMENT_REQUESTED`). S2 non tocca RBAC. |
| RF-TO-01 | +S2 | `PLATFORM_ADMIN` vede le proprie mutazioni (`create`/`open`/`cancel`) propagate ai Local entro 5 min. Il client PLAYER vede i tornei disponibili (sul Local) tramite le viste `PlayerTournamentSummaryController` (deferito a S3). |
| RF-TO-02 | n/a | Vincolo ≥2 edifici: invariante preservato. S2 non tocca `buildingIds` validation. |
| RF-TO-03/04 | Parziale | PLAYER iscrizione torneo via §7.A.7 + §7.B W6 (outbox `PARTICIPANT_REGISTER_REQUESTED`). S2 attiva la replica `TOURNAMENT_SUMMARY_UPSERTED` che il client userà per vedere i tornei a cui iscriversi (endpoint client deferito a S3). |
| RF-TO-05..12 | +S2 parziale | Le mutazioni `create`/`open`/`cancel` sono ora replicate ai Local; `update`/`delete` lo erano da S1. La disponibilità delle viste PLAYER (`PlayerTournamentSummaryController`) è deferita a S3. |

---

## 19. FASE 7-B completa — Local residue (entity + AdminRequest + SyncService + endpoint client-facing + W use case)

### 19.1 Decisioni

Il batch S4 completa tutto il residue Local della FASE 7 §7.B (righe 683-721 del PIANO), includendo:
1. **4 nuove entità JPA + repo + adapter + mapper**: `TournamentStandingLocal` (PK composta), `TournamentParticipantLocal` (PK composta), `RegisteredLocalServerLocal` (PK `buildingId`), `AdminRequestLocal` (PK `requestId` UUID) + enum `AdminRequestStatus`.
2. **3 nuovi `/internal/*` SyncServices** (delete+insert full-snapshot per standings/participants; upsert per buildingId per il registry) + estensione `markCompleted` su `TournamentSummarySyncService`/`UserSyncService`/`GameDefinitionSyncService` quando `originatingRequestId != null`.
3. **7 W use case** (W6/W9/W10/W12a-f). Pattern condiviso:
   - `RolePreCheck.requireRole(userRepository, actingUserId, requiredRole)` helper statico — defense-in-depth oltre il `@PreAuthorize` Spring Security: tira su `User` via `findById` dalla replica `replicated_users` e verifica `roles.contains(requiredRole)` (`.trim()`-tolerant per via del mapping CSV "PLATFORM_ADMIN,PLAYER" — vedi `UserMapper.toDomain`); throw `AccessDeniedException` (→ 403 via `GlobalExceptionHandler`) su mismatch e `IllegalArgumentException` (→ 400) su user-not-replicated.
   - `AdminRequestOutboxWriter` `@Component` helper che incapsula la scrittura atomica PENDING (UUID `requestId == outbox eventId`): `writePendingRequest(...)` serializza il payload DTO via `ObjectMapper.writeValueAsString` e scrive `admin_requests_local PENDING` + `OutboxEvent PENDING` nello stesso `@Transactional` caller. `writeFailedRequest(...)` per il DRAFT pre-check (FAILED without outbox).
   - W12e (update) e W12f (delete) hanno pre-check DRAFT su `TournamentSummaryLocalRepository.findById(tournamentId)`: se status != DRAFT (o summary missing) → `writeFailedRequest` con `resultData={"reason":"NOT_DRAFT","status":...}`/`{"reason":"NOT_FOUND"}` (no outbox written); altrimenti `writePendingRequest` standard.
4. **`AdminRequestTimeoutService`** `@Service` `@Scheduled` (poll ogni `${admin.request.timeout-check-ms:60000}`): `findPendingOlderThan(now.minus(timeoutMs, MILLIS))` + `markFailed(requestId, "{\"reason\":\"TIMEOUT\"}", now)` per-`row` (idempotente: `markFailed=0` su overlap è un no-op debug-logged, no exception). `@EnableScheduling` già attivo in `LocalServerApplication.java`.
5. **Endpoint client-facing**:
   - Read PLAYER: `PlayerMatchHistoryController` (`GET /api/players/me/matches/history[?gameType=]`), `PlayerTournamentSummaryController` (`GET /api/tournaments[?status=]`, `/{id}`, `/{id}/standings`, `/{id}/matches`, `/{id}/participants`) — entrambi basati sulle 4 repliche Local.
   - Read admin: `AdminRequestsController` (self-service `GET /api/admin/requests[/{requestId}]` con cross-user read filter `actingUserId==principal`), `PlatformAdminServerController` (`GET /api/admin/servers/health` aggrega own outbox count + registry), `PlatformAdminUsersController` (`GET /api/admin/users` directory utenti senza `hashedPassword`).
   - Write async via outbox: `PlayerTournamentRegistrationController`, `GameAdminController` Local, `PlatformAdminUserController`, `PlatformAdminTournamentController` (POST/POST lifecycle/PUT/DELETE).
6. **Allineamenti**: `GameController.toDto`/`AdminLocalController.toDto` leggono `minPlayers`/`maxPlayers` da `game_definitions_local` (con fallback al `GameFactory` statico quando la definizione non è ancora replicata — offline-first). `AuthController.getCurrentUser` ritorna `UserInfoDto` arricchito con `userId`/`roles` (da `replicated_users` via `findByUsername`) e `buildings` (da `local_admin_buildings_local` per LOCAL_ADMIN).

### 19.2 Matrice file (creati/estesi in S4)

Production code (~54 nuove file + 12 estese):
- shared-dto: `PlayerMatchDto.java`, `TournamentSummaryDto.java`, `TournamentDetailDto.java`, `UsersDirectoryDto.java`, `ServerHealthViewDto.java`, `AdminRequestDto.java` (6 NUOVI); `UserInfoDto.java` (estesa con ctor short retrocompatibile).
- local-server domain/model: `TournamentStandingLocal.java`, `TournamentParticipantLocal.java`, `RegisteredLocalServerLocal.java`, `AdminRequestLocal.java`, `AdminRequestStatus.java` (5 NUOVI).
- local-server domain/ports/out: `TournamentStandingsLocalRepository.java`, `TournamentParticipantsLocalRepository.java`, `RegisteredLocalServerLocalRepository.java`, `AdminRequestRepository.java` (4 NUOVI).
- local-server domain/ports/in: `RegisterTournamentParticipantRequestedUseCase`, `UpsertGameDefinitionRequestedUseCase`, `AssignRoleRequestedUseCase`, `CreateTournamentRequestedUseCase`, `TournamentLifecycleRequestedUseCase`, `UpdateTournamentRequestedUseCase`, `DeleteTournamentRequestedUseCase`, `ListPlayerMatchesUseCase`, `ListTournamentSummariesUseCase`, `GetTournamentDetailUseCase`, `ListAdminRequestsUseCase`, `GetLocalServerHealthViewUseCase`, `ListUsersDirectoryUseCase` (13 NUOVI).
- local-server infrastructure/adapters/out: 2 `IdClass` Serializable (`TournamentStandingLocalId`, `TournamentParticipantLocalId`); 4 JpaEntity (`TournamentStandingLocalJpaEntity`, `TournamentParticipantLocalJpaEntity`, `RegisteredLocalServerLocalJpaEntity`, `AdminRequestLocalJpaEntity`); 4 JpaRepository (con `@Modifying @Query` `markCompleted`/`markFailed` sul `AdminRequestLocalJpaRepository`); 4 Mapper; 4 Adapter (16 NUOVI).
- local-server infrastructure/adapters/in/rest: 3 internal controllers (`InternalTournamentStandingsController`, `InternalTournamentParticipantsController`, `InternalLocalServerRegistryController`); 9 client-facing controllers (`PlayerMatchHistoryController`, `PlayerTournamentSummaryController`, `PlayerTournamentRegistrationController`, `GameAdminController`, `PlatformAdminUserController`, `PlatformAdminTournamentController`, `AdminRequestsController`, `PlatformAdminServerController`, `PlatformAdminUsersController`) (12 NUOVI); 4 estesi (`AuthController`, `GameController`, `AdminLocalController`, `PlayerTournamentController`).
- local-server application/service: `TournamentStandingsLocalSyncService`, `TournamentParticipantsLocalSyncService`, `RegisteredLocalServerSyncService`, `AdminRequestTimeoutService`, `AdminRequestOutboxWriter`, `RolePreCheck`, `RegisterTournamentParticipantRequestedService`, `UpsertGameDefinitionRequestedService`, `AssignRoleRequestedService`, `CreateTournamentRequestedService`, `TournamentLifecycleRequestedService`, `UpdateTournamentRequestedService`, `DeleteTournamentRequestedService`, `ListPlayerMatchesService`, `ListTournamentSummariesService`, `GetTournamentDetailService`, `ListAdminRequestsService`, `GetLocalServerHealthViewService`, `ListUsersDirectoryService` (19 NUOVI); 3 estesi (`TournamentSummarySyncService`, `UserSyncService`, `GameDefinitionSyncService`).
- infrastructure/mysql-local: `init.sql`, `init-building-2.sql`, `init-building-3.sql` (3 ESTESI con 4 nuove tabelle: `tournament_standings_local`, `tournament_participants_local`, `registered_local_servers_local`, `admin_requests_local`).

### 19.3 Contract surface

- Outbox: il `AdminRequestOutboxWriter` scrive `OutboxEvent(id=requestId, eventType="*_REQUESTED", payload=serializedDto, status="PENDING", createdAt=now)` con lo stesso pattern del S2 (codec JSON via `ObjectMapper` injected). Il `SyncEventProcessor` del Central (S3 — 8 branch `*_REQUESTED`) consuma questi eventi, applica la mutazione e ritorna il DTO di ritorno (`TournamentSummaryEventDto`, `UserSyncDto`, `GameDefinitionEventDto`, `TournamentStandingsEventDto`, `TournamentParticipantsEventDto`, `LocalServerRegistryEventDto`) con `originatingRequestId=requestId`. Il `*SyncService` locale chiama `AdminRequestRepository.markCompleted(requestId, resultData, now)` — condizionale `WHERE status='PENDING'` quindi idempotente su re-delivery.
- `markCompleted`/`markFailed` sono `@Modifying @Query` bulk UPDATE nel `AdminRequestLocalJpaRepository` — ritornano il numero di righe mutate (`int`); un valore 0 indica che la riga era già COMPLETED/FAILED (no-op logged at DEBUG).
- DRAFT pre-check: il W12e (UpdateTournament) e W12f (DeleteTournament) leggono la proiezione `tournaments_summary_local` e rifiutano con `writeFailedRequest` (no outbox) quando `status != DRAFT` o summary missing. Le restanti 5 lifecycle (create/open/cancel/schedule/participant-register) non richiedono il pre-check perché il Central esegue la propria validazione.

### 19.4 Schema DB (3 init files)

```sql
-- 4 nuove tabelle FASE 7-B (S4) — aggiunte a tutti i 3 init.sql:
CREATE TABLE IF NOT EXISTS tournament_standings_local (
    tournament_id   VARCHAR(36) NOT NULL,
    participant_id VARCHAR(64) NOT NULL,
    display_name   VARCHAR(100) NOT NULL,
    wins           INT NOT NULL DEFAULT 0,
    losses         INT NOT NULL DEFAULT 0,
    points         INT NOT NULL DEFAULT 0,
    rank           INT NULL,
    updated_at     TIMESTAMP NOT NULL,
    PRIMARY KEY (tournament_id, participant_id),
    INDEX idx_tsl_tournament (tournament_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tournament_participants_local (
    tournament_id   VARCHAR(36) NOT NULL,
    participant_id  VARCHAR(64) NOT NULL,
    is_team         BOOLEAN NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    registered_at   TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    PRIMARY KEY (tournament_id, participant_id),
    INDEX idx_tpl_tournament (tournament_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS registered_local_servers_local (
    building_id   VARCHAR(64) PRIMARY KEY,
    base_url      VARCHAR(255) NOT NULL,
    last_seen_at  TIMESTAMP NULL,
    active        BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at    TIMESTAMP NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS admin_requests_local (
    request_id        VARCHAR(36) PRIMARY KEY,
    event_type        VARCHAR(64) NOT NULL,
    acting_user_id    VARCHAR(64) NOT NULL,
    acting_role       VARCHAR(32) NOT NULL,
    building_id       VARCHAR(64) NULL,
    payload           TEXT NOT NULL,
    status            VARCHAR(16) NOT NULL,
    result_data       TEXT NULL,
    created_at        TIMESTAMP NOT NULL,
    completed_at      TIMESTAMP NULL,
    outbox_event_id   VARCHAR(64) NULL,
    INDEX idx_arl_user_status (acting_user_id, status),
    INDEX idx_arl_status_created (status, created_at)
) ENGINE=InnoDB;
```

`docker-compose down -v` obbligatorio perché `ddl-auto: validate` non ammette ALTER su tabelle già create. I test Mockito/IT non richiedono il DB perché `validate` non è attivo sui test slice (no `@SpringBootTest`/`@DataJpaTest` — vedi §19.7).

### 19.5 Test

Complessivamente +165 test (633→798):
- 3 SyncService unit test files (×9+×9+×8 = 26 nuovi test). Mirror S2 `TournamentSummarySyncServiceTest` — happy path/full-snapshot replace/idempotency/no-op on null/empty/unknown eventType/blank id/markCompleted su `originatingRequestId != null`/no-markCompleted su null.
- 7 W use case unit test files (×5+×6+×5+×7+×6+×8+×5 = 42 nuovi test). Verificano: role pre-check (`AccessDeniedException` matching), user-not-replicated (`IllegalArgumentException`), validation argomenti (blank, null, minPlayers=0, buildingIds.size<2), DRAFT pre-check (W12e/f: `writeFailedRequest` su status != DRAFT, missing, `writePendingRequest` su status==DRAFT), payload corretto via `argThat`/`ArgumentCaptor`.
- `AdminRequestTimeoutServiceTest` × 6 (happy/no-op empty/null/idempotente su markFailed=0/multi-stale/threshold exact-match).
- 4 adapter unit test files (×8+×7+×6+×17 = 38 nuovi test). Verificano delegation JpaRepo+Mapper, no-op su args null/blank, `markCompleted`/`markFailed` dual-path (clock-fixed vs explicit-now).
- 12 IT test files per i 12 nuovi controllers (51 nuovi test). Slice `MockMvcBuilders.standaloneSetup` + `InternalApiKeyFilter` addFilter per `/internal/*` (template `InternalTournamentSummaryControllerTest` S2). NO `@SpringBootTest` (vedi §19.7 per il vincolo).

Estensioni a test esistenti: `TournamentSummarySyncServiceTest.applyEvents_*` (1 rename → `applyEvents_originatingRequestIdTriggersMarkCompleted` + `@Mock AdminRequestRepository`); `UserSyncServiceTest`/`UserSyncServiceAckContractTest`/`UserSyncServiceOrderingGuardTest` (`@Mock AdminRequestRepository`); `AuthControllerTest`/`AuthControllerSignupEdgeCaseTest` (`@Mock UserRepository` + `@Mock LocalAdminBuildingLocalRepository`); `GameControllerTest`/`LocalServerRestControllerCompatibilityTest` (`@Mock GameDefinitionLocalRepository`).

### 19.6 Backward-compat

- `UserInfoDto` extend compact-ctor pattern: il ctor short `(String username)` delega a `(username, null, List.of(), List.of())` — retrocompatibile con il S2 FASE 6 contract (`AuthController.getCurrentUser` ritornava `new UserInfoDto(auth.getName())`).
- `TournamentSummarySyncService` ctor cambiato da `(TournamentSummaryLocalRepository)` → `(TournamentSummaryLocalRepository, AdminRequestRepository)`: i test `TournamentSummarySyncServiceTest` S2 sono stati estesi con `@Mock AdminRequestRepository` (`@InjectMocks` funziona per convenzione).
- `UserSyncService` ctor cambiato da `(UserRepository, Clock)` → `(UserRepository, AdminRequestRepository, Clock)`: i 3 test esistenti sono stati estesi con `@Mock AdminRequestRepository`.
- `GameDefinitionSyncService` ctor cambiato da `(GameDefinitionLocalRepository, Clock)` → `(GameDefinitionLocalRepository, AdminRequestRepository, Clock)`: il metodo `applyEvents` signature è invariato; nessun test `GameDefinitionSyncServiceTest` preesistente.
- `GameController` ctor cambiato da `(GetAvailableGamesUseCase)` → `(GetAvailableGamesUseCase, GameDefinitionLocalRepository)`: `GameControllerTest` e `LocalServerRestControllerCompatibilityTest` estesi con `@Mock GameDefinitionLocalRepository`.
- `AuthController` ctor cambiato da `(AuthenticateLocalUserUseCase, RegisterLocalUserUseCase)` → `(AuthenticateLocalUserUseCase, RegisterLocalUserUseCase, UserRepository, LocalAdminBuildingLocalRepository)`: `AuthControllerTest` e `AuthControllerSignupEdgeCaseTest` estesi con 2 nuove `@Mock` deps.

### 19.7 Limiti noti

- **`EventTypeContractTest` (Central) NON contiene ancora i 8 literal `*_REQUESTED` Local-emitted** (`PARTICIPANT_REGISTER_REQUESTED`, `GAME_DEFINITION_UPSERT_REQUESTED`, `ROLE_ASSIGNMENT_REQUESTED`, `TOURNAMENT_CREATE/Open/Cancel/Schedule/Update/Delete_REQUESTED`). S4 EMETTE questi 8 literal Local-side (il W use case scrive l'`OutboxEvent` con `eventType=*_REQUESTED`); il test Central andrebbe esteso a richiedere che ognuno dei 8 literal abbia (a) un branch `SyncEventProcessor` su Central e (b) un producer Local — S4 lo lascia come follow-up per evitare di toccare i 343 test Central nel batch.
- **`@SpringBootTest`/`@DataJpaTest` local ancora non utilizzabili**: la causa è `MqttConfig.mqttClient` che si eagerly instanzia sul context refresh e fa connect a `tcp://localhost:1883` (FAILED in CI/dev senza broker). Tutti i 12 nuovi IT controllers usano slice `MockMvcBuilders.standaloneSetup` + Mockito only. Solve futuro: escludere `MqttConfig` dai test slices con `excludeFilters` o spostare la connect in `@PostConstruct` lazy.
- **`ReplicationEventTypeContractTest` (gap emerso in S1 §16.7 A5)**: NON esiste ancora un test speculare a `EventTypeContractTest` per eventi Central-emitted drained. S4 attiva il drain di `TOURNAMENT_STANDINGS_UPSERTED`/`TOURNAMENT_PARTICIPANTS_UPSERTED`/`LOCAL_SERVER_REGISTRY_UPSERTED` ma non c'è salvaguardia architetturale che impedisca un futuro evento Central-emitted di essere silenziosamente ignorato dai drain schedulers. Raccomandazione: introdurre `ReplicationEventTypeContractTest` in S5.
- **`PlayerTournamentController.myMatches` team-match membership gap**: S4 ha esteso il javadoc di `myMatches` per documentare il limite che un `participantId` di tipo `TeamId` (non `UserId`) NON può essere risolto in membri utente usando solo `tournament_participants_local` (la tabella replica il `participantId` Value, non i membri del team). Solve futuro: nuova tabella `team_members_local (TournamentId, TeamId, UserId)` replicata dal Central via outbox `TEAM_MEMBERS_UPSERTED`, joined nella query `findScheduledByParticipant`. Implementazione deferred per evitare speculazione nel batch residue Local.
- **`@Modifying @Query` flush detection**: `markCompleted`/`markFailed` nel `AdminRequestLocalJpaRepository` ritornano un `int` che l'`AdminRequestRepositoryAdapter` propaga; l'`AdminRequestTimeoutService` testa quel return = 0 come no-op (idempotente su overlap). La condizione `WHERE status='PENDING'` rende l'UPDATE idempotente anche sotto concurrency (no DB-LOCK, ma la precondition è sufficiente).

### 19.8 Follow-up

- **S5 — Client Emulator GUI (§7.C)**: il residue Local della FASE 7 è ora completo. S5 può attivare il tokenizer client `ApiClient`/`HttpClientHelper.setRoles/setBuildings` etc. usando gli endpoint client-facing esposti in S4 (`GET /api/auth/me` arricchito, `GET /api/tournaments[?status=]`, `POST /api/tournaments/{id}/participants` PLAYER async-write flow).
- **S5 — `EventTypeContractTest` estensione per i 8 `*_REQUESTED` Local-emitted**: il test deve ora validare che ogni literal Local-emitted ha un branch `SyncEventProcessor.processOne` Central-side + un producer sul Local. S4 lascia la empty-slot assunzione invariata nel batch residue Local per evitare di rompere i 343 test Central; S5 lo chiude.
- **S5 — `ReplicationEventTypeContractTest`**: vedere §19.7 gap emerso S1 §16.7 A5.
- **S5 — `team_members_local` table**: per chiudere il gap di `PlayerTournamentController.myMatches` team-match membership (vedi §19.7).
- **S6 — chiusura dei due gap soprastanti**: `ReplicationEventTypeContractTest` viene creato in S6 (vedi §21.4 sotto); il gap `team_members_local` rimane aperto (out-of-scope FASE 7, follow-up FASE 8).

---

## 20. FASE 7-C — Client Emulator GUI (batch S5)

### 20.1 Decisioni

1. **Client-side architettura Clean Architecture importata alla lettera — `infrastructure/rest/` per le boundary HTTP/UI e `application/service/` per l'orchestrazione**: il nuovo package `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/rest/` ospita il `ApiClient` (`HttpClient` wrapper), `ObjectMappers` (config riutilizzabile di Jackson con `JavaTimeModule`) e le tre eccezioni UI dedicate (`AuthenticationException` / `AuthorizationException` / `ServerUnavailableException`). Il `application/service/PlayerTournamentFlow` orchestra i 8 endpoint PLAYER del flusso torneo (list/detail/standings/matches/participants/register/me-matches/start-match) delegando all'`ApiClient` — senza stato. Tutto il session state (token / username / roles / buildings) rimane in `HttpClientHelper` (esteso in S5 con `volatile List<String> roles/buildings` + `clearSession()`), accessibile sia all'`ApiClient` (per attachment header `Authorization`) sia al `NavbarController` (per drive la UI ruolo-aware). Nessun decoding JWT lato client (decisione E1 §7.B — `GET /api/auth/me` arricchito è la bound deterministica per roles/buildings).
2. **Routing e navbar "marshellld in `NavbarController` + `MainView.navigateTo`**: le 14 costanti `VIEW_*` sono definite in `NavbarController` (6 ereditate + 8 nuove §7.C riga 734). `NavbarController.rebuild()` ricostruisce l'`HBox` JavaFX ogni volta che il login rinnova la sessione, con cascading `roles.contains("…")` (PLAYER + ogni admin vede Games/MyStats/MyMatches/Tournaments; LOCAL_ADMIN & PLATFORM_ADMIN → Local Dashboard; GAME_ADMIN & PLATFORM_ADMIN → Game Admin; solo PLATFORM_ADMIN → Platform Admin + Admin Requests). Logout button rosso a destra (`#c0392b`), separato da un `Region()` con `HBox.setHgrow(ALWAYS)`. Multi-ruolo de-duplicato via `Map<String, Button>` `LinkedHashMap` con `if (buttons.containsKey(targetView)) return;`. `MainView.navigateTo` accetta la stringa via `Consumer<String>` set in `NavbarController.setOnNavigate`. Non viene mai toccato MQTT o il central/local source (solo `game-client-emulator`).
3. **Sostituzione progressiva con `ApiClient` — SURGICAL**: le 4 viste legacy (`LoginView`, `GameSelectionView`, `LobbyView`, `StatisticsView`) sono state migrate dalla forma "inline `HttpClient.sendAsync` con `HttpResponse.BodyHandlers.ofString()` e ObjectMapper localize per-pattern" alla `ApiClient.get/post/put/delete(... Class<T>|TypeReference<T>)` tipata. `LoginView` fa pipeline `client.post("/api/auth/login").thenCompose(r -> client.get("/api/auth/me", UserInfoDto.class))` per conservare il dataflow "login → fetch-me → storeRoles" come singolo future chain. `LobbyView` had due inline HTTPs (`fetchActiveLobbySession` con fallback 404 → creator mode; `cancelLobbyByGameViaRest` background thread) — migrated to `ApiClient` mantenendo same UX path. `SignupView` NON migrato in S5 (la `POST /api/auth/signup` non shareggia auth-header, il body-only `SignupRequestDto` flow funziona legacy senza `Authorization` header requirement, e la signature response `SignupResponseDto` non richiede JavaTimeModule) — backward-compat preservata. La const displine ("NIENTE inline HttpClient dopo S5" è regola follow-up FASE 8).
4. **Async pattern `CompletableFuture<T>` + `Platform.runLater` marshaling**: ogni metodo `ApiClient` è asincrono e derived `java.net.http.HttpClient.sendAsync` (the JDK11 HTTP client). Lo stub `thenAccept(p -> Platform.runLater(() -> {...}))` è il pattern fisso per ogni view-layer callback (JavaFX scene graph mutation must be on the FX Application Thread). Il blocco `.exceptionally(ex -> { Platform.runLater(() -> {...}); return null; })` è un'unica lambda blocco perché `exceptionally` richiede `Function<Throwable, T>` con T che è `Void` quando la pipeline è VOID-aware; un'espressione lambda del tipo `ex -> Platform.runLater(...)` fallisce la compilazione con `void → Void` incompatible types (provato in compilazione S5).
5. **Polling via `javafx.animation.Timeline` per `AdminRequestsView`**: ogni 8s, `TableView`-free renderizzazione a `VBox` di cards (Label + ProgressIndicator JavaFX per PENDING, Label verde per COMPLETED, Label rosso "Operazione non confermata entro il timeout — riprova/riesamina" per FAILED). Parser `readableResult(r)` da `resultData` JSON string estrae `reason` field con Jackson `ObjectMapper.readTree`. `onEnter()` avvia il poller, `onLeave()` lo ferma — `MainView.navigateTo` reclama `stopPollers()` ad ogni vista switchata per rilasciare il `Timeline` (evita leak del thread animation).
6. **Limiti accettati dalla deviazione S5 spec**:
   - **`RoleAssignmentRequestDto` NON esiste in shared-dto** (la spec §7.B aveva una lista "DTO read per il client" che lo includeva speculativamente; il controller effettivo `PlatformAdminUserController` accetta `@RequestBody List<String>` inline — nessun DTO wrapper). S5 sposta `RoleAssignmentRequestDto` nella "lista follow-up FASE 8 se emerge un caso d'uso per una wrapper-class con metadata (acting admin id, timestamp, audit motivo)". Il client emette `List<String>` raw JSON.
   - **`GET /api/admin/local/games` NON esiste** — bug-documentato del piano riga 744. Il `AdminLocalController` espone solo `/devices`, `/sessions/active`, `/statistics?gameType=`, più POST/PUT/DELETE `/games`. S5 interpreta "giochi building" == "devices" (la `List<GameStateDto>` di `/devices` è la lista delle macchine gioco del building). Javadoc del `LocalAdminDashboard` esplicita il caveat. Le POST/PUT/DELETE (LOCAL_ADMIN CRUD\Catalogo games) sono fuori scope S5 §7.C (riga 743 pone solo read).
   - **Sezione "binding LOCAL_ADMIN↔building" (riga 749)** — stub minimale: la Central-only API `POST/DELETE/GET /api/admin/local/buildings` (con `AssignLocalAdminBuildingsDto`) esiste ma non è esposta dal `local-server` via endpoint client-facing (Central runtime而非 Local runtime). S5 lo lascia come documentation-only nel javadoc — la più pulita sostituzione è usare `POST /api/admin/users/{userId}/roles` con ruoli `["LOCAL_ADMIN"]` per assegnare il ruolo, mentre i binding edificio richiederebbero una futura estensione del body roles DTO con un campo `buildings`.
   - **`theme.css` e i18n opzionali skipped** — la FASE 7 §7.C li marca come "(Opzionale)"; la regola inline-CSS linearizza pattern legacy dark-theme (`#1e1e1e/#333/#3498db`) senza migration effort. Follow-up FASE 8.
   - **`ErrorPane` è disponibile come component ma non integrato nella navbar** come pagina di default error route: ogni vista gestisce localmente il `statusLabel` con full message+cause-tail. Il retry pattern è `ExceptionPane.show(title, msg, retryCallback)` pronto al consumo, ma il routing "swap to error-pane on fatal error" non è wired a `MainView.navigateTo` — FOLLOW-UP FASE 8.

### 20.2 Matrice file (creati/estesi in S5)

| File | Tipo | Righe (medie) | Nota |
|------|------|---------------|------|
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/rest/ApiClient.java` | NUOVO | 230 | Singleton lazy `ApiClient` (`HttpClientHelper.getHttpClient(baseUrl)`) con `get/post/put/delete` tipati via `Class<T>` / `TypeReference<T>`. Header `Authorization: Bearer` auto. Mappa 401→`AuthenticationException`, 403→`AuthorizationException`, 5xx/timeout→`ServerUnavailableException`. Timeout 15s. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/rest/ObjectMappers.java` | NUOVO | 18 | Single immutable `SHARED` con `JavaTimeModule` registrato. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/rest/AuthenticationException.java`, `AuthorizationException.java`, `ServerUnavailableException.java` | NUOVO | ~15 each | UI-specific exceptions. ServerUnavailable ha ctor `(message, cause)`. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/security/HttpClientHelper.java` | ESTESO | +65 | +`volatile List<String> roles/buildings` + `setRoles/getRoles/setBuildings/getBuildings/hasRole(String)/clearSession()`. Defensive copy via `List.copyOf`. Backward-compat con i 2 field legacy invariant. |
| `game-client-emulator/src/main/java/com/gameplatform/client/application/service/PlayerTournamentFlow.java` | NUOVO | 100 | Service orchestrating 8 PLAYER endpoints async. ctor default `new PlayerTournamentFlow()` lega l'`ApiClient.instance()` singleton; test ctor accepts ApiClient. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/NavbarController.java` | NUOVO | 110 | 14 costanti `VIEW_*` + `rebuild()` role-aware con de-duplicazione `LinkedHashMap`. Logout button rosso a destra con spacer `HBox.setHgrow(Region, Priority.ALWAYS)`. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/MainView.java` | ESTESO | +100 | `NavbarController` al posto del `HBox navBar` legacy; `initializeViews()` costruisce le 13 view instances (6 ereditate + 7 nuove). Switch `navigateTo` con 14 case (3 pattern `showNavbar=false` per login/signup/lobby/game_play; 11 showNavbar=true). `doLogout()` + `shutdown()`. MtgQTT lifecycle preservato invariato. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/LoginView.java` | RISCRITTO | 145 | `ApiClient.post(/api/auth/login).thenCompose(get(/api/auth/me))` pipeline; salva token/username/roles/buildings in `HttpClientHelper`. `exceptionally` blocco con dispatch cause. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/GameSelectionView.java` | ESTESO | +5 netto / -36 inline HTTP | `refreshGames()` ora via `ApiClient.get("/api/games", TypeReference<List<GameStateDto>>)` 1-liner. MQTT subscription preservation invariant (`StateSubscriber` per real-time updates). |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/LobbyView.java` | ESTESO | ~0 netto / -55 inline HTTP | Due blocchi inline HTTP (fetchActiveLobbySession + cancelLobbyByGameViaRest) sostituiti con `ApiClient.get` e `ApiClient.post(...)`. Tutta la logica MQTT/lobby-create/lobby-join preservata. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/StatisticsView.java` | RISCRITTO | 100 | `ApiClient.get("/api/statistics", TypeReference<List<StatisticsDto>>)` rx mappa cards; buildings field esposto (legacy displayStats esteso). |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/MyStatisticsView.java` | NUOVO | 160 | `TableView<PlayerStatisticsDto>` (4 colonne: Gioco, Partite giocate, Vittorie, Ultima partita) + `ComboBox<GameType>` filter + LoadingIndicator + StalenessBadge. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/MyMatchesView.java` | NUOVO | 130 | `TableView<PlayerMatchDto>` 6 colonne + `ComboBox<GameType>` filter + LoadingIndicator + StalenessBadge. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/TournamentsView.java` | NUOVO | 280 | Layout `SplitPane` 3-colonne: summary ListView (drill-down on select) / detail VBox (classifica + bracket + partecipanti) / my-matches ListView. Toolbar con 5 bottoni (refresh + register self + register team + load my-matches + start match). ViewModel: `PlayerTournamentFlow` injectable via factory. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/LocalAdminDashboard.java` | NUOVO | 175 | 2 `TableView` (GameStateDto dispositivi / GameSessionDto sessioni attive) + ComboBox gameType per statistics. `GET` via `ApiClient`. Javadoc caveat: `GET /api/admin/local/games` spec bug endpoint inesistente, uso `/devices`. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/GameAdminDashboard.java` | NUOVO | 175 | Catalog `TableView<GameStateDto>` + GridPane editor per `UpsertGameDefinitionRequestDto`. Bottoni POST/PUT → `AdminRequestDto(PENDING)` → navigate VIEW_ADMIN_REQUESTS. `setOnNavigateToRequests(Runnable)`. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/PlatformAdminDashboard.java` | NUOVO | 285 | 6 sezioni: Users directory (`TableView<UsersDirectoryDto>` + roles TextField + assignBtn) / Tournament lifecycle (createTournamentArea JSON + Update fields + open/cancel/schedule buttons + update + delete) / Aggregated-stats TextArea (`GET /api/statistics` JsonNode) / Server monitor (`TableView<ServerHealthDto>` from ServerHealthViewDto) / Classifiche&bracket stub "(riuso viste PLAYER)" / Super-set read-only dashboards soft-button retained. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/AdminRequestsView.java` | NUOVO | 200 | `Timeline @8s` poller (`onEnter` start / `onLeave` stop). VBox cards: PENDING=ProgressIndicator arancione + label, COMPLETED=label verde "✓ COMPLETED — reason", FAILED=label rosso "Operazione non confermata entro il timeout — riprova/riesamina". Parser `readableResult(r.tree.reason)`. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/components/LoadingIndicator.java` | NUOVO | 50 | Wrapper `StackPane` con `ProgressIndicator` 48x48 (`-fx-progress-color: #3498db`), `setMouseTransparent(true)` per non bloccare l'interaction. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/components/StalenessBadge.java` | NUOVO | 80 | HBox con timestampLabel "Dati aggiornati al: HH:mm:ss" + staleBadge "in attesa di replica". `refresh()` confronta `Duration.between(max, now) > staleThresholdMs` (default `System.getProperty("ui.stale-threshold-ms", "300000")`). |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/components/ErrorPane.java` | NUOVO | 60 | VBox per global error handler. retryBtn (`#3498db`) wired a `Runnable retryCallback`. `show(title, message, callback)` + `clear()`. |
| `game-client-emulator/src/main/java/com/gameplatform/client/infrastructure/ui/components/TableColumns.java` | NUOVO | 25 | Helper `addColumn(TableView<S>, header, Function<S,String>)` per non ripetere il boilerplate `PropertyValueFactory` / `SimpleStringProperty` nelle tre admin dashboards. |
| `game-client-emulator/src/main/resources/application.yml` | ESTESO | +2 righe | +`ui.stale-threshold-ms: ${UI_STALE_THRESHOLD_MS:300000}` (env-overridable, 5 minuti default). |
| `documenti/PIANO_UTENTI_TORNEI.md` | ESTESO | 0 netto (only content override) | §7.C righe 723-763 tutte le checkbox `[ ]` → `[x]`; note italic comprehensive. |

Totali: **18 nuovi file** + **5 file estesi**

### 20.3 Contract surface (endpoint consumati dal client S5)

Tutti i path sono sull'unico `${LOCAL_SERVER_URL}` del `local-server` Local:

**PLAYER read (6)**:
- `GET /api/auth/login` ↔ `LoginResponseDto` (legacy, kept for compatibility)
- `GET /api/auth/me` ↔ `UserInfoDto`, `GET /api/games` ↔ `List<GameStateDto>` (game selection)
- `GET /api/players/me/statistics[?gameType=X]` ↔ `List<PlayerStatisticsDto>` (MyStatistics)
- `GET /api/players/me/matches/history[?gameType=X]` ↔ `List<PlayerMatchDto>` (MyMatches)
- `GET /api/tournaments[?status=X]` ↔ `List<TournamentSummaryDto>` (TournamentsView list)
- `GET /api/tournaments/{id}` ↔ `TournamentDetailDto` (TournamentsView drill-down)
- `GET /api/tournaments/{id}/standings|matches|participants` ↔ lists (modalità secondaria — il `TournamentDetailDto` aggregato già li expose; le viste S5 preferiscono il singolo endpoint)
- `GET /api/players/tournaments/me/matches` ↔ `List<TournamentMatchDto>` ("I miei match")
- `GET /api/statistics` ↔ `List<StatisticsDto>` (StatisticsView legacy + PlatformAdminDashboard stats globali)

**PLAYER write (2)**:
- `POST /api/tournaments/{id}/participants` (body `RegisterTournamentParticipantDto` o assente) → `AdminRequestDto(PENDING)` (outbox `PARTICIPANT_REGISTER_REQUESTED` → polling)
- `POST /api/players/tournaments/matches/{matchId}/start[?gameId=Y]` → `GameSessionDto` 201 (non-async)

**GAME_ADMIN write (2)**:
- `POST /api/admin/games` (body `UpsertGameDefinitionRequestDto`) → `AdminRequestDto(PENDING)` (outbox `GAME_DEFINITION_UPSERT_REQUESTED`)
- `PUT /api/admin/games/{gameType}` (stesso body) → `AdminRequestDto(PENDING)`

**PLATFORM_ADMIN read (3)**:
- `GET /api/admin/users` ↔ `List<UsersDirectoryDto>`
- `GET /api/admin/servers/health` ↔ `ServerHealthViewDto`
- `GET /api/admin/requests` + `GET /api/admin/requests/{requestId}` ↔ `List<AdminRequestDto>` / `AdminRequestDto` (polling view — `actingUserId==principal` filter server-side)

**PLATFORM_ADMIN write (6)**:
- `POST /api/admin/users/{userId}/roles` (body `List<String>` raw) → `AdminRequestDto(PENDING)` (outbox `ROLE_ASSIGNMENT_REQUESTED`)
- `POST /api/admin/tournaments` (body `CreateTournamentRequestDto`) → `AdminRequestDto(PENDING)` (outbox `TOURNAMENT_CREATE_REQUESTED`)
- `POST /api/admin/tournaments/{id}/{open|cancel|schedule}` (body assente) → `AdminRequestDto(PENDING)` (outbox `TOURNAMENT_OPEN|CANCEL|SCHEDULE_REQUESTED`)
- `PUT /api/admin/tournaments/{id}` (body `UpdateTournamentRequestDto` DRAFT-only) → `AdminRequestDto(PENDING)` (outbox `TOURNAMENT_UPDATE_REQUESTED`)
- `DELETE /api/admin/tournaments/{id}` DRAFT-only → 202 (outbox `TOURNAMENT_DELETE_REQUESTED`)

**LOCAL_ADMIN read (3)**:
- `GET /api/admin/local/devices` ↔ `List<GameStateDto>` (macchine gioco del building)
- `GET /api/admin/local/sessions/active` ↔ `List<GameSessionDto>` (sessioni attive)
- `GET /api/admin/local/statistics?gameType=X` ↔ `LocalStatistics` (JsonNode projection)

Totale: **25 endpoint locali consumati dal client S5**.

### 20.4 Backward-compat

- `HttpClientHelper` API pubblica: il contract della classe esistente (4 metodi statici `setToken/getToken/setCurrentUsername/getCurrentUsername/getHttpClient`) è invariato. S5 aggiunge solo 6 nuovi metodi (`setRoles/getRoles/setBuildings/getBuildings/hasRole/clearSession`) senza rimuovere o alterare esistenti —> basso impatto per i 4 clients già esistenti della API (`LoginView`, `GameSelectionView`, `LobbyView`, `StatisticsView`) via inline HTTP; il `getHttpClient(String)` legacy path rimane.
- `MainView` API `navigateTo` rinominate costanti `VIEW_*` da locale-this-file (`"login"`, `"signup"`, `"game_selection"`, `"lobby"`, `"game_play"`, `"statistics"`) a costanti public in `NavbarController`. Compatibilità del string-value invariata (`"login"`, `"signup"`, …), quindi il behavior del routing persiste — MainView stesso chiama `NavbarController.VIEW_LOGIN` invece delle costanti private legacy.
- `MainView` field access (`primaryStage`, `root`, ecc.) rimane private; `initializeServices()` per MQTT è quasi invariato (semplificato il MqttCallbackExtended inline e la chiusura del try/catch della certificate enrollment).
- `LoginView` API pubblica `setOnLoginSuccess(Runnable)` / `setOnNavigateToSignup(Runnable)` / `reset()` invariata. `performLogin()` signature pubblica rimasta; il body internalspassato da `HttpClient.sendAsync` + `ObjectMapper.readValue` in line a `ApiClient.post().thenCompose(get())` chain.
- `GameSelectionView` API pubblica `setOnGameSelected(Consumer<GameStateDto>)` / `refreshGames()` / `getView()` invariata. La sottoscrizione MQTT via `StateSubscriber` è rimasta intatta (la sostituzione è solo il refresh REST).
- `LobbyView` API pubblica `setOnCancel(Runnable)` / `setOnLobbyStarted(BiConsumer)` / `configure(GameStateDto)` / `setCurrentUser(String)` invariata. I 2 blocchi HTTP sostituiti rispettano l'`exceptionally` 404→`fallbackToCreatorMode()` legacy behavior con il `cause.getMessage().contains("HTTP 404")`match testuale (l'`ApiClient`propagate il 404 come `RuntimeException("HTTP 404 — body=…")`).
- `StatisticsView` API pubblica `showStats()` / `getView()` invariata. Riscritta con `ApiClient.get`; layout/displayStats invariato in semantica (5 label estese per buildingId che è nuovo).
- `SignupView` API pubblica invariata — vista non toccata (legacy HTTP inline kept per la backward-compat).
- `application.yml` la chiave `app.local-server-url` legacy dovuto essere sostituita da `app.local-server-url` esistente ma S5 legge la base URL da `${LOCAL_SERVER_URL}` env var (via `ApiClient` ctor) — non canonical configuration via Spring Boot perché il client non usa Spring container. La chiave in `application.yml` diventa documentaria più che consumed (cargo-cult); l'`ApiClient`ifica la env-var in `System.getenv()`.
- DTO imports `TournamentSummaryDto/TournamentDetailDto/TournamentParticipantViewDto/PlayerMatchDto/PlayersDirectoryDto/ServerHealthViewDto/AdminRequestDto/UpdateTournamentRequestDto/RegisterTournamentParticipantDto/CreateTournamentRequestDto/UpsertGameDefinitionRequestDto/UpsertGameDefinitionRequestDto/GameDefinitionDto` importati dallo `shared-dto`. `GameDefinitionDto` importato ma non usato attualmente (catalog read è via `GameStateDto` arricchito); `RoleAssignmentRequestDto` speculativo non esiste — usato `List<String>` raw body inline.
- Build `mvn -q -pl :game-client-emulator -am clean compile` → BUILD SUCCESS in S5 (0 errori, 2 warning di dipendenza OpenJFX pre-esistenti). Il `javafx-maven-plugin 0.0.8` configurato con `mainClass=com.gameplatform.client.infrastructure.ui.MainView` per il run (`mvn -pl :game-client-emulator javafx:run`); la compilazione non richiede JavaFX runtime ma la run sì.

### 20.5 Limiti noti (fuori scope S5)

- **Nessun test automatico UI**: il piano §7.B riga 763 esplicita "nessun test automatico UI; copertura manuale come da piano §641". S5 NON aggiunge test files (il modulo `game-client-emulator` risulta senza test directory). Le coperture sono manuali via `mvn -pl :game-client-emulator javafx:run` post `docker-compose up`. Una eventuale FASE 8 potrebbe introdurre TestFX (JavaFX testing) headless sul modulo client — ma non richiesto dal piano.
- **`theme.css` skipped** (opzionale §7.C riga 760): CSS rimane inline nelle viste (`-fx-background-color: #1e1e1e/#333/#3498db` ripetuto). Non bloccante per la build o la run. Follow-up: estrarre regole in `src/main/resources/theme.css` + `scene.getStylesheets().add(...)` in MainView.
- **i18n via `ResourceBundle` skipped** (opzionale §7.C riga 761): label hard-coded IT/EN miste come nelle viste legacy. Follow-up: `ResourceBundle.getBundle("i18n.client")` + `Label.text` binded.
- **`ErrorPane` non integrato globalmente**: il component è utilizzabile come error pane per la pagina offline/5xx con retry, ma `MainView.navigateTo` non ha un caso "fatal-error view swap". Per ora ogni vista displaya il proprio `Label statusLabel`. Integrazione: intercettare e `Exception` fatali del `ApiClient.exceptionally` a livello `MainView` e `root.setCenter(errorPane)` con retry callback. Follow-up FASE 8.
- **`RoleAssignmentRequestDto` mancante**: la spec §7.C riga 762 lo elenca nella "DTO list" ma il server-side `PlatformAdminUserController` accetta `List<String>` raw body. S5 lo lascia come "lista speculativa" — Optionale aggiungerlo in shared-dto in FASE 8 se serve una wrapper class (es. `actingAdminUserId`, `reason`, `auditTimestamp`). Da FASE 7 spec, il client non ne ha bisogno.
- **`GET /api/admin/local/games` spec bug** (riga 744 piano): il controller reale esporta solo `/devices` (+ POST/PUT/DELETE `/games`). S5 usa `/devices` come "giochi building view", documentato nel `LocalAdminDashboard` javadoc.-write-through CRUD/non-read admin Local_S5_skip.
- **Sezione binding LOCAL_ADMIN↔building stub minimale** (riga 749): spec elencava `POST/DELETE/GET /api/admin/local/buildings` (Central runtime) ma non esposte dal `local-server` client-facing. S5 documents-only nel `PlatformAdminDashboard` javadoc; la vera UX path consigliata è `POST /api/admin/users/{userId}/roles` con roles `["LOCAL_ADMIN"]` per assegnare ruolo, e lato Central via outbox `LOCAL_ADMIN_BUILDING_ASSIGNED/REVOKED`. Binding edificio come field post-roles DTO è fuori scope FASE 7.
- **`MainView.stopPollers()` ha solo `AdminRequestsView.onLeave()`**: altre viste non hanno poller attivi (REFRESHers sono manuali via bottone "Aggiorna"). Una eventuale auto-refresh di `TournamentsView` per segnalare nuovi iscritti sarebbe un `Timeline` analogo — non implementato in S5 per evitare over-polling; l'utente preme "Aggiorna tornei" manualmente.
- **`PlatformAdminDashboard.deleteTournament()`** l'`ApiClient.delete()` ritorna `CompletableFuture<Void>` — il server in realtà ritorna un `AdminRequestDto(PENDING)` body. S5 discarding il body per via del `delete` tipato Void; il polling `AdminRequestsView` successivo (via `onNavigateToRequests.run()`) rivela il nuovo `AdminRequestDto`. Da sistemare in un follow-up se Serve un overload `delete(path, Class<T>)` nell'`ApiClient`.
- **`TournamentDetailDto` = single endpoint aggregato ma `TournamentsView` ristianente le `standalone` `standings|matches|participants` sub-endpoint**: il client chiama principalmente `flow.getTournament(id)` (1 endpoint che ritorna tutte le 3 liste aggregate). Le chiamate isolate a `flow.getStandings(id)`/`flow.getMatches(id)`/`flow.getParticipants(id)` sono disponibili nel `PlayerTournamentFlow` ma non usate dalle viste S5 (ridondanti). Per la coerenza con la spec §7.C riga 738 "classifica `GET /{id}/standings`, bracket `GET /{id}/matches`, partecipanti `GET /{id}/participants`" — le view S5 li riceve dal dettaglio aggregato ma la specificationazione delle risorse è ben nota e navigabile dal flow service `PlayerTournamentFlow`.

### 20.6 Follow-up

- **S6 — cross-cutting test di contratto**: il residue S6 close-out (§21.4) non è impacted da S5 (S6 varit solo central-system test files). Il follow-up FASE 8 può chiudere ` EventTypeContractTest` / `ReplicationEventTypeContractTest` come già fatto in S6.
- **FASE 8 — `theme.css` + i18n**: extracting CSS inStyleSheet file + `ResourceBundle` per label internazionalizzate. Modulo: `game-client-emulator/src/main/resources/theme.css` + `i18n/client.properties` — non bloccante in FASE 7.
- **FASE 8 — `ErrorPane` global routing**: aggiungere un caso `fatal-error` nel `MainView.navigateTo` che swap ad un pane con `ErrorPane.show(title, msg, retryCallback)` quando l'`ApiClient.exceptionally` ritorna un `ServerUnavailableException` persistente (pi di 3 retry).
- **FASE 8 — `RoleAssignmentRequestDto` valutazione**: se serve una wrapper class con metadata (actingAdminUserId, reason string, auditTimestamp) per auditing, immigrarla in shared-dto e migrare il `PlatformAdminUserController` signature (breaking change!).
- **FASE 8 — `GET /api/admin/local/games` alignment**: allineare il piano riga 744 (bug typo) o aggiungere il `@GetMapping("/games")` endpoint al `AdminLocalController` (returns `List<GameStateDto>` games積 del building) per essere esplicitamente distinto da `/devices` caso in cui in futuro la azimuth venga ampliata (es. catalogo "logico" di definizioni locali allowed nel building ≠ dispositivi fisici).
- **FASE 8 — `PlayerTournamentFlow.refreshIfStale()`**: auto-refresh di `listTournaments()` quando il `StalenessBadge` diventa giallo (> 5 min) — verificare l'UX; può essere confusionario per l'utente se la lista si rimescola mentre sta selezionando un torneo.

---

*Aggiornamento §20 (batch S5) — FASE 7-C Client Emulator GUI implementato. Build `mvn -q -pl :game-client-emulator -am clean compile` → BUILD SUCCESS.*

## 21. FASE 7-D — Cross-cutting + EventTypeContractTest + ReplicationEventTypeContractTest (batch S6)

### 21.1 Decisioni

1. **Chiusura gap S3-A `EventTypeContractTest`**: la veste S3-A del `SyncEventProcessor` aveva aggiunto 8 branch `*_REQUESTED` (9 con `PARTICIPANT_REGISTER_REQUESTED`) con un `// TODO ONDATA-2` che ne differiva l'aggiunta al set `EXPECTED_EVENT_TYPES` difeso dal test, perché i producers Local non erano ancora emessi nel batch S3 Central. S4 ha emesso i 8 literal Local-side (W6/W9/W10/W12 use case); S6 chiude il gap aggiungendoli al set (15 totali: 6 baseline + 9 FASE 7) e rimuovendo il TODO. Verifica strutturale bidirezionale preservata (ogni literal è gestito Central-side ed emesso Local-side).
2. **`ReplicationEventTypeContractTest` nuovo (gap S1 §16.7 A5)**: speculare al sibling `EventTypeContractTest`, ma per la direzione opposta (Central-emitted → drained). Pinna ogni literal drained dal `UserReplicationSchedulerService.isReplicationEvent` (10: `USER_REGISTERED`/`USER_UPDATED`/`LOCAL_ADMIN_BUILDING_ASSIGNED`/`LOCAL_ADMIN_BUILDING_REVOKED`/`GAME_DEFINITION_UPSERTED`/`TOURNAMENT_MATCH_SCHEDULED`/`TOURNAMENT_SUMMARY_UPSERTED`/`TOURNAMENT_STANDINGS_UPSERTED`/`TOURNAMENT_PARTICIPANTS_UPSERTED`/`LOCAL_SERVER_REGISTRY_UPSERTED`) al (a) literal nel source `UserReplicationSchedulerService.java` AND (b) literal in almeno uno degli 8 producer Central (`UserService`/`LocalAdminBuildingService`/`GameDefinitionService`/`TournamentService`/`TournamentStandingsService`/`TournamentRegistrationService`/`TournamentMatchOutboxAdapter`/`LocalServerRepositoryAdapter`). `LateRegistrationCatchUpService` escluso dalla lista producer perché è un re-drain consumer (contiene tutti i 10 literal come drain filter, non come emittente).
3. **Regression `mvn verify` scelta strategia**: eseguito `mvn verify -DskipITs -Dfailsafe.skip=true -pl :central-system,:local-server -am` per limitare il scope §7.D riga 768 ("shared + central + local") ed evitare rompere il build su `e2e-tests` (5 failure pre-esistenti: ruolo legacy `USER`→`PLAYER` in `B13`/`B16`; doppio insert `replication_progress` in `B2`/`B5`/`B9`). Le modifiche S6 sono puro file-scan test (`EventTypeContractTest` + `ReplicationEventTypeContractTest`) e non toccano runtime: le failure e2e sono preesistenti (verificato confrontando git status: 0 modifiche di S6 su file impattati). `-DskipITs` non era necessario (i test e2e sono surefire `*Test.java` non `*IT.java`) ma è stato mantenuto come salvaguardia preventiva.

### 21.2 Matrice file (creati/estesi in S6)

| File | Tipo | Nota |
|------|------|------|
| `central-system/src/test/java/com/gameplatform/central/application/service/EventTypeContractTest.java` | ESTESO | +8 literal in `EXPECTED_EVENT_TYPES` (15 totali: 6 baseline + 9 FASE 7). TODO S3-A rimosso. Documentazione javadoc arricchita con sezione "FASE 7 §7.B W6/W9/W10/W12". |
| `central-system/src/test/java/com/gameplatform/central/application/service/ReplicationEventTypeContractTest.java` | NUOVO | 2 test methods: `everyReplicationEventTypeIsDrainedByScheduler` + `everyReplicationEventTypeIsEmittedByCentralProducer`. 10 event types + 8 producer files. Speculare a `EventTypeContractTest`. |
| `documenti/PIANO_UTENTI_TORNEI.md` | ESTESO | §7.D righe 766-770 tickate `[x]` con note implementative S6. |
| `documenti/REQUIREMENTS.md` | ESTESO | RI-03 endpoint Local +24 righe (viste PLAYER/dashboards/write async + 3 internal sync). §6.1 matrice +12 righe (RF-UT-02/RF-TO-03/04 update async + 9 nuovi RF-Fase7-*). |
| `documenti/IMPLEMENTATION.md` | ESTESO | §13 FASE 7 nuovo (viste PLAYER/3 dashboard admin/`ApiClient`/navbar dinamica/`admin_requests_local` flow/`AdminRequestTimeoutService`/test di contratto). Indice aggiornato. |
| `workflow/workflow.md` | ESTESO | §8 FASE 7 nuovo con checkbox tickate (`[x]`) per §8.A.1/A.2/A.3 (S1-S3 Central), §8.B.1/B.2/B.3 (S4 Local), §8.C (S5 client), §8.D (S6 cross-cutting). |
| `workflow/architettura_classi.md` | ESTESO | §21 (questa sezione). Cross-riferimento finale aggiornato a S1+S2+S3+S4+S5+S6. |

### 21.3 Contract surface

- `EventTypeContractTest` copertura: 15/15 literal Local-emitted → branch `SyncEventProcessor.processEvent`. Pre-S6: 6/6 baseline (5 FASE 0-2 + 1 FASE 6 `TOURNAMENT_MATCH_COMPLETED`) più TODO. Post-S6: 15/15.

- `ReplicationEventTypeContractTest` copertura: 10/10 literal Central-emitted drained → 8 producer Central. Direzioni di verifica: (a) ogni literal dichiarato come costante `*_EVENT` in `UserReplicationSchedulerService` (drain filter), (b) ogni literal emesso come literal stringa in almeno uno degli 8 producer Central.

- Totali test Central post-S6: 345/0/0/0 (343 baseline + 2 `ReplicationEventTypeContractTest`). Local invariato: 798/0/0/0.

### 21.4 Backward-compat

- Nessuna rottura: i due test sono puro file-scan via `Files.walk` + regex `Pattern.quote` (no Reflection, no ArchUnit, no DB). Aggiungere un nuovo literal a una delle due liste richiede solo che il literal sia effettivamente emesso/drenato da qualche parte del source — API binary compat invariata.
- `EventTypeContractTest.EXPECTED_EVENT_TYPES` è una `Set.of(...)` immutabile — un update futuro deve solo aggiungere il literal al set ed il test forzerà la presenza del branch SyncEventProcessor + del producer Local. Aggiungere un literal senza producer/branch → fail del test con messaggio esplicito (`missing` TreeSet).
- `ReplicationEventTypeContractTest.PRODUCER_FILES` è una `List.of(...)` immutabile. Aggiungere un producer (`*OutboxAdapter`/`*Service`) deve solo aggiungere il file name alla lista; omettere un producer existing non rompe il test fintantoche il literal è emesso altrove nella lista.
- Backward-compat dei 5 baseline Local-emitted (`USER_REGISTERED`/`RESERVATION_CREATED`/`RESERVATION_CANCELLED`/`GAME_SESSION_COMPLETED`/`GAME_SESSION_ABORTED`) e di `TOURNAMENT_MATCH_COMPLETED`: preservata invariata (nessun cambiamento ai 6 literal originali, solo +8 append).

### 21.5 Limiti noti

- **`EventTypeContractTest` se esteso richiede che i Local emettano effettivamente i literal**: la direzione bidirezionale del test vincola ogni literal ad essere presente in `local-server/src/main/java` come literal string. S6 ha verificato tutti gli 8 tramite grep (29 match in 18 file Local, di cui 8 sono dichiarazioni `static final String EVENT_TYPE = "..._REQUESTED"`). Un futuro branch `SyncEventProcessor` aggiunto senza producer Local romperà `everyExpectedEventTypeIsEmittedByLocalServer` — questo è il comportamento atteso (guardia architetturale).
- **`ReplicationEventTypeContractTest` non distingue producer outbox da re-emission inline**: grep stringa generoso. `LateRegistrationCatchUpService` è escluso dalla lista producer per evitare falsi positivi (contiene tutti i 10 literal come drain filter). Futuri re-drain consumer dovranno essere esclusi manualmente dalla `PRODUCER_FILES` (o il test dovrà essere esteso con una denylist esplicita).
- **Modulo `e2e-tests` out-of-scope §7.D riga 768**: il piano dichiara il target regression solo "shared + central + local". `e2e-tests` presenta 5 failure pre-esistenti su 28 test (legacy role `USER`→`PLAYER`, double `replication_progress`) — non causate da S6. Follow-up FASE 8: allineare i test e2e alla migrazione RBAC 4 ruoli e al drain raddoppiato (probabile causa: `@Scheduled` `replicateUsers` runner nel contesto IT e deadline H2 state).
- **S5 formalizzato in `architettura_classi.md` §20**: il batch S5 (§7.C Client Emulator GUI) è stato formalizzato retroattivamente in §20 di questo documento (vedi soprastante): 18 nuovi file + 5 file estesi, build `mvn -q -pl :game-client-emulator -am clean compile` verde. Nessuna regressione test (modulo senza test directory, conforme al piano §7.B riga 763 "nessun test automatico UI").

### 21.6 Follow-up

- **FASE 8 — Docs, smoke test, requirements** (PIANO riga 780-782): estendere il `README.md` "Smoke test" con scenario torneo end-to-end; estendere `e2e-tests` con uno smoke torneo (simile a `MultiBuildingEndToEndIT`). Risolvere le 5 failure e2e soprastanti.
- **`team_members_local` table** (vedi §19.7): per chiudere il gap `PlayerTournamentController.myMatches` team membership. Implementazione deferred a FASE 8+.
- **Poison events back-channel `ADMIN_REQUEST_FAILED`** (limiti noti §7.D (f)): opzionale, non bloccante in FASE 7. Considerare in FASE 8 se la UX del timeout 60s standard risulta troppo penalizzante.
- **Mutua autenticazione/firma outbox** (limiti noti §7.D (b)): criptograficamente firmare i payload `*_REQUESTED` per evitare che un Local compromesso auto-elargisca `PLATFORM_ADMIN`. Follow-up FASE 8+.

---

*Aggiornamento §21 (batch S6) — FASE 7-D cross-cutting + EventTypeContractTest + ReplicationEventTypeContractTest completato.*
*Fine `architettura_classi.md`.*
*Cross-riferimenti: `documenti/PIANO_UTENTI_TORNEI.md` FASE 0 + FASE 1 + FASE 2 + FASE 3 + FASE 4 + FASE 5 + FASE 6 + FASE 7 §7.A.1-A3 (batch S1+S3 Central) + §7.A.2-completa (batch S2 Central→Local) + §7.B-completo (batch S4 Local residue) + §7.C (batch S5 client emulator) + §7.D (batch S6 cross-cutting + test contratto); `documenti/REQUIREMENTS.md` §6.1 matrice RF-AU-05/RF-UT-LA-01..04/RF-UT-GA-01..03/RF-UT-PL-01..02/RF-TO-01..12 + RF-UT-02 (FASE 7 update async) + RF-Fase7-DA1..DA3/AR/PLAYER/NAV/CLIENT/COMP/ADM/CONTRACT; `documenti/IMPLEMENTATION.md` §13 FASE 7 (viste PLAYER/dashboard admin/ApiClient/navbar/admin_requests_local/timeout/contratti); `workflow/analisi/problemi_noti.md`.*