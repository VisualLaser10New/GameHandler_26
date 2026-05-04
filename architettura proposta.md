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
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐     │
│  │ Auth Service │  │Stats Service│  │ Sync Coordinator │     │
│  └─────────────┘  └─────────────┘  └──────────────────┘     │
│                        REST / HTTPS (TLS)                    │
└────────────────┬──────────────────────────┬──────────────────┘
                 │                          │
     ┌───────────▼──────────┐   ┌───────────▼──────────┐
     │  LOCAL SERVER #1     │   │  LOCAL SERVER #2      │
     │  (Edificio A)        │   │  (Edificio B)         │
     │  ┌────────────────┐  │   │  ┌────────────────┐   │
     │  │ MySQL + Outbox │  │   │  │ MySQL + Outbox │   │
     │  └────────────────┘  │   │  └────────────────┘   │
     │  ┌────────────────┐  │   │  ┌────────────────┐   │
     │  │ MQTT Broker    │  │   │  │ MQTT Broker     │   │
     │  │ (TLS)          │  │   │  │ (TLS)           │   │
     │  └────────────────┘  │   │  └────────────────┘   │
     └──────────┬───────────┘   └──────────┬────────────┘
          MQTTS │                     MQTTS│
     ┌──────────▼──────────┐   ┌───────────▼───────────┐
     │ Game Client #1      │   │ Game Client #3         │
     │ Game Client #2      │   │ Game Client #4         │
     └─────────────────────┘   └───────────────────────┘
```

### 2.2 Componenti

*   **Central System (L'Hub):** Microservizio Spring Boot responsabile della *Source of Truth* globale: registrazione utenti, aggregazione statistiche cross-building, coordinamento sincronizzazione.

*   **Local Server (Lo Spoke / Edge Node):** Microservizio Spring Boot installato fisicamente in ogni edificio. Funziona come gateway e nodo di persistenza locale. Persiste gli stati dei giochi nel database locale e genera le statistiche localmente (partite completate, in corso, tempo di utilizzo). I dati aggregati delle statistiche vengono poi inviati al Central System.

*   **Endpoint (Game Clients):** Applicazioni client con interfaccia grafica (JavaFX) che comunicano **esclusivamente** con il Local Server del proprio edificio tramite MQTT over TLS. L'uso di MQTT disaccoppia i client dal server ed è compatibile con la futura integrazione ESP32/Arduino.

### 2.3 Health Check degli Endpoint

Il Local Server esegue un **health check periodico ogni 5 minuti** sugli endpoint connessi tramite MQTT (ping/pong su topic dedicato). Se un endpoint non risponde:

1. Le partite in esecuzione su quell'endpoint vengono terminate con stato `ABORTED` e motivo `ENDPOINT_UNREACHABLE`.
2. Lo stato del gioco fisico associato torna a `AVAILABLE`.
3. Viene generato un evento di allarme persistito nel DB locale e sincronizzato al Central System.

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
*   **Token:** **JWT** firmato con chiave asimmetrica (RSA-256). Il Central System detiene la chiave privata e firma i token; i Local Server detengono la chiave pubblica e validano i token offline.
*   **JWT Claims:** `{ sub: userId, buildingId, roles: [...], exp: timestamp }`.
*   **MQTT Auth:** Username/password per ogni Game Client, configurati nel broker Mosquitto tramite `password_file`.

### 3.3 Flusso di Autenticazione

```
Login Online:
  Client → POST /auth/login → Local Server → verifica BCrypt → emette JWT locale
  Local Server → (se online) valida anche con Central System

Login Offline:
  Client → POST /auth/login → Local Server → verifica BCrypt da tabella replicated_users → emette JWT

Accesso Risorsa:
  Client → GET /games (Header: Authorization: Bearer <JWT>) → Local Server → validazione firma + scadenza
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
building/{buildingId}/users/sync                   → Replicazione utenti dal Central System
building/{buildingId}/alerts                       → Allarmi (endpoint irraggiungibile, errori)
```

### 4.2 Pattern di Comunicazione

*   Il **Game Client** pubblica su `building/{id}/game/{gameId}/state` e `session/*` per notificare cambiamenti di stato.
*   Il **Local Server** si sottoscrive a `building/{id}/game/+/state` e `building/{id}/game/+/session/#` per aggiornare il DB locale e generare statistiche.
*   Il **Local Server** pubblica su `building/{id}/game/{gameId}/heartbeat/ack` in risposta ai ping.
*   Il **Local Server** pubblica su `building/{id}/users/sync` quando riceve nuovi dati utente dal Central System.
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
│   │       │   ├── UserId.java          ← Value Object (record Java)
│   │       │   ├── GameId.java
│   │       │   ├── BuildingId.java
│   │       │   ├── GameSessionId.java
│   │       │   ├── ReservationId.java
│   │       │   ├── GameType.java        ← Enum: CHESS, FOOSBALL, DARTS, MONOPOLY, RISK
│   │       │   ├── GameStatus.java      ← Enum: WAITING, IN_PROGRESS, PAUSED, COMPLETED, ABORTED
│   │       │   ├── WinCondition.java    ← Enum: WIN, DRAW, ABANDONED, TIMEOUT
│   │       │   └── StopReason.java      ← Enum: COMPLETED, ABORTED, TIMEOUT
│   │       ├── game/
│   │       │   ├── GameLifecycle.java    ← Interfaccia radice (start, stop, pause, resume)
│   │       │   ├── TurnBasedGame.java   ← Estensione per giochi a turni
│   │       │   ├── ScoredGame.java      ← Estensione per giochi a punteggio
│   │       │   ├── ResourceBasedGame.java ← Estensione per giochi a risorse
│   │       │   └── BoardGame.java       ← Estensione per giochi con board
│   │       ├── result/
│   │       │   ├── GameResult.java      ← Interfaccia (@JsonTypeInfo)
│   │       │   ├── FoosballResult.java  ← record
│   │       │   ├── ChessResult.java
│   │       │   ├── DartsResult.java
│   │       │   ├── MonopolyResult.java
│   │       │   └── RiskResult.java
│   │       └── events/
│   │           ├── UserRegisteredEvent.java
│   │           ├── ReservationCreatedEvent.java
│   │           └── GameStateChangedEvent.java
│   │
│   ├── shared-dto/                      ← DTO / Contratti API REST
│   │   ├── pom.xml                      ← dipende da shared-domain
│   │   └── src/main/java/com/gameplatform/shared/dto/
│   │       ├── UserDto.java
│   │       ├── ReservationDto.java
│   │       ├── GameStateDto.java
│   │       └── GameSessionDto.java
│   │
│   └── shared-mqtt/                     ← Costanti topic e payload tipizzati
│       ├── pom.xml                      ← dipende da shared-dto
│       └── src/main/java/com/gameplatform/shared/mqtt/
│           ├── MqttTopics.java          ← Costanti statiche dei topic
│           └── MqttPayload.java         ← Record per payload tipizzati
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
│       ├── domain/                      ← Implementazioni concrete dei giochi
│       ├── ui/                          ← JavaFX Scene Graph
│       ├── application/
│       └── infrastructure/mqtt/
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

### 6.2 Struttura Completa del Local Server

```
local-server/src/main/java/com/gameplatform/local/
│
├── domain/                              ← ZERO dipendenze da Spring/JPA
│   ├── model/
│   │   ├── Reservation.java            ← Rich Domain Model
│   │   ├── Game.java                   ← Macchina fisica + stato
│   │   ├── User.java                   ← Replica locale (dati essenziali)
│   │   ├── GameSession.java            ← Sessione di gioco con GameResult
│   │   └── OutboxEvent.java            ← Evento in coda per sync
│   ├── ports/
│   │   ├── in/                          ← Use Case Interfaces
│   │   │   ├── CreateReservationUseCase.java
│   │   │   ├── CancelReservationUseCase.java
│   │   │   ├── UpdateGameStateUseCase.java
│   │   │   ├── GetAvailableGamesUseCase.java
│   │   │   ├── StartGameSessionUseCase.java
│   │   │   └── EndGameSessionUseCase.java
│   │   └── out/                         ← Infrastructure Interfaces
│   │       ├── ReservationRepository.java
│   │       ├── GameRepository.java
│   │       ├── UserRepository.java
│   │       ├── GameSessionRepository.java
│   │       ├── OutboxEventRepository.java
│   │       ├── SyncCentralSystemPort.java
│   │       └── PublishGameStatePort.java
│   └── exception/
│       ├── GameNotAvailableException.java
│       └── UserNotFoundException.java
│
├── application/
│   └── service/
│       ├── ReservationService.java      ← Implementa CreateReservation/CancelReservation
│       ├── GameStateService.java        ← Implementa UpdateGameState
│       ├── GameSessionService.java      ← Implementa Start/EndGameSession
│       ├── StatisticsService.java       ← Genera statistiche locali
│       ├── SyncSchedulerService.java    ← Job @Scheduled: legge outbox, invia al Central
│       └── HealthCheckService.java      ← Job @Scheduled ogni 5 min: verifica endpoint
│
└── infrastructure/
    ├── adapters/
    │   ├── in/
    │   │   ├── rest/
    │   │   │   ├── ReservationController.java
    │   │   │   ├── GameController.java
    │   │   │   ├── StatisticsController.java
    │   │   │   └── InternalSyncController.java  ← Riceve sync dal Central
    │   │   └── mqtt/
    │   │       ├── GameStateListener.java
    │   │       ├── GameSessionListener.java
    │   │       ├── HeartbeatListener.java
    │   │       └── UserSyncListener.java
    │   └── out/
    │       ├── mysql/
    │       │   ├── entity/              ← @Entity JPA (separate dal domain model)
    │       │   ├── repository/          ← extends JpaRepository
    │       │   └── adapter/             ← Implementa porte di dominio (mapper incluso)
    │       ├── rest/
    │       │   └── CentralSystemRestAdapter.java
    │       └── mqtt/
    │           └── MqttPublisherAdapter.java
    └── config/
        ├── MqttConfig.java
        ├── SecurityConfig.java
        ├── TlsConfig.java
        └── SchedulerConfig.java
```

### 6.3 Mapping Domain ↔ JPA Entity

Per evitare che il dominio dipenda da JPA, ogni entità ha una **controparte infrastrutturale**:

*   `domain/model/Reservation.java` — POJO puro con logica di business (es. `canBeCancelled()`).
*   `infrastructure/out/mysql/entity/ReservationJpaEntity.java` — Classe con annotazioni `@Entity`, `@Table`, `@Id`. Nessuna logica di business.
*   `infrastructure/out/mysql/adapter/ReservationRepositoryAdapter.java` — Implementa `ReservationRepository` (porta di dominio), usa `ReservationJpaRepository` (Spring Data) e converte tramite mapper esplicito.

```java
// Esempio: domain/model/Reservation.java (PURO)
public class Reservation {
    private final ReservationId id;
    private final GameId gameId;
    private final UserId userId;
    private ReservationStatus status;
    private final Instant createdAt;

    public boolean canBeCancelled() {
        return status == ReservationStatus.PENDING &&
               createdAt.isAfter(Instant.now().minus(Duration.ofHours(24)));
    }
}

// Esempio: infrastructure/out/mysql/adapter/ReservationRepositoryAdapter.java
@Component
public class ReservationRepositoryAdapter implements ReservationRepository {
    private final ReservationJpaRepository jpaRepo;

    @Override
    public Optional<Reservation> findById(ReservationId id) {
        return jpaRepo.findById(id.value()).map(ReservationMapper::toDomain);
    }
}
```

---

## 7. Sincronizzazione Offline (Transactional Outbox Pattern)

### 7.1 Principio

Il Local Server sincronizza i dati con il Central System **ogni 5 minuti**. Se la connessione non è disponibile, i dati vengono accumulati in un backlog locale (tabella `outbox_events`) e inviati al ripristino della connessione.

### 7.2 Meccanismo

1. Quando si verifica un evento locale (prenotazione, fine sessione, statistica generata), l'entità viene salvata nel DB locale.
2. **Nella stessa transazione atomica**, si inserisce un record nella tabella `outbox_events` (per lo schema DDL si veda la Sezione 10.2).
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

1. Il Game Client pubblica su MQTT `building/{id}/game/{gameId}/session/start` all'inizio di una partita.
2. Il Local Server riceve l'evento, crea una `GameSession` nel DB locale con `status = IN_PROGRESS`.
3. Durante la partita, gli aggiornamenti di stato transitano via MQTT.
4. A fine partita, il Game Client pubblica su `session/end` con il `result_data` specifico del gioco.
5. Il Local Server aggiorna la `GameSession`: `status = COMPLETED`, `ended_at`, `duration_s`, `winner_id`, `result_data`.
6. Il `StatisticsService` locale genera aggregazioni: partite per tipo, durata media, win rate per utente, preferenze.
7. Le statistiche aggregate vengono inserite nell'`outbox_events` e inviate al Central System al prossimo ciclo di sync.

---

## 9. Modellazione Generica dei Giochi (Interfacce di Dominio)

### 9.1 Principio (Scelta Utente)

Poiché il sistema deve gestire giochi radicalmente diversi (calciobalilla, scacchi, freccette, monopoli, risiko), si implementa una **gerarchia di interfacce** nel modulo `shared-domain` che definisce i diversi stati e le diverse azioni eseguibili.

### 9.2 Analisi dei Giochi Supportati

| Gioco | Metrica di Vittoria | Contabilità Interna | Turni | Interfacce Applicabili |
|---|---|---|---|---|
| **Calciobalilla** | Gol (es. 5-3) | Nessuna | No | `ScoredGame` |
| **Scacchi** | Scacco matto / abbandono | Pezzi catturati | Sì | `BoardGame` |
| **Freccette** | Chi scende prima a 0 (501) | Punteggio decrescente | Sì | `ScoredGame`, `TurnBasedGame` |
| **Monopoli** | Ultimo non in bancarotta | Denaro + proprietà | Sì | `ResourceBasedGame` |
| **Risiko** | Conquista totale | Carri armati per territorio | Sì | `ResourceBasedGame`, `BoardGame` |

Questi giochi hanno strutture dati di risultato **incompatibili tra loro**, rendendo impossibile un modello dati unificato a colonne fisse. La soluzione adottata è la combinazione di interfacce polimorfiche Java (per la type safety a compile-time) e colonna JSON nel database (per la flessibilità a runtime).

### 9.3 Gerarchia delle Interfacce

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

/** Giochi a turni (Scacchi, Monopoli, Risiko, Freccette). */
public interface TurnBasedGame extends GameLifecycle {
    UserId getCurrentPlayer();
    void endTurn();
    int getTurnNumber();
}

/** Giochi con punteggio numerico (Calciobalilla, Freccette). */
public interface ScoredGame extends GameLifecycle {
    Map<UserId, Integer> getCurrentScores();
    void recordScore(UserId player, int delta);
}

/** Giochi con risorse multiple per giocatore (Monopoli, Risiko). */
public interface ResourceBasedGame extends TurnBasedGame {
    Map<UserId, Map<String, Integer>> getResources();
    void updateResource(UserId player, String resourceKey, int newValue);
}

/** Giochi con board/mappa (Scacchi, Risiko). */
public interface BoardGame extends TurnBasedGame {
    String serializeBoardState();
    void restoreBoardState(String serializedState);
}
```

### 9.4 Implementazioni Concrete (nel Game Client)

```java
public class FoosballGame implements ScoredGame { ... }
public class ChessGame implements BoardGame { ... }
public class DartsGame implements ScoredGame, TurnBasedGame { ... }
public class MonopolyGame implements ResourceBasedGame { ... }
public class RiskGame implements ResourceBasedGame, BoardGame { ... }
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
    String getWinnerId();
    WinCondition getWinCondition();
}

// Esempi di implementazione (record Java)
public record FoosballResult(String winnerId, Map<String, Integer> finalScores,
                              WinCondition winCondition) implements GameResult {}

public record ChessResult(String winnerId, String terminationReason,
                           String finalFenState, WinCondition winCondition) implements GameResult {}

public record MonopolyResult(String winnerId, Map<String, Integer> finalMoney,
                              Map<String, List<String>> ownedProperties,
                              WinCondition winCondition) implements GameResult {}
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
    INDEX idx_user (user_id)
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
public class GameSessionMapper {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static GameSession toDomain(GameSessionJpaEntity entity) {
        GameResult result = null;
        if (entity.getResultData() != null) {
            result = objectMapper.readValue(entity.getResultData(), GameResult.class);
            // Jackson usa @JsonTypeInfo.property="type" per istanziare il sottotipo corretto
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

    public static GameSessionJpaEntity toEntity(GameSession domain) {
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

### 11.1 Flusso

1. Utente si registra → `POST /api/users` → Central System.
2. Central System salva l'utente e crea un `OutboxEvent: USER_REGISTERED`.
3. Il sync job del Central System invia a **tutti** i Local Server registrati:
   `PUT /internal/users/sync` → `[{ userId, username, hashedPassword, roles }]`.
4. Ogni Local Server salva nella tabella `replicated_users`.
5. Da quel momento, il login offline funziona su qualsiasi Local Server.

### 11.2 Gestione Conflitti

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
      - JWT_PUBLIC_KEY_PATH=/certs/public.pem
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
    environment:
      - GAME_ID=game-foosball-1
      - GAME_TYPE=FOOSBALL
      - BUILDING_ID=building-1
      - MQTT_BROKER_URL=ssl://mqtt-broker-1:8883
    networks:
      - local-net-1

  game-client-2:
    build:
      context: ./game-client-emulator
      dockerfile: Dockerfile
    depends_on:
      - mqtt-broker-1
    environment:
      - GAME_ID=game-chess-1
      - GAME_TYPE=CHESS
      - BUILDING_ID=building-1
      - MQTT_BROKER_URL=ssl://mqtt-broker-1:8883
    networks:
      - local-net-1

volumes:
  central-db-data:
  local-db-1-data:

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
| Autenticazione | JWT + BCrypt | Login offline con chiave pubblica |
| Crittografia | TLS 1.3 su REST e MQTT | End-to-end encryption |
| Architettura interna | Esagonale (Ports & Adapters) | DIP, testabilità, indipendenza da framework |
| Monorepo | Maven Multi-Module con shared-* | Anti-duplicazione inter-microservizi |
| Modello giochi | Gerarchia interfacce + GameResult polimorfico | OCP, estensibilità senza modifiche DB |
| Game Client UI | JavaFX | Nativo Java, compatibile con Paho MQTT |
| Statistiche | Generate in locale, aggregate al centrale | Autonomia offline |
