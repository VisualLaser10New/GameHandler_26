# PERF_ANALYSIS.md — Prove di assenza bottleneck, deadlock, desincronizzazione

Documento di analisi (FASE 4 del piano `workflow/analisi/risoluzione_comunicazioni_local_central.md`).

## 1. Deadlock

**Modello di concorrenza**: scheduler single-threaded (`@Scheduled` default `TaskScheduler` 1 thread). `SyncReceiverService.receiveSyncPayload` non è più `@Transactional` (B7); ogni evento è processato in una nuova tx REQUIRES_NEW via `SyncEventProcessor.processOne`. Il lock pessimistico `aggregated_statistics` è preso per `(building, gameType, period)` dentro `findByBuildingAndTypeAndPeriodWithLock`.

**Ordine di acquisizione**: il batch riceve eventi nell'ordine di `OutboxEvent.createdAt ASC` (vedi `OutboxEventJpaRepository.findByStatusOrderByCreatedAtAsc`). Per batch concorrenti sullo stesso building, i lock sono acquisibili nello stesso ordine deterministico (per `gameType`+`period` come appaiono nel batch). Poiché ogni evento è ora in una tx separata (REQUIRES_NEW), il lock è rilasciato prima del prossimo evento — non c'è cross-event lock trattenuto. L'heartbeat `localServerRegistryPort.updateLastSeenAt` è fuori dal loop REQUIRES_NEW e prende un lock su `local_servers` (tabella diversa) — non c'è lock cross-tabella annidato.

**Conclusione**: no deadlock possibile. Validato da `SyncReceiverConcurrencyStressTest` (10 thread, 100 task concorrenti, stesso eventId — nessun hang, nessun batch abort, completamento < 15s).

## 2. Bottleneck / rallentamenti

| Bottleneck | Fix | Test |
|---|---|---|
| `LocalServerRestAdapter.pushUsers` retry con `Thread.sleep` bloccante | B14: rimosso, sostituito con `RetryTemplate` programmatico (backoff 100ms × 2.0, max 3 tentativi) | `LocalServerRestAdapterRetryTest` (503→200, 400-no-retry, 503×3-fail) |
| Outbox loop N read-modify-write | B5: bulk UPDATE JPQL singolo statement (`markAsSentBatch`, `incrementRetryBatch`) | `BugL05_SyncSchedulerNonAtomicMarkAsSentTest` + `OutboxEventBulkUpdateAtomicityIT` (1000 eventi → 1 query, atomicità tx) |
| `receiveSyncPayload` lock pessimistico tenuto per tutto il batch | B7: refactoring per-evento REQUIRES_NEW → lock tenuto ~1 evento, non tutto il batch | `SyncReceiverConcurrencyStressTest` |
| Outbox growth: SENT mai eliminati | `OutboxPurgeService` (@Scheduled daily, delete SENT older than 7gg, configurable via `app.outbox-purge-retention-days`) | `OutboxPurgeServiceTest` |
| Outbox FAILED senza DLQ | B13: `OutboxDlqPromotionService` (@Scheduled, sposta FAILED → `outbox_dead_letter` + delete) | `OutboxDlqPromotionTest` |

**Throughput target documentato**: 50 eventi/batch ogni 5 min (default `app.sync-interval-ms=300000`) = 600 eventi/h. Carico reale: 1 edificio, ~100 sessioni/giorno ≈ 4 eventi/h. Margine ~150×.

## 3. Desincronizzazione local ↔ central

### 3.1 Exactly-once sul central (inbound)
- Retry local invia stesso `eventId` (`OutboxEvent.id` stabile, `OutboxEventMapper` preserva id).
- Central deduplica via `processed_events(eventId)` PK + check `existsByEventId` pre-process + catch `DataIntegrityViolationException` sul save (race condition) in `SyncEventProcessor.processOne`.
- **Test**: `SyncIdempotencyEndToEndTest` (sync 2× con stesso eventId → `statisticsRepository.save` chiamato 1×).

### 3.2 Exactly-once downstream (central → local replication)
- `replication_progress(eventId, serverId)` PK composta → exactly-once per server.
- Mark SENT solo quando tutti i server attivi hanno ACK.

### 3.3 Late-registration catch-up (gap trovato e fixato)
- **Gap**: un nuovo server registrato DOPO che un evento `USER_REGISTERED/USER_UPDATED` era stato marcato SENT (perché al tempo la lista attivi era vuota) non riceverà mai quell'utente via lo scheduler periodico (che guarda solo PENDING).
- **Fix (FASE 4 step 3)**: `LateRegistrationCatchUpService.catchUpNewlyRegisteredServer(server)` invocato da `LocalServerRepositoryAdapter.register()` SOLO su nuove registrazioni (`isNewRegistration = existing.isEmpty()`). REQUIRES_NEW tx. Replay di tutti gli outbox SENT `USER_REGISTERED/USER_UPDATED` non presenti in `replication_progress` per il nuovo server. Push best-effort (fallimento loggato e ingoiato — la registazione non deve fallire; il dedup lato local via upsert `PUT /internal/users/sync` rende sicuro un replay successivo).
- **Test**: `LateRegistrationCatchUpServiceTest` (3 casi: push non-replicati, no-op quando vuoto, swallow push failure).

## 4. Test di prova (sintesi)

| Test | Prova |
|---|---|
| `SyncReceiverConcurrencyStressTest` | no-deadlock, no-hang, no-batch-abort (10 thread, 100 task, stesso eventId) |
| `SyncIdempotencyEndToEndTest` | exactly-once inbound (retry 2× → stats 1×) |
| `OutboxEventBulkUpdateAtomicityIT` | atomicità bulk UPDATE + efficienza 1000 eventi |
| `OutboxPurgeServiceTest` | no-growth (purge SENT > 7gg) |
| `OutboxDlqPromotionTest` | no-orphan FAILED (promozione a DLQ) |
| `LateRegistrationCatchUpServiceTest` | no-desync su late registration |

## 5. Avvio reale ( Docker )

**Non eseguito in questa iterazione** — richiede Docker daemon attivo. Procedura manuale documentata in `gamehandler-platform/README.md` (sezione "Smoke test Docker").
