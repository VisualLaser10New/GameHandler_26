# Problemi noti

### POF-3: Outbox Unbounded Growth — Disk Exhaustion
**Stato:** **RISOLTO (lato Local Server); ANCORA PRESENTE (lato Central System)**
**Analisi:** 
Né `architettura_classi.md` né `architettura proposta.md` implementano o documentano un meccanismo di pulizia (cleanup), TTL (Time-to-Live) o archiviazione per la tabella `outbox_events`.
- Gli eventi `FAILED` rimangono nel database per sempre.
- Gli eventi `SENT` rimangono nel database per sempre. 
- Il database MySQL del Local Server è destinato a saturare lo spazio su disco. 

**Cosa è stato fatto (Local Server):**
- `OutboxPurgeService` — job `@Scheduled` che elimina gli eventi `SENT` più vecchi della retention configurata da `app.outbox-purge-retention-days` (default 7 giorni).
- `OutboxDlqPromotionService` — job `@Scheduled` che promuove gli eventi `FAILED` nella tabella `outbox_dead_letter` (rimuovendoli da `outbox_events`), impedendo l'accumulo indefinito di record falliti.

**Rischio residuo (Central System):**
- Sul Central System non esiste (ancora) un servizio di purge/DLQ equivalente: la tabella centrale `outbox_events` continua a crescere senza limite per gli eventi `SENT`. La risoluzione attuale riguarda esclusivamente il lato Local Server.

---

### POF-5: Concurrent Game State Corruption — Race Condition MQTT/REST
**Stato:** **RISOLTO**
**Analisi:** 
Non c'è traccia di meccanismi di concorrenza per prevenire la race condition tra chiamate REST e messaggi MQTT sulla stessa macchina da gioco.
- I documenti menzionano `@Transactional`, ma questo garantisce solo l'atomicità dell'Outbox Pattern (salvataggio db + evento outbox), **non blocca le modifiche concorrenti**.
- Non esiste alcuna annotazione `@Version` (Optimistic Locking) sulla `GameJpaEntity` o `ReservationJpaEntity`.
- Non ci sono riferimenti a lock pessimistici (`PESSIMISTIC_WRITE` / `SELECT ... FOR UPDATE`) nei Repository Adapter.

**Cosa è stato fatto:**
- Aggiunta di `@Version` (colonna `version BIGINT NOT NULL DEFAULT 0`) su `GameJpaEntity` e `ReservationJpaEntity`.
- Gli adapter out (`GameRepositoryAdapter`, `ReservationRepositoryAdapter`) usano `saveAndFlush` e traducono `OptimisticLockingFailureException` nella nuova eccezione di dominio `com.gameplatform.local.domain.exception.ConcurrentStateException` (catturata e rigenerata dentro l'adapter stesso).
- Lato REST: il `GlobalExceptionHandler` mappa `ConcurrentStateException` -> **409 Conflict**.
- Lato MQTT: `GameSessionListener` e `GameStateListener` catturano `ConcurrentStateException`, loggano e fanno ack (drop senza retry) per evitare loops bloccanti sul broker.

**Rischio residuo:**
- `GameSessionJpaEntity` è stata lasciata intenzionalmente **senza** `@Version`: in caso di `end()` concorrente sulla stessa sessione resta il rischio (accettato e documentato) di un doppio `GAME_SESSION_COMPLETED`. Il coverage delle race MQTT/REST su `game_catalog` e `reservations` è invece garantito dai nuovi test `BugL10`/`BugL11`/`BugL12`, dai guard test `GameRepositoryAdapterOptimisticLockGuardTest` / `ReservationRepositoryAdapterOptimisticLockGuardTest` e dall'e2e `B17ConcurrentGameMachineStartOptimisticLockTest`.

---

### POF-7: Sync Starvation — Outbox Ordering Deadlock
**Stato:** **RISOLTO**
**Analisi:** 
Il `SyncSchedulerService` (Sez. 7.2) legge gli eventi `PENDING` e riprova per 10 volte. Tuttavia:
- **Nessun Fail-Safe descritto:** Se l'invio del primo record lancia un'eccezione non catturata correttamente (o va in timeout bloccante lungo), il ciclo si interrompe e il resto degli eventi non viene processato, fermando l'intero sync per 5 minuti.
- **Nessuna Paginazione:** Viene fatta una query che "legge tutti i record con status = 'PENDING'". Una coda outbox bloccata che cresce porterebbe la query a estrarre migliaia di record in un colpo solo, saturando la memoria (OOM Exception). Non è menzionato né un limite (`LIMIT 100`) né l'uso di code separate (Dead-Letter Queue).

**Cosa è stato fatto:**
- Riscritura del `SyncSchedulerService` come ibrido Option-C: lettura limitata via `outboxEventRepository.findPendingLimit(batchSize)` con `app.outbox.batch-size` (default 50) al posto dell'illimitato `findPending()`.
- In caso di **successo** del trasporto del batch -> `markAsSentBatch` atomico (preserva il contratto del `BugL05`).
- In caso di **fallimento** del trasporto -> retry per-event con isolamento del poison: per-event `markAsSent(id)` / `incrementRetry(id)`, `try/catch` per singolo evento e `continue`, così un evento poison non blocca il resto del batch.
- Dopo 10 retry l'evento va in `FAILED` e viene promosso in `outbox_dead_letter` dal servizio esistente `OutboxDlqPromotionService`.
- Aggiunte le nuove port methods `findPendingLimit(int)` e `markAsFailed(String)`, e un indice composito `idx_outbox_status_created_at (status, created_at)` su `outbox_events`.
- Nuovi test: `BugL07_SyncStarvationPoisonIsolationTest`; `SyncSchedulerServiceTest` + `BugL05` aggiornati al nuovo contratto.

---

### Riepilogo Prossimi Interventi
Per completare l'architettura, mancano le integrazioni architetturali per i seguenti punti:
1. **Per POF-3 (risolto lato Local; residuo Central):** lato Local Server completato (`OutboxPurgeService` + `OutboxDlqPromotionService`). Resta da portare sul Central System un servizio di purge/DLQ equivalente, dove la tabella `outbox_events` SENT continua a crescere senza limite.
2. **Per POF-5 (risolto; residuo GameSession):** `@Version` su `GameJpaEntity`/`ReservationJpaEntity` + `ConcurrentStateException` + 409 REST + ack-and-drop MQTT implementati. Residuo accettato: `GameSessionJpaEntity` lasciata senza `@Version` (doppio `GAME_SESSION_COMPLETED` su `end()` concorrente).
3. **Per POF-7 (risolto):** `SyncSchedulerService` riscritto con `findPendingLimit(batchSize)`, isolamento del poison event per-event, `markAsSentBatch` atomico sul successo, promozione DLQ via `OutboxDlqPromotionService`, indice composito `idx_outbox_status_created_at`. Nessuna azione residua.
