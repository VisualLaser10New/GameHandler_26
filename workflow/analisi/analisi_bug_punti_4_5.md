# Analisi bug nascosti — Workflow punti 4 e 5

Data analisi: 2026-06-29  
Ambito: `central-system` e `local-server` di `Progetto/gamehandler-platform`  
Documenti di riferimento:

- `Progetto/workflow/workflow.md`
- `Progetto/workflow/architettura_classi.md`

## Vincoli rispettati

- Nessun codice produttivo modificato.
- Codice aggiunto solo sotto le cartelle `src/test/...` dei moduli analizzati.
- Analisi eseguita separando gli ambiti tramite due subagent:
  - subagent punto 4: `central-system` (`com.gameplatform.central`)
  - subagent punto 5: `local-server` (`com.gameplatform.local`)
- Test progettati per esporre bug esistenti: diversi test sono quindi attesi fallire finché il codice produttivo non viene corretto.

## Metodologia

L'analisi è stata condotta seguendo il workflow e l'architettura dichiarati:

1. lettura dei documenti di progetto;
2. lettura del codice dei moduli dei punti 4 e 5;
3. analisi riga-per-riga dei flussi principali, funzioni e chiamate tra classi;
4. simulazione virtuale degli scenari critici;
5. creazione di test mirati nelle rispettive folder di test per rendere riproducibili i bug;
6. diagnostica statica sui nuovi test.

## Test creati

### Punto 4 — `central-system`

| Test | Bug coperto |
|---|---|
| `Progetto/gamehandler-platform/central-system/src/test/java/com/gameplatform/central/application/service/AuthServiceJwtExpirationBugTest.java` | mismatch tra scadenza JWT reale e `LoginResponseDto.expiresAt` |
| `Progetto/gamehandler-platform/central-system/src/test/java/com/gameplatform/central/application/service/SyncReceiverBatchPoisoningBugTest.java` | batch sync bloccabile da evento con timestamp malformato |
| `Progetto/gamehandler-platform/central-system/src/test/java/com/gameplatform/central/application/service/UserReplicationSchedulerPoisonedOutboxBugTest.java` | scheduler replica utenti bloccabile da outbox event corrotto |

### Punto 5 — `local-server`

| Test | Bug coperto |
|---|---|
| `Progetto/gamehandler-platform/local-server/src/test/java/com/gameplatform/local/domain/model/BugL06_ConfirmedReservationExpirationTest.java` | prenotazioni `CONFIRMED` non scadibili nel domain model |
| `Progetto/gamehandler-platform/local-server/src/test/java/com/gameplatform/local/infrastructure/adapters/out/mysql/adapter/BugL07_ReservationRepositoryFindExpiredConfirmedTest.java` | `findExpired()` ignora prenotazioni `CONFIRMED` |
| `Progetto/gamehandler-platform/local-server/src/test/java/com/gameplatform/local/application/service/BugL08_ConfirmedReservationStartSessionTest.java` | start session rifiuta prenotazioni `CONFIRMED` |
| `Progetto/gamehandler-platform/local-server/src/test/java/com/gameplatform/local/application/service/BugL09_LocalAuthTokenExpiryMismatchTest.java` | mismatch temporale tra scadenza JWT firmata e risposta login locale |

---

# Punto 4 — Analisi `central-system`

## Flussi analizzati

### Registrazione e aggiornamento utenti

Classi principali:

- `central-system/src/main/java/com/gameplatform/central/application/service/UserService.java`
- `central-system/src/main/java/com/gameplatform/central/domain/model/User.java`
- `central-system/src/main/java/com/gameplatform/central/domain/model/OutboxEvent.java`
- `central-system/src/main/java/com/gameplatform/central/domain/ports/out/UserRepository.java`
- `central-system/src/main/java/com/gameplatform/central/domain/ports/out/OutboxEventRepository.java`

Simulazione logica:

1. `register(username, password, email)` verifica duplicati su username/email.
2. Genera hash BCrypt.
3. Salva `User` tramite porta `UserRepository`.
4. Serializza un `UserSyncDto`.
5. Inserisce nella outbox un evento `USER_REGISTERED` in stato `PENDING`.

Il disegno è coerente con Clean Architecture: il servizio applicativo dipende da porte di dominio, non da JPA/REST. L'evento outbox nella stessa transazione è coerente con resilienza offline e replica asincrona.

### Autenticazione e JWT

Classi principali:

- `central-system/src/main/java/com/gameplatform/central/application/service/AuthService.java`
- `central-system/src/main/java/com/gameplatform/central/infrastructure/security/JwtTokenProvider.java`
- `central-system/src/main/java/com/gameplatform/central/infrastructure/config/JwtConfig.java`

Bug rilevato: `AUTH-01`.

### Ricezione sync e idempotenza

Classi principali:

- `central-system/src/main/java/com/gameplatform/central/application/service/SyncReceiverService.java`
- `central-system/src/main/java/com/gameplatform/central/domain/ports/out/ProcessedEventRepository.java`
- `central-system/src/main/java/com/gameplatform/central/domain/ports/out/StatisticsRepository.java`

Simulazione logica:

1. `receiveSyncPayload(payload)` riceve un batch dal Local Server.
2. Per ogni evento verifica idempotenza tramite `ProcessedEventRepository.existsByEventId()`.
3. In base al tipo evento aggiorna statistiche aggregate.
4. Marca l'evento come processato.
5. Aggiorna `lastSeenAt` del local server.

Bug rilevato: `SYNC-01`.

### Replica utenti verso Local Server

Classi principali:

- `central-system/src/main/java/com/gameplatform/central/application/service/UserReplicationSchedulerService.java`
- `central-system/src/main/java/com/gameplatform/central/domain/ports/out/OutboxEventRepository.java`
- `central-system/src/main/java/com/gameplatform/central/domain/ports/out/LocalServerRegistryPort.java`
- `central-system/src/main/java/com/gameplatform/central/domain/ports/out/PushUserToLocalServersPort.java`

Simulazione logica:

1. Scheduler legge eventi outbox pending con limite batch.
2. Filtra eventi `USER_REGISTERED` e `USER_UPDATED`.
3. Deserializza payload in `UserSyncDto`.
4. Invio verso ciascun Local Server attivo.
5. Marca evento come sent solo quando tutti i server risultano sincronizzati.

Bug rilevato: `REPL-01`.

## Bug `AUTH-01` — `expiresAt` della risposta login non coincide con il JWT reale

**File coinvolti:**

- `Progetto/gamehandler-platform/central-system/src/main/java/com/gameplatform/central/application/service/AuthService.java`, righe circa `51-55`
- `Progetto/gamehandler-platform/central-system/src/main/java/com/gameplatform/central/infrastructure/security/JwtTokenProvider.java`
- `Progetto/gamehandler-platform/central-system/src/main/java/com/gameplatform/central/infrastructure/config/JwtConfig.java`

**Codice critico:**

```java
return new LoginResponseDto(
    jwtTokenProvider.generateToken(user),
    user.getId().value(),
    Instant.now(clock).plus(24, ChronoUnit.HOURS)
);
```

**Contratto atteso:**

La scadenza comunicata al client in `LoginResponseDto.expiresAt` deve rappresentare la scadenza effettiva del JWT firmato.

**Comportamento attuale:**

`AuthService` dichiara sempre una scadenza a 24 ore, mentre `JwtTokenProvider` genera token usando la configurazione `jwt.expiration-ms`.

**Simulazione:**

- configurazione JWT: durata 1 ora;
- login riuscito alle 10:00;
- token firmato: scade alle 11:00;
- response DTO: dichiara scadenza alle 10:00 del giorno dopo.

**Impatto:**

- Il client può credere valido un token già scaduto.
- Session management e refresh token lato client diventano incoerenti.
- Il sistema espone una verità applicativa diversa dalla verità crittografica del token.

**Test creato:**

`AuthServiceJwtExpirationBugTest.authenticate_reportsSameExpirationAsGeneratedJwt()`

Il test confronta la scadenza dichiarata dalla response con il claim `exp` del JWT.

## Bug `SYNC-01` — un evento con timestamp invalido avvelena l'intero batch sync

**File coinvolto:**

- `Progetto/gamehandler-platform/central-system/src/main/java/com/gameplatform/central/application/service/SyncReceiverService.java`, righe circa `98-102`, `111-115`, `156-160`

**Codice critico:**

```java
Instant occurredAt = payloadNode.has("occurredAt")
        ? Instant.parse(payloadNode.get("occurredAt").asText())
        : Instant.now();
```

**Contratto atteso:**

In un sistema distribuito resiliente offline, un evento difettoso non dovrebbe impedire la lavorazione degli eventi validi successivi nello stesso batch, salvo policy esplicita di rigetto atomico dell'intero batch.

**Comportamento attuale:**

`Instant.parse(...)` può lanciare una eccezione unchecked. L'eccezione non viene isolata a livello di singolo evento, quindi abortisce tutto `receiveSyncPayload()`.

**Simulazione:**

Batch ricevuto:

1. evento `GAME_SESSION_COMPLETED` con `occurredAt = "not-an-instant"`;
2. evento `GAME_SESSION_COMPLETED` valido;
3. heartbeat finale implicito tramite update `lastSeenAt`.

Esecuzione attuale:

1. primo evento: `Instant.parse` fallisce;
2. metodo abortisce;
3. secondo evento non viene processato;
4. `lastSeenAt` non viene aggiornato;
5. il Local Server può ritentare indefinitamente lo stesso batch.

**Impatto:**

- Un singolo evento corrotto blocca statistiche valide successive.
- La resilienza offline peggiora perché un payload poison può causare retry ricorrenti.
- L'idempotenza non basta: l'evento non arriva nemmeno a una gestione controllata di fallimento.

**Test creato:**

`SyncReceiverBatchPoisoningBugTest.invalidOccurredAtInOneEvent_doesNotAbortSubsequentValidEvents()`

Il test descrive il comportamento robusto atteso: evento invalido isolato, evento valido successivo processato.

## Bug `REPL-01` — outbox event utente corrotto blocca la replica degli eventi successivi

**File coinvolto:**

- `Progetto/gamehandler-platform/central-system/src/main/java/com/gameplatform/central/application/service/UserReplicationSchedulerService.java`, righe circa `89-93` e `129-133`

**Codice critico:**

```java
for (OutboxEvent event : pendingUserEvents) {
    UserSyncDto user = deserializeUser(event);
    ...
}
```

```java
private UserSyncDto deserializeUser(OutboxEvent event) {
    try {
        return objectMapper.readValue(event.getPayload(), UserSyncDto.class);
    ...
}
```

**Contratto atteso:**

Lo scheduler outbox dovrebbe isolare i failure per evento: un evento malformato non dovrebbe bloccare la replica degli eventi successivi.

**Comportamento attuale:**

Se il primo evento pending ha payload non JSON o incompatibile con `UserSyncDto`, `deserializeUser()` lancia `IllegalStateException`; `replicateUsers()` termina e non processa gli eventi successivi.

**Simulazione:**

Outbox pending ordinata:

1. `USER_REGISTERED`, payload `"{bad-json"`;
2. `USER_UPDATED`, payload valido.

Esecuzione attuale:

1. evento 1: deserializzazione fallisce;
2. scheduler run abortita;
3. evento 2 non inviato;
4. al prossimo giro si riparte dall'evento poison.

**Impatto:**

- Una singola riga outbox corrotta può bloccare tutta la coda utenti.
- Gli utenti validi successivi non vengono replicati ai Local Server.
- Il login offline locale può non ricevere utenti aggiornati.

**Test creato:**

`UserReplicationSchedulerPoisonedOutboxBugTest.malformedUserReplicationEvent_doesNotAbortSchedulerRun()`

---

# Punto 5 — Analisi `local-server`

## Flussi analizzati

### Prenotazioni

Classi principali:

- `local-server/src/main/java/com/gameplatform/local/domain/model/Reservation.java`
- `local-server/src/main/java/com/gameplatform/local/application/service/ReservationService.java`
- `local-server/src/main/java/com/gameplatform/local/application/service/ReservationExpirationService.java`
- `local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/adapter/ReservationRepositoryAdapter.java`

Simulazione logica:

1. `ReservationService.create()` verifica il gioco, riserva la macchina e crea prenotazione `PENDING`.
2. `Reservation.confirm()` porta la prenotazione a `CONFIRMED`.
3. `ReservationExpirationService.expireReservations()` legge prenotazioni scadute tramite `findExpired(now)`.
4. Per ogni prenotazione scaduta chiama `reservation.expire()`, rilascia la macchina e pubblica stato via MQTT.

Bug rilevati: `L-06`, `L-07`.

### Session lifecycle

Classi principali:

- `local-server/src/main/java/com/gameplatform/local/application/service/GameSessionService.java`
- `local-server/src/main/java/com/gameplatform/local/domain/model/GameSession.java`
- `local-server/src/main/java/com/gameplatform/local/domain/model/Game.java`

Simulazione logica:

1. `start(gameId, gameType, participants, reservationId)` verifica assenza di sessione attiva.
2. Se presente una prenotazione, ne valida stato, gameId e finestra temporale.
3. Mette la macchina `IN_USE`.
4. Crea sessione `IN_PROGRESS`.
5. A fine sessione `end()` completa, calcola durata, rilascia macchina e crea evento outbox.

Bug rilevato: `L-08`.

### Login offline

Classi principali:

- `local-server/src/main/java/com/gameplatform/local/application/service/LocalAuthService.java`
- `local-server/src/main/java/com/gameplatform/local/infrastructure/security/JwtTokenProvider.java`

Il flusso è correttamente offline-first perché usa utenti replicati localmente e non dipende dal Central System. È però presente un bug sulla coerenza temporale della scadenza JWT.

Bug rilevato: `L-09`.

## Bug `L-06` — prenotazioni `CONFIRMED` non scadibili dal domain model

**File coinvolto:**

- `Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/domain/model/Reservation.java`, righe circa `77-81`

**Codice critico:**

```java
public void expire() {
    if (this.status != ReservationStatus.PENDING) {
        throw new InvalidGameStateTransitionException(
            "Cannot expire reservation because status is: " + this.status
        );
    }
    this.status = ReservationStatus.EXPIRED;
}
```

**Contratto atteso da workflow/architettura:**

La scadenza deve applicarsi a prenotazioni con `status IN (PENDING, CONFIRMED)`.

**Comportamento attuale:**

Il domain model accetta solo `PENDING`; una prenotazione `CONFIRMED` scaduta genera `InvalidGameStateTransitionException`.

**Simulazione:**

1. prenotazione creata `PENDING`;
2. prenotazione confermata `CONFIRMED`;
3. nessun client avvia la sessione entro `endTime`;
4. job di expiration prova a chiamare `expire()`;
5. eccezione: la prenotazione resta non scaduta.

**Impatto:**

- Una prenotazione confermata ma mai usata può rimanere bloccata.
- La macchina può non essere rilasciata correttamente.
- Il comportamento diverge dal contratto del punto 5.

**Test creato:**

`BugL06_ConfirmedReservationExpirationTest`

## Bug `L-07` — `ReservationRepositoryAdapter.findExpired()` ignora le prenotazioni `CONFIRMED`

**File coinvolto:**

- `Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/infrastructure/adapters/out/mysql/adapter/ReservationRepositoryAdapter.java`, righe circa `63-67`

**Codice critico:**

```java
return jpaRepository.findByStatusInAndEndTimeBefore(List.of("PENDING"), now).stream()
    .map(mapper::toDomain)
    .collect(Collectors.toList());
```

**Contratto atteso:**

`findExpired(now)` dovrebbe restituire prenotazioni con `status IN (PENDING, CONFIRMED)` e `end_time < now`.

**Comportamento attuale:**

L'adapter passa a JPA solo `PENDING`.

**Simulazione:**

Database:

- prenotazione A: `PENDING`, `endTime < now`;
- prenotazione B: `CONFIRMED`, `endTime < now`.

Risultato attuale:

- A restituita;
- B ignorata.

**Impatto:**

Anche correggendo `Reservation.expire()`, il job non vedrebbe mai le prenotazioni confermate scadute finché l'adapter resta filtrato su `PENDING`.

**Test creato:**

`BugL07_ReservationRepositoryFindExpiredConfirmedTest`

## Bug `L-08` — start session rifiuta prenotazioni `CONFIRMED`

**File coinvolto:**

- `Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/application/service/GameSessionService.java`, righe circa `90-94`

**Codice critico:**

```java
if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
    throw new ReservationAlreadyUsedException("Reservation has already been used");
}
```

**Contratto atteso da workflow:**

`StartGameSessionUseCase` deve accettare una prenotazione opzionale se valida, con `status IN (PENDING, CONFIRMED)`.

**Comportamento attuale:**

`CONFIRMED` viene interpretato come prenotazione già usata e quindi bloccato.

**Simulazione:**

1. prenotazione valida già confermata;
2. utente arriva e avvia sessione con `reservationId`;
3. `GameSessionService` carica la prenotazione;
4. trova `CONFIRMED`;
5. lancia `ReservationAlreadyUsedException`.

**Impatto:**

- Il Local Server può rifiutare sessioni legittime.
- Il significato di `CONFIRMED` diventa ambiguo: per il workflow è uno stato valido, per il service è già consumato.
- Il bug è collegato a `L-06`/`L-07`: l'intero ciclo `PENDING -> CONFIRMED -> start/expire` è incoerente.

**Test creato:**

`BugL08_ConfirmedReservationStartSessionTest`

## Bug `L-09` — scadenza JWT dichiarata diversa dalla scadenza firmata nel token locale

**File coinvolti:**

- `Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/application/service/LocalAuthService.java`, righe circa `39-43`
- `Progetto/gamehandler-platform/local-server/src/main/java/com/gameplatform/local/infrastructure/security/JwtTokenProvider.java`, righe circa `17-21`

**Codice critico:**

```java
// LocalAuthService
Instant expiresAt = Instant.now(clock).plus(1, ChronoUnit.HOURS);
return new LoginResponseDto(token, user.getUserId().value(), expiresAt);
```

```java
// JwtTokenProvider
Instant now = Instant.now();
Instant expiresAt = now.plus(1, ChronoUnit.HOURS);
```

**Contratto atteso:**

La risposta login locale dovrebbe dichiarare la stessa scadenza contenuta nel JWT.

**Comportamento attuale:**

`LocalAuthService` usa il `Clock` iniettato, mentre `JwtTokenProvider` usa `Instant.now()` diretto. In presenza di clock fisso nei test, skew temporale o runtime non sincronizzato, le due scadenze divergono.

**Simulazione:**

- `LocalAuthService` usa `Clock.fixed(2026-06-29T10:00:00Z)`;
- `JwtTokenProvider` usa il clock reale del sistema;
- response DTO dichiara scadenza alle 11:00 del clock fisso;
- JWT contiene `exp` basato sull'orologio reale.

**Impatto:**

- Client e server possono avere scadenze divergenti.
- Testabilità ridotta.
- In modalità offline, la coerenza temporale locale è importante per evitare sessioni apparentemente valide ma token scaduti, o viceversa.

**Test creato:**

`BugL09_LocalAuthTokenExpiryMismatchTest`

---

# Osservazioni architetturali

## Clean Architecture e DIP

I bug individuati non derivano principalmente da dipendenze invertite male: i service applicativi usano in larga parte porte (`Repository`, `Port`) e gli adapter implementano correttamente i confini infrastrutturali.

Le criticità sono soprattutto:

- incoerenze tra contratto documentato e regole di dominio effettive (`CONFIRMED` nelle prenotazioni);
- assenza di isolamento per record/evento in processi batch (`SyncReceiverService`, `UserReplicationSchedulerService`);
- incoerenza del tempo tra token firmato e DTO restituito (`AuthService`, `LocalAuthService`).

## Resilienza offline/distribuita

I punti più rischiosi per sistemi distribuiti resilienti offline sono:

1. **Poison message in sync centralizzato**: un evento malformato blocca eventi validi successivi.
2. **Poison message in outbox replica utenti**: un evento corrotto blocca la coda.
3. **Prenotazioni confermate non gestite coerentemente**: rischio di macchine bloccate o sessioni legittime rifiutate.
4. **JWT con scadenza incoerente**: il client offline può prendere decisioni su una scadenza diversa da quella verificata lato server.

---

# Validazione

## Diagnostica statica

Eseguita diagnostica IDE sui nuovi test:

- `AuthServiceJwtExpirationBugTest.java`: nessun errore/warning
- `SyncReceiverBatchPoisoningBugTest.java`: nessun errore/warning
- `UserReplicationSchedulerPoisonedOutboxBugTest.java`: nessun errore/warning
- `BugL06_ConfirmedReservationExpirationTest.java`: nessun errore/warning
- `BugL07_ReservationRepositoryFindExpiredConfirmedTest.java`: nessun errore/warning
- `BugL08_ConfirmedReservationStartSessionTest.java`: nessun errore/warning
- `BugL09_LocalAuthTokenExpiryMismatchTest.java`: nessun errore/warning

## Maven

Non è stato possibile eseguire i test Maven in questo ambiente perché Maven non è installato:

```text
bash: mvn: command not found
```

## Comandi da eseguire in ambiente con Maven

Dalla root `Progetto`:

```bash
mvn -f gamehandler-platform/pom.xml -pl central-system -am \
  -Dtest=AuthServiceJwtExpirationBugTest,SyncReceiverBatchPoisoningBugTest,UserReplicationSchedulerPoisonedOutboxBugTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

```bash
mvn -f gamehandler-platform/pom.xml -pl local-server -am \
  -Dtest=BugL06_ConfirmedReservationExpirationTest,BugL07_ReservationRepositoryFindExpiredConfirmedTest,BugL08_ConfirmedReservationStartSessionTest,BugL09_LocalAuthTokenExpiryMismatchTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

Oppure l'intera suite:

```bash
mvn -f gamehandler-platform/pom.xml test
```

---

# Priorità di correzione consigliata

1. **Alta — `SYNC-01` e `REPL-01`**: impediscono resilienza batch/outbox e possono bloccare progressivamente sincronizzazione e replica.
2. **Alta — `L-06`, `L-07`, `L-08`**: incoerenza di dominio sul ciclo di vita prenotazione/sessione; può bloccare macchine o utenti.
3. **Media — `AUTH-01`, `L-09`**: bug di sicurezza/UX/session management; criticità aumenta se le durate token diventano configurabili o se esistono clock skew/offline mode prolungata.

# Sintesi finale

L'analisi dei punti 4 e 5 ha evidenziato 7 bug nascosti riproducibili tramite test:

- 3 nel `central-system`:
  - scadenza JWT dichiarata incoerente;
  - batch sync avvelenabile da timestamp invalido;
  - outbox replica utenti bloccabile da payload corrotto.
- 4 nel `local-server`:
  - prenotazioni `CONFIRMED` non scadibili;
  - repository expiration filtrato solo su `PENDING`;
  - start session rifiuta prenotazioni `CONFIRMED`;
  - scadenza JWT locale incoerente tra DTO e token.

I test creati formalizzano gli scenari nascosti e permettono di guidare le future correzioni senza violare Clean Architecture o DIP.
