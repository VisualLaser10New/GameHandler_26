# Documento di Architettura — Piattaforma Giochi da Tavolo e da Bar

---

## 1. Panoramica del Sistema

Il sistema implementa una piattaforma distribuita per la gestione di giochi da tavolo e da bar (calciobalilla, scacchi, freccette, monopoli, risiko, ecc.). L'architettura è ibrida, basata sul paradigma dell'**Edge Computing**, unito ai principi dei **Microservizi** e della **Clean Architecture** (Architettura Esagonale / *Ports and Adapters*). Questa combinazione garantisce resilienza offline, scalabilità orizzontale e manutenibilità.

Il codice sorgente risiede in un **Monorepo Maven Multi-Module**, ispezionabile dal docente, mentre Docker Compose orchestra la compilazione e l'esecuzione in isolamento.

---

## 2. Design Architetturale del Sistema Distribuito

### 2.1 Pattern Hub-and-Spoke con Pub/Sub

Il sistema implementa il **Pattern Hub-and-Spoke** combinato con il **Pattern Pub/Sub (MQTT)**.

```
┌──────────────────────────────────────────────────────────────┐
│              CENTRAL SYSTEM (Hub / Server Principale)        │
│  ┌──────────────┐  ┌─────────────┐  ┌──────────────────┐     │
│  │ Auth Service │  │Stats Service│  │ Sync Coordinator │     │
│  └──────────────┘  └─────────────┘  └──────────────────┘     │
│                        REST / HTTPS (TLS)                    │
└────────────────┬──────────────────────────┬──────────────────┘
                 │                          │
     ┌───────────▼──────────┐   ┌───────────▼───────────┐
     │  LOCAL SERVER #1     │   │  LOCAL SERVER #2      │
     │  (Edificio A)        │   │  (Edificio B)         │
     │  ┌────────────────┐  │   │  ┌────────────────┐   │
     │  │ MySQL + Outbox │  │   │  │ MySQL + Outbox │   │
     │  └────────────────┘  │   │  └────────────────┘   │
     │  ┌────────────────┐  │   │  ┌─────────────────┐  │
     │  │ MQTT Broker    │  │   │  │ MQTT Broker     │  │
     │  │ (TLS)          │  │   │  │ (TLS)           │  │
     │  └────────────────┘  │   │  └─────────────────┘  │
     └──────────┬───────────┘   └──────────┬────────────┘
          MQTTS │                    MQTTS │
     ┌──────────▼───────────┐   ┌───────────▼───────────┐
     │ Game Client #1       │   │ Game Client #3        │
     │ Game Client #2       │   │ Game Client #4        │
     └──────────────────────┘   └───────────────────────┘
```

### 2.2 Componenti

*   **Central System (L'Hub):** Microservizio Spring Boot responsabile della *Source of Truth* globale: registrazione utenti, aggregazione statistiche cross-building, coordinamento sincronizzazione.

*   **Local Server (Lo Spoke / Edge Node):** Microservizio Spring Boot installato fisicamente in ogni edificio. Funziona come gateway e nodo di persistenza locale. Persiste gli stati dei giochi nel database locale e genera le statistiche localmente (partite completate, in corso, tempo di utilizzo). I dati aggregati delle statistiche vengono poi inviati al Central System.

*   **Endpoint (Game Clients):** Applicazioni client con interfaccia grafica (JavaFX) che comunicano **esclusivamente** con il Local Server del proprio edificio tramite MQTT over TLS. L'uso di MQTT disaccoppia i client dal server ed è compatibile con la futura integrazione ESP32/Arduino.

### 2.3 Health Check degli Endpoint

Il Local Server esegue un **health check periodico ogni 5 minuti** sugli endpoint connessi tramite MQTT (ping/pong su topic dedicato). Per evitare falsi positivi causati da jitter di rete, il sistema adotta un **grace period di 3 cicli consecutivi mancati** (15 minuti totali). Un singolo heartbeat mancato viene loggato come warning ma non attiva alcuna azione distruttiva.

Se un endpoint non risponde per **3 cicli consecutivi**:

1. Le partite in esecuzione su quell'endpoint vengono terminate con stato `ABORTED` e motivo `ENDPOINT_UNREACHABLE`.
2. Lo stato del gioco fisico associato torna a `AVAILABLE`.
3. Viene generato un evento di allarme persistito nel DB locale e sincronizzato al Central System.

**Late arrival del risultato:** Se un client che era stato dichiarato irraggiungibile torna online e invia il `session/end` con il `GameResult`, il `GameSessionService` accetta la transizione da `ABORTED` a `COMPLETED`, preservando il risultato della partita.

### 2.4 Session Recovery all'Avvio

Al riavvio del Local Server, il `SessionRecoveryService` (implementa `SmartLifecycle`) esegue una scansione di tutte le `GameSession` con `status = IN_PROGRESS` o `PAUSED` nel database. Il servizio è annotato con `@DependsOn("mqttClient")` per garantire che il broker MQTT sia connesso **prima** che il recovery inizi — altrimenti i heartbeat sarebbero inviati su un canale non disponibile, portando alla terminazione errata di sessioni attive. Per ciascuna sessione orfana:

1. Invia un heartbeat MQTT al client associato con un timeout di **30 secondi**.
2. Se il client risponde, la sessione rimane attiva e il normale ciclo di vita prosegue.
3. Se il client non risponde, la sessione viene marcata come `ABORTED` con motivo `SERVER_RESTART`, la macchina viene rilasciata a `AVAILABLE`, e viene generato un `OutboxEvent`.

Questo meccanismo previene le sessioni "zombie" che rimarrebbero `IN_PROGRESS` indefinitamente dopo un crash o un riavvio del server.

### 2.5 Scadenza Automatica delle Prenotazioni

Il `ReservationExpirationService` esegue un job `@Scheduled` **ogni minuto** per verificare le prenotazioni scadute. Il job:

1. Invoca `ReservationRepository.findExpired(Instant.now())` per trovare tutte le prenotazioni con `status IN (PENDING, CONFIRMED)` e `end_time < NOW()`.
2. Per ciascuna prenotazione scaduta: imposta `status = EXPIRED` tramite `Reservation.expire()`.
3. Rilascia la macchina di gioco associata a `AVAILABLE` tramite `Game.release()`.
4. Pubblica il nuovo stato macchina via MQTT affinché tutti i client aggiornino la propria UI.

Questo previene il blocco indefinito delle macchine fisiche quando una prenotazione non viene utilizzata.

---

## 3. Sicurezza (TLS End-to-End)

### 3.1 Crittografia delle Comunicazioni

| Canale | Protocollo | Dettaglio |
|---|---|---|
| Local Server ↔ Central System | HTTPS (TLS 1.3) | Certificati server; REST API protette |
| Game Client ↔ MQTT Broker | MQTTS (TLS 1.3) | Mosquitto configurato con `certfile`, `keyfile`, `cafile`; porta 8883 |
| Browser ↔ Central System | HTTPS (TLS 1.3) | Interfaccia web per statistiche globali |

### 3.2 Autenticazione e Autorizzazione

*   **Password:** Hashing con **BCrypt** nel Central System. L'hash viene replicato ai Local Server per consentire il login offline.
*   **Token:** **JWT** firmato con chiave asimmetrica (RSA-256). Ogni nodo (Central System e ciascun Local Server) possiede la **propria coppia di chiavi RSA** (privata + pubblica). Il Central System firma i propri JWT con la sua chiave privata; ogni Local Server firma i propri JWT con la sua chiave privata locale e li valida con la corrispondente chiave pubblica. Questo consente l'emissione autonoma di JWT anche in assenza di connettività con il Central, garantendo il login offline.
*   **JWT Claims:** `{ sub: userId, buildingId, roles: [...], exp: timestamp }`.
*   **MQTT Auth:** Username/password per ogni Game Client, configurati nel broker Mosquitto tramite `password_file`.

#### 3.2.1 Modello RBAC (Role-Based Access Control)

Il sistema implementa un controllo d'accesso basato sui ruoli tramite Spring Security `@EnableMethodSecurity` e annotazioni `@PreAuthorize` su tutti i controller.

**Ruoli definiti:**

| Ruolo | Descrizione |
|---|---|
| `ROLE_USER` | Utente standard. Può effettuare prenotazioni, partecipare a sessioni di gioco, visualizzare statistiche proprie. |
| `ROLE_ADMIN` | Amministratore. Può gestire utenti, visualizzare tutte le statistiche, mettere macchine in manutenzione. |

**Matrice di accesso — Central System:**

| Endpoint | Metodo | Ruolo Richiesto |
|---|---|---|
| `POST /api/users` | Registrazione | Pubblico |
| `POST /api/auth/login` | Login | Pubblico |
| `PUT /api/users/{id}` | Aggiornamento utente | `ROLE_ADMIN` |
| `GET /api/statistics` | Statistiche globali | `ROLE_ADMIN` |
| `POST /internal/sync/receive` | Ricezione sync | API Key (service-to-service) |

**Matrice di accesso — Local Server:**

| Endpoint | Metodo | Ruolo Richiesto |
|---|---|---|
| `POST /api/auth/login` | Login locale | Pubblico |
| `POST /api/reservations` | Crea prenotazione | `ROLE_USER` |
| `DELETE /api/reservations/{id}` | Cancella prenotazione | `ROLE_USER` (proprietario) |
| `GET /api/reservations` | Lista prenotazioni | `ROLE_USER` |
| `GET /api/games` | Lista giochi | `ROLE_USER` |
| `GET /api/games/available` | Giochi disponibili | `ROLE_USER` |
| `POST /api/sessions/start` | Avvia sessione | `ROLE_USER` |
| `POST /api/sessions/{id}/end` | Termina sessione | `ROLE_USER` |
| `POST /api/sessions/{id}/pause` | Pausa sessione | `ROLE_USER` |
| `POST /api/sessions/{id}/resume` | Riprendi sessione | `ROLE_USER` |
| `GET /api/statistics` | Statistiche locali | `ROLE_USER` |
| `PUT /internal/users/sync` | Ricezione utenti | API Key (service-to-service) |

Il `JwtAuthenticationFilter` estrae i ruoli dal claim `roles` del JWT e li mappa a `GrantedAuthority` di Spring Security, rendendo disponibili le verifiche `@PreAuthorize("hasRole('USER')")` e `@PreAuthorize("hasRole('ADMIN')")` su ogni endpoint.

#### 3.2.2 Autenticazione Server-to-Server (Endpoint Interni)

Gli endpoint con prefisso `/internal/**` sono destinati esclusivamente alla comunicazione tra microservizi e sono **esclusi dal filtro JWT**. La loro protezione è affidata a un meccanismo separato basato su **API Key pre-condivisa**:

*   Il Central System e ogni Local Server condividono un segreto (`INTERNAL_API_KEY`) caricato da variabile d'ambiente.
*   Ogni richiesta verso `/internal/**` deve includere l'header `X-Internal-Api-Key` con il valore corretto.
*   Un filtro dedicato `InternalApiKeyFilter` (registrato in `SecurityConfig`) valida l'header prima di consentire l'accesso.
*   Se l'header è assente o il valore non corrisponde, il filtro risponde con `401 Unauthorized`.

Questa separazione garantisce che un utente con JWT valido (`ROLE_USER` o `ROLE_ADMIN`) non possa invocare direttamente gli endpoint di sincronizzazione interna.

#### 3.2.3 Separazione dei Trust Domain JWT

I JWT emessi dal Central System e quelli emessi da un Local Server **non sono intercambiabili**:

*   Il **Game Client JavaFX** esegue login sul Local Server e riceve un JWT locale. Usa questo JWT esclusivamente per le chiamate verso quel Local Server (`/api/**`).
*   Il **Central System** emette JWT per accesso alla propria web interface (statistiche globali). Questi JWT sono validi solo sul Central System.
*   I Local Server **non accettano JWT del Central** (chiavi RSA distinte). Il Central **non accetta JWT locali**.
*   La comunicazione Local Server ↔ Central avviene sempre tramite **API Key** (`X-Internal-Api-Key`), mai tramite JWT utente.

### 3.3 Flusso di Autenticazione

```
Login Online:
  Client → POST /auth/login → Local Server → verifica BCrypt → firma JWT con chiave privata locale → emette JWT
  Local Server → (se online) valida anche con Central System

Login Offline:
  Client → POST /auth/login → Local Server → verifica BCrypt da tabella replicated_users → firma JWT con chiave privata locale → emette JWT

Accesso Risorsa:
  Client → GET /games (Header: Authorization: Bearer <JWT>) → Local Server → validazione firma (chiave pubblica locale) + scadenza
```

---

## 4. Protocollo MQTT: Topic Schema e Payload

### 4.1 Topic Schema

Tutti i topic seguono una convenzione gerarchica strutturata con `buildingId` e `gameId`:

```
building/{buildingId}/game/{gameId}/state         → Stato della macchina (AVAILABLE|RESERVED|IN_USE)
building/{buildingId}/game/{gameId}/session/start  → Inizio sessione di gioco
building/{buildingId}/game/{gameId}/session/end    → Fine sessione (con result_data)
building/{buildingId}/game/{gameId}/session/pause  → Pausa sessione
building/{buildingId}/game/{gameId}/session/resume → Ripresa sessione
building/{buildingId}/game/{gameId}/heartbeat      → Ping dal client (per health check)
building/{buildingId}/game/{gameId}/heartbeat/ack  → Pong dal server
building/{buildingId}/alerts                       → Allarmi (endpoint irraggiungibile, errori)
```

### 4.2 Pattern di Comunicazione

*   Il **Game Client** pubblica su `building/{id}/game/{gameId}/state` e `session/*` per notificare cambiamenti di stato.
*   Il **Local Server** si sottoscrive a `building/{id}/game/+/state` e `building/{id}/game/+/session/#` per aggiornare il DB locale e generare statistiche.
*   Il **Local Server** pubblica su `building/{id}/game/{gameId}/heartbeat/ack` in risposta ai ping.
*   I **Game Client** si sottoscrivono a `building/{id}/game/+/state` per aggiornare la propria UI in tempo reale (es. vedere che un altro gioco è diventato "in uso").

### 4.3 QoS e Retained Messages

*   Topic `state`: **QoS 1** (at least once) + **Retained = true** (ogni nuovo subscriber riceve l'ultimo stato noto).
*   Topic `session/*`: **QoS 1** (at least once).
*   Topic `heartbeat`: **QoS 0** (best effort, non critico).

---

## 5. Struttura del Monorepo (Maven Multi-Module)

### 5.1 Principio Anti-Duplicazione

Per evitare duplicazioni di codice tra microservizi, il progetto adotta un **Maven Parent POM** con moduli condivisi. I moduli `shared-*` non contengono alcuna dipendenza da Spring, JPA o framework infrastrutturali.

### 5.2 Struttura delle Cartelle

Per le classi riferirsi al file **architettura_classi.md**

```text
boardgame-platform/
│
├── pom.xml                              ← Parent POM (gestisce versioni)
│
├── .devcontainer/
│   └── devcontainer.json
│
├── shared/                              ← Librerie comuni (no Spring, no JPA)
│   ├── shared-domain/                   ← Value Object, interfacce gioco, eventi
│   │   ├── pom.xml
│   │   └── src/main/java/com/gameplatform/shared/domain/
│   │       ├── model/
│   │       ├── game/
│   │       ├── result/
│   │       └── events/
│   │
│   ├── shared-dto/                      ← DTO / Contratti API REST
│   │   ├── pom.xml                      ← dipende da shared-domain
│   │   └── src/main/java/com/gameplatform/shared/dto/
│   │
│   └── shared-mqtt/                     ← Costanti topic e payload tipizzati
│       ├── pom.xml                      ← dipende da shared-dto
│       └── src/main/java/com/gameplatform/shared/mqtt/
│           └── payload/
│
├── central-system/                      ← Microservizio 1
│   ├── pom.xml                          ← dipende da shared-domain, shared-dto
│   ├── Dockerfile                       ← Multi-stage build
│   └── src/main/java/com/gameplatform/central/
│       ├── domain/
│       ├── application/
│       └── infrastructure/
│
├── local-server/                        ← Microservizio 2 (Edge Node)
│   ├── pom.xml                          ← dipende da shared-domain, shared-dto, shared-mqtt
│   ├── Dockerfile
│   └── src/main/java/com/gameplatform/local/
│       ├── domain/
│       ├── application/
│       └── infrastructure/
│
├── game-client-emulator/                ← Microservizio 3 (Client JavaFX)
│   ├── pom.xml                          ← dipende da shared-domain, shared-dto, shared-mqtt
│   ├── Dockerfile
│   └── src/main/java/com/gameplatform/client/
│       ├── domain/
│       │   ├── games/                   ← Implementazioni concrete dei giochi
│       ├── application/
│       │   └── service/
│       └── infrastructure/
│           ├── mqtt/
│           ├── ui/                      ← JavaFX Scene Graph
│           │   └── components/
│           └── config/
│
├── infrastructure/
│   ├── mysql-central/init.sql
│   ├── mysql-local/init.sql
│   ├── mosquitto/
│   │   ├── mosquitto.conf
│   │   ├── certs/                       ← Certificati TLS per MQTTS
│   │   └── password_file
│   └── tls/                             ← Certificati TLS per HTTPS
│
├── docker-compose.yml
└── README.md
```

### 5.3 Regole di Dipendenza tra Moduli

```
shared-domain   ← ZERO dipendenze esterne (solo Java standard + Jackson annotations)
     ↑
shared-dto      ← dipende da shared-domain (usa i Value Object come ID)
     ↑
shared-mqtt     ← dipende da shared-dto (i payload sono i DTO)
     ↑
[central-system | local-server | game-client-emulator]
```

**Regola aurea:** il codice va nel modulo più profondo possibile.

*   **Va in `shared`:** Value Object immutabili, Domain Events (POJO serializzabili), DTO per contratti REST, costanti MQTT, interfacce `GameLifecycle`, record `GameResult`.
*   **NON va in `shared`:** Annotazioni `@Entity` JPA, annotazioni `@RestController` Spring, logica di business specifica di un singolo microservizio.

---

## 6. Clean Architecture Interna (Architettura Esagonale)

### 6.1 Principio

All'interno di ogni microservizio il codice è rigorosamente diviso secondo l'**Architettura Esagonale** per rispettare il *Dependency Inversion Principle (DIP)*. Nessuna logica di business dipende da Spring Boot, JPA o MySQL.

### 6.3 Mapping Domain ↔ JPA Entity

Per evitare che il dominio dipenda da JPA, ogni entità ha una **controparte infrastrutturale**:

*   `domain/model/Reservation.java` — POJO puro con logica di business (es. `canBeCancelled()`).
*   `infrastructure/out/mysql/entity/ReservationJpaEntity.java` — Classe con annotazioni `@Entity`, `@Table`, `@Id`. Nessuna logica di business.
*   `infrastructure/out/mysql/adapter/ReservationRepositoryAdapter.java` — Implementa `ReservationRepository` (porta di dominio), usa `ReservationJpaRepository` (Spring Data) e converte tramite mapper esplicito.

```java
// Esempio: domain/model/Reservation.java (PURO — zero dipendenze framework)
public class Reservation {
    private final ReservationId id;
    private final GameId gameId;
    private final UserId userId;
    private ReservationStatus status;
    private final Instant createdAt;

    // Clock passato come parametro dal service (non iniettato nel domain)
    // per garantire testabilità deterministica
    public boolean canBeCancelled(Clock clock) {
        // Annullabile se la prenotazione è PENDING e manca almeno 1 ora all'inizio
        return status == ReservationStatus.PENDING &&
               startTime.isAfter(Instant.now(clock).plus(Duration.ofHours(1)));
    }
}

// Esempio: infrastructure/out/mysql/adapter/ReservationRepositoryAdapter.java
// I mapper sono bean Spring @Component con metodi di istanza (non statici)
// per consentire mocking nei test e rispettare il DIP.
@Component
public class ReservationRepositoryAdapter implements ReservationRepository {
    private final ReservationJpaRepository jpaRepo;
    private final ReservationMapper mapper;  // iniettato via costruttore

    @Override
    public Optional<Reservation> findById(ReservationId id) {
        return jpaRepo.findById(id.value()).map(mapper::toDomain);
    }
}
```

---

## 7. Sincronizzazione Offline (Transactional Outbox Pattern)

### 7.1 Principio

Il Local Server sincronizza i dati con il Central System **ogni 5 minuti**. Se la connessione non è disponibile, i dati vengono accumulati in un backlog locale (tabella `outbox_events`) e inviati al ripristino della connessione.

### 7.2 Meccanismo

1. Quando si verifica un evento locale (prenotazione, fine sessione, statistica generata), l'entità viene salvata nel DB locale.
2. **Nella stessa transazione atomica** (garantita dall'annotazione `@Transactional` sul metodo del service applicativo), si inserisce un record nella tabella `outbox_events` (per lo schema DDL si veda la Sezione 10.2). L'atomicità è fondamentale: se il salvataggio dell'entità o dell'outbox event fallisce, l'intera operazione viene annullata tramite rollback.
3. Un job `@Scheduled` (`SyncSchedulerService`) esegue ogni **5 minuti**:
   - Verifica la connettività verso il Central System (HTTP health check).
   - Se connesso, legge tutti i record con `status = 'PENDING'` ordinati per `created_at`.
   - Per ciascuno, invia il payload via REST al Central System.
   - In caso di `200 OK`, aggiorna `status = 'SENT'` e imposta `sent_at`.
   - In caso di errore, incrementa `retry_count`. Dopo 10 tentativi falliti, imposta `status = 'FAILED'` e genera un allarme.

### 7.3 Idempotenza

Ogni evento contiene un `eventId` (UUID) generato alla creazione. Il Central System implementa la deduplica tramite una tabella `processed_events(event_id PRIMARY KEY)`. Se riceve un `eventId` già presente, risponde `200 OK` senza rielaborare.

---

## 8. Statistiche e Sessioni di Gioco

### 8.1 Principio (Scelta Utente)

Gli stati dei giochi vengono **salvati in locale** e utilizzati **in locale** per generare le statistiche (elenco partite completate e in corso, durate, preferenze). I dati aggregati delle statistiche vengono poi inviati al Central System tramite l'Outbox.

### 8.2 Flusso

1. Il Game Client pubblica su MQTT `building/{id}/game/{gameId}/session/start` all'inizio di una partita. Il payload include un `reservationId` opzionale.
2. Il Local Server riceve l'evento e, se presente, verifica che la prenotazione sia valida: `reservation.userId == userId`, `reservation.gameId == gameId`, `reservation.status IN (PENDING, CONFIRMED)`. Se la verifica fallisce, pubblica un errore sul topic `building/{id}/alerts`. Se non è presente `reservationId`, la sessione è avviata direttamente (modalità walk-in).
3. Il Local Server crea una `GameSession` nel DB locale con `status = IN_PROGRESS` e, se era presente una prenotazione, la transiziona a `CONFIRMED`.
4. Durante la partita, gli aggiornamenti di stato transitano via MQTT.
5. A fine partita, il Game Client pubblica su `session/end` con il `result_data` specifico del gioco.
6. Il Local Server aggiorna la `GameSession`: `status = COMPLETED`, `ended_at`, `duration_s`, `winner_id`, `result_data`.
7. Il `StatisticsService` locale genera aggregazioni: partite per tipo, durata media, win rate per utente, preferenze.
8. Le statistiche aggregate vengono inserite nell'`outbox_events` e inviate al Central System al prossimo ciclo di sync.

---

## 9. Modellazione Generica dei Giochi (Interfacce di Dominio)

### 9.1 Principio (Scelta Utente)

Poiché il sistema deve gestire giochi radicalmente diversi (calciobalilla, scacchi, freccette, monopoli, risiko), si implementa una **gerarchia di interfacce** nel modulo `shared-domain` che definisce i diversi stati e le diverse azioni eseguibili.

### 9.2 Analisi dei Giochi Supportati

| Gioco | Metrica di Vittoria | Contabilità Interna | Turni | Interfacce Applicabili |
|---|---|---|---|---|
| **Calciobalilla** | Gol (es. 5-3) | Nessuna | No | `GameLifecycle`, `ScoredGame` |
| **Scacchi** | Scacco matto / abbandono | Pezzi catturati | Sì | `GameLifecycle`, `TurnBasedGame`, `BoardGame` |
| **Freccette** | Chi scende prima a 0 (501) | Punteggio decrescente | Sì | `GameLifecycle`, `ScoredGame`, `TurnBasedGame` |
| **Monopoli** | Ultimo non in bancarotta | Denaro + proprietà | Sì | `GameLifecycle`, `TurnBasedGame`, `ResourceBasedGame` |
| **Risiko** | Conquista totale | Carri armati per territorio | Sì | `GameLifecycle`, `TurnBasedGame`, `ResourceBasedGame`, `BoardGame` |

Questi giochi hanno strutture dati di risultato **incompatibili tra loro**, rendendo impossibile un modello dati unificato a colonne fisse. La soluzione adottata è la combinazione di interfacce polimorfiche Java (per la type safety a compile-time) e colonna JSON nel database (per la flessibilità a runtime).

### 9.3 Capability Interfaces (Composizione senza Diamond Inheritance)

Le interfacce di capability sono **standalone**: non estendono `GameLifecycle` né tra di loro. Ogni classe concreta implementa `GameLifecycle` (per il ciclo di vita) più le capability necessarie tramite composizione piatta. Questo elimina il diamond inheritance che si verificherebbe quando un gioco implementa più capability che condividono un antenato comune.

```java
/**
 * Interfaccia radice: ciclo di vita di QUALSIASI gioco.
 * Risiede in shared-domain. Zero dipendenze framework.
 */
public interface GameLifecycle {
    void start(List<UserId> participants);
    void stop(StopReason reason);     // COMPLETED, ABORTED, TIMEOUT
    void pause();
    void resume();
    GameStatus getStatus();           // WAITING, IN_PROGRESS, PAUSED, COMPLETED, ABORTED
    GameType getGameType();
    GameSessionId getSessionId();
}

/** Capability: giochi a turni (Scacchi, Monopoli, Risiko, Freccette). Standalone, non estende GameLifecycle. */
public interface TurnBasedGame {
    UserId getCurrentPlayer();
    void endTurn();
    int getTurnNumber();
}

/** Capability: giochi con punteggio numerico (Calciobalilla, Freccette). Standalone, non estende GameLifecycle. */
public interface ScoredGame {
    Map<UserId, Integer> getCurrentScores();
    void recordScore(UserId player, int delta);
}

/** Capability: giochi con risorse multiple (Monopoli, Risiko). Standalone, non estende TurnBasedGame. */
public interface ResourceBasedGame {
    Map<UserId, Map<String, Integer>> getResources();
    void updateResource(UserId player, String resourceKey, int newValue);
}

/** Capability: giochi con board/mappa serializzabile (Scacchi, Risiko). Standalone, non estende TurnBasedGame. */
public interface BoardGame {
    String serializeBoardState();
    void restoreBoardState(String serializedState);
}
```

### 9.4 Implementazioni Concrete (nel Game Client)

```java
public class FoosballGame implements GameLifecycle, ScoredGame { ... }
public class ChessGame implements GameLifecycle, TurnBasedGame, BoardGame { ... }
public class DartsGame implements GameLifecycle, ScoredGame, TurnBasedGame { ... }
public class MonopolyGame implements GameLifecycle, TurnBasedGame, ResourceBasedGame { ... }
public class RiskGame implements GameLifecycle, TurnBasedGame, ResourceBasedGame, BoardGame { ... }
```

### 9.5 Risultati Polimorfici (GameResult)

Ogni gioco produce un tipo di risultato diverso. Tutti implementano `GameResult` e vengono serializzati/deserializzati automaticamente da Jackson tramite `@JsonTypeInfo`:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FoosballResult.class, name = "FOOSBALL"),
    @JsonSubTypes.Type(value = ChessResult.class,    name = "CHESS"),
    @JsonSubTypes.Type(value = DartsResult.class,    name = "DARTS"),
    @JsonSubTypes.Type(value = MonopolyResult.class, name = "MONOPOLY"),
    @JsonSubTypes.Type(value = RiskResult.class,     name = "RISK"),
})
public interface GameResult {
    UserId getWinnerId();              // Vincitore primario (es. capitano squadra)
    List<UserId> getWinnerIds();       // Tutti i vincitori (es. entrambi i giocatori del Team Red)
    WinCondition getWinCondition();
}

// Esempi di implementazione (record Java) — winnerId è UserId, non String
public record FoosballResult(UserId winnerId, List<UserId> winnerIds,
                              Map<String, Integer> finalScores,
                              WinCondition winCondition) implements GameResult {
    // winnerId  = primo membro del team vincente (compatibilita' colonna winner_id)
    // winnerIds = tutti i componenti del team (statistiche win-rate corrette)
}

public record ChessResult(UserId winnerId, List<UserId> winnerIds,
                           String terminationReason,
                           String finalFenState, WinCondition winCondition) implements GameResult {}

public record MonopolyResult(UserId winnerId, List<UserId> winnerIds,
                              Map<String, Integer> finalMoney,
                              Map<String, List<String>> ownedProperties,
                              WinCondition winCondition) implements GameResult {}

public record RiskResult(UserId winnerId, List<UserId> winnerIds,
                         Map<String, Map<String, Integer>> territoriesAtEnd,
                         int totalRounds, WinCondition winCondition) implements GameResult {}
```

---

## 10. Strategia di Persistenza: MySQL con Colonna JSON

### 10.1 Scelta Architetturale e Giustificazione

Si utilizza un **singolo database MySQL** sia per il Central System che per ogni Local Server. Sono state valutate 5 strategie di persistenza per i dati eterogenei dei giochi:

| Strategia | Flessibilità | Complessità DB | Query Statistiche | Verdetto |
|---|---|---|---|---|
| **A — Single Table Inheritance** (colonne nullable per tipo) | ❌ Bassa | ✅ Semplice | ✅ Facile | ❌ Esplosione colonne: 10 giochi = 50+ colonne nullable |
| **B — Table Per Type** (una tabella per gioco) | ❌ Bassa | ❌ Complessa | ✅ Facile | ❌ Ogni nuovo gioco = ALTER TABLE + nuovo repository |
| **C — EAV** (Entity-Attribute-Value) | ✅ Alta | ⚠️ Media | ❌ Impossibile | ❌ Anti-pattern noto; aggregazioni SQL impraticabili |
| **D — MySQL + colonna JSON** | ✅ Alta | ✅ Semplice | ✅ Buona | ✅ **Scelta adottata** |
| **E — MongoDB** (database documentale) | ✅ Alta | ✅ Semplice | ✅ Buona | ❌ Overkill: aggiunge tecnologia senza benefici reali |

**Motivazione della scelta D:** MySQL con colonna JSON offre il miglior trade-off perché:
- Schema stabile: aggiungere un nuovo gioco richiede **zero** modifiche al DB.
- I campi comuni (`winner_id`, `duration_s`, `game_type`) sono colonne native indicizzate → query statistiche aggregate veloci.
- MySQL 5.7+ supporta funzioni JSON (`JSON_EXTRACT`, `JSON_CONTAINS`) per query su dati specifici.
- Un solo `GameSessionRepository` → un solo mapper → un solo use case, indipendentemente dal numero di giochi.
- MongoDB è stato scartato perché aggiungerebbe una tecnologia (container + driver + config) senza fornire benefici che MySQL+JSON non offra già in questo contesto.

### 10.2 Schema Ibrido

Le colonne native gestiscono i campi comuni a tutti i giochi (per query aggregate veloci). La colonna `result_data JSON` contiene i dati specifici del gioco (schema-free, zero `ALTER TABLE` per nuovi giochi).

```sql
-- =============== TABELLE COMUNI (Central + Local) ===============

CREATE TABLE users (
    id              VARCHAR(36) PRIMARY KEY,
    username        VARCHAR(100) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    roles           VARCHAR(255) DEFAULT 'USER',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE game_catalog (
    id          VARCHAR(36) PRIMARY KEY,
    game_type   VARCHAR(50) NOT NULL,    -- FK logica a GameType enum
    name        VARCHAR(100) NOT NULL,
    building_id VARCHAR(36) NOT NULL,
    status      ENUM('AVAILABLE','RESERVED','IN_USE','MAINTENANCE') DEFAULT 'AVAILABLE',
    INDEX idx_building (building_id),
    INDEX idx_type (game_type)
);

-- =============== TABELLE LOCAL SERVER ===============

CREATE TABLE reservations (
    id          VARCHAR(36) PRIMARY KEY,
    game_id     VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    status      ENUM('PENDING','CONFIRMED','CANCELLED','EXPIRED') NOT NULL,
    start_time  DATETIME NOT NULL,
    end_time    DATETIME,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_game (game_id),
    INDEX idx_user (user_id),
    INDEX idx_expiration (status, end_time),
    INDEX idx_availability (game_id, status, start_time, end_time)  -- query disponibilità
);

CREATE TABLE game_sessions (
    id            VARCHAR(36) PRIMARY KEY,
    game_id       VARCHAR(36) NOT NULL,
    game_type     VARCHAR(50) NOT NULL,
    building_id   VARCHAR(36) NOT NULL,
    status        ENUM('WAITING','IN_PROGRESS','PAUSED','COMPLETED','ABORTED') NOT NULL,
    started_at    DATETIME NOT NULL,
    ended_at      DATETIME,
    duration_s    INT,
    winner_id     VARCHAR(36),
    win_condition VARCHAR(30),
    result_data   JSON,                   -- ← Dati specifici del gioco
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_game_type (game_type),
    INDEX idx_building (building_id),
    INDEX idx_status (status),
    INDEX idx_winner (winner_id)
);

CREATE TABLE session_participants (
    session_id  VARCHAR(36) NOT NULL,
    user_id     VARCHAR(36) NOT NULL,
    role        VARCHAR(30),
    joined_at   DATETIME,
    PRIMARY KEY (session_id, user_id),
    INDEX idx_user (user_id)
);

CREATE TABLE outbox_events (
    id          VARCHAR(36) PRIMARY KEY,
    event_type  VARCHAR(50) NOT NULL,
    payload     JSON NOT NULL,
    status      ENUM('PENDING','SENT','FAILED') DEFAULT 'PENDING',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at     DATETIME,
    retry_count INT DEFAULT 0,
    INDEX idx_status (status)
);

CREATE TABLE replicated_users (
    user_id       VARCHAR(36) PRIMARY KEY,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    roles         VARCHAR(255),
    synced_at     DATETIME NOT NULL
);

-- =============== TABELLE CENTRAL SYSTEM ===============

CREATE TABLE aggregated_statistics (
    id            VARCHAR(36) PRIMARY KEY,
    building_id   VARCHAR(36) NOT NULL,
    game_type     VARCHAR(50) NOT NULL,
    period_start  DATE NOT NULL,
    period_end    DATE NOT NULL,
    total_sessions INT DEFAULT 0,
    avg_duration_s INT DEFAULT 0,
    total_reservations INT DEFAULT 0,
    data          JSON,
    UNIQUE KEY uk_building_type_period (building_id, game_type, period_start)
);

CREATE TABLE processed_events (
    event_id    VARCHAR(36) PRIMARY KEY,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =============== TABELLE CENTRAL SYSTEM — REGISTRY ===============

CREATE TABLE local_servers (
    id           VARCHAR(36) PRIMARY KEY,
    building_id  VARCHAR(36) UNIQUE NOT NULL,
    base_url     VARCHAR(255) NOT NULL,   -- es. https://local-server-1:8080
    last_seen_at DATETIME,
    is_active    BOOLEAN DEFAULT TRUE,
    INDEX idx_active (is_active)
);
-- Popolata al boot di ogni Local Server via POST /internal/register (API Key protetto).

-- =============== TABELLE LOCAL SERVER — CACHE STATISTICHE ===============

CREATE TABLE local_statistics_cache (
    id          VARCHAR(36) PRIMARY KEY,
    game_type   VARCHAR(50) NOT NULL,
    period      DATE NOT NULL,
    data        JSON,              -- statistiche aggregate (totalSessions, avgDuration, etc.)
    computed_at DATETIME NOT NULL,
    UNIQUE KEY uk_type_period (game_type, period)
);
-- Aggiornata da StatisticsService al termine di ogni partita e al boot.
-- Evita ricalcoli O(n) su tutte le game_sessions ad ogni GET /api/statistics.
```

### 10.3 Esempi di `result_data` JSON

```json
// Calciobalilla
{"type":"FOOSBALL","team_red":{"goals":5,"users":["uuid1"]},"team_blue":{"goals":3,"users":["uuid2"]}}

// Scacchi
{"type":"CHESS","white":"uuid1","black":"uuid2","termination":"CHECKMATE","total_moves":42}

// Monopoli
{"type":"MONOPOLY","finalMoney":{"uuid1":4500,"uuid2":0},"bankruptOrder":["uuid2"]}

// Risiko
{"type":"RISK","territoriesAtEnd":{"uuid1":{"Italy":5,"France":3}},"totalRounds":15}
```

### 10.4 Mapping GameSession: Domain ↔ JPA ↔ JSON

Il `GameSessionMapper` utilizza il polimorfismo Jackson (`@JsonTypeInfo`) per serializzare/deserializzare automaticamente la colonna `result_data` nel tipo Java corretto:

```java
// infrastructure/out/mysql/adapter/GameSessionMapper.java
// Bean Spring @Component — ObjectMapper iniettato per configurazione centralizzata Jackson
@Component
public class GameSessionMapper {
    private final ObjectMapper objectMapper;  // iniettato via costruttore

    public GameSessionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GameSession toDomain(GameSessionJpaEntity entity) {
        GameResult result = null;
        if (entity.getResultData() != null) {
            try {
                result = objectMapper.readValue(entity.getResultData(), GameResult.class);
                // Jackson usa @JsonTypeInfo.property="type" per istanziare il sottotipo corretto
            } catch (JsonProcessingException e) {
                // result_data corrotto o tipo non registrato: session leggibile ma senza risultato
                log.warn("Cannot deserialize result_data for session {}: {}", entity.getId(), e.getMessage());
            }
        }
        return new GameSession(
            new GameSessionId(entity.getId()),
            new GameId(entity.getGameId()),
            GameType.valueOf(entity.getGameType()),
            new BuildingId(entity.getBuildingId()),
            GameStatus.valueOf(entity.getStatus()),
            entity.getStartedAt(), entity.getEndedAt(),
            entity.getDurationSeconds(),
            entity.getWinnerId() != null ? new UserId(entity.getWinnerId()) : null,
            entity.getWinCondition() != null ? WinCondition.valueOf(entity.getWinCondition()) : null,
            result
        );
    }

    public GameSessionJpaEntity toEntity(GameSession domain) {
        GameSessionJpaEntity entity = new GameSessionJpaEntity();
        entity.setId(domain.getId().value());
        entity.setGameId(domain.getGameId().value());
        entity.setGameType(domain.getGameType().name());
        entity.setBuildingId(domain.getBuildingId().value());
        entity.setStatus(domain.getStatus().name());
        entity.setStartedAt(domain.getStartedAt());
        entity.setEndedAt(domain.getEndedAt());
        entity.setDurationSeconds(domain.getDurationSeconds());
        entity.setWinnerId(domain.getWinnerId() != null ? domain.getWinnerId().value() : null);
        entity.setWinCondition(domain.getWinCondition() != null ? domain.getWinCondition().name() : null);
        entity.setResultData(domain.getResult() != null
            ? objectMapper.writeValueAsString(domain.getResult())
            : null);
        return entity;
    }
}
```

Questo pattern garantisce che il dominio non dipenda da JPA e che Jackson ricostruisca automaticamente il sottotipo corretto di `GameResult` (es. `FoosballResult`, `ChessResult`) basandosi sul campo `"type"` nel JSON.

---

## 11. Replicazione Utenti

La replica utenti avviene **esclusivamente via REST** (push dal Central verso i Local Server tramite `PUT /internal/users/sync`, protetto da API Key). Non viene utilizzato alcun canale MQTT per la sincronizzazione utenti, evitando race condition e inconsistenze derivanti da dual-channel.

### 11.1 Flusso di Registrazione

1. Utente si registra → `POST /api/users` → Central System.
2. Central System salva l'utente e crea un `OutboxEvent: USER_REGISTERED`.
3. Il sync job del Central System invia a **tutti** i Local Server registrati:
   `PUT /internal/users/sync` → `[{ userId, username, hashedPassword, roles }]`.
4. Ogni Local Server salva nella tabella `replicated_users`.
5. Da quel momento, il login offline funziona su qualsiasi Local Server.

### 11.2 Flusso di Aggiornamento (Password/Ruoli)

1. Utente modifica password o ruoli → `PUT /api/users/{id}` → Central System.
2. Central System aggiorna l'utente nel DB, ri-esegue hash BCrypt se la password è cambiata, e crea un `OutboxEvent: USER_UPDATED`.
3. Il `UserReplicationSchedulerService` rileva l'evento PENDING e invia i dati aggiornati a **tutti** i Local Server registrati.
4. Ogni Local Server riceve la lista aggiornata tramite `PUT /internal/users/sync` e il `UserSyncService` esegue un **upsert** nella tabella `replicated_users`.
5. Da quel momento, il login offline usa la nuova password e i nuovi ruoli.

### 11.3 Gestione Conflitti

Ogni gioco fisico (`game_id`) è univoco e appartiene a un solo edificio. I conflitti di prenotazione inter-edificio non esistono. All'interno dello stesso edificio, il Local Server serializza le prenotazioni atomicamente nel DB locale, ed MQTT propaga lo stato aggiornato in tempo reale a tutti i client.

---

## 12. Orchestrazione Docker Compose

```yaml
version: '3.8'

services:
  # ============================================
  # INFRASTRUTTURA CENTRALE
  # ============================================
  central-db:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${CENTRAL_DB_PASSWORD:-root}
      MYSQL_DATABASE: central_db
    volumes:
      - ./infrastructure/mysql-central/init.sql:/docker-entrypoint-initdb.d/init.sql
      - central-db-data:/var/lib/mysql
    networks:
      - central-net

  central-system:
    build:
      context: ./central-system
      dockerfile: Dockerfile
    depends_on:
      - central-db
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://central-db:3306/central_db
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=${CENTRAL_DB_PASSWORD:-root}
      - JWT_PRIVATE_KEY_PATH=/certs/private.pem
      - INTERNAL_API_KEY=${INTERNAL_API_KEY}
    volumes:
      - ./infrastructure/tls:/certs:ro
    networks:
      - central-net
      - integration-net

  # ============================================
  # INFRASTRUTTURA LOCALE (Edificio 1)
  # ============================================
  local-db-1:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${LOCAL_DB_PASSWORD:-root}
      MYSQL_DATABASE: local_db
    volumes:
      - ./infrastructure/mysql-local/init.sql:/docker-entrypoint-initdb.d/init.sql
      - local-db-1-data:/var/lib/mysql
    networks:
      - local-net-1

  mqtt-broker-1:
    image: eclipse-mosquitto:2.0
    volumes:
      - ./infrastructure/mosquitto/mosquitto.conf:/mosquitto/config/mosquitto.conf
      - ./infrastructure/mosquitto/certs:/mosquitto/certs:ro
      - ./infrastructure/mosquitto/password_file:/mosquitto/config/password_file
    ports:
      - "8883:8883"
    networks:
      - local-net-1

  local-server-1:
    build:
      context: ./local-server
      dockerfile: Dockerfile
    depends_on:
      - local-db-1
      - mqtt-broker-1
    ports:
      - "8081:8080"
    environment:
      - BUILDING_ID=building-1
      - SPRING_DATASOURCE_URL=jdbc:mysql://local-db-1:3306/local_db
      - MQTT_BROKER_URL=ssl://mqtt-broker-1:8883
      - CENTRAL_SYSTEM_URL=https://central-system:8080
      - SYNC_INTERVAL_MS=300000
      - HEALTHCHECK_INTERVAL_MS=300000
      - JWT_LOCAL_PRIVATE_KEY_PATH=/certs/local-private.pem
      - JWT_LOCAL_PUBLIC_KEY_PATH=/certs/local-public.pem
      - INTERNAL_API_KEY=${INTERNAL_API_KEY}
    volumes:
      - ./infrastructure/tls:/certs:ro
    networks:
      - local-net-1
      - integration-net

  # ============================================
  # EMULATORI CLIENT (Endpoint)
  # ============================================
  game-client-1:
    build:
      context: ./game-client-emulator
      dockerfile: Dockerfile
    depends_on:
      - mqtt-broker-1
      - local-server-1
    environment:
      - GAME_ID=game-foosball-1
      - GAME_TYPE=FOOSBALL
      - BUILDING_ID=building-1
      - MQTT_BROKER_URL=ssl://mqtt-broker-1:8883
      - LOCAL_SERVER_URL=http://local-server-1:8080
      - MQTT_USERNAME=client-foosball-1
      - MQTT_PASSWORD=${GAME_CLIENT_MQTT_PASSWORD}
    networks:
      - local-net-1

  game-client-2:
    build:
      context: ./game-client-emulator
      dockerfile: Dockerfile
    depends_on:
      - mqtt-broker-1
      - local-server-1
    environment:
      - GAME_ID=game-chess-1
      - GAME_TYPE=CHESS
      - BUILDING_ID=building-1
      - MQTT_BROKER_URL=ssl://mqtt-broker-1:8883
      - LOCAL_SERVER_URL=http://local-server-1:8080
      - MQTT_USERNAME=client-chess-1
      - MQTT_PASSWORD=${GAME_CLIENT_MQTT_PASSWORD}
    networks:
      - local-net-1

volumes:
  central-db-data:
  local-db-1-data:
  mqtt-broker-1-data:     # Persistenza messaggi MQTT (QoS 1 in-flight al crash del broker)

# Nota: il file mosquitto.conf deve includere:
#   persistence true
#   persistence_location /mosquitto/data/
#   max_inflight_messages 20
#   max_queued_messages 1000
# Il container mqtt-broker-1 monta il volume mqtt-broker-1-data su /mosquitto/data/

networks:
  central-net:
  local-net-1:
  integration-net:
```

---

## 13. Scalabilità

Per aggiungere un nuovo edificio, è sufficiente replicare il blocco "Infrastruttura Locale" nel `docker-compose.yml` con un nuovo `BUILDING_ID`, una nuova rete `local-net-N`, un nuovo database locale e un nuovo broker MQTT. Il codice sorgente rimane invariato: la variabile d'ambiente `BUILDING_ID` parametrizza il comportamento del Local Server.

---

## 14. Dipendenze Maven

### 14.1 Parent POM (`pom.xml` radice)

```xml
<groupId>com.gameplatform</groupId>
<artifactId>boardgame-platform</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>pom</packaging>

<modules>
    <module>shared/shared-domain</module>
    <module>shared/shared-dto</module>
    <module>shared/shared-mqtt</module>
    <module>central-system</module>
    <module>local-server</module>
    <module>game-client-emulator</module>
</modules>

<properties>
    <java.version>17</java.version>
    <spring-boot.version>3.2.0</spring-boot.version>
    <paho.version>1.2.5</paho.version>
    <jjwt.version>0.12.3</jjwt.version>
</properties>
```

### 14.2 Dipendenze `local-server/pom.xml`

```xml
<dependencies>
    <!-- Moduli condivisi -->
    <dependency>
        <groupId>com.gameplatform</groupId>
        <artifactId>shared-domain</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>com.gameplatform</groupId>
        <artifactId>shared-dto</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>com.gameplatform</groupId>
        <artifactId>shared-mqtt</artifactId>
        <version>${project.version}</version>
    </dependency>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <!-- MQTT -->
    <dependency>
        <groupId>org.eclipse.paho</groupId>
        <artifactId>org.eclipse.paho.client.mqttv3</artifactId>
        <version>${paho.version}</version>
    </dependency>
    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>${jjwt.version}</version>
    </dependency>
    <!-- MySQL -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

---

## 15. Riepilogo delle Scelte Architetturali

| Decisione | Scelta | Motivazione |
|---|---|---|
| Pattern distribuito | Hub-and-Spoke + Pub/Sub | Resilienza offline, edge computing |
| Comunicazione centrale | REST over HTTPS (TLS) | Sincronizzazione asincrona, idempotenza |
| Comunicazione locale | MQTT over TLS | Disaccoppiamento, compatibilità IoT/ESP32 |
| Sincronizzazione offline | Transactional Outbox ogni 5 min | Atomicità, backlog automatico |
| Health check endpoint | Ping/pong MQTT ogni 5 min | Terminazione automatica partite orfane |
| Database | MySQL unico (per nodo) con colonna JSON | Generalizzazione giochi senza ALTER TABLE |
| Secondo database | No (MongoDB scartato) | Overkill, complessità non giustificata |
| Autenticazione | JWT (RSA per-nodo) + BCrypt | Login offline con chiave privata locale |
| Crittografia | TLS 1.3 su REST e MQTT | End-to-end encryption |
| Architettura interna | Esagonale (Ports & Adapters) | DIP, testabilità, indipendenza da framework |
| Monorepo | Maven Multi-Module con shared-* | Anti-duplicazione inter-microservizi |
| Modello giochi | Gerarchia interfacce + GameResult polimorfico | OCP, estensibilità senza modifiche DB |
| Game Client UI | JavaFX | Nativo Java, compatibile con Paho MQTT |
| Statistiche | Generate in locale, aggregate al centrale | Autonomia offline |
