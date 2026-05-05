# Problemi Noti

### POF-3: 🟠 Outbox Unbounded Growth — Disk Exhaustion

**Condizione di fallimento**: Il Central System è irraggiungibile per giorni/settimane.

**Perché fallisce**:
- Ogni evento (prenotazione, sessione, statistica) crea un record in `outbox_events`.
- I record `PENDING` si accumulano senza limite.
- Dopo 10 retry, diventano `FAILED` ma **non vengono mai eliminati**.
- Nessun meccanismo di TTL, archiving, o cleanup.

**Propagazione**: `outbox_events` cresce → query `findByStatus('PENDING')` diventa lenta → il job `SyncSchedulerService` impiega più di 5 minuti → overlap tra esecuzioni successive → potenziale invio duplicato → eventualmente il disco si riempie → MySQL va in crash → **il Local Server è completamente offline**.

---

### POF-4: 🟠 Health Check Race Condition — Sessioni Terminate Erroneamente

**Condizione di fallimento**: Un client è in una partita attiva. Il network subisce un jitter di 10 secondi esattamente durante il ciclo di health check.

**Perché fallisce**:
- L'health check ha un timeout (non specificato, ma implicito).
- Se il heartbeat ACK arriva in ritardo rispetto al timeout, il `HealthCheckService`:
  1. Termina la sessione come `ABORTED`.
  2. Rilascia la macchina a `AVAILABLE`.
  3. Pubblica allarme.
- 2 secondi dopo, il client è di nuovo raggiungibile e continua a pubblicare aggiornamenti di gioco.

**Propagazione**: Sessione marcata `ABORTED` nel DB → client continua a giocare → client pubblica `session/end` con `GameResult` → `GameSessionListener` tenta di aggiornare una sessione già `ABORTED` → `InvalidGameStateTransitionException` → il risultato della partita è **perso** → `OutboxEvent` ha già inviato l'evento `ABORTED` al Central → dati inconsistenti.

---

### POF-5: 🟠 Concurrent Game State Corruption — Race Condition MQTT/REST

**Condizione di fallimento**: Un client pubblica `state = IN_USE` via MQTT mentre contemporaneamente un altro utente chiama `POST /api/reservations` via REST per lo stesso gioco.

**Perché fallisce**:
- Il `GameStateListener` (MQTT) e il `ReservationController` (REST) operano su **thread diversi**.
- Entrambi accedono a `GameRepository` e modificano `GameMachineStatus`.
- Senza lock pessimistico (`SELECT ... FOR UPDATE`) o versioning ottimistico (`@Version`), la sequenza:
  1. Thread MQTT legge `status = AVAILABLE`
  2. Thread REST legge `status = AVAILABLE`
  3. Thread REST scrive `status = RESERVED`
  4. Thread MQTT scrive `status = IN_USE`
  → La prenotazione è persa, la macchina è `IN_USE` senza prenotazione.

**Propagazione**: Stato macchina inconsistente → prenotazione "fantasma" nel DB (status `CONFIRMED` ma macchina `IN_USE` da altro utente) → conflitto irrisolvibile senza intervento manuale.

---

### POF-6: 🟡 Deserialization Failure — GameResult Corrotto

**Condizione di fallimento**: Un client con una versione più recente del software invia un `GameResult` con campi nuovi che il Local Server non conosce.

**Perché fallisce**:
- Il `GameSessionMapper` deserializza `result_data` con `objectMapper.readValue(json, GameResult.class)`.
- Se il JSON contiene un `"type"` non registrato in `@JsonSubTypes`, Jackson lancia `InvalidTypeIdException`.
- L'eccezione non è gestita nel mapper (metodi statici senza try-catch documentato).

**Propagazione**: `readValue()` → eccezione non catturata → la transazione di persistenza fallisce → rollback → la `GameSession` rimane `IN_PROGRESS` nel DB anche se la partita è finita → l'health check la termina come `ABORTED` → dati persi → l'outbox ha già generato un evento `SESSION_COMPLETED` → il Central riceve dati inconsistenti con lo stato effettivo.

---

### POF-7: 🟡 Sync Starvation — Outbox Ordering Deadlock

**Condizione di fallimento**: Il primo evento nella coda outbox (`ORDER BY created_at ASC`) fallisce ripetutamente (es. payload corrotto, Central lo rifiuta con 400).

**Perché fallisce**:
- Il `SyncSchedulerService` legge i record `PENDING` **ordinati per `created_at`**.
- Invia "per ciascuno" sequenzialmente.
- Se il primo evento fallisce ma non raggiunge i 10 retry, rimane `PENDING`.
- Al ciclo successivo, viene ripreso per primo.

**Propagazione**: Primo evento bloccante → nessun altro evento viene processato (se il servizio è implementato con logica sequenziale con fail-fast) → tutti gli eventi successivi sono bloccati → il Central non riceve più dati → le statistiche globali diventano stale → un singolo evento corrotto **paralizza l'intera pipeline di sincronizzazione**.
