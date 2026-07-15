Ho tutto ciò che serve. Eccovi il piano completo e ingegnerizzato. (Modalità piano: nessun file sarà toccato finché non darete via libera.)

---

# Piano di risoluzione bug e completamento della comunicazione local-server ⟷ central-system

## 0. Decisioni di progettazione (congelate dal vostro input)

- **D1 — Auto-registrazione local**: nuova porta outbound `RegisterLocalServerPort` + adapter REST che chiama `POST /internal/servers/register` con retry/backoff allo startup. Il central resta passivo (la sua `register()` diventa idempotente/upsert).
- **D2 — Sessioni abortite**: nuovo `eventType = "GAME_SESSION_ABORTED"` emesso dal local (varianti TIMEOUT e SERVER_RESTART); il central aggiunge branch dedicato e contatore `abortedSessions` separato. `GAME_SESSION_COMPLETED` resta riservato agli `status=COMPLETED`.
- **D3 — `UserSyncDto`**: lasciato inalterato (email/createdAt non propagati).
- **D4 — Test E2E**: `@SpringBootTest` con H2 in-memory + WireMock per simulare REST e un broker MQTT mock (Paho `MemoryPersistence` + `MqttClient` su broker embedded `Moquette` o `MqttAsyncClient` con `IMqttMessageListener` in-process). Niente Docker in CI.

---

## 1. Catalogo a priori dei messaggi scambiati (contratto congelato)

Questa tabella è il contratto su cui si basano tutti i fix e i test. Per ogni messaggio: produttore, consumatore, campi, esito matching.

### 1.1 local → central (outbox events, trasportati in `OutboxEventDto.payload` come JSON string dentro `SyncPayloadDto`)

| # | eventType | Produttore (file:line) | Payload campi | Consumatore central | Note / fix |
|---|---|---|---|---|---|
| M1 | `USER_REGISTERED` | `LocalSignupService.java:103-128` | `UserRegisteredEventDto{userId,username,email,hashedPassword,roles,createdAt}` | `SyncReceiverService.java:151-154` → `UserService.registerFromSync` | OK, allineato |
| M2 | `RESERVATION_CREATED` | `ReservationService.java:87-108` | `{eventId,occurredAt,reservationId,gameId,userId,buildingId,gameType}` | `SyncReceiverService.java:131-141` | Consumer usa solo `gameType,occurredAt`. Mantenuto ma aggiungerò log di campi ignoti (audit) |
| M3 | `RESERVATION_CANCELLED` | `ReservationService.java:156-178` | stessi 7 campi | `SyncReceiverService.java:143-150` | come M2 |
| M4 | `GAME_SESSION_COMPLETED` | `GameSessionService.java:224-252` | `{eventId,occurredAt,sessionId,gameType,durationSeconds,status="COMPLETED",resultJson?}` | `SyncReceiverService.java:117-129` | Ridotto a `gameType,occurredAt,durationSeconds`. **Fix**: branch accetta solo `status=COMPLETED` altrimenti devia a M5 |
| M5 | `GAME_SESSION_ABORTED` (**NUOVO**) | `HealthCheckService.java:125-146` e `SessionRecoveryHelper.java:79-100` | `{eventId,occurredAt,sessionId,gameType,durationSeconds,status="ABORTED",stopReason}` | nuovo branch `SyncReceiverService` | **Nuovo contratto**; il central conta in `abortedSessions` e NON in `totalSessions` |

### 1.2 central → local

| # | Canale | Produttore central | Body | Consumatore local | Note / fix |
|---|---|---|---|---|---|
| M6 | `PUT /internal/users/sync` | `UserReplicationSchedulerService.java:73` → `LocalServerRestAdapter.java:45` | `List<UserSyncDto>` | `InternalSyncController.java:20` → `UserSyncService.java:24` | Invariato (D3) |
| M7 | **`POST /internal/servers/register`** (chiamato dal local) | nuovo `RegisterLocalServerAdapter` lato local; riceve `SyncController.java:65` lato central | `RegisterServerRequest{buildingId,baseUrl}` | `SyncController.java:66` → `LocalServerRegistryPort.register` | **Nuovo flusso di bootstrap** (D1). `register` diventa upsert idempotente |
| M8 | `GET /internal/sync/receive` (probe reachability) | `CentralSystemRestAdapter.java:63` | nessuno (header) | central risponde 405 by design | OK, nessun fix funzionale |

### 1.3 Verifica coperturaProduttore→Consumatore
Per ciascun M1–M8 il piano prevede un **test di contratto parametrizzato** che esegue: serializzazione lato produttore → trasporto sul wire (WireMock cattura body+header) → deserializzazione lato consumatore → asserzione che i campi chiave siano presenti e processati. Esito atteso: ogni messaggio prodotto ha un consumatore attivo e gestisce i campi minimi.

---

## 2. Fasi di esecuzione

### FASE 0 — Baseline e congelamento contratto (no fix)
1. Eseguire `mvn -q -pl central-server,local-server,shared/shared-dto,shared/shared-domain,shared/shared-mqtt -am test` per ottenere lo stato attuale (pass/fail) dei 95 test esistenti. Riportare la baseline in un file `TEST_BASELINE.md` (in `documenti/`). Si esegue dopo lo sfreeze del codice (punto di partenza).
2. Eseguire `mvn -pl central-system,local-server -am clean package -DskipTests` per verificare che entrambi i moduli compilino e impacchettino senza errori.
3. Verificare l'avvio: `docker-compose up -d central-db local-db-1 mqtt-broker-1`, poi `mvn spring-boot:run -pl central-system` e `mvn spring-boot:run -pl local-server`. Registrare su log l'attuale **comportamento di crash/no-crash** (in particolare: il local-server attualmente crasha all'avvio se `INTERNAL_API_KEY` non è in env — vedi B2).

### FASE 1 — Sblocco della pipeline (bug bloccanti)
Obiettivo: dopo questa fase il flusso bidirezionale local↔central è vivo end-to-end.

**B1 — Auto-registrazione del local-server (D1)**
- Creare la porta `domain/ports/out/RegisterLocalServerPort.java` con metodo `boolean register()`.
- Creare adapter REST `infrastructure/adapters/out/rest/RegisterLocalServerAdapter.java` che fa `POST {centralSystemUrl}/internal/servers/register` con body `RegisterServerRequest{buildingId="${app.building-id}", baseUrl="${app.local-base-url}}"` (nuova property `app.local-base-url`) e header `X-Internal-Api-Key`. Restituisce `true` se 2xx.
- Creare servizio applicativo `application/service/LocalServerRegistrationService.java` (use case `RegisterLocalServerUseCase`) con retry/backoff esponenziale jitterato (1s, 2s, 4s, max ~30s) finché `isReachable()` è true e `register()` ha successo; messo in `SmartLifecycle.start()` con fase alta (dopo MqttConfig e scheduler). Logga ogni tentativo.
- Lato central: rendere `LocalServerRepositoryAdapter.register()` (`:37-48`) **idempotente**: `findById(buildingId).orElse(new Registered...JpaEntity(...))`, setta `baseUrl/lastSeenAt/active=true`, `save`. Aggiungere `@Transactional`. Aggiornare `init.sql` central: la riga `building-1` non è più pre-seed (ora si auto-registra). Mantengo un seed opzionale dietro profile `dev`.
- Test: `RegisterLocalServerAdapterWireMockTest` (WireMock stub `POST /internal/servers/register` 201), `LocalServerRegistrationServiceTest` (verifica retry finché successo), `LocalServerRegistryIdempotentRegisterTest` (seconda chiamata register non duplica).

**B2 — `internal.api-key` mancante nel local-server**
- `local-server/.../application.yml`: aggiungere `internal.api-key: ${INTERNAL_API_KEY:secret}` alla sezione `internal`. (Default `secret` allineato al central.) Oppure dare default inline in `CentralSystemRestAdapter.java:35` e `InternalApiKeyFilter` (`@Value("${internal.api-key:secret}")`). Scelta ingegneristica: property nell'yml + default nell'annotation per difesa in profondità.
- Test: `CentralSystemRestAdapterTest` già esistente — estendere con caso "property assente".

**B3 — Rimozione della classe stub morta**
- Eliminare `central-system/.../infrastructure/adapters/out/registry/LocalServerRegistryAdapter.java` (3 righe vuote). Verificare con grep che non sia referenziata. Pulizia architetturale.

**Verifica FASE 1**
- Eseguire `mvn test -pl central-system,local-server` (tutti i test esistenti + nuovi).
- Eseguire avvio reale (FASE 0.3) → log di entrambi i sistemi NON crashano; il log del local mostra "Registration succeeded"; il log del central mostra una nuova riga in `local_servers` con `building-1` e `base_url=https://local-server-1:8081`.
- Verifica DB: `SELECT * FROM central_db.local_servers;` → riga building-1 presente con `is_active=1`.

### FASE 2 — Bug di correttezza e contratto
**B4 — `GAME_SESSION_ABORTED` (D2)**
- Local: in `HealthCheckService.java:139` e `SessionRecoveryHelper.java:93` cambiare `eventType` da `"GAME_SESSION_COMPLETED"` a `"GAME_SESSION_ABORTED"`. Aggiungere `stopReason` sempre presente (`"TIMEOUT"` / `"SERVER_RESTART"`).
- Local: in `GameSessionService.java:245` mantenere `"GAME_SESSION_COMPLETED"` e aggiungere `status="COMPLETED"` (già presente).
- Central: in `SyncReceiverService.processEvent` aggiungere branch `else if ("GAME_SESSION_ABORTED".equals(...))` che chiama nuovo `updateAbortedStats(buildingId, gameType, period)` (incrementa `abortedSessions` in `AggregatedStatistics.data` o nuova colonna `total_aborted_sessions`). NON incrementa `totalSessions`.
- Estendere `AggregatedStatistics` con campo `totalAbortedSessions` (dominio + entità JPA + `init.sql` central `aggregated_statistics` + mapper). Per Clean Architecture il campo nasce nel dominio (`AggregatedStatistics.java`) e viene propagato all'entità.
- Test: nuovo `GameSessionAbortedSyncTest` (local emette ABORTED → central conta in abortedSessions, non in totalSessions). Modificare i test esistenti `SyncReceiverServiceTest` per coprire il nuovo branch.

**B5 — BugL05 atomicità/efficienza outbox**
- Sostituire il loop read-modify-write in `OutboxSyncHelper.java:19-33` con **bulk UPDATE** JPQL: aggiungere a `OutboxEventRepository` i metodi `markAsSentBatch(ids)` e `incrementRetryBatch(ids)` mappati su `@Modifying @Query("UPDATE OutboxEventJpaEntity e SET e.status='SENT', e.sentAt=:now WHERE e.id IN :ids AND e.status='PENDING'")`. Singola statement, una transazione, atomico ed efficiente.
- Aggiungere `@Version` o `@Lock(PESSIMISTIC_WRITE)` opzionale solo se servisse concorrenza (per ora no, ma documentato).
- Test: rafforzare `BugL05_SyncSchedulerNonAtomicMarkAsSentTest` con un test _di integrazione_ su H2: fallire artificialmente metà del batch e verificare che o tutto è committed o tutto rolled back (bulk UPDATE atomica). Aggiungere test di efficienza: 1000 eventi → 1 sola query.

**B6 — Scheduler hardcoded che ignora config**
- `SyncSchedulerService.java:33` → `@Scheduled(fixedRateString = "${app.sync-interval-ms:300000}")`.
- `HealthCheckService.java:93` → `@Scheduled(fixedRateString = "${app.healthcheck-interval-ms:300000}")`.
- **Migliore**: cambiare da `fixedRate` a `fixedDelayString` per evitare overlap se un run supera l'intervallo (vedi FASE 4/F3). Scelta: `fixedDelayString` su sync (più sicuro, no overlap), `fixedRateString` su healthcheck (deve ticcare).
- Test: `SchedulerConfigTest` esteso per verificare che `SYNC_INTERVAL_MS=10000` cambi la cadenza (mock del `TaskScheduler`).

**B7 — `SyncReceiverService`: transazione per-evento + poison isolation (root cause di C-01, BUG-SYNC-01, BUG-REPL-01, BUG-01)**
- **Root cause**: `receiveSyncPayload` è `@Transactional` (`SyncReceiverService.java:78`) con tutto il batch in una tx; un'eccezione dentro `processEvent` che esce dalla tx ne causa il rollback completo (C-01) e il test BUG-SYNC-01 documenta il poisoning. L'attuale catch (`:99-107`) marca processed ma non garantisce consistenza della Persistence Context dopo alcuni JPA errors.
- **Fix radicale**: estrarre `processEvent` + `processedEventRepository.save` in un **collaboratore separato** `SyncEventProcessor` con metodo `@Transactional(propagation = REQUIRES_NEW) public boolean processOne(BuildingId, OutboxEventDto)`. Il `receiveSyncPayload` resta `@Transactional` solo per l'heartbeat finale; itera chiamando il collaboratore tramite proxy (bean separato → REQUIRES_NEW funziona). Ogni evento commit/rollback indipendente → poison isolato, no batch abort, no double-mark.
- `registerFromSync` già `REQUIRES_NEW` (`UserService.java:75`) — coerente.
- Idempotenza: `processed_events` con PK `eventId` + **unique constraint** (`init.sql` central) + catch `DataIntegrityViolationException` → deduplica anche sotto concorrenza.
- Test: `SyncReceiverBatchPoisoningBugTest` (esistente) e `SyncReceiverServiceBugTest` (C-01) devono PASSARE davvero con il nuovo design; aggiungere `SyncReceiverConcurrencyTest` (due thread, stesso eventId, verify exactly-once stats).

**B8 — `extractDuration` a 0 silenzioso**
- `SyncReceiverService.java:208`: quando nessuna delle 3 chiavi è trovata, `log.warn("Event {} missing durationSeconds, assuming 0", eventId)` (non cambia il return).
- Test: `SyncReceiverServiceTest` caso "payload senza duration" → verifica warn log (LogCaptor) e stats con durata 0.

**B9 — Branch default silenzioso**
- `SyncReceiverService.java:157-158`: sostituire `return true` silenzioso con `log.warn("Unknown eventType '{}' from building {} — marking processed", eventType, buildingId)` + `return true`. Aggiungere un tipo "UNKNOWN" (no-op) con audit.
- Test: `SyncReceiverUnknownEventTypeTest`.

**B10 — C-02 ruoli updateUser**
- `central/.../UserService.updateUser`: sostituire merge additivo con `user.replaceRoles(newRoles)`. Aggiungere metodo `replaceRoles` al modello `central.User`.
- Test: `UserServiceBugTest` (esistente) deve PASSARE.

**B11 — BUG-AUTH-01 / BugL09 expiresAt vs exp JWT**
- Central `AuthService`: calcolare `expiresAt` dal `exp` claim del JWT emesso (non dal Clock separato).
- Local `LocalAuthService`: idem. Approccio radicale: `JwtTokenProvider` restituisce sia il token sia l'`Instant exp`; il service usa quello, eliminando la doppia fonte di verità.
- Test: `AuthServiceJwtExpirationBugTest` e `BugL09_LocalAuthTokenExpiryMismatchTest` devono PASSARE.

**B12 — Verifica e fix di BugL01–L04, L06–L08** (domain/service bugs già coperti da regression test)
- Eseguire i test (FASE 0 baseline) e leggere il codice di produzione relativo:
    - L01 `HealthCheckService.java:108` (`missed >= 3` + reset) — verificare già fixato.
    - L02 `GameSessionService.start` validazione `reservation.getUserId()` ∈ participants.
    - L03 walk-in su RESERVED rifiutato.
    - L04 `Reservation.confirm()` con state guard.
    - L06 `Reservation.expire()` accetta PENDING e CONFIRMED.
    - L07 `findExpired` query `status IN (PENDING, CONFIRMED)`.
    - L08 `StartGameSession` accetta CONFIRMED.
- Per ognuno non fixato, applicare patch minima nel dominio. Questi bug NON bloccano la comunicazione ma il piano completo li include.

**Verifica FASE 2**
- `mvn test` (tutti i 95+ test + i nuovi) → tutti verdi.
- Test di contratto parametrizzato M1–M8 tutti verdi.

### FASE 3 — Resilienza / robustessa / prestazionali (no bottleneck/deadlock/desync)

**B13 — Outbox FAILED senza DLQ**
- Aggiungere tabella `outbox_dead_letter` (local) + porta `DeadLetterRepository`; `OutboxEvent.incrementRetry` quando raggiunge 10 → sposta riga in DLQ (copia + motivo) e cancella da outbox. Scheduler nuovo `DlqMonitorService` (@Scheduled, logga count). Questo toglie il growth illimitato di `outbox_events` PENDING→FAILED.
- Test: `OutboxDlqPromotionTest`.

**B14 — LocalServerRestAdapter retry bloccante/hardcoded**
- Estrarre timeout in properties `central.replication.connect-timeout-ms:5000`, `read-timeout-ms:5000`.
- Sostituire `Thread.sleep` bloccante con **`@Retryable`/`RetryTemplate` di Spring Retry** (backoff esponenziale jitterato, max 3). Alternativa più leggera: `ScheduledExecutor` per ritentare async → non blocca il thread scheduler.
- Test: `LocalServerRestAdapterRetryTest` con WireMock (503→200).

**B15 — JWT fallback key silenzioso (central)**
- `JwtTokenProvider.init`: se `private.pem` non caricabile, **fail-fast** (lancia eccezione che bloquea l'avvio) invece di generare chiave effimera.
- Test: `JwtTokenProviderTest` caso "file mancante" → aspetta IllegalStateException.

**B16 — Exception swallow MQTT (fuori dal scope local↔central?):**
- Il piano è focalizzato su local↔central. MqttPublisherAdapter non blocca la comm central→local. Lo elenco come **follow-up facoltativo** (out-of-scope per questa iterazione): aggiungere un `mqtt_events_outbox` per publish QoS1 persi. Non necessario per il flusso central↔local. Lo segnalo esplicitamente.

**B17 — Schema alignment (DB)**
- `init.sql` central: aggiungere `LOBBY` all'ENUM `game_catalog.status`; aggiungere colonna `total_aborted_sessions INT DEFAULT 0` a `aggregated_statistics`; aggiungere `unique (event_id)` a `processed_events`; aggiungere `retry_count` a `outbox_events` central; allineare `payload` a `JSON`.
- `init.sql` local: allineare `users` UNIQUE su username (lasciare email non-unique oppure gestire — D3 dice no cambio UserSyncDto, quindi lascio).
- `ddl-auto: validate` ovunque (local già `validate`, central era `update`) + migrazioni `init.sql` autorevoli.
- Test: `SchemaAlignmentTest` (H2 con init.sql di entrambi → verificare colonne attese).

**B18 — devcontainer forwardPorts**
- `.devcontainer/devcontainer.json:21`: aggiungere `1883` e `3307`.

**B19 — Doc / script**
- Aggiornare `gamehandler-platform/README.md` con il nuovo flusso di auto-registrazione e `app.local-base-url`. Aggiungere `AGENTS.md` con comandi `mvn test`, `mvn package`, `docker-compose up -d`. (Solo se richiesto esplicitamente dal repo.)

### FASE 4 — Prova di assenza bottleneck, deadlock, desincronizzazione

Documento di analisi (in `documenti/PERF_ANALYSIS.md`):

1. **Deadlock**: scheduler single-threaded (`@Scheduled` default `TaskScheduler` 1 thread). `receiveSyncPayload` prende lock pessimistico `aggregated_statistics` per `(building,gameType,period)` in ordine **deterministico** (ordine degli eventi nel batch). Per garantire no-deadlock su concorrenza cross-batch, verificare che l'ordine di acquisizione dei lock sia **coerente** (per building+gameType+period ordinato). Azione: in `updateSessionStats`/`updateReservationStats` ordinare i lock per chiave. Prova: due batch concorrenti → lock nella stessa chiave (lock singolo) → no deadlock (al più serializzazione). Nessun lock cross-tabella annidato (heartbeat dopo il loop, no lock su `local_servers` contemporaneo a `aggregated_statistics`). **Conclusione: no deadlock possibile.** Test `SyncReceiverConcurrencyStressTest` (10 thread, 1000 eventi, stesso building) → asserisce stato finale coerente e no timeout/lock contention > 5s.

2. **Bottleneck / rallentamenti**:
    - `LocalServerRestAdapter` retry con `Thread.sleep` bloccante (B14) → rimosso.
    - Outbox loop N read-modify-write (B5) → bulk UPDATE 1 query.
    - `receiveSyncPayload` lock pessimistico tenuto per tutto il batch (B7) → ora lock per-evento (REQUIRES_NEW) → lock tenuto ~1 evento, non tutto il batch.
    - Outbox growth: SENT mai eliminati → aggiungo `OutboxPurgeService` (@Scheduled daily, delete SENT older than 7gg). Evita growth illimitato. Test di efficienza: 100k righe SENT → purge < 2s.
    - Throughput target documentato: 50 eventi/batch ogni 5 min = 600/h, ben oltre il carico reale (1 edificio, ~100 sessioni/giorno).

3. **Desincronizzazione local↔central**:
    - Retry local invia stesso `eventId` (verificato: `OutboxEvent.id` stabile, `OutboxEventMapper` preserva id) → central deduplica via `processed_events(eventId)` PK + unique constraint (B7) → exactly-once anche con ritardi/retry. Test: `SyncIdempotencyEndToEndTest` (local retry 3 volte → central conta 1).
    - Replication downstream: `replication_progress(eventId,serverId)` PK composta (verificato) → exactly-once per server. Mark SENT solo quando tutti i server hanno ACK. Se un server si registra dopo che l'evento è stato SENT... **Gap trovato**: se un nuovo server si registra DOPO che un evento utente è stato marcato SENT (perché al tempo la lista attivi era vuota), quell'utente non verrà mai replicato al nuovo server. **Fix**: nel `register()` dell'adapter central, dopo upsert, replicare in catch-up tutti gli outbox `USER_REGISTERED/USER_UPDATED` `SENT` non presenti in `replication_progress` per il nuovo server. Test: `LateRegistrationCatchUpTest`.

4. **Duplicato `SyncReceiverServiceBugTest` omonimi in due package** (segnalato): consolidare i due file in uno solo (package `application.service`). Pulizia.

**Verifica FASE 3+4**
- `mvn verify` → tutti verdi, inclusi test di concorrenza/stress.
- Esecuzione reale `docker-compose up -d --build` per 10 min → osservare log: nessun errore, outbox si svuota, statistiche popolate, replica utenti avviene.

### FASE 5 — Test automatici strutturati

Creazione di un nuovo source set di test di integrazione `src/test/java/.../integration/`:

1. **`ContractTestBase`** con WireMock per central e local entrambi in `@SpringBootTest` con profilo `test` (H2 + MQTT mock).
2. **Test messaggi parametrizzati M1..M8** (`MessageContractIT`): per ogni tipo messaggio, il test serializza dal lato produttore, invia via REST su WireMock, il lato consumatore processa, si asserisce su DB H2.
3. **`EndToEndSimulationIT`** (simulazione virtuale passo-passo): una singola classe ordina i passi:
    1. Avvia central (WireMock per outbound) + local in `@SpringBootTest`.
    2. Local si auto-registra (M7) → asserisce riga in `local_servers`.
    3. Local crea sessione di gioco → end → outbox `GAME_SESSION_COMPLETED` → scheduler (trigger manuale via `awaitility` o `capture` nel test) → POST sync (M4) → central conta `totalSessions=1`.
    4. Simula timeout heartbeat → outbox `GAME_SESSION_ABORTED` (M5) → central conta `abortedSessions=1, totalSessions=1`.
    5. Local signup → outbox `USER_REGISTERED` (M1) → central ha utente.
    6. Central `UserService.register` → scheduler replica → PUT (M6) → local ha utente replicato.
    7. Verifica idempotenza: ritarda il sync ( WireMock ritarda) → retry → central deduplica → mismos count.
    8. Verifica poison: inietta evento malformato → batch non abortisce (B7).
4. **`SmokeDockerTest`** (manuale, documentato in README): `docker-compose up -d --build` + script `curl` che esercita il flusso; NOPMD.

Questi test sostituiscono la verifica "manuale" oggi inesistente.

### FASE 6 — Verifica finale: simulazione, root cause, patch radicale

Loop chiuso (per ogni bug residuo emerso):

1. **Analisi messaggi inviabili + gestione codice** → tabella M1–M8 (sezione 1) completata e validata da ContractTest.
2. **Simulazione virtuale passo-passo** via `EndToEndSimulationIT` + avvio reale docker.
3. **Trovare problematiche residue** → ogni fail del test produce un ticket.
4. **Root cause analysis** per ogni ticket: tracciare la catena fino al bug primario. Per ciascuno, domandare: "questo stesso bug è presente in altre sezioni?" — esempi:
    - Se `expiresAt != exp` (B11) è dovuto a doppia fonte di verità Clock vs JWT → cercare tutti i `Instant.now(clock)` in AuthService/JwtTokenProvider e unificare. Stesso pattern in central e local: fix unico in `JwtTokenProvider`.
    - Se il batch poisoning (B7) è dovuto a `@Transactional` su tutto il loop → cercare altri `@Transactional` con loop interno (candidates: `UserReplicationSchedulerService.replicateUsers`, `OutboxSyncHelper`) e applicare lo stesso refactor per-evento.
    - Se l'assenza di auto-registrazione (B1) lascia `updateLastSeenAt` no-op → verificare che altri comandi downstream (se aggiunti in futuro) non cadano nello stesso buco: aggiungere un test che fallisce se `getActiveLocalServers()` è vuoto quando il local è avviato.
5. **Patch permanente alla radice**: per ogni root cause applicare la patch che elimina la classe di bug (non solo l'istanza), con regress test che "red" prima della patch e "green" dopo. Esempi di patch radicali:
    - Aggiungere un **invariante architetturale** test (`ArchitectureTest` via ArchUnit) che vieta classe stubvuote in `adapters/out/` e vieta `@Transactional` su metodi con loop di chiamate a porte esterne.
    - Aggiungere invariante: ogni `eventType` litterale nel local DEVE comparire come branch nello `switch` del central → test `EventTypeContractTest` che enumera i letterali via reflection.
6. **Re-run completo** di `mvn verify` + `docker-compose up -d --build` + osservazione 15 min:
    - Entrambi i sistemi NON crashano.
    - Outbox locale si svuota (PENDING → SENT).
    - `central_db.aggregated_statistics` popolata con `totalSessions`/`abortedSessions`/`totalReservations` corretti.
    - `central_db.local_servers` contiene il local con `last_seen_at` recente.
    - `central_db.users` replicato in `local_db.users`.
    - Log senza ERROR/WARN inattesi.

### 6.1 Criteri di accettazione finali
- Tutti i 95 test esistenti + i nuovi (~25) verdi.
- Avvio senza crash di entrambi i sistemi (locale e docker).
- Flusso M1, M4, M5, M6, M7 dimostrato end-to-end via `EndToEndSimulationIT` e via curl su docker.
- Prova documentata di no-deadlock (test stress), no-bottleneck (bulk UPDATE + retry non bloccante + purge), no-desync (idempotency + late-registration catch-up).

---

## 3. Mappa sintetica bug → fix → test

| ID | File | Fix | Test |
|---|---|---|---|
| B1 | nuovo `RegisterLocalServerPort`+adapter; `LocalServerRepositoryAdapter.register` upsert | auto-registrazione allo startup | `RegisterLocalServerAdapterWireMockTest`, `LocalServerRegistrationServiceTest`, `LateRegistrationCatchUpTest` |
| B2 | `application.yml` local + `@Value` default | `internal.api-key` default | `CentralSystemRestAdapterTest` |
| B3 | elimina `LocalServerRegistryAdapter.java` | rimozione stub | `ArchitectureTest` (ArchUnit) |
| B4 | `HealthCheckService.java:139`/`SessionRecoveryHelper.java:93` + `SyncReceiverService` + `AggregatedStatistics` | `GAME_SESSION_ABORTED` | `GameSessionAbortedSyncTest` |
| B5 | `OutboxSyncHelper`+repo bulk UPDATE | atomicità/efficienza | `BugL05...` (rafforzato) |
| B6 | `SyncSchedulerService.java:33`/`HealthCheckService.java:93` | config-driven | `SchedulerConfigTest` |
| B7 | nuovo `SyncEventProcessor` REQUIRES_NEW + unique `processed_events` | per-event tx, poison isolation | `SyncReceiverBatchPoisoningBugTest`, `SyncReceiverServiceBugTest`, `SyncReceiverConcurrencyTest` |
| B8 | `SyncReceiverService.java:208` log.warn | duration silenzioso | `SyncReceiverServiceTest` |
| B9 | `SyncReceiverService.java:157` log.warn | branch default | `SyncReceiverUnknownEventTypeTest` |
| B10 | `UserService.updateUser` replaceRoles | C-02 | `UserServiceBugTest` |
| B11 | `JwtTokenProvider`/`AuthService`/`LocalAuthService` | exp unica fonte | `AuthServiceJwtExpirationBugTest`, `BugL09...` |
| B12 | domini/service L01–L08 | patch minime | test esistenti |
| B13 | `OutboxDlqPromotionService` + tabella DLQ | no FAILED orfani | `OutboxDlqPromotionTest` |
| B14 | `LocalServerRestAdapter` Spring Retry/timeout config | no Thread.sleep | `LocalServerRestAdapterRetryTest` |
| B15 | `JwtTokenProvider.init` fail-fast | no key effimera | `JwtTokenProviderTest` |
| B17 | `init.sql` allineati | schema | `SchemaAlignmentTest` |
| B18 | `devcontainer.json` | porte | — |
| — | consolidamento `SyncReceiverServiceBugTest` | pulizia | — |
| — | `OutboxPurgeService` | no growth | `OutboxPurgeTest` |
| — | `ArchitectureTest` (ArchUnit) | invarianti | — |
| — | `EventTypeContractTest` | contratto eventType | — |
| — | `EndToEndSimulationIT` | simulazione E2E | — |

---

## 4. Note ingegneristiche e limiti

- **ArchUnit** non è una dipendenza attuale: aggiungerla (test scope) al `pom.xml` parent per gli invarianti (FASE 6). Alternativa senza dipendenza: test di reflection manuale (`EventTypeContractTest`).
- **Spring Retry** non è dipendenza attuale: aggiungere `spring-retry` + `spring-aspects` (test/main scope) per B14. Alternativa: implementare retry con `ScheduledExecutor` (no nuova dipendenza). Scelta consigliata: `spring-retry` (Standard, dichiarativo).
- **Moquette** (broker embedded) per test: dipendenza test `io.moquette:moquette-broker`. Alternativa pura: non testare MQTT in E2E (mockare `IMqttClient` con `MemoryPersistence` + listener in-process). Scelta consigliata: mock in-process (no broker reale) per isolare il scope local↔central.
- H2: aggiungere dipendenza test `com.h2database:h2` + profilo `test` con `spring.datasource.url=jdbc:h2:mem:...;MODE=MySQL`. H2 in modalità MySQL per compatibilità `JSON`/`ENUM`.
- **Schema divergence `LOBBY`** (D-level): aggiungo `LOBBY` al central (B17) → allineamento safe.
- I bug domain L01–L08 (B12) esulano strettamente dalla comunicazione ma sono inclusi per "piano completo"; se volete possono essere separati in un sub-piano.

---

## 5. Ordine di esecuzione consigliato

1. FASE 0 (baseline + compilazione + avvio osservato).
2. FASE 1 (B1, B2, B3) → sblocca pipeline.
3. FASE 2 (B4–B12) → correttezza.
4. FASE 3 (B13–B18) → resilienza/perf.
5. FASE 4 → prove no-deadlock/perf (in parallelo con FASE 3).
6. FASE 5 → test integration E2E.
7. FASE 6 → simulazione finale + root cause loop + patch radicali + invarianti ArchUnit.

Ogni fase termina con `mvn test` verde + avvio reale senza crash prima di passare alla successiva.

---

Questo è il piano. Volete che proceda con l'esecuzione a partire dalla FASE 0 (baseline + verifica stato attuale), oppure volete rivedere/aggiustare qualcheDecisione o priorità?