# Report Completo del Protocollo MQTT — Piattaforma Giochi da Tavolo e da Bar

> **Versione:** 1.1 | **Data:** 2026-07-21
> **Scope:** Documentazione esaustiva di ogni topic MQTT, payload JSON, schema di pubblicazione/sottoscrizione e flusso dei messaggi scambiati tra Game Client, Local Server e Broker MQTT.
> **Documenti di riferimento:** Codice sorgente (`shared-mqtt`, `local-server`, `game-client-emulator`).

---

## Indice

1. [Panoramica e Modello di Deploy](#1-panoramica-e-modello-di-deploy)
2. [Sicurezza, Autenticazione e Qualità del Servizio](#2-sicurezza-autenticazione-e-qualità-del-servizio)
3. [Convenzioni di Naming dei Topic](#3-convenzioni-di-naming-dei-topic)
4. [Topic MQTT — Schema Completo](#4-topic-mqtt--schema-completo)
   - 4.1 [Game State](#41-game-state)
   - 4.2 [Session Lifecycle](#42-session-lifecycle)
   - 4.3 [Lobby Management](#43-lobby-management)
   - 4.4 [Multiplayer Sync (Turni, Mosse, Punteggi)](#44-multiplayer-sync-turni-mosse-punteggi)
   - 4.5 [Heartbeat](#45-heartbeat)
   - 4.6 [Alerts](#46-alerts)
5. [Payload JSON — Schemi e Record](#5-payload-json--schemi-e-record)
   - 5.1 [GameStatePayload](#51-gamestatepayload)
   - 5.2 [SessionStartPayload](#52-sessionstartpayload)
   - 5.3 [SessionEndPayload](#53-sessionendpayload)
   - 5.4 [SessionPausePayload](#54-sessionpausepayload)
   - 5.5 [SessionResumePayload](#55-sessionresumepayload)
   - 5.6 [LobbyCreatePayload](#56-lobbycreatepayload)
   - 5.7 [LobbyJoinPayload / LobbyLeavePayload / LobbyCancelPayload](#57-lobbyjoinpayload--lobbyleavepayload--lobbycancelpayload)
   - 5.8 [LobbyStartPayload](#58-lobbystartpayload)
   - 5.9 [TurnPayload](#59-turnpayload)
   - 5.10 [MovePayload](#510-movepayload)
   - 5.11 [ScorePayload](#511-scorepayload)
   - 5.12 [HeartbeatPayload / HeartbeatAckPayload](#512-heartbeatpayload--heartbeatackpayload)
   - 5.13 [AlertPayload](#513-alertpayload)
   - 5.14 [GameResult Polimorfico (resultData)](#514-gameresult-polimorfico-resultdata)
6. [Sottoscrizioni MQTT](#6-sottoscrizioni-mqtt)
   - 6.1 [Local Server](#61-local-server)
   - 6.2 [Game Client](#62-game-client)
7. [Flussi End-to-End](#7-flussi-end-to-end)
   - 7.1 [Prenotazione → Avvio Sessione](#71-prenotazione--avvio-sessione)
   - 7.2 [Heartbeat e Health Check](#72-heartbeat-e-health-check)
   - 7.3 [Lobby Multiplayer](#73-lobby-multiplayer)
   - 7.4 [Pausa/Ripresa e Termine Sessione](#74-pausaripresa-e-termine-sessione)
8. [Note di Implementazione e Vincoli](#8-note-di-implementazione-e-vincoli)

---

## 1. Panoramica e Modello di Deploy

Il sistema utilizza MQTT come protocollo di comunicazione **real-time** tra i componenti locali (Game Client e Local Server), in complemento a REST/HTTPS per le operazioni transazionali.

| Componente | Ruolo MQTT | Connessione |
|---|---|---|
| **MQTT Broker** (Mosquitto) | Broker di messaggi locale per edificio | Porta `8883` (MQTTS con mTLS) |
| **Local Server** | Pubblica stato giochi, eventi sessione, heartbeat PING, alert | Client MQTT (Paho) |
| **Game Client** (JavaFX) | Pubblica eventi sessione, heartbeat, mosse/turni/punteggi; riceve aggiornamenti stato | Client MQTT (Paho) |

Ogni edificio ha il proprio broker MQTT isolato (`mqtt-broker-1`, `mqtt-broker-2`, `mqtt-broker-3`). Il **Game Client comunica esclusivamente** con il broker del proprio edificio. Non esiste routing MQTT cross-edificio.

---

## 2. Sicurezza, Autenticazione e Qualità del Servizio

### 2.1 Trasporto: MQTTS con mTLS

- Tutte le connessioni MQTT sono su **TLS 1.3** (`ssl://mqtt-broker-N:8883`).
- **Mutual TLS**: sia il Local Server sia i Game Client presentano un certificato X.509 firmato dalla **Local CA** (Certificate Authority locale).
- I certificati client sono ottenuti tramite **CSR** su `POST /api/devices/register` (endpoint REST).
- Il broker Mosquitto è configurato con `use_identity_as_username true`: il CN del certificato diventa lo username MQTT.
- Fallback a `tcp://localhost:1883` (no TLS) solo in sviluppo locale.

### 2.2 Qualità del Servizio (QoS)

| Categoria | QoS | Valore | Comportamento |
|---|---|---|---|
| `STATE` | 1 (At Least Once) | `MqttQos.STATE = 1` | Consegna garantita, possibili duplicati; retained = true |
| `SESSION` | 1 (At Least Once) | `MqttQos.SESSION = 1` | Consegna garantita, possibili duplicati; retained = false |
| `HEARTBEAT` | 0 (At Most Once) | `MqttQos.HEARTBEAT = 0` | Fire-and-forget, nessuna conferma — usato dal client per pubblicare heartbeat regolari. Il server pubblica heartbeat PING e ACK tramite `publishSessionEvent()` (QoS SESSION = 1) |

### 2.3 Deduplicazione degli Echo (OutboundMessageDeduplicationCache)

Il Local Server è iscritto agli stessi topic su cui pubblica. Per evitare il loopback (`echo`), l'`OutboundMessageDeduplicationCache` registra la coppia `(topic, payload)` di ogni messaggio pubblicato. I messaggi in ingresso che corrispondono a una pubblicazione recente vengono scartati prima dell'elaborazione.

---

## 3. Convenzioni di Naming dei Topic

Tutti i topic seguono la struttura gerarchica:

```
building/{buildingId}/game/{gameId}/{categoria}[/{sotto-categoria}]
```

- `{buildingId}` — identificativo dell'edificio (es. `building-1`)
- `{gameId}` — identificativo della macchina da gioco (es. `foosball-01`)
- `+` — wildcard MQTT per un singolo livello
- `#` — wildcard MQTT per livelli multipli (multilevel)

---

## 4. Topic MQTT — Schema Completo

### 4.1 Game State

| Topic | Direzione | QoS | Retained | Descrizione |
|---|---|---|---|---|
| `building/{bId}/game/{gId}/state` | Client ↔ Server | 1 | Sì | Pubblicazione/cambio stato macchina da gioco (`AVAILABLE`, `RESERVED`, `IN_USE`, `LOBBY`, `MAINTENANCE`) |

Pubblicato da: `GameStatePublisher` (client), `MqttPublisherAdapter.publishState` (server)
Metodo costruttore: `MqttTopics.gameState(buildingId, gameId)`

### 4.2 Session Lifecycle

| Topic | Direzione | QoS | Retained | Descrizione |
|---|---|---|---|---|
| `building/{bId}/game/{gId}/session/start` | Client → Server (+ broadcast) | 1 | No | Avvio sessione di gioco (`IN_PROGRESS`) |
| `building/{bId}/game/{gId}/session/end` | Client → Server (+ broadcast) | 1 | No | Termine sessione (`COMPLETED`) |
| `building/{bId}/game/{gId}/session/pause` | Client → Server (+ broadcast) | 1 | No | Pausa sessione |
| `building/{bId}/game/{gId}/session/resume` | Client → Server (+ broadcast) | 1 | No | Ripresa sessione |

Pubblicato da: `SessionPublisher` (client), `MqttPublisherAdapter.publishSessionEvent` (server)
Metodo costruttore: `MqttTopics.sessionStart/Building/End/Pause/Resume(buildingId, gameId)`

### 4.3 Lobby Management

| Topic | Direzione | QoS | Retained | Descrizione |
|---|---|---|---|---|
| `building/{bId}/game/{gId}/session/lobby/create` | Client ↔ Server | 1 | No | Creazione lobby |
| `building/{bId}/game/{gId}/session/lobby/join` | Client ↔ Server | 1 | No | Partecipazione a lobby |
| `building/{bId}/game/{gId}/session/lobby/leave` | Client ↔ Server | 1 | No | Abbandono lobby |
| `building/{bId}/game/{gId}/session/lobby/start` | Client ↔ Server | 1 | No | Avvio partita dalla lobby |
| `building/{bId}/game/{gId}/session/lobby/cancel` | Client ↔ Server | 1 | No | Cancellazione lobby |

Pubblicato da: `SessionPublisher` (client), `GameSessionService` tramite `MqttPublisherAdapter` (server)
Nota: questi topic NON sono definiti come costanti in `MqttTopics.java` ma vengono costruiti inline.

### 4.4 Multiplayer Sync (Turni, Mosse, Punteggi)

| Topic | Direzione | QoS | Retained | Descrizione |
|---|---|---|---|---|
| `building/{bId}/game/{gId}/session/turn` | Client ↔ Client | 1 | No | Cambio turno (giochi a turni: Scacchi, Risiko, ecc.) |
| `building/{bId}/game/{gId}/session/move` | Client ↔ Client | 1 | No | Mossa sulla scacchiera (attualmente Chess) |
| `building/{bId}/game/{gId}/session/score` | Client ↔ Client | 1 | No | Istantanea punteggi (Darts, Foosball) |

Pubblicato da: `SessionPublisher` (client) — scambio **peer-to-peer** tra client.
Il server locale NON gestisce la logica di turno/mossa/punteggio: si limita a inoltrarli via `session/#`.
Metodo costruttore: `MqttTopics.sessionTurn/Move/Score(buildingId, gameId)`

### 4.5 Heartbeat

| Topic | Direzione | QoS | Retained | Descrizione |
|---|---|---|---|---|---|
| `building/{bId}/game/{gId}/heartbeat` | Client ↔ Server | 0/1 | No | Heartbeat client-initiated (request, QoS 0) o server-initiated (PING, QoS 1) |
| `building/{bId}/game/{gId}/heartbeat/ack` | Client ↔ Server | 0/1 | No | ACK heartbeat: server → client per client-initiated (QoS 1), client → server per PING (QoS 0) |

Pubblicato da: `HeartbeatPublisher.publishHeartbeat` (client, QoS 0), `HealthCheckService` e `SessionRecoveryService` tramite `MqttPublisherAdapter.publishSessionEvent` (server, QoS 1)
Metodo costruttore: `MqttTopics.heartbeat(buildingId, gameId)`, `MqttTopics.heartbeatAck(buildingId, gameId)`

**Flusso heartbeat:**
- **Client-Initiated (normale):** Client pubblica `HeartbeatPayload` (JSON) su `heartbeat` (QoS 0) → Server registra heartbeat e pubblica `HeartbeatAckPayload` (JSON) su `heartbeat/ack` (QoS 1 via `publishSessionEvent`)
- **Server-Initiated (health check ogni 5 min):** Server pubblica la stringa JSON `"PING"` su `heartbeat` (QoS 1) → Client risponde con la stringa `"PONG"` su `heartbeat/ack` (QoS 0)
- **Server-Initiated (recovery al boot):** `SessionRecoveryService` pubblica la stringa JSON `"RECOVERY_PING"` su `heartbeat` (QoS 1) → Client risponde con `"PONG"` su `heartbeat/ack` (QoS 0)
- 3 cicli consecutivi mancati (15 min) → sessione abortita, alert pubblicato

### 4.6 Alerts

| Topic | Direzione | QoS | Retained | Descrizione |
|---|---|---|---|---|
| `building/{bId}/alerts` | Server → Client | 1 | No | Alert a livello di edificio (es. macchina non raggiungibile) |

Metodo costruttore: `MqttTopics.alerts(buildingId)`

---

## 5. Payload JSON — Schemi e Record

Tutti i payload sono serializzati come JSON tramite `MqttPayloadSerializer` (ObjectMapper con `JavaTimeModule`, date ISO-8601, `FAIL_ON_UNKNOWN_PROPERTIES = false`).

### 5.1 GameStatePayload

Pubblicato su: `building/{bId}/game/{gId}/state`

```json
{
  "gameId": "foosball-01",
  "status": "AVAILABLE",
  "userId": null
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `gameId` | string | sì | Identificativo della macchina da gioco |
| `status` | enum | sì | `AVAILABLE`, `RESERVED`, `IN_USE`, `MAINTENANCE`, `LOBBY` |
| `userId` | string\|null | no | Utente che ha causato il cambio stato (opzionale) |

Java record: `GameStatePayload(String gameId, GameMachineStatus status, String userId)`

### 5.2 SessionStartPayload

Pubblicato su: `building/{bId}/game/{gId}/session/start`

```json
{
  "sessionId": "uuid-della-sessione",
  "gameType": "CHESS",
  "participants": ["user-1", "user-2"]
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `sessionId` | string | sì | UUID della sessione |
| `gameType` | enum | sì | `CHESS`, `FOOSBALL`, `DARTS`, `MONOPOLY`, `RISK`, `SLOT_MACHINE`, `ROULETTE` |
| `participants` | string[] | sì | Lista ID partecipanti (può essere vuota) |

Java record: `SessionStartPayload(String sessionId, GameType gameType, List<String> participants)`

### 5.3 SessionEndPayload

Pubblicato su: `building/{bId}/game/{gId}/session/end`

```json
{
  "sessionId": "uuid-della-sessione",
  "winnerId": "user-vincitore",
  "winCondition": "WIN",
  "resultData": "{\"type\":\"CHESS\",\"winnerId\":\"...\",\"terminationReason\":\"CHECKMATE\",\"finalFenState\":\"...\",\"winCondition\":\"WIN\"}"
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `sessionId` | string | sì | UUID della sessione |
| `winnerId` | string\|null | no | ID vincitore (null in caso di pareggio/abbandono) |
| `winCondition` | enum | sì | `WIN`, `DRAW`, `ABANDONED`, `TIMEOUT`, `TEAM_VICTORY` |
| `resultData` | string\|null | no | JSON del risultato polimorfico (GameResult serializzato) |

Java record: `SessionEndPayload(String sessionId, String winnerId, WinCondition winCondition, String resultData)`

### 5.4 SessionPausePayload

Pubblicato su: `building/{bId}/game/{gId}/session/pause`

```json
{
  "sessionId": "uuid-della-sessione",
  "pausedBy": "user-che-ha-messo-in-pausa"
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `sessionId` | string | sì | UUID della sessione |
| `pausedBy` | string\|null | no | Utente che ha richiesto la pausa |

Java record: `SessionPausePayload(String sessionId, String pausedBy)`

### 5.5 SessionResumePayload

Pubblicato su: `building/{bId}/game/{gId}/session/resume`

```json
{
  "sessionId": "uuid-della-sessione"
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `sessionId` | string | sì | UUID della sessione |

Java record: `SessionResumePayload(String sessionId)`

### 5.6 LobbyCreatePayload

Pubblicato su: `building/{bId}/game/{gId}/session/lobby/create`

```json
{
  "gameType": "CHESS",
  "creatorId": "user-creatore"
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `gameType` | enum | sì | Tipo di gioco per la lobby |
| `creatorId` | string | sì | ID del creatore |

Java record: `LobbyCreatePayload(GameType gameType, String creatorId)`

### 5.7 LobbyJoinPayload / LobbyLeavePayload / LobbyCancelPayload

Pubblicato su: `building/{bId}/game/{gId}/session/lobby/{join,leave,cancel}`

**Client → Server (request):**
```json
{
  "sessionId": "uuid-della-sessione",
  "userId": "user-id"
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `sessionId` | string | sì | UUID della sessione |
| `userId` | string | sì | ID utente che compie l'azione |

Java records: `LobbyJoinPayload(String sessionId, String userId)`, `LobbyLeavePayload(String sessionId, String userId)`, `LobbyCancelPayload(String sessionId, String userId)`

**Server → Client (echo — solo per `lobby/cancel`):**
Il server pubblica l'intero oggetto `GameSession` serializzato (non un `LobbyCancelPayload` strutturato), perché il topic `lobby/cancel` non matcha i costruttori di payload tipizzati in `MqttPublisherAdapter.publishSessionEvent()`. I client che ricevono l'echo di cancellazione devono deserializzare il `GameSession` JSON risultante.

**Server → Client (echo — `lobby/join` e `lobby/leave`):**
Il server ripubblica rispettivamente `LobbyJoinPayload` e `LobbyLeavePayload` identici alla richiesta.

### 5.8 LobbyStartPayload

Pubblicato su: `building/{bId}/game/{gId}/session/lobby/start`

```json
{
  "sessionId": "uuid-della-sessione"
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `sessionId` | string | sì | UUID della sessione |

Java record: `LobbyStartPayload(String sessionId)`

### 5.9 TurnPayload

Pubblicato su: `building/{bId}/game/{gId}/session/turn`

```json
{
  "sessionId": "uuid-della-sessione",
  "turnIndex": 3,
  "playerName": "giocatore-attivo"
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `sessionId` | string | sì | UUID della sessione |
| `turnIndex` | int | sì | Indice turno (0-based) |
| `playerName` | string | sì | Giocatore di turno |

Java record: `TurnPayload(String sessionId, int turnIndex, String playerName)`

### 5.10 MovePayload

Pubblicato su: `building/{bId}/game/{gId}/session/move`

```json
{
  "sessionId": "uuid-della-sessione",
  "fromRow": 1,
  "fromCol": 0,
  "toRow": 3,
  "toCol": 0,
  "capturedPiece": "♟"
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `sessionId` | string | sì | UUID della sessione |
| `fromRow` | int | sì | Riga origine (0-based) |
| `fromCol` | int | sì | Colonna origine (0-based) |
| `toRow` | int | sì | Riga destinazione (0-based) |
| `toCol` | int | sì | Colonna destinazione (0-based) |
| `capturedPiece` | string\|null | no | Glifo Unicode pezzo catturato (opzionale) |

Java record: `MovePayload(String sessionId, int fromRow, int fromCol, int toRow, int toCol, String capturedPiece)`

### 5.11 ScorePayload

Pubblicato su: `building/{bId}/game/{gId}/session/score`

```json
{
  "sessionId": "uuid-della-sessione",
  "scores": {
    "player-1": 10,
    "player-2": 7
  }
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `sessionId` | string | sì | UUID della sessione |
| `scores` | map | sì | Mappa `{playerId: punteggio}` (snapshot completo, non delta) |

Java record: `ScorePayload(String sessionId, Map<String, Integer> scores)`

### 5.12 HeartbeatPayload / HeartbeatAckPayload

Pubblicato su: `building/{bId}/game/{gId}/heartbeat` e `building/{bId}/game/{gId}/heartbeat/ack`

**HeartbeatPayload (client → server):**
```json
{
  "gameId": "foosball-01",
  "timestamp": "2026-07-21T10:30:00Z"
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `gameId` | string | sì | ID macchina da gioco |
| `timestamp` | ISO-8601 | sì | Istante di generazione |

Java record: `HeartbeatPayload(String gameId, Instant timestamp)`

**HeartbeatAckPayload (server → client):**
```json
{
  "gameId": "foosball-01",
  "serverTimestamp": "2026-07-21T10:30:00.123Z"
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `gameId` | string | sì | ID macchina da gioco |
| `serverTimestamp` | ISO-8601 | sì | Istante del server al momento dell'ACK |

Java record: `HeartbeatAckPayload(String gameId, Instant serverTimestamp)`

**Server-Initiated PING/PONG:**
- Il server pubblica la stringa `"PING"` come payload JSON (serializzata da Jackson → `"\"PING\""`) sul topic `heartbeat`, tramite `MqttPublisherAdapter.publishSessionEvent()` (QoS SESSION = 1)
- `SessionRecoveryService` all'avvio pubblica `"RECOVERY_PING"` con lo stesso meccanismo
- Il client risponde con la stringa `"PONG"` sul topic `heartbeat/ack`

### 5.13 AlertPayload

Pubblicato su: `building/{bId}/alerts`

```json
{
  "alertType": "UNREACHABLE",
  "gameId": "foosball-01",
  "message": "Client has missed 3 consecutive heartbeat cycles (15 minutes). Declaring unreachable.",
  "timestamp": "2026-07-21T10:45:00Z"
}
```

| Campo | Tipo | Obbligatorio | Descrizione |
|---|---|---|---|
| `alertType` | string | sì | Tipo di alert (es. `UNREACHABLE`) |
| `gameId` | string | sì | ID macchina da gioco |
| `message` | string | sì | Messaggio descrittivo |
| `timestamp` | ISO-8601 | sì | Istante di generazione |

Java record: `AlertPayload(String alertType, String gameId, String message, Instant timestamp)`

### 5.14 GameResult Polimorfico (resultData)

Il campo `resultData` di `SessionEndPayload` contiene un JSON annidato con discriminatore `type` per il polimorfismo (`@JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")`).

**Sottotipi supportati:**

| `type` | Classe | Campi specifici |
|---|---|---|
| `CHESS` | `ChessResult` | `winnerId`, `winnerIds`, `terminationReason`, `finalFenState`, `winCondition` |
| `DARTS` | `DartsResult` | — |
| `FOOSBALL` | `FoosballResult` | `winnerId`, `winnerIds`, `finalScores` (Map), `winCondition` |
| `MONOPOLY` | `MonopolyResult` | — |
| `RISK` | `RiskResult` | — |
| `ROULETTE` | `RouletteResult` | — |
| `SLOT` | `SlotResult` | — |
| `TEAM` | `TeamResult` | — |

**Esempio con ChessResult:**
```json
{
  "sessionId": "uuid",
  "winnerId": "user-1",
  "winCondition": "WIN",
  "resultData": "{\"type\":\"CHESS\",\"winnerId\":\"user-1\",\"winnerIds\":[\"user-1\"],\"terminationReason\":\"CHECKMATE\",\"finalFenState\":\"rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1\",\"winCondition\":\"WIN\"}"
}
```

**Esempio con FoosballResult:**
```json
{
  "sessionId": "uuid",
  "winnerId": "team-1",
  "winCondition": "TEAM_VICTORY",
  "resultData": "{\"type\":\"FOOSBALL\",\"winnerId\":\"team-1\",\"winnerIds\":[\"player-a\",\"player-b\"],\"finalScores\":{\"player-a\":5,\"player-b\":3},\"winCondition\":\"TEAM_VICTORY\"}"
}
```

---

## 6. Sottoscrizioni MQTT

### 6.1 Local Server

Il `MqttConfig` (local-server) sottoscrive i seguenti topic al `connectComplete`:

| Pattern di Sottoscrizione | QoS | Listener | Descrizione |
|---|---|---|---|
| `building/{buildingId}/game/+/state` | 1 | `GameStateListener` | Aggiornamenti stato macchine da gioco |
| `building/{buildingId}/game/+/session/#` | 1 | `GameSessionListener` | Eventi sessione (start/end/pause/resume) e lobby (create/join/leave/start/cancel) |
| `building/{buildingId}/game/+/heartbeat` | 0 | `HeartbeatListener` | Heartbeat request (client-initiated) e PING (server-initiated echo) |
| `building/{buildingId}/game/+/heartbeat/ack` | 0 | `HeartbeatListener` | Heartbeat ACK (risposta a PING o echo di ACK server) |

Il `buildingId` è sostituito con l'ID dell'edificio corrente tramite la variabile `${app.building-id}`.

**Deduplicazione:** Ogni messaggio in ingresso viene verificato contro `OutboundMessageDeduplicationCache` prima di essere inoltrato al listener. Se corrisponde a un messaggio pubblicato recentemente dallo stesso server, viene scartato per evitare l'echo loopback.

### 6.2 Game Client

Lo `StateSubscriber` (game-client-emulator) sottoscrive:

| Pattern di Sottoscrizione | QoS | Metodo | Descrizione |
|---|---|---|---|
| `building/{buildingId}/game/+/state` | 1 | `subscribeToStates()` | Aggiornamenti stato (qualsiasi gioco) |
| `building/{buildingId}/game/+/session/+` | 1 | `subscribeToSessionEvents()` | Eventi sessione (qualsiasi gioco) — start, end, pause, resume, turn, move, score |
| `building/{buildingId}/game/+/heartbeat` | 0 | `subscribeToHeartbeats()` | Heartbeat PING dal server |
| `building/{buildingId}/game/{gameId}/session/lobby/+` | 1 | `subscribeToLobbyEvents(gameId)` | Eventi lobby per gioco specifico (create, join, leave, start, cancel) |
| `building/{buildingId}/game/+/heartbeat/ack` | 0 | `subscribeToHeartbeatAcks()` | Heartbeat ACK dal server |

Nota: la sottoscrizione `session/+` (singolo `+`) è più specifica di `session/#` (multilivello) e copre solo i topic a 1 livello sotto `session/`. I topic lobby (`lobby/create`, ecc.) hanno 2 livelli sotto `session/`, quindi sono coperti solo dalla sottoscrizione specifica `subscribeToLobbyEvents`.

---

## 7. Flussi End-to-End

### 7.1 Prenotazione → Avvio Sessione

```
POST /api/reservations (REST/HTTPS)
  → DB: Reservation CREATED, GameMachine → RESERVED
  → MQTT: publishState(building/{bId}/game/{gId}/state, RESERVED) [retained]
  → Outbox: RESERVATION_CREATED

Client pubblica su MQTT (dopo che il giocatore prende possesso):
  MQTT: session/start { sessionId, gameType, participants }
  → Server riceve, deserializza, avvia GameSession IN_PROGRESS
  → Server pubblica state IN_USE [retained]
  → Server broadcast session/start (echo)
  → Outbox: GAME_SESSION_COMPLETED (al termine)
```

### 7.2 Heartbeat e Health Check

**Client-Initiated (normale):**
```
Client → MQTT heartbeat/{gameId}: { gameId, timestamp }
  → Server: registerHeartbeat(gameId)
  → Server → MQTT heartbeat/ack/{gameId}: { gameId, serverTimestamp }
```

**Server-Initiated (ogni 5 min):**
```
Server → MQTT heartbeat/{gameId}: "PING" (JSON string, QoS 1)
  → Client → MQTT heartbeat/ack/{gameId}: "PONG" (JSON string, QoS 0)
  → Server: registerHeartbeat(gameId) + registerHeartbeatAck(gameId)
```

**Server-Initiated (recovery al boot):**
```
SessionRecoveryService (all'avvio):
  → MQTT heartbeat/{gameId}: "RECOVERY_PING" (JSON string, QoS 1)
  → Attende 30 secondi
  → Se nessun ACK: abortAndEmit(session, ABORTED, "SERVER_RESTART")
```

**Timeout (3 cicli = 15 min senza risposta):**
```
HealthCheckService (per ogni macchina senza risposta):
  Se sessione attiva presente:
    1. abortAndEmit(session, TIMEOUT) in REQUIRES_NEW
       → sessione ABORTED + game.release() + publishState(AVAILABLE) + outbox GAME_SESSION_ABORTED
  Se macchina bloccata IN_USE/LOBBY senza sessione attiva:
    1. game.release() → gameRepository.save()
    2. publishState(AVAILABLE) [retained] (deferred)
  In ogni caso:
    3. publishAlert(AlertPayload "UNREACHABLE") su building/{bId}/alerts (deferred)
```

### 7.3 Lobby Multiplayer

```
Client A → MQTT lobby/create { gameType, creatorId }
  → Server: createLobby() → GameSession WAITING, GameMachine LOBBY
  → Server: publishState(LOBBY), publishSessionEvent(lobby/create, SessionStartPayload)

Client B → MQTT lobby/join { sessionId, userId }
  → Server: joinLobby() → aggiunge partecipante
  → Server: publishSessionEvent(lobby/join, LobbyJoinPayload)

Client A → MQTT lobby/start { sessionId }
  → Server: startLobby() → GameSession IN_PROGRESS, GameMachine IN_USE
  → Server: publishState(IN_USE), publishSessionEvent(lobby/start, SessionStartPayload)

Client A → MQTT lobby/cancel { sessionId, userId }
  → Server: cancelLobby() → GameSession CANCELLED, GameMachine AVAILABLE
  → Server: publishState(AVAILABLE), publishSessionEvent(lobby/cancel, GameSession serializzato)

Client B → MQTT lobby/leave { sessionId, userId }
  → Server: leaveLobby() → rimuove partecipante
  → Server: publishSessionEvent(lobby/leave, LobbyLeavePayload)
```

### 7.4 Pausa/Ripresa e Termine Sessione

```
Client → MQTT session/pause { sessionId, pausedBy }
  → Server: pauseGameSession() → status PAUSED
  → Server broadcast session/pause

Client → MQTT session/resume { sessionId }
  → Server: resumeGameSession() → status IN_PROGRESS
  → Server broadcast session/resume

Client → MQTT session/end { sessionId, winnerId, winCondition, resultData }
  → Server: endGameSession()
    1. Deserializza GameResult da resultData (JSON polimorfico)
    2. GameSession → COMPLETED
    3. GameMachine → AVAILABLE
    4. publishState(AVAILABLE) [retained]
    5. broadcast session/end
    6. Outbox: GAME_SESSION_COMPLETED
    7. Se match torneo: anche TOURNAMENT_MATCH_COMPLETED
```

---

## 8. Note di Implementazione e Vincoli

### 8.1 Outbound Echo Deduplication
Il Local Server è contemporaneamente publisher e subscriber sugli stessi topic (es. `state`, `session/#`). `OutboundMessageDeduplicationCache` (cache LRU con TTL configurabile) registra ogni pubblicazione in uscita e filtra i messaggi in ingresso corrispondenti, prevenendo l'elaborazione di un proprio eco.

### 8.2 Pubblicazione Differita (Transactional Outbox)
Tutti i servizi applicativi (`GameSessionService`, `HealthCheckService`, ecc.) pubblicano MQTT in modo differito tramite `deferMqttPublish()`: il Runnable di pubblicazione viene eseguito **dopo il commit** della transazione Spring (`afterCommit`). Se non c'è una transazione attiva, viene eseguito immediatamente. Questo garantisce che il DB e l'outbox siano consistenti prima della notifica MQTT.

### 8.3 Idempotenza QoS 1 (Redelivery)
Tutti i listener MQTT gestiscono eccezioni di `InvalidGameStateTransitionException` e `SessionAlreadyActiveException` come **no-op loggati a debug**, perché MQTT QoS 1 può recapitare duplicati e il primo recapito ha già applicato la transizione di stato.

### 8.4 Concorrenza (Optimistic Locking)
Le eccezioni `ConcurrentStateException` vengono gestite con un warn log e il messaggio viene scartato: un'altra transazione REST/MQTT concorrente ha già vinto il lock ottimistico.

### 8.5 Late Arrival (ABORTED → COMPLETED)
Un client che era stato dichiarato `ABORTED` (heartbeat timeout) ma torna online e pubblica `session/end` viene accettato: la transizione `ABORTED → COMPLETED` è consentita, preservando il risultato finale.

### 8.6 Session Recovery al Boot
Il `SessionRecoveryService` (annotato con `@DependsOn("mqttClient")`) all'avvio del Local Server pinga via MQTT le sessioni `IN_PROGRESS`/`PAUSED`. Se un client non risponde, la sessione viene portata a `ABORTED` con `StopReason.SERVER_RESTART`.

### 8.7 GameMachineStatus (Enum)
Valori: `AVAILABLE`, `RESERVED`, `IN_USE`, `MAINTENANCE`, `LOBBY`
- `LOBBY` indica che la macchina è occupata da una lobby in attesa di partecipanti
- La transazione LOBBY → IN_USE avviene quando la lobby viene avviata
- LOBBY → AVAILABLE quando la lobby viene cancellata o scade

### 8.8 GameType (Enum)
Valori: `CHESS`, `FOOSBALL`, `DARTS`, `MONOPOLY`, `RISK`, `SLOT_MACHINE`, `ROULETTE`
(Il codice usa `SLOT_MACHINE`; il mapping JSON del GameResult usa `SLOT`.)

### 8.9 WinCondition (Enum)
Valori: `WIN`, `DRAW`, `ABANDONED`, `TIMEOUT`, `TEAM_VICTORY`

### 8.10 Relazioni con altri documenti
- **Certificati e mTLS:** Vedi `certificates_structure.md` — configurazione TLS per MQTTS
- **API REST:** Vedi `report_api_rest.md` — operazioni transazionali complementari
- **Diagramma di flusso:** Vedi `messages_flow.md` — diagramma di sequenza completo
- **Indirizzamento:** Vedi `indirizzamento_ip.md` — URL broker per edificio
