# Problemi noti

### 🔴 POF-3: Outbox Unbounded Growth — Disk Exhaustion
**Stato:** **ANCORA PRESENTE**
**Analisi:** 
Né `architettura_classi.md` né `architettura proposta.md` implementano o documentano un meccanismo di pulizia (cleanup), TTL (Time-to-Live) o archiviazione per la tabella `outbox_events`.
- Gli eventi `FAILED` rimangono nel database per sempre.
- Gli eventi `SENT` rimangono nel database per sempre.
- Il database MySQL del Local Server è destinato a saturare lo spazio su disco. 

---

### 🔴 POF-5: Concurrent Game State Corruption — Race Condition MQTT/REST
**Stato:** **ANCORA PRESENTE**
**Analisi:** 
Non c'è traccia di meccanismi di concorrenza per prevenire la race condition tra chiamate REST e messaggi MQTT sulla stessa macchina da gioco.
- I documenti menzionano `@Transactional`, ma questo garantisce solo l'atomicità dell'Outbox Pattern (salvataggio db + evento outbox), **non blocca le modifiche concorrenti**.
- Non esiste alcuna annotazione `@Version` (Optimistic Locking) sulla `GameJpaEntity` o `ReservationJpaEntity`.
- Non ci sono riferimenti a lock pessimistici (`PESSIMISTIC_WRITE` / `SELECT ... FOR UPDATE`) nei Repository Adapter.

---

### 🔴 POF-7: Sync Starvation — Outbox Ordering Deadlock
**Stato:** **ANCORA PRESENTE (O PARZIALMENTE MITIGATO MA AMBIGUO)**
**Analisi:** 
Il `SyncSchedulerService` (Sez. 7.2) legge gli eventi `PENDING` e riprova per 10 volte. Tuttavia:
- **Nessun Fail-Safe descritto:** Se l'invio del primo record lancia un'eccezione non catturata correttamente (o va in timeout bloccante lungo), il ciclo si interrompe e il resto degli eventi non viene processato, fermando l'intero sync per 5 minuti.
- **Nessuna Paginazione:** Viene fatta una query che "legge tutti i record con status = 'PENDING'". Una coda outbox bloccata che cresce porterebbe la query a estrarre migliaia di record in un colpo solo, saturando la memoria (OOM Exception). Non è menzionato né un limite (`LIMIT 100`) né l'uso di code separate (Dead-Letter Queue).

---

### Riepilogo Prossimi Interventi
Per completare l'architettura, mancano le integrazioni architetturali per i seguenti punti:
1. **Per POF-3:** Un job `@Scheduled` di *Outbox Cleanup* per eliminare record `SENT` e archiviare i `FAILED` più vecchi di 30 giorni. E un sistema di paginazione nella lettura.
2. **Per POF-5:** Aggiungere `@Version` nel domain e nelle entità JPA, gestendo l'`ObjectOptimisticLockingFailureException` nel controller REST o broker MQTT per risolvere la race condition in modo pulito.
3. **Per POF-7:** Assicurarsi che `SyncSchedulerService` sia documentato per eseguire cicli non-bloccanti, loggando e saltando l'elemento (continue) o implementando una Dead-Letter Queue esplicita.
