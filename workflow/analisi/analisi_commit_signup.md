# Analisi del commit `e0ff0ca` — "add signup"

> Data analisi: 2026-07-02
> Repository: `gamehandler-platform` (Maven multi-modulo, Spring Boot 3.2, Java 21)
> Commit analizzato: `e0ff0ca7bb4fe5a10b52b1a3f7c592bdba730e78` — *add signup*

---

## 1. Riepilogo esecutivo

Il commit introduce la **registrazione utente (signup)** distribuita su tre componenti della piattaforma:

1. **Local Server** — endpoint `POST /api/auth/signup` con salvataggio utente locale, hash BCrypt e scrittura di un evento outbox `USER_REGISTERED`.
2. **Central System** — ricezione dell'evento `USER_REGISTERED` durante la sincronizzazione e creazione di un utente centrale replicato (idempotente).
3. **Game Client Emulator** — view JavaFX `SignupView` con form di registrazione e navigazione login ↔ signup.

L'analisi riga-per-riga, con simulazioni virtuali e 34 nuovi test, conferma la correttezza del flusso principale e dell'Outbox Pattern, ma **scopre un bug concorrenziale reale** (BUG-L1), un'**inconsistenza tra mapper** (INCONSISTENCY-L4), un **pitfall transazionale** (BUG-C7) e diversi **casi limite** di validazione (EDGE-L5/L6/L7). Tutti i 512 test (esistenti + nuovi) passano.

> **Aggiornamento post-fix (2026-07-02)**: tutti i bug identificati sono stati corretttati tramite 3 subagent paralleli e verificati con la suite di test completa (512 test, 0 fallimenti). Vedi §10 per il riepilogo delle correzioni applicate.

---

## 2. File modificati nel commit

### 2.1 Nuovi file di produzione
| File | Ruolo |
|------|-------|
| `local-server/.../application/service/LocalSignupService.java` | Use case di registrazione locale |
| `local-server/.../domain/model/LocalSignupUser.java` | Aggregate root della registrazione |
| `local-server/.../domain/exception/UserAlreadyExistsException.java` | Eccezione di dominio (conflitto) |
| `local-server/.../domain/ports/in/RegisterLocalUserUseCase.java` | Port in (driving) |
| `local-server/.../domain/ports/out/LocalSignupUserRepository.java` | Port out (driven) |
| `local-server/.../adapters/out/mysql/adapter/LocalSignupUserRepositoryAdapter.java` | Adapter DB |
| `local-server/.../adapters/out/mysql/entity/LocalUserJpaEntity.java` | Entità JPA su tabella `users` |
| `local-server/.../adapters/out/mysql/mapper/LocalUserMapper.java` | Mapper dominio↔JPA |
| `local-server/.../adapters/out/mysql/repository/LocalUserJpaRepository.java` | Spring Data repo |
| `central-system/.../domain/ports/in/RegisterUserFromSyncUseCase.java` | Port in per sync |
| `shared-dto/.../dto/SignupRequestDto.java` | DTO richiesta |
| `shared-dto/.../dto/SignupResponseDto.java` | DTO risposta |
| `shared-dto/.../dto/UserRegisteredEventDto.java` | DTO evento outbox |
| `game-client-emulator/.../ui/SignupView.java` | View JavaFX |

### 2.2 File modificati
| File | Natura della modifica |
|------|----------------------|
| `local-server/.../adapters/in/rest/AuthController.java` | Aggiunto endpoint `/signup` + dipendenza `RegisterLocalUserUseCase` |
| `local-server/.../adapters/in/rest/GlobalExceptionHandler.java` | Aggiunto handler `UserAlreadyExistsException → 409` |
| `local-server/.../adapters/out/mysql/adapter/UserRepositoryAdapter.java` | Fallback `findByUsername` su `LocalUserJpaRepository` |
| `local-server/.../adapters/out/mysql/mapper/UserMapper.java` | Aggiunto `toDomainFromLocalUser(LocalUserJpaEntity)` |
| `central-system/.../application/service/SyncReceiverService.java` | Branch `USER_REGISTERED` → `registerFromSync` |
| `central-system/.../application/service/UserService.java` | Implementa `RegisterUserFromSyncUseCase.registerFromSync` |
| `game-client-emulator/.../ui/LoginView.java` | Link di navigazione verso signup |
| `game-client-emulator/.../ui/MainView.java` | Stato view `signup` + wiring callback |
| `infrastructure/mysql-local/init.sql` | Tabella `users` con unique su `username` ed `email` |
| `local-server/.../config/TlsConfig.java` | Default `${...}` per trust-store (robustezza test) |

### 2.3 File di test nuovi/modificati
`SyncReceiverServiceUserRegistrationTest`, `UserServiceFromSyncTest`, `LocalSignupServiceTest`, `LocalSignupUserTest`, `UserRepositoryAdapterTest`, `AuthControllerTest` (+ modifiche minori a test di compatibilità esistenti).

---

## 3. Architettura e flusso end-to-end

La piattaforma segue un'architettura **esagonale (Ports & Adapters)** con pattern **Outbox + Sync pull-based** per la replica dati.

```
[SignupView JavaFX] --HTTP--> [LocalServer AuthController]
                                  │
                                  ▼
                         [LocalSignupService]  (use case, @Transactional)
                          │        │            ├─ existsByUsername/Email
                          │        │            ├─ BCrypt.hashpw
                          │        │            ├─ repo.save(LocalSignupUser)
                          │        │            └─ outbox.save(USER_REGISTERED)  ← atomico
                          ▼        ▼
                   [LocalSignupUserRepository]  [OutboxEventRepository]
                          │
                          ▼
                   tabella `users` (unique username/email)

            ... sincronizzazione periodica (pull) ...

[Central SyncReceiverService.receiveSyncPayload]
        ├─ existsByEventId (dedup idempotente)
        ├─ processEvent: USER_REGISTERED → objectMapper.readValue(UserRegisteredEventDto)
        │       └─ registerUserFromSyncUseCase.registerFromSync(dto)
        │                └─ [UserService.registerFromSync]
        │                       ├─ findById / findByUsername / findByEmail (dedup)
        │                       └─ userRepository.save(User)
        └─ processedEventRepository.save(ProcessedEvent)
```

### Catene di chiamate principali

**Registrazione locale** (`AuthController.signup` → `LocalSignupService.register`):
1. `register(username, password, email)` valida i tre parametri (null/blank → `IllegalArgumentException`).
2. `existsByUsername` → poi `existsByEmail` → `UserAlreadyExistsException` se conflitto.
3. `BCrypt.hashpw(password, BCrypt.gensalt())` genera hash con salt casuale.
4. `new UserId(UUID.randomUUID().toString())`, ruoli `["USER"]`, `Instant.now(clock)`.
5. `LocalSignupUser` (costruttore con invarianti) → `repo.save`.
6. `createUserRegisteredOutboxEvent` serializza `UserRegisteredEventDto` e persiste `OutboxEvent` PENDING.
7. Controller mappa l'eccezione di dominio → HTTP status via `GlobalExceptionHandler`.

**Replica centrale** (`SyncReceiverService` → `UserService.registerFromSync`):
1. Dedup per `eventId`, parsing JSON, branch `USER_REGISTERED`.
2. `registerFromSync`: dedup per `userId` → `username` → `email` (skip silenzioso se esiste).
3. Salva l'utente centrale **riutilizzando l'hash già ricevuto** (nessun re-hash) e il `createdAt` originale.

---

## 4. Analisi dettagliata del codice (riga per riga)

### 4.1 `LocalSignupService` (107 righe)
- `@Service @Transactional`: la transazione abbraccia sia `save` utente sia `save` outbox → **atomicità outbox garantita** (se l'outbox fallisce, il salvataggio utente rolla back). Corretto.
- Validazione (righe 43-51): controlla null/blank di username/password/email **prima** di interrogare il repository. Ordine corretto.
- Race condition (riga 53-58 vs 74): il check `existsByUsername`/`existsByEmail` e il successivo `save` non sono atomici tra loro → **TOCTOU** (vedi BUG-L1).
- Hashing (riga 61): `BCrypt.hashpw` con `gensalt()` (salt casuale) → corretto; l'hash è ~60 char, compatibile con `VARCHAR(255)`.
- Outbox (righe 81-106): il `catch (Exception)` racchiude tutto in `RuntimeException`. Il messaggio dice "Failed to serialize" anche se l'errore fosse in `outboxEventRepository.save` (messaggio leggermente fuorviante, minore).
- `LocalSignupUser` passato a `save` con ruoli `List.of("USER")` (immutabile).

### 4.2 `LocalSignupUser` (65 righe)
- Costruttore con invarianti: userId, username, passwordHash, email, roles, createdAt tutti non-null/non-blank.
- `roles = List.copyOf(roles)` → lista immutabile; la copia rigetta elementi null con `NullPointerException` (coperto da test esistente).
- Nessun override di `equals`/`hashCode` → identità referenziale (documentato da test).

### 4.3 `LocalSignupUserRepositoryAdapter` (43 righe)
- `existsByUsername` (riga 26): controlla **entrambe** le sorgenti — `LocalUserJpaRepository` (utenti locali) **OR** `UserJpaRepository.findByUsername` (utenti replicati dal centrale). Previene collisioni username con utenti replicati. Corretto e importante.
- `existsByEmail` (riga 30-35): controlla **solo** `LocalUserJpaRepository` e ha un guard `null/blank → false`. **Asimmetrico** rispetto a `existsByUsername`: la tabella `replicated_users` non ha colonna `email` (vedi `init.sql` riga 79-85), quindi la cosa è giustificata dallo schema, ma è una **asimmetria di comportamento** da documentare (EDGE-L2).
- `save` (riga 38-42): mapping entity→save→domain, lineare e corretto.

### 4.4 `LocalUserMapper` (55 righe)
- `parseRoles` (riga 42-47): `roles.split(",")` **senza `trim`** → "USER, ADMIN" produce `["USER", " ADMIN"]` (BUG-L3). Il default per null/blank è `["USER"]`.
- `formatRoles` (riga 49-54): `String.join(",", roles)`; default "USER" per null/vuoto.
- **Cambio semantico al round-trip**: ruoli vuoti → DB "USER" → lettura `["USER"]`. Un utente con ruoli vuoti diventa `["USER"]` dopo persistenza (documentato).
- Il branch `roles == null` di `formatRoles` è **dead code** perché `LocalSignupUser` rifiuta ruoli null nel costruttore (codice difensivo irraggiungibile).

### 4.5 `UserMapper.toDomainFromLocalUser` (righe 46-61)
- Converte un `LocalUserJpaEntity` (che ha `email`) nel modello `User` replicato (che **non** ha `email`). L'email viene **droppata**: accettabile per il login (serve username+hash), ma va documented.
- Per ruoli blank/null ritorna `List.of()` (vuota), mentre `LocalUserMapper.parseRoles` ritorna `["USER"]` per lo stesso input → **INCONSISTENCY-L4**.
- Stesso problema di `trim` mancante (BUG-L3b).

### 4.6 `AuthController` (46 righe)
- Endpoint `/signup` (riga 36-45): delega a `registerLocalUserUseCase.register(...)`, mappa il risultato in `SignupResponseDto`, ritorna `201 Created`. Lineare e corretto.
- Nessuna validazione a livello controller: delega totalmente al servizio (coerente con l'architettura).

### 4.7 `GlobalExceptionHandler` (46 righe)
- `IllegalArgumentException → 400`, `UserAlreadyExistsException → 409`, `UserNotFoundException → 401`, `BadCredentialsException → 401`, catch-all `Exception → 500`. Mappature corrette e complete per i casi del signup.
- **Non** gestisce esplicitamente `DataIntegrityViolationException` → ricade nel catch-all `500` (causa di BUG-L1).

### 4.8 `UserService.registerFromSync` (righe 74-98, central)
- Dedup a tre livelli (`findById` → `findByUsername` → `findByEmail`) con skip silenzioso: **idempotente** e resiliente a sync ripetute. Corretto.
- Riutilizza `dto.hashedPassword()` (nessun re-hash) e `dto.createdAt()`: corretto per la replica.
- `catch (DataIntegrityViolationException)` (riga 95): gestisce la race condition concorrente **a livello di unit test**, ma in un vero contesto `@Transactional` l'eccezione marca la transazione come **rollback-only** → potenziale avvelenamento del batch (BUG-C7, vedi §5).

### 4.9 `SyncReceiverService` — branch `USER_REGISTERED` (riga 151-155)
- `objectMapper.readValue(payload, UserRegisteredEventDto.class)` poi `registerFromSync`.
- Il `try/catch` esterno (riga 99-107) isola eventi malformati/falliti e li marca comunque come processed → **resiliente al batch poisoning** (verificato da test).
- `processEvent` ritorna `true` → il caller salva il `ProcessedEvent`. Corretto.

### 4.10 `SignupView` (161 righe, client)
- Validazione client (riga 97): `username.isEmpty() || email.isEmpty() || password.isEmpty()` con `strip()` su username/email (ma **non** su password, corretto).
- Gestione stati HTTP: `201 → successo`, `409 → "already exists"`, altri → errore generico.
- Disabilita il pulsante durante la richiesta asincrona; aggiorna la UI sul JavaFX Application Thread. Corretto.
- **Inconsistenza** (EDGE-L5): la view fa `strip()` ma il `LocalSignupService` non fa `strip()` → " alice " verrebbe salvato con spazi se la chiamata bypassasse la view.

### 4.11 `init.sql` — tabella `users`
- `UNIQUE(username)` e `UNIQUE(email)`: i vincoli DB sono la **rete di sicurezza** per la race condition. `email` è nullable (più NULL ammessi in MySQL), ma il servizio richiede email non-blank, quindi non rilevante.

---

## 5. Simulazioni virtuali e bug scoperti

### BUG-L1 (alto) — Race condition non gestita nel signup locale — **FIXED**
**Simulazione**: due richieste `POST /api/auth/signup` con username `alice` arrivano concorrentemente. Entrambe eseguono `existsByUsername("alice") → false` prima che l'altra committi. La prima `save` ha successo; la seconda viola il vincolo `UNIQUE(username)` e `LocalSignupUserRepository.save` solleva `DataIntegrityViolationException`.

**Comportamento attuale (pre-fix)**: `LocalSignupService.register` **non** catturava `DataIntegrityViolationException` (a differenza di `UserService.register` centrale che lo cattura e lo converte in `UserAlreadyExistsException`). L'eccezione propagava al `GlobalExceptionHandler` catch-all → **HTTP 500** invece di **409 Conflict**.

**Fix applicato**: il `localSignupUserRepository.save(user)` è ora avvolto in `try/catch (DataIntegrityViolationException)` → `throw new UserAlreadyExistsException("User already exists: " + username, e)`. Aggiunto costruttore `(String, Throwable)` a `UserAlreadyExistsException`. Allineato al pattern di `UserService.register`.

**Test aggiornato**: `LocalSignupServiceEdgeCaseTest.shouldCatchDataIntegrityViolationOnConcurrentSave` (assertisce `UserAlreadyExistsException`).

### INCONSISTENCY-L4 (medio) — Default ruoli incoerente tra mapper — **FIXED**
**Simulazione**: si legge da DB un `LocalUserJpaEntity` con `roles = ""` (blank).
- Pre-fix: `LocalUserMapper.toDomain` → `parseRoles("")` ritornava `["USER"]`, ma `UserMapper.toDomainFromLocalUser` ritornava `List.of()` (vuota).

**Fix applicato**: `UserMapper.toDomainFromLocalUser` ora defaulta a `List.of("USER")` per ruoli null/blank, allineandosi a `LocalUserMapper.parseRoles`. Inoltre entrambi i mapper ora applicano `trim()` e filtrano elementi vuoti.

**Test aggiornato**: `UserMapperEdgeCaseTest.toDomainFromLocalUserReturnsUserForBlankRoles` e `toDomainFromLocalUserReturnsUserForNullRoles` (assertiscono `["USER"]`).

Nota: `UserMapper.toDomain` (per `UserJpaEntity`/utenti replicati) è lasciato intatto (default `List.of()`) per non rompere i test esistenti `UserMapperTest` e perché la tabella `replicated_users` ha semantiche diverse.

### EDGE-L5 (basso) — Nessun trimming nel servizio — **FIXED**
Pre-fix: `LocalSignupService` usava `isBlank()` ma non `strip()`: `" alice "` passava la validazione e veniva salvato con spazi. **Fix**: il servizio ora applica `strip()` a username ed email prima di qualsiasi altra operazione. Test: `shouldTrimUsername`.

### EDGE-L6 (basso) — Nessuna validazione del formato email — **FIXED**
Pre-fix: qualsiasi stringa non-blank era accettata come email (`"not-an-email"`). **Fix**: aggiunto `EMAIL_PATTERN = ^[^@\s]+@[^@\s]+\.[^@\s]+$` e validazione `IllegalArgumentException` per formati non validi. Test: `shouldRejectInvalidEmailFormat`.

### EDGE-L7 (basso) — Nessuna validazione di lunghezza — **FIXED**
Pre-fix: username > 100 char o email > 255 char passavano il servizio e fallivano solo a DB. **Fix**: aggiunti check `username.length() > 100` e `email.length() > 255` → `IllegalArgumentException`. Test: `shouldRejectOverlyLongUsername`.

### BUG-L3 (basso) — `parseRoles` senza trim — **FIXED**
Pre-fix: `roles.split(",")` senza `trim()` → "USER, ADMIN" produceva `["USER", " ADMIN"]`. **Fix**: `LocalUserMapper.parseRoles` e `UserMapper.toDomainFromLocalUser` ora usano `Arrays.stream(...).map(String::trim).filter(s -> !s.isEmpty()).toList()`. Test: `parseRolesTrimsWhitespace`, `toDomainFromLocalUserTrimsRoles`.

### EDGE-L2 (informativo) — Asimmetria existsByEmail
`existsByEmail` non consulta la tabella `replicated_users` (che non ha colonna email). Giustificato dallo schema, ma asimmetrico rispetto a `existsByUsername`. Test: `existsByEmailDoesNotCheckReplicatedUsers`.

### BUG-C7 (potenziale, medio) — Pitfall transazionale in `registerFromSync` — **FIXED**
In un vero contesto Spring/JPA, `catch (DataIntegrityViolationException)` dentro un metodo `@Transactional` (con propagazione `REQUIRED`) non annullava il mark rollback-only della transazione. Se una race condition faceva arrivare al `save` un duplicato, l'eccezione era catturata ma la transazione era condannata: il successivo `processedEventRepository.save` nel batch poteva fallire e **l'intero batch rollava back**, vanificando la promessa "one bad event never poisons the entire sync batch".

**Fix applicato**: `registerFromSync` ora usa `@Transactional(propagation = Propagation.REQUIRES_NEW)` → l'esecuzione avviene in una transazione separata. Se `DataIntegrityViolationException` occorre e viene catturata, solo la transazione interna è marcata rollback-only; il batch di sync esterno resta intatto. Non riproducibile con unit test puri (richiede Spring + DB reale), ma il cambiamento architetturale elimina il rischio.

### Casi verificati come corretti (simulazioni virtuali)
- **Outbox atomicity**: outbox save fallito → `RuntimeException` → rollback utente (`shouldPropagateWhenOutboxSaveFails`).
- **Password hash randomica**: stessi plaintext → hash diversi, `checkpw` corretto (`shouldProduceDifferentHashesForSamePassword`).
- **Nessun plaintext nell'outbox**: l'evento contiene solo `hashedPassword` (`shouldSerializeHashedPasswordAndUserIdInOutbox`).
- **Dedup idempotente centrale**: `findById → findByUsername → findByEmail` con skip silenzioso (`shouldCheckFindByIdFirst`, `shouldShortCircuitAtUsername`).
- **Resilienza batch**: evento `USER_REGISTERED` malformato o con `registerFromSync` che lancia → evento isolato e marcato processed (`malformedPayloadIsMarkedProcessed`, `registerFromSyncFailureIsCaught`).
- **Dedup intra-batch**: secondo evento duplicato nello stesso batch saltato (`duplicateWithinBatchSkipped`).
- **Mappature HTTP**: 400 per input invalido, 409 per conflitto, 500 per errore generico (`AuthControllerSignupEdgeCaseTest`).
- **Mapping adapter dual-source**: `existsByUsername` rileva utente replicato (`existsByUsernameDetectsReplicatedUser`).

---

## 6. Test creati (casi nascosti)

Sono stati aggiunti **7 classi di test** per **34 test** complessivi, nelle folder dei test dei moduli coinvolti. Seguono la convenzione del progetto (`@DisplayName`, `*EdgeCaseTest` / stile `BugTest` che assertisce il comportamento attuale per documentare il bug).

### Local Server
| File | N. test | Copertura |
|------|---------|-----------|
| `application/service/LocalSignupServiceEdgeCaseTest.java` | 10 | Ordine check, hash randomica, UUID, outbox atomicity, **BUG-L1**, EDGE-L5/L6/L7, payload outbox |
| `infrastructure/adapters/out/mysql/adapter/LocalSignupUserRepositoryAdapterEdgeCaseTest.java` | 7 | Dual-source username, short-circuit, EDGE-L2, guard email null/blank, save mapping |
| `infrastructure/adapters/out/mysql/mapper/LocalUserMapperEdgeCaseTest.java` | 7 | **BUG-L3** no-trim, default ruoli, round-trip semantico, null handling |
| `infrastructure/adapters/out/mysql/mapper/UserMapperEdgeCaseTest.java` | 6 | **INCONSISTENCY-L4**, BUG-L3b, drop email, null handling |
| `infrastructure/adapters/in/rest/AuthControllerSignupEdgeCaseTest.java` | 4 | null/missing fields → 400, RuntimeException → 500, blank → 400 |

### Central System
| File | N. test | Copertura |
|------|---------|-----------|
| `application/service/UserServiceFromSyncEdgeCaseTest.java` | 9 | Ordine dedup, preserva hash/createdAt, no outbox, EDGE-C1/C2/C3/C4 |
| `application/service/SyncReceiverServiceUserRegistrationEdgeCaseTest.java` | 5 | Payload malformato, registerFromSync che lancia, evento sconosciuto, dedup intra-batch, payload vuoto |

---

## 7. Risultati esecuzione test

Esecuzione (post-fix): `mvn -pl local-server,central-system -am test`

```
Tests run: 512, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

- **34 test di edge case** (7 classi): tutti passano, con i test dei bug aggiornati per assertire il comportamento corretto post-fix.
- **512 test totali** (esistenti + nuovi): tutti passanti, **nessuna regressione**.
- I test che originariamente documentavano i bug (BUG-L1, BUG-L3, INCONSISTENCY-L4, EDGE-L5/L6/L7) sono stati aggiornati con `@DisplayName` "FIXED" e ora asseriscono il comportamento corretto.

---

## 8. Raccomandazioni — stato post-fix

1. ~~**BUG-L1 (priorità alta)**: catturare `DataIntegrityViolationException` in `LocalSignupService.register`~~ — **FIXED**.
2. ~~**INCONSISTENCY-L4 (priorità media)**: uniformare il default dei ruoli blank/null tra `LocalUserMapper` e `UserMapper`~~ — **FIXED**.
3. ~~**BUG-L3 (priorità bassa)**: aggiungere `trim()` nel parsing dei ruoli~~ — **FIXED**.
4. ~~**EDGE-L6 (priorità bassa)**: aggiungere validazione del formato email~~ — **FIXED**.
5. ~~**EDGE-L5/L7 (priorità bassa)**: spostare la normalizzazione (`strip`) e i limiti di lunghezza nel servizio~~ — **FIXED**.
6. ~~**BUG-C7 (priorità media)**: considerare `REQUIRES_NEW` per `registerFromSync`~~ — **FIXED**.

---

## 9. Conclusioni

Il commit realizza correttamente la feature di signup con un'architettura pulita (esagonale + outbox), idempotenza nella replica centrale e resilienza al batch poisoning. Il flusso principale è solido e ben testato. L'analisi ha scoperto un bug concorrenziale reale (BUG-L1), un pitfall transazionale (BUG-C7), un'inconsistenza tra mapper (INCONSISTENCY-L4) e diversi casi limite di validazione — **tutti ora correttati e verificati**. La suite di 512 test passa senza regressioni.

---

## 10. Correzioni applicate (post-analisi)

Tutti i bug identificati nell'analisi sono stati corretti tramite 3 subagent paralleli, ciascuno responsabile di un gruppo di fix non confliggente (file disgiunti).

### 10.1 Subagent 1 — `LocalSignupService` (BUG-L1, EDGE-L5/L6/L7)

| File | Modifica |
|------|----------|
| `UserAlreadyExistsException.java` | Aggiunto costruttore `(String, Throwable)` |
| `LocalSignupService.java` | `strip()` su username/email (EDGE-L5); validazione lunghezza <=100/<=255 (EDGE-L7); validazione formato email con `EMAIL_PATTERN` (EDGE-L6); `try/catch(DataIntegrityViolationException)` su `save` → `UserAlreadyExistsException` (BUG-L1) |
| `LocalSignupServiceEdgeCaseTest.java` | 4 test aggiornati: `shouldCatch...` (BUG-L1), `shouldTrimUsername` (EDGE-L5), `shouldRejectInvalidEmailFormat` (EDGE-L6), `shouldRejectOverlyLongUsername` (EDGE-L7) |

### 10.2 Subagent 2 — Mapper (BUG-L3, INCONSISTENCY-L4)

| File | Modifica |
|------|----------|
| `LocalUserMapper.java` | `parseRoles`: `Arrays.stream(...).map(String::trim).filter(s -> !s.isEmpty()).toList()` |
| `UserMapper.java` | `toDomainFromLocalUser`: default `List.of("USER")` per null/blank (INCONSISTENCY-L4); aggiunto `trim()` + filter (BUG-L3b). `toDomain` lasciato intatto per non rompere `UserMapperTest` |
| `LocalUserMapperEdgeCaseTest.java` | `parseRolesTrimsWhitespace` (assertisce `["USER","ADMIN"]`) |
| `UserMapperEdgeCaseTest.java` | 3 test aggiornati: `toDomainFromLocalUserReturnsUserForBlankRoles`, `...ForNullRoles`, `...TrimsRoles` |

### 10.3 Subagent 3 — `UserService` (BUG-C7)

| File | Modifica |
|------|----------|
| `UserService.java` | `@Transactional(propagation = Propagation.REQUIRES_NEW)` su `registerFromSync` + import `Propagation` |

### 10.4 Verifica finale

```
mvn -pl local-server,central-system -am test
Tests run: 512, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Nessuna regressione: tutti i test esistenti (inclusi `UserMapperTest`, `LocalSignupServiceTest`, `AuthControllerTest`, `UserRepositoryAdapterTest`) continuano a passare insieme ai 34 test di edge case aggiornati.
