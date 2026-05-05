# Architettura delle Classi — Piattaforma Giochi da Tavolo e da Bar

---

## 1. Struttura Completa dei Package e delle Classi

### 1.1 shared-domain

```
shared/shared-domain/src/main/java/com/gameplatform/shared/domain/
├── model/
│   ├── UserId.java                    (record)
│   ├── GameId.java                    (record)
│   ├── BuildingId.java                (record)
│   ├── GameSessionId.java             (record)
│   ├── ReservationId.java             (record)
│   ├── GameType.java                  (enum)
│   ├── GameStatus.java                (enum)
│   ├── GameMachineStatus.java         (enum)
│   ├── ReservationStatus.java         (enum)
│   ├── WinCondition.java              (enum)
│   └── StopReason.java                (enum)
├── game/
│   ├── GameLifecycle.java             (interface)
│   ├── TurnBasedGame.java             (interface)
│   ├── ScoredGame.java                (interface)
│   ├── ResourceBasedGame.java         (interface)
│   └── BoardGame.java                 (interface)
├── result/
│   ├── GameResult.java                (interface)
│   ├── FoosballResult.java            (record)
│   ├── ChessResult.java               (record)
│   ├── DartsResult.java               (record)
│   ├── MonopolyResult.java            (record)
│   └── RiskResult.java                (record)
└── events/
    ├── DomainEvent.java               (interface)
    ├── UserRegisteredEvent.java        (record)
    ├── UserUpdatedEvent.java           (record)
    ├── ReservationCreatedEvent.java    (record)
    ├── ReservationCancelledEvent.java  (record)
    ├── GameSessionCompletedEvent.java  (record)
    ├── GameStateChangedEvent.java      (record)
    └── StatisticsUpdatedEvent.java     (record)
```

### 1.2 shared-dto

```
shared/shared-dto/src/main/java/com/gameplatform/shared/dto/
├── UserDto.java                       (record)
├── UserSyncDto.java                   (record)
├── LoginRequestDto.java               (record)
├── LoginResponseDto.java              (record)
├── ReservationDto.java                (record)
├── CreateReservationRequestDto.java   (record)
├── GameStateDto.java                  (record)
├── GameSessionDto.java                (record)
├── GameSessionResultDto.java          (record)
├── StatisticsDto.java                 (record)
├── OutboxEventDto.java                (record)
├── SyncPayloadDto.java                (record)
├── AlertDto.java                      (record)
└── ErrorResponseDto.java              (record)
```

### 1.3 shared-mqtt

```
shared/shared-mqtt/src/main/java/com/gameplatform/shared/mqtt/
├── MqttTopics.java                    (final class, costanti)
├── MqttQos.java                       (final class, costanti)
├── payload/
│   ├── GameStatePayload.java          (record)
│   ├── SessionStartPayload.java       (record)
│   ├── SessionEndPayload.java         (record)
│   ├── SessionPausePayload.java       (record)
│   ├── HeartbeatPayload.java          (record)
│   ├── HeartbeatAckPayload.java       (record)
│   └── AlertPayload.java             (record)
└── MqttPayloadSerializer.java        (utility class)
```

### 1.4 central-system

```
central-system/src/main/java/com/gameplatform/central/
├── CentralSystemApplication.java      (Spring Boot main)
├── domain/
│   ├── model/
│   │   ├── User.java                  (class)
│   │   ├── AggregatedStatistics.java  (class)
│   │   ├── RegisteredLocalServer.java (class)
│   │   ├── ProcessedEvent.java        (class)
│   │   └── OutboxEvent.java           (class)
│   ├── ports/
│   │   ├── in/
│   │   │   ├── RegisterUserUseCase.java           (interface)
│   │   │   ├── UpdateUserUseCase.java             (interface)
│   │   │   ├── AuthenticateUserUseCase.java       (interface)
│   │   │   ├── GetGlobalStatisticsUseCase.java    (interface)
│   │   │   ├── ReceiveSyncDataUseCase.java        (interface)
│   │   │   └── GetAllUsersUseCase.java            (interface)
│   │   └── out/
│   │       ├── UserRepository.java                (interface)
│   │       ├── StatisticsRepository.java          (interface)
│   │       ├── ProcessedEventRepository.java      (interface)
│   │       ├── OutboxEventRepository.java         (interface)
│   │       ├── LocalServerRegistryPort.java       (interface)
│   │       └── PushUserToLocalServersPort.java    (interface)
│   └── exception/
│       ├── UserAlreadyExistsException.java        (class)
│       ├── InvalidCredentialsException.java       (class)
│       └── DuplicateEventException.java           (class)
├── application/
│   └── service/
│       ├── UserService.java                       (class)
│       ├── AuthService.java                       (class)
│       ├── StatisticsAggregationService.java      (class)
│       ├── SyncReceiverService.java               (class)
│       └── UserReplicationSchedulerService.java   (class)
└── infrastructure/
    ├── adapters/
    │   ├── in/
    │   │   └── rest/
    │   │       ├── UserController.java            (class)
    │   │       ├── AuthController.java            (class)
    │   │       ├── StatisticsController.java      (class)
    │   │       └── SyncController.java            (class)
    │   └── out/
    │       ├── mysql/
    │       │   ├── entity/
    │       │   │   ├── UserJpaEntity.java          (class)
    │       │   │   ├── AggregatedStatisticsJpaEntity.java (class)
    │       │   │   ├── ProcessedEventJpaEntity.java       (class)
    │       │   │   └── OutboxEventJpaEntity.java          (class)
    │       │   ├── repository/
    │       │   │   ├── UserJpaRepository.java             (interface)
    │       │   │   ├── StatisticsJpaRepository.java       (interface)
    │       │   │   ├── ProcessedEventJpaRepository.java   (interface)
    │       │   │   └── OutboxEventJpaRepository.java      (interface)
    │       │   ├── adapter/
    │       │   │   ├── UserRepositoryAdapter.java         (class)
    │       │   │   ├── StatisticsRepositoryAdapter.java   (class)
    │       │   │   ├── ProcessedEventRepositoryAdapter.java (class)
    │       │   │   └── OutboxEventRepositoryAdapter.java  (class)
    │       │   └── mapper/
    │       │       ├── UserMapper.java                    (class)
    │       │       ├── StatisticsMapper.java              (class)
    │       │       └── OutboxEventMapper.java             (class)
    │       └── rest/
    │           └── LocalServerRestAdapter.java    (class)
    ├── config/
    │   ├── SecurityConfig.java                    (class)
    │   ├── JwtConfig.java                         (class)
    │   ├── SchedulerConfig.java                   (class)
    │   └── CorsConfig.java                        (class)
    └── security/
        ├── JwtTokenProvider.java                  (class)
        ├── JwtAuthenticationFilter.java           (class)
        ├── InternalApiKeyFilter.java              (class)
        └── PasswordEncoderConfig.java             (class)
```

### 1.5 local-server

```
local-server/src/main/java/com/gameplatform/local/
├── LocalServerApplication.java        (Spring Boot main)
├── domain/
│   ├── model/
│   │   ├── Reservation.java          (class)
│   │   ├── Game.java                 (class)
│   │   ├── User.java                 (class)
│   │   ├── GameSession.java          (class)
│   │   ├── OutboxEvent.java          (class)
│   │   └── LocalStatistics.java      (class)
│   ├── ports/
│   │   ├── in/
│   │   │   ├── CreateReservationUseCase.java      (interface)
│   │   │   ├── CancelReservationUseCase.java      (interface)
│   │   │   ├── GetReservationsUseCase.java        (interface)
│   │   │   ├── UpdateGameStateUseCase.java        (interface)
│   │   │   ├── GetAvailableGamesUseCase.java      (interface)
│   │   │   ├── StartGameSessionUseCase.java       (interface)
│   │   │   ├── EndGameSessionUseCase.java         (interface)
│   │   │   ├── PauseGameSessionUseCase.java       (interface)
│   │   │   ├── ResumeGameSessionUseCase.java      (interface)
│   │   │   ├── GetStatisticsUseCase.java          (interface)
│   │   │   ├── AuthenticateLocalUserUseCase.java  (interface)
│   │   │   └── SyncUsersUseCase.java              (interface)
│   │   └── out/
│   │       ├── ReservationRepository.java         (interface)
│   │       ├── GameRepository.java                (interface)
│   │       ├── UserRepository.java                (interface)
│   │       ├── GameSessionRepository.java         (interface)
│   │       ├── OutboxEventRepository.java         (interface)
│   │       ├── SyncCentralSystemPort.java         (interface)
│   │       ├── PublishGameStatePort.java           (interface)
│   │       └── PublishAlertPort.java              (interface)
│   └── exception/
│       ├── GameNotAvailableException.java         (class)
│       ├── ReservationNotFoundException.java      (class)
│       ├── ReservationExpiredException.java       (class)
│       ├── UserNotFoundException.java             (class)
│       ├── SessionAlreadyActiveException.java     (class)
│       └── InvalidGameStateTransitionException.java (class)
├── application/
│   └── service/
│       ├── ReservationService.java                (class)
│       ├── ReservationExpirationService.java      (class)
│       ├── GameStateService.java                  (class)
│       ├── GameSessionService.java                (class)
│       ├── SessionRecoveryService.java            (class)
│       ├── StatisticsService.java                 (class)
│       ├── LocalAuthService.java                  (class)
│       ├── UserSyncService.java                   (class)
│       ├── SyncSchedulerService.java              (class)
│       └── HealthCheckService.java                (class)
└── infrastructure/
    ├── adapters/
    │   ├── in/
    │   │   ├── rest/
    │   │   │   ├── ReservationController.java     (class)
    │   │   │   ├── GameController.java            (class)
    │   │   │   ├── GameSessionController.java     (class)
    │   │   │   ├── StatisticsController.java      (class)
    │   │   │   ├── AuthController.java            (class)
    │   │   │   └── InternalSyncController.java    (class)
    │   │   └── mqtt/
    │   │       ├── GameStateListener.java         (class)
    │   │       ├── GameSessionListener.java       (class)
    │   │       └── HeartbeatListener.java         (class)
    │   └── out/
    │       ├── mysql/
    │       │   ├── entity/
    │       │   │   ├── ReservationJpaEntity.java   (class)
    │       │   │   ├── GameJpaEntity.java          (class)
    │       │   │   ├── UserJpaEntity.java          (class)
    │       │   │   ├── GameSessionJpaEntity.java   (class)
    │       │   │   ├── SessionParticipantJpaEntity.java (class)
    │       │   │   └── OutboxEventJpaEntity.java   (class)
    │       │   ├── repository/
    │       │   │   ├── ReservationJpaRepository.java   (interface)
    │       │   │   ├── GameJpaRepository.java          (interface)
    │       │   │   ├── UserJpaRepository.java          (interface)
    │       │   │   ├── GameSessionJpaRepository.java   (interface)
    │       │   │   └── OutboxEventJpaRepository.java   (interface)
    │       │   ├── adapter/
    │       │   │   ├── ReservationRepositoryAdapter.java   (class)
    │       │   │   ├── GameRepositoryAdapter.java          (class)
    │       │   │   ├── UserRepositoryAdapter.java          (class)
    │       │   │   ├── GameSessionRepositoryAdapter.java   (class)
    │       │   │   └── OutboxEventRepositoryAdapter.java   (class)
    │       │   └── mapper/
    │       │       ├── ReservationMapper.java      (class)
    │       │       ├── GameMapper.java             (class)
    │       │       ├── UserMapper.java             (class)
    │       │       ├── GameSessionMapper.java      (class)
    │       │       └── OutboxEventMapper.java      (class)
    │       ├── rest/
    │       │   └── CentralSystemRestAdapter.java  (class)
    │       └── mqtt/
    │           └── MqttPublisherAdapter.java      (class)
    ├── config/
    │   ├── MqttConfig.java                        (class)
    │   ├── SecurityConfig.java                    (class)
    │   ├── TlsConfig.java                         (class)
    │   └── SchedulerConfig.java                   (class)
    └── security/
        ├── JwtTokenProvider.java                  (class)
        ├── JwtTokenValidator.java                 (class)
        ├── JwtAuthenticationFilter.java           (class)
        └── InternalApiKeyFilter.java              (class)
```

### 1.6 game-client-emulator

```
game-client-emulator/src/main/java/com/gameplatform/client/
├── GameClientApplication.java         (JavaFX Application main)
├── domain/
│   ├── games/
│   │   ├── FoosballGame.java          (class implements ScoredGame)
│   │   ├── ChessGame.java            (class implements BoardGame)
│   │   ├── DartsGame.java            (class implements ScoredGame, TurnBasedGame)
│   │   ├── MonopolyGame.java         (class implements ResourceBasedGame)
│   │   └── RiskGame.java             (class implements ResourceBasedGame, BoardGame)
│   ├── GameFactory.java              (class)
│   └── ClientState.java              (enum)
├── application/
│   └── service/
│       ├── GameOrchestrationService.java   (class)
│       ├── HeartbeatService.java           (class)
│       └── ConnectionMonitorService.java   (class)
└── infrastructure/
    ├── mqtt/
    │   ├── MqttClientAdapter.java         (class)
    │   ├── MqttConnectionManager.java     (class)
    │   ├── GameStatePublisher.java        (class)
    │   ├── SessionPublisher.java          (class)
    │   ├── HeartbeatPublisher.java        (class)
    │   └── StateSubscriber.java           (class)
    ├── ui/
    │   ├── MainView.java                  (class)
    │   ├── LoginView.java                 (class)
    │   ├── GameSelectionView.java         (class)
    │   ├── GamePlayView.java             (class)
    │   ├── StatisticsView.java           (class)
    │   └── components/
    │       ├── ScoreboardComponent.java   (class)
    │       ├── TimerComponent.java        (class)
    │       └── StatusBarComponent.java    (class)
    └── config/
        └── MqttClientConfig.java          (class)
```

---

## 2. Tabelle Descrittive delle Classi

### 2.1 shared-domain — `com.gameplatform.shared.domain`

#### model/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `UserId` | record | Value Object immutabile. Wrappa un `String` (UUID). Usato come identificatore utente in tutto il sistema. | — |
| `GameId` | record | Value Object immutabile. Identifica univocamente una macchina di gioco fisica in un edificio. | — |
| `BuildingId` | record | Value Object immutabile. Identifica un edificio (Local Server). | — |
| `GameSessionId` | record | Value Object immutabile. Identifica una sessione/partita di gioco. | — |
| `ReservationId` | record | Value Object immutabile. Identifica una prenotazione. | — |
| `GameType` | enum | Enumera i tipi di gioco supportati: `CHESS`, `FOOSBALL`, `DARTS`, `MONOPOLY`, `RISK`. Estensibile. | — |
| `GameStatus` | enum | Stati del ciclo di vita di una sessione: `WAITING`, `IN_PROGRESS`, `PAUSED`, `COMPLETED`, `ABORTED`. | — |
| `GameMachineStatus` | enum | Stati della macchina fisica: `AVAILABLE`, `RESERVED`, `IN_USE`, `MAINTENANCE`. | — |
| `ReservationStatus` | enum | Stati di una prenotazione: `PENDING`, `CONFIRMED`, `CANCELLED`, `EXPIRED`. | — |
| `WinCondition` | enum | Condizioni di fine partita: `WIN`, `DRAW`, `ABANDONED`, `TIMEOUT`. | — |
| `StopReason` | enum | Motivazione dell'arresto: `COMPLETED`, `ABORTED`, `TIMEOUT`. | — |

#### game/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `GameLifecycle` | interface | Interfaccia radice del ciclo di vita di qualsiasi gioco. Metodi: `start(List<UserId>)`, `stop(StopReason)`, `pause()`, `resume()`, `getStatus()`, `getGameType()`, `getSessionId()`. | `UserId`, `StopReason`, `GameStatus`, `GameType`, `GameSessionId` |
| `TurnBasedGame` | interface | Capability interface standalone (non estende `GameLifecycle`). Aggiunge: `getCurrentPlayer()`, `endTurn()`, `getTurnNumber()`. Per giochi a turni. Le classi concrete implementano `GameLifecycle` + `TurnBasedGame`. | `UserId` |
| `ScoredGame` | interface | Capability interface standalone (non estende `GameLifecycle`). Aggiunge: `getCurrentScores()`, `recordScore(UserId, int)`. Per giochi con punteggio numerico. Le classi concrete implementano `GameLifecycle` + `ScoredGame`. | `UserId` |
| `ResourceBasedGame` | interface | Capability interface standalone (non estende `TurnBasedGame`). Aggiunge: `getResources()`, `updateResource(UserId, String, int)`. Per giochi con risorse multiple (Monopoli, Risiko). Le classi concrete implementano `GameLifecycle` + `TurnBasedGame` + `ResourceBasedGame`. | `UserId` |
| `BoardGame` | interface | Capability interface standalone (non estende `TurnBasedGame`). Aggiunge: `serializeBoardState()`, `restoreBoardState(String)`. Per giochi con board serializzabile (Scacchi, Risiko). Le classi concrete implementano `GameLifecycle` + `TurnBasedGame` + `BoardGame`. | — |

#### result/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `GameResult` | interface | Interfaccia polimorfica per i risultati di gioco. Annotata con `@JsonTypeInfo` per serializzazione Jackson. Metodi: `getWinnerId(): UserId`, `getWinCondition()`. | `UserId`, `WinCondition` |
| `FoosballResult` | record | Risultato calciobalilla: `winnerId: UserId`, `finalScores: Map<String,Integer>`, `winCondition`. | `GameResult`, `UserId` |
| `ChessResult` | record | Risultato scacchi: `winnerId: UserId`, `terminationReason`, `finalFenState`, `winCondition`. | `GameResult`, `UserId` |
| `DartsResult` | record | Risultato freccette: `winnerId: UserId`, `finalScores`, `dartsThrown: Map<String,Integer>`, `winCondition`. | `GameResult`, `UserId` |
| `MonopolyResult` | record | Risultato monopoli: `winnerId: UserId`, `finalMoney`, `ownedProperties: Map<String,List<String>>`, `bankruptOrder`, `winCondition`. | `GameResult`, `UserId` |
| `RiskResult` | record | Risultato risiko: `winnerId: UserId`, `territoriesAtEnd: Map<String,Map<String,Integer>>`, `eliminatedOrder`, `totalRounds`, `winCondition`. | `GameResult`, `UserId` |

#### events/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `DomainEvent` | interface | Interfaccia marker per tutti gli eventi di dominio. Metodi: `getEventId()`, `getOccurredAt()`, `getEventType()`. | — |
| `UserRegisteredEvent` | record | Evento emesso alla registrazione utente. Payload: `userId`, `username`, `hashedPassword`, `roles`. | `DomainEvent` |
| `UserUpdatedEvent` | record | Evento emesso alla modifica di un utente (cambio password o ruoli). Payload: `userId`, `username`, `hashedPassword`, `roles`. Attiva la replica aggiornata verso tutti i Local Server. | `DomainEvent` |
| `ReservationCreatedEvent` | record | Evento emesso alla creazione prenotazione. Payload: `reservationId`, `gameId`, `userId`, `buildingId`. | `DomainEvent` |
| `ReservationCancelledEvent` | record | Evento emesso alla cancellazione prenotazione. | `DomainEvent` |
| `GameSessionCompletedEvent` | record | Evento emesso al completamento di una sessione. Payload: `sessionId`, `gameType`, `result (JSON)`. | `DomainEvent` |
| `GameStateChangedEvent` | record | Evento emesso al cambio stato macchina fisica. Payload: `gameId`, `oldStatus`, `newStatus`. | `DomainEvent`, `GameMachineStatus` |
| `StatisticsUpdatedEvent` | record | Evento emesso quando le statistiche locali sono pronte per la sync. | `DomainEvent` |

---

### 2.2 shared-dto — `com.gameplatform.shared.dto`

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `UserDto` | record | DTO utente per API REST. Campi: `id`, `username`, `email`, `roles`, `createdAt`. | — |
| `UserSyncDto` | record | DTO per replica utenti Central→Local. Campi: `userId`, `username`, `hashedPassword`, `roles`. | — |
| `LoginRequestDto` | record | DTO per richiesta login. Campi: `username`, `password`. | — |
| `LoginResponseDto` | record | DTO per risposta login. Campi: `token (JWT)`, `userId`, `expiresAt`. | — |
| `ReservationDto` | record | DTO prenotazione. Campi: `id`, `gameId`, `userId`, `status`, `startTime`, `endTime`. | `ReservationStatus` |
| `CreateReservationRequestDto` | record | DTO per creare una prenotazione. Campi: `gameId`, `userId`, `startTime`, `endTime`. | — |
| `GameStateDto` | record | DTO stato macchina fisica. Campi: `gameId`, `gameType`, `name`, `buildingId`, `status`. | `GameType`, `GameMachineStatus` |
| `GameSessionDto` | record | DTO sessione di gioco. Campi: `id`, `gameId`, `gameType`, `status`, `startedAt`, `endedAt`, `durationSeconds`, `winnerId`, `winCondition`, `resultData (JSON string)`. | `GameType`, `GameStatus` |
| `GameSessionResultDto` | record | DTO risultato sessione per la sync. Wrappa `GameSessionDto` + lista `participants`. | `GameSessionDto` |
| `StatisticsDto` | record | DTO statistiche aggregate. Campi: `buildingId`, `gameType`, `periodStart`, `periodEnd`, `totalSessions`, `avgDuration`, `totalReservations`, `data (JSON)`. | — |
| `OutboxEventDto` | record | DTO per scambio outbox tra Central e Local. Campi: `eventId`, `eventType`, `payload (JSON)`, `createdAt`. | — |
| `SyncPayloadDto` | record | DTO container per batch di eventi outbox. Campi: `buildingId`, `events: List<OutboxEventDto>`. | `OutboxEventDto` |
| `AlertDto` | record | DTO allarme. Campi: `buildingId`, `gameId`, `alertType`, `message`, `timestamp`. | — |
| `ErrorResponseDto` | record | DTO risposta errore REST standard. Campi: `status`, `error`, `message`, `timestamp`. | — |

---

### 2.3 shared-mqtt — `com.gameplatform.shared.mqtt`

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `MqttTopics` | final class | Costanti statiche per tutti i topic MQTT. Metodi factory: `gameState(buildingId, gameId)`, `sessionStart(...)`, `heartbeat(...)`, ecc. | — |
| `MqttQos` | final class | Costanti QoS: `STATE = 1`, `SESSION = 1`, `HEARTBEAT = 0`. | — |
| `GameStatePayload` | record | Payload per topic `state`. Campi: `gameId`, `status (GameMachineStatus)`, `userId` (opzionale). | `GameMachineStatus` |
| `SessionStartPayload` | record | Payload per `session/start`. Campi: `sessionId`, `gameType`, `participants: List<String>`. | `GameType` |
| `SessionEndPayload` | record | Payload per `session/end`. Campi: `sessionId`, `winnerId`, `winCondition`, `resultData (JSON)`. | `WinCondition` |
| `SessionPausePayload` | record | Payload per `session/pause`. Campi: `sessionId`, `pausedBy`. | — |
| `HeartbeatPayload` | record | Payload per `heartbeat`. Campi: `gameId`, `timestamp`. | — |
| `HeartbeatAckPayload` | record | Payload per `heartbeat/ack`. Campi: `gameId`, `serverTimestamp`. | — |
| `AlertPayload` | record | Payload per `alerts`. Campi: `alertType`, `gameId`, `message`, `timestamp`. | — |
| `MqttPayloadSerializer` | utility | Metodi statici `serialize(Object): byte[]` e `deserialize(byte[], Class<T>): T` usando Jackson ObjectMapper. | — |

---

### 2.4 central-system — `com.gameplatform.central`

#### domain/model/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `User` | class | Entità utente nel Central System. Campi: `UserId id`, `username`, `passwordHash`, `email`, `roles`, `createdAt`. Logica: `changePassword()`, `updateRoles()`. Rich Domain Model, **NO annotazioni JPA**. | `UserId` |
| `AggregatedStatistics` | class | Statistiche aggregate per building+gameType+periodo. Campi: `id`, `buildingId`, `gameType`, `periodStart`, `periodEnd`, `totalSessions`, `avgDurationSeconds`, `totalReservations`, `data (Map)`. Logica: `mergeWith(AggregatedStatistics)`. | `BuildingId`, `GameType` |
| `RegisteredLocalServer` | class | Rappresenta un Local Server registrato. Campi: `buildingId`, `baseUrl`, `lastSeenAt`, `isActive`. | `BuildingId` |
| `ProcessedEvent` | class | Record di un evento già elaborato (idempotenza). Campi: `eventId`, `processedAt`. | — |
| `OutboxEvent` | class | Evento in coda per la replica utenti verso i Local Server. Riusa lo stesso pattern del local-server. | — |

#### domain/ports/in/ (Use Case Interfaces)

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `RegisterUserUseCase` | interface | `User register(String username, String password, String email)`. | `User` |
| `UpdateUserUseCase` | interface | `User updateUser(UserId id, String newPassword, List<String> newRoles)`. Aggiorna password/ruoli e crea `OutboxEvent: USER_UPDATED` per la replica ai Local Server. | `User`, `UserId` |
| `AuthenticateUserUseCase` | interface | `LoginResponseDto authenticate(String username, String password)`. | `LoginResponseDto` |
| `GetGlobalStatisticsUseCase` | interface | `List<StatisticsDto> getStatistics(BuildingId, GameType, DateRange)`. | `StatisticsDto`, `BuildingId`, `GameType` |
| `ReceiveSyncDataUseCase` | interface | `void receiveSyncPayload(SyncPayloadDto payload)`. Riceve batch di eventi dai Local Server, deduplica e persiste. | `SyncPayloadDto` |
| `GetAllUsersUseCase` | interface | `List<UserSyncDto> getAllUsersForSync()`. Usato dal job di replica. | `UserSyncDto` |

#### domain/ports/out/ (Infrastructure Interfaces)

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `UserRepository` | interface | CRUD utenti. Metodi: `save()`, `findById()`, `findByUsername()`, `findAll()`. | `User`, `UserId` |
| `StatisticsRepository` | interface | CRUD statistiche. Metodi: `save()`, `findByBuildingAndType()`, `findByPeriod()`. | `AggregatedStatistics` |
| `ProcessedEventRepository` | interface | `existsByEventId(String)`, `save(ProcessedEvent)`. Per idempotenza. | `ProcessedEvent` |
| `OutboxEventRepository` | interface | `save()`, `findPending()`, `markAsSent()`. | `OutboxEvent` |
| `LocalServerRegistryPort` | interface | `List<RegisteredLocalServer> getActiveLocalServers()`. | `RegisteredLocalServer` |
| `PushUserToLocalServersPort` | interface | `void pushUsers(List<UserSyncDto>, RegisteredLocalServer)`. Invia replica utenti via REST. | `UserSyncDto`, `RegisteredLocalServer` |

#### application/service/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `UserService` | class | `@Transactional`. Implementa `RegisterUserUseCase`, `UpdateUserUseCase`, `GetAllUsersUseCase`. Crea/aggiorna utente, hash BCrypt, salva nel repo, crea `OutboxEvent` (`USER_REGISTERED` o `USER_UPDATED`) nella stessa transazione atomica per la replica verso i Local Server. | `UserRepository`, `OutboxEventRepository` |
| `AuthService` | class | Implementa `AuthenticateUserUseCase`. Verifica credenziali, emette JWT con chiave privata. | `UserRepository`, `JwtTokenProvider` |
| `StatisticsAggregationService` | class | Implementa `GetGlobalStatisticsUseCase`. Legge statistiche aggregate, supporta filtri per building/gameType/periodo. | `StatisticsRepository` |
| `SyncReceiverService` | class | `@Transactional`. Implementa `ReceiveSyncDataUseCase`. Riceve payload sync dai Local Server, deduplica con `ProcessedEventRepository`, persiste sessioni e statistiche nella stessa transazione. | `ProcessedEventRepository`, `StatisticsRepository` |
| `UserReplicationSchedulerService` | class | Job `@Scheduled`. Legge OutboxEvent PENDING (di tipo `USER_REGISTERED` e `USER_UPDATED`), invia utenti a tutti i Local Server via `PushUserToLocalServersPort`. | `OutboxEventRepository`, `LocalServerRegistryPort`, `PushUserToLocalServersPort` |

#### infrastructure/adapters/in/rest/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `UserController` | class | `@RestController`. Endpoint: `POST /api/users` (registrazione, pubblico). Delega a `RegisterUserUseCase`. | `RegisterUserUseCase`, `UserDto` |
| `AuthController` | class | `@RestController`. Endpoint: `POST /api/auth/login` (pubblico). Delega a `AuthenticateUserUseCase`. | `AuthenticateUserUseCase`, `LoginRequestDto`, `LoginResponseDto` |
| `StatisticsController` | class | `@RestController`. `@PreAuthorize("hasRole('ADMIN')")`. Endpoint: `GET /api/statistics`. Delega a `GetGlobalStatisticsUseCase`. | `GetGlobalStatisticsUseCase`, `StatisticsDto` |
| `SyncController` | class | `@RestController`. Endpoint: `POST /internal/sync/receive` (protetto da `InternalApiKeyFilter`, non da JWT). Delega a `ReceiveSyncDataUseCase`. | `ReceiveSyncDataUseCase`, `SyncPayloadDto` |

#### infrastructure/adapters/out/mysql/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `UserJpaEntity` | class | `@Entity`. Mappa tabella `users`. NO logica di business. | — |
| `AggregatedStatisticsJpaEntity` | class | `@Entity`. Mappa tabella `aggregated_statistics`. | — |
| `ProcessedEventJpaEntity` | class | `@Entity`. Mappa tabella `processed_events`. | — |
| `OutboxEventJpaEntity` | class | `@Entity`. Mappa tabella `outbox_events`. | — |
| `UserJpaRepository` | interface | `extends JpaRepository<UserJpaEntity, String>`. Query: `findByUsername()`. | `UserJpaEntity` |
| `StatisticsJpaRepository` | interface | `extends JpaRepository`. Query: `findByBuildingIdAndGameType()`. | `AggregatedStatisticsJpaEntity` |
| `ProcessedEventJpaRepository` | interface | `extends JpaRepository`. Query: `existsById()`. | `ProcessedEventJpaEntity` |
| `OutboxEventJpaRepository` | interface | `extends JpaRepository`. Query: `findByStatusOrderByCreatedAt()`. | `OutboxEventJpaEntity` |
| `UserRepositoryAdapter` | class | `@Component`. Implementa `UserRepository` (porta di dominio). Usa `UserJpaRepository` + `UserMapper`. | `UserRepository`, `UserJpaRepository`, `UserMapper` |
| `StatisticsRepositoryAdapter` | class | `@Component`. Implementa `StatisticsRepository`. | `StatisticsRepository`, `StatisticsJpaRepository`, `StatisticsMapper` |
| `ProcessedEventRepositoryAdapter` | class | `@Component`. Implementa `ProcessedEventRepository`. | `ProcessedEventRepository` |
| `OutboxEventRepositoryAdapter` | class | `@Component`. Implementa `OutboxEventRepository`. | `OutboxEventRepository` |
| `UserMapper` | class | `@Component`. Metodi di istanza `toDomain(UserJpaEntity): User` e `toEntity(User): UserJpaEntity`. Iniettato nei `RepositoryAdapter` via costruttore. | `User`, `UserJpaEntity` |
| `StatisticsMapper` | class | `@Component`. Metodi di istanza. Converte tra `AggregatedStatistics` e `AggregatedStatisticsJpaEntity`. | — |
| `OutboxEventMapper` | class | `@Component`. Metodi di istanza. Converte tra `OutboxEvent` e `OutboxEventJpaEntity`. | — |

#### infrastructure/adapters/out/rest/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `LocalServerRestAdapter` | class | `@Component`. Implementa `PushUserToLocalServersPort`. Usa `RestTemplate`/`WebClient` per `PUT /internal/users/sync` verso i Local Server. | `PushUserToLocalServersPort`, `UserSyncDto` |

#### infrastructure/config/ e security/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `SecurityConfig` | class | `@Configuration @EnableMethodSecurity`. Configura Spring Security: filtro JWT per `/api/**`, `InternalApiKeyFilter` per `/internal/**`, endpoint pubblici (`/api/auth/**`, `/api/users`), CORS. | `JwtAuthenticationFilter`, `InternalApiKeyFilter` |
| `JwtConfig` | class | `@Configuration`. Carica chiave privata RSA da file. Bean `JwtTokenProvider`. | — |
| `SchedulerConfig` | class | `@Configuration`. Abilita `@Scheduled` con `@EnableScheduling`. | — |
| `CorsConfig` | class | `@Configuration`. CORS policy per browser. | — |
| `JwtTokenProvider` | class | Genera e firma JWT con chiave privata RSA. Estrae `roles` dal `User` e li inserisce come claim nel JWT. Metodi: `generateToken(User): String`. | `User` |
| `JwtAuthenticationFilter` | class | `OncePerRequestFilter`. Estrae JWT da header, valida, estrae ruoli dal claim `roles` e li mappa a `GrantedAuthority`, setta `SecurityContext`. | `JwtTokenProvider` |
| `InternalApiKeyFilter` | class | `OncePerRequestFilter`. Valida l'header `X-Internal-Api-Key` contro il valore configurato da variabile d'ambiente `INTERNAL_API_KEY`. Applicato solo a `/internal/**`. Risponde `401` se assente o non valido. | — |
| `PasswordEncoderConfig` | class | `@Configuration`. Bean `BCryptPasswordEncoder`. | — |

---

### 2.5 local-server — `com.gameplatform.local`

#### domain/model/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `Reservation` | class | Rich Domain Model. Campi: `ReservationId`, `GameId`, `UserId`, `ReservationStatus`, `startTime`, `endTime`, `createdAt`. Logica: `canBeCancelled(Clock)` (riceve `Clock` come parametro per testabilità deterministica), `confirm()`, `cancel()`, `expire()`. | `ReservationId`, `GameId`, `UserId`, `ReservationStatus` |
| `Game` | class | Macchina di gioco fisica. Campi: `GameId`, `GameType`, `name`, `BuildingId`, `GameMachineStatus`. Logica: `reserve()`, `startUse()`, `release()`, `setMaintenance()`. | `GameId`, `GameType`, `BuildingId`, `GameMachineStatus` |
| `User` | class | Replica locale utente. Campi: `UserId`, `username`, `passwordHash`, `roles`, `syncedAt`. Solo dati essenziali per login offline. | `UserId` |
| `GameSession` | class | Sessione di gioco. Campi: `GameSessionId`, `GameId`, `GameType`, `BuildingId`, `GameStatus`, `startedAt`, `endedAt`, `durationSeconds`, `winnerId`, `winCondition`, `GameResult`. Logica: `complete(GameResult)`, `abort(StopReason)`, `pause()`, `resume()`, `calculateDuration()`. | `GameSessionId`, `GameId`, `GameType`, `BuildingId`, `GameStatus`, `GameResult`, `WinCondition` |
| `OutboxEvent` | class | Evento in coda per sync. Campi: `id`, `eventType`, `payload (JSON)`, `status (PENDING/SENT/FAILED)`, `createdAt`, `sentAt`, `retryCount`. Logica: `markAsSent()`, `incrementRetry()`, `hasFailed()`. | — |
| `LocalStatistics` | class | Statistiche locali aggregate. Campi: `gameType`, `totalSessions`, `avgDuration`, `totalReservations`, `winRateByUser`. Logica: `recalculate(List<GameSession>)`. | `GameType`, `GameSession` |

#### domain/ports/in/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `CreateReservationUseCase` | interface | `Reservation create(GameId, UserId, Instant start, Instant end)`. | `Reservation`, `GameId`, `UserId` |
| `CancelReservationUseCase` | interface | `void cancel(ReservationId)`. | `ReservationId` |
| `GetReservationsUseCase` | interface | `List<Reservation> getByUser(UserId)`, `List<Reservation> getByGame(GameId)`. | `Reservation` |
| `UpdateGameStateUseCase` | interface | `void updateState(GameId, GameMachineStatus)`. | `GameId`, `GameMachineStatus` |
| `GetAvailableGamesUseCase` | interface | `List<Game> getAvailable()`, `List<Game> getAll()`. | `Game` |
| `StartGameSessionUseCase` | interface | `GameSession start(GameId, GameType, List<UserId>)`. | `GameSession`, `GameId`, `GameType`, `UserId` |
| `EndGameSessionUseCase` | interface | `void end(GameSessionId, GameResult)`. | `GameSessionId`, `GameResult` |
| `PauseGameSessionUseCase` | interface | `void pause(GameSessionId)`. | `GameSessionId` |
| `ResumeGameSessionUseCase` | interface | `void resume(GameSessionId)`. | `GameSessionId` |
| `GetStatisticsUseCase` | interface | `LocalStatistics getStatistics(GameType)`, `List<GameSession> getActiveSessions()`. | `LocalStatistics`, `GameSession` |
| `AuthenticateLocalUserUseCase` | interface | `LoginResponseDto authenticate(String username, String password)`. Login offline con hash replicato. | `LoginResponseDto` |
| `SyncUsersUseCase` | interface | `void syncUsers(List<UserSyncDto>)`. Riceve replica utenti dal Central. | `UserSyncDto` |

#### domain/ports/out/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `ReservationRepository` | interface | CRUD prenotazioni: `save()`, `findById()`, `findByUserId()`, `findByGameId()`, `findByStatus()`, `findExpired(Instant now)`. Il metodo `findExpired()` restituisce le prenotazioni con `status IN (PENDING, CONFIRMED)` e `end_time < now`. | `Reservation` |
| `GameRepository` | interface | CRUD macchine gioco: `save()`, `findById()`, `findByBuildingId()`, `findByStatus()`, `findAll()`. | `Game` |
| `UserRepository` | interface | CRUD utenti replicati: `save()`, `findByUsername()`, `saveAll()`. | `User` |
| `GameSessionRepository` | interface | `save()`, `findById()`, `findByBuildingId()`, `findByGameType()`, `findByStatus()`, `findPendingSync()`, `findActiveByGameId(GameId)`. | `GameSession` |
| `OutboxEventRepository` | interface | `save()`, `findByStatus()`, `markAsSent()`, `incrementRetry()`. | `OutboxEvent` |
| `SyncCentralSystemPort` | interface | `boolean isReachable()`, `boolean sendSyncPayload(SyncPayloadDto)`. | `SyncPayloadDto` |
| `PublishGameStatePort` | interface | `void publishState(GameId, GameMachineStatus)`, `void publishSessionEvent(...)`. Pubblica su MQTT. | `GameId`, `GameMachineStatus` |
| `PublishAlertPort` | interface | `void publishAlert(AlertPayload)`. Pubblica allarmi su MQTT. | `AlertPayload` |

#### application/service/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `ReservationService` | class | `@Transactional`. Implementa `CreateReservationUseCase`, `CancelReservationUseCase`, `GetReservationsUseCase`. Verifica disponibilità gioco, crea prenotazione atomica, aggiorna stato macchina, crea OutboxEvent nella stessa transazione. Inietta `Clock` come bean e lo passa a `Reservation.canBeCancelled(Clock)`. | `ReservationRepository`, `GameRepository`, `OutboxEventRepository`, `PublishGameStatePort`, `Clock` |
| `ReservationExpirationService` | class | `@Transactional`. Job `@Scheduled(fixedRate=60000)`. Ogni minuto invoca `ReservationRepository.findExpired(Instant.now(clock))` per trovare tutte le prenotazioni con `status IN (PENDING, CONFIRMED)` e `end_time < NOW()`. Per ciascuna: (1) imposta `status = EXPIRED` tramite `Reservation.expire()`, (2) rilascia la macchina associata a `AVAILABLE` tramite `Game.release()`, (3) pubblica il nuovo stato macchina via MQTT. Previene il blocco indefinito delle macchine prenotate e mai utilizzate. | `ReservationRepository`, `GameRepository`, `PublishGameStatePort`, `Clock` |
| `GameStateService` | class | `@Transactional`. Implementa `UpdateGameStateUseCase`, `GetAvailableGamesUseCase`. Aggiorna stato macchina, pubblica su MQTT. | `GameRepository`, `PublishGameStatePort` |
| `GameSessionService` | class | `@Transactional`. Implementa `StartGameSessionUseCase`, `EndGameSessionUseCase`, `PauseGameSessionUseCase`, `ResumeGameSessionUseCase`. Gestisce ciclo di vita sessione, calcola durata, serializza GameResult in JSON, crea OutboxEvent nella stessa transazione. L'`EndGameSessionUseCase` accetta anche sessioni in stato `ABORTED` (late arrival) per preservare il risultato se il client lo invia dopo che l'health check ha già marcato la sessione come abortita: in tal caso la sessione viene aggiornata a `COMPLETED` con i dati del risultato. | `GameSessionRepository`, `GameRepository`, `OutboxEventRepository`, `PublishGameStatePort` |
| `SessionRecoveryService` | class | Implementa `SmartLifecycle` (Spring). All'avvio del Local Server, scansiona tutte le `GameSession` con `status = IN_PROGRESS` o `PAUSED` nel DB. Per ciascuna, invia un heartbeat MQTT al client associato con un timeout di 30 secondi. Se il client risponde, la sessione rimane attiva. Se non risponde, la sessione viene marcata come `ABORTED` con motivo `SERVER_RESTART`, la macchina viene rilasciata a `AVAILABLE`, e viene generato un `OutboxEvent`. Previene sessioni zombie dopo un riavvio del server. | `GameSessionRepository`, `GameRepository`, `OutboxEventRepository`, `PublishGameStatePort`, `PublishAlertPort` |
| `StatisticsService` | class | Implementa `GetStatisticsUseCase`. Genera aggregazioni locali da `GameSessionRepository`. | `GameSessionRepository` |
| `LocalAuthService` | class | Implementa `AuthenticateLocalUserUseCase`. Verifica BCrypt hash da `replicated_users`, **firma e genera JWT con la chiave privata RSA locale** (`JwtTokenProvider`). Funziona identicamente sia online che offline: non dipende dalla connettività al Central. | `UserRepository`, `JwtTokenProvider` |
| `UserSyncService` | class | Implementa `SyncUsersUseCase`. Riceve lista utenti dal Central (sia nuovi che aggiornati), salva/aggiorna in `replicated_users` tramite upsert. Gestisce sia `USER_REGISTERED` che `USER_UPDATED`. | `UserRepository` |
| `SyncSchedulerService` | class | Job `@Scheduled(fixedRate=300000)`. Legge outbox PENDING, verifica connettività, invia payload al Central via `SyncCentralSystemPort`. Gestisce retry e failure. | `OutboxEventRepository`, `SyncCentralSystemPort` |
| `HealthCheckService` | class | Job `@Scheduled(fixedRate=300000)`. Invia heartbeat MQTT a tutti gli endpoint registrati con timeout di **3 cicli consecutivi mancati** (grace period) prima di dichiarare un endpoint irraggiungibile. Se un endpoint non risponde per 3 cicli consecutivi (15 minuti): termina sessioni attive con `ABORTED`, rilascia macchina, pubblica allarme. Un singolo heartbeat mancato viene solo loggato come warning. | `GameSessionRepository`, `GameRepository`, `PublishGameStatePort`, `PublishAlertPort` |

#### infrastructure/ (adapters, config, security)

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `ReservationController` | class | `@RestController`. `@PreAuthorize("hasRole('USER')")`. Endpoint: `POST /api/reservations`, `DELETE /api/reservations/{id}`, `GET /api/reservations`. | Use Case ports |
| `GameController` | class | `@RestController`. `@PreAuthorize("hasRole('USER')")`. Endpoint: `GET /api/games`, `GET /api/games/available`. | `GetAvailableGamesUseCase` |
| `GameSessionController` | class | `@RestController`. `@PreAuthorize("hasRole('USER')")`. Endpoint: `POST /api/sessions/start`, `POST /api/sessions/{id}/end`, `POST /api/sessions/{id}/pause`, `POST /api/sessions/{id}/resume`. | Session Use Cases |
| `StatisticsController` | class | `@RestController`. `@PreAuthorize("hasRole('USER')")`. Endpoint: `GET /api/statistics`, `GET /api/sessions/active`. | `GetStatisticsUseCase` |
| `AuthController` | class | `@RestController`. Endpoint: `POST /api/auth/login` (pubblico). | `AuthenticateLocalUserUseCase` |
| `InternalSyncController` | class | `@RestController`. Endpoint: `PUT /internal/users/sync` (protetto da `InternalApiKeyFilter`, non da JWT). | `SyncUsersUseCase` |
| `GameStateListener` | class | Subscriber MQTT su `building/{id}/game/+/state`. Converte payload, delega a `UpdateGameStateUseCase`. | `UpdateGameStateUseCase`, `MqttPayloadSerializer` |
| `GameSessionListener` | class | Subscriber MQTT su `building/{id}/game/+/session/#`. Gestisce start/end/pause/resume da client. | Session Use Cases, `MqttPayloadSerializer` |
| `HeartbeatListener` | class | Subscriber MQTT su `building/{id}/game/+/heartbeat`. Risponde con ACK, aggiorna timestamp ultimo contatto. | `MqttPublisherAdapter` |
| `ReservationJpaEntity` | class | `@Entity @Table("reservations")`. Controparte JPA di `Reservation`. | — |
| `GameJpaEntity` | class | `@Entity @Table("game_catalog")`. Controparte JPA di `Game`. | — |
| `UserJpaEntity` | class | `@Entity @Table("replicated_users")`. Controparte JPA di `User` (locale). | — |
| `GameSessionJpaEntity` | class | `@Entity @Table("game_sessions")`. Controparte JPA di `GameSession`. Campo `@Column(columnDefinition="JSON") resultData`. | — |
| `SessionParticipantJpaEntity` | class | `@Entity @Table("session_participants")`. Chiave composta `(sessionId, userId)`. | — |
| `OutboxEventJpaEntity` | class | `@Entity @Table("outbox_events")`. Controparte JPA di `OutboxEvent`. | — |
| `ReservationJpaRepository` | interface | `extends JpaRepository`. Query: `findByUserId()`, `findByGameId()`, `findByStatus()`. | `ReservationJpaEntity` |
| `GameJpaRepository` | interface | `extends JpaRepository`. Query: `findByBuildingId()`, `findByStatus()`. | `GameJpaEntity` |
| `UserJpaRepository` | interface | `extends JpaRepository`. Query: `findByUsername()`. | `UserJpaEntity` |
| `GameSessionJpaRepository` | interface | `extends JpaRepository`. Query: `findByBuildingId()`, `findByGameType()`, `findByStatus()`. | `GameSessionJpaEntity` |
| `OutboxEventJpaRepository` | interface | `extends JpaRepository`. Query: `findByStatusOrderByCreatedAtAsc()`. | `OutboxEventJpaEntity` |
| `ReservationRepositoryAdapter` | class | Implementa `ReservationRepository`. Usa `ReservationJpaRepository` + `ReservationMapper`. | — |
| `GameRepositoryAdapter` | class | Implementa `GameRepository`. Usa `GameJpaRepository` + `GameMapper`. | — |
| `UserRepositoryAdapter` | class | Implementa `UserRepository`. Usa `UserJpaRepository` + `UserMapper`. | — |
| `GameSessionRepositoryAdapter` | class | Implementa `GameSessionRepository`. Usa `GameSessionJpaRepository` + `GameSessionMapper`. Il mapper deserializza `result_data` JSON in `GameResult` tramite Jackson `@JsonTypeInfo`. | — |
| `OutboxEventRepositoryAdapter` | class | Implementa `OutboxEventRepository`. | — |
| `ReservationMapper` | class | `@Component`. Metodi di istanza `toDomain(ReservationJpaEntity): Reservation`, `toEntity(Reservation): ReservationJpaEntity`. Iniettato nei `RepositoryAdapter` via costruttore. | — |
| `GameMapper` | class | `@Component`. Metodi di istanza `toDomain / toEntity` per `Game ↔ GameJpaEntity`. | — |
| `UserMapper` | class | `@Component`. Metodi di istanza `toDomain / toEntity` per `User ↔ UserJpaEntity`. | — |
| `GameSessionMapper` | class | `@Component`. Metodi di istanza `toDomain / toEntity` con serializzazione/deserializzazione Jackson di `result_data` JSON. `ObjectMapper` iniettato via costruttore per configurazione centralizzata. | `ObjectMapper`, `GameResult` |
| `OutboxEventMapper` | class | `@Component`. Metodi di istanza `toDomain / toEntity` per `OutboxEvent ↔ OutboxEventJpaEntity`. | — |
| `CentralSystemRestAdapter` | class | `@Component`. Implementa `SyncCentralSystemPort`. Usa `RestTemplate` per `POST /internal/sync/receive` e health check. | `SyncCentralSystemPort`, `SyncPayloadDto` |
| `MqttPublisherAdapter` | class | `@Component`. Implementa `PublishGameStatePort` e `PublishAlertPort`. Usa Paho MQTT client per pubblicare su topic. | `PublishGameStatePort`, `PublishAlertPort`, `MqttTopics`, `MqttPayloadSerializer` |
| `MqttConfig` | class | `@Configuration`. Crea bean `MqttClient` con TLS, configura username/password. | — |
| `SecurityConfig` | class | `@Configuration @EnableMethodSecurity`. Filtro JWT per `/api/**` con chiave pubblica locale, `InternalApiKeyFilter` per `/internal/**`, endpoint pubblici (`/api/auth/**`). | `JwtAuthenticationFilter`, `InternalApiKeyFilter` |
| `TlsConfig` | class | `@Configuration`. Configura `SSLContext` per connessioni HTTPS verso il Central. | — |
| `JwtConfig` | class | `@Configuration`. Carica la coppia di chiavi RSA locale da file (`JWT_LOCAL_PRIVATE_KEY_PATH`, `JWT_LOCAL_PUBLIC_KEY_PATH`). Espone bean `JwtTokenProvider` e `JwtTokenValidator`. | — |
| `SchedulerConfig` | class | `@Configuration @EnableScheduling`. Bean `Clock` (`Clock.systemUTC()`) per iniezione nei service di dominio. | — |
| `JwtTokenProvider` | class | Genera e **firma JWT con la chiave privata RSA locale** del Local Server. Metodi: `generateToken(User): String`. Garantisce l'emissione autonoma di JWT anche offline. | — |
| `JwtTokenValidator` | class | Valida JWT con la chiave pubblica RSA locale. Estrae ruoli dal claim `roles` e li mappa a `GrantedAuthority`. Metodi: `validateToken(String): Claims`. | — |
| `JwtAuthenticationFilter` | class | `OncePerRequestFilter`. Estrae JWT, valida, estrae ruoli e li mappa a `GrantedAuthority`, setta `SecurityContext`. | `JwtTokenValidator` |
| `InternalApiKeyFilter` | class | `OncePerRequestFilter`. Valida l'header `X-Internal-Api-Key` contro il valore configurato da variabile d'ambiente `INTERNAL_API_KEY`. Applicato solo a `/internal/**`. Risponde `401` se assente o non valido. | — |

---

### 2.6 game-client-emulator — `com.gameplatform.client`

#### domain/games/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `FoosballGame` | class | Implementa `GameLifecycle`, `ScoredGame`. Gestisce punteggio a gol per due squadre. Produce `FoosballResult` a fine partita. | `GameLifecycle`, `ScoredGame`, `FoosballResult`, `UserId`, `GameSessionId` |
| `ChessGame` | class | Implementa `GameLifecycle`, `TurnBasedGame`, `BoardGame`. Gestisce board serializzato in FEN, turni alternati. Produce `ChessResult`. | `GameLifecycle`, `TurnBasedGame`, `BoardGame`, `ChessResult`, `UserId` |
| `DartsGame` | class | Implementa `GameLifecycle`, `ScoredGame`, `TurnBasedGame`. Punteggio decrescente da 501, turni alternati. Produce `DartsResult`. | `GameLifecycle`, `ScoredGame`, `TurnBasedGame`, `DartsResult` |
| `MonopolyGame` | class | Implementa `GameLifecycle`, `TurnBasedGame`, `ResourceBasedGame`. Gestisce denaro e proprietà per giocatore, bancarotta. Produce `MonopolyResult`. | `GameLifecycle`, `TurnBasedGame`, `ResourceBasedGame`, `MonopolyResult` |
| `RiskGame` | class | Implementa `GameLifecycle`, `TurnBasedGame`, `ResourceBasedGame`, `BoardGame`. Gestisce carri armati per territorio, board serializzata. Produce `RiskResult`. | `GameLifecycle`, `TurnBasedGame`, `ResourceBasedGame`, `BoardGame`, `RiskResult` |
| `GameFactory` | class | Factory Method. Crea l'implementazione concreta corretta dato un `GameType`. Metodo: `createGame(GameType, GameSessionId): GameLifecycle`. | `GameType`, `GameLifecycle`, tutte le impl concrete |
| `ClientState` | enum | Stato del client: `DISCONNECTED`, `CONNECTED`, `LOGGED_IN`, `IN_GAME`, `PAUSED`. | — |

#### application/service/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `GameOrchestrationService` | class | Coordina il ciclo di vita del gioco localmente. Usa `GameFactory` per istanziare il gioco, gestisce transizioni di stato, pubblica eventi MQTT (start/end/pause/resume), raccoglie `GameResult` a fine partita. | `GameFactory`, `GameLifecycle`, `SessionPublisher`, `GameStatePublisher` |
| `HeartbeatService` | class | Pubblica heartbeat periodico via MQTT. Ascolta ACK dal server. Se non riceve ACK entro timeout, notifica `ConnectionMonitorService`. | `HeartbeatPublisher`, `ConnectionMonitorService` |
| `ConnectionMonitorService` | class | Monitora lo stato della connessione MQTT. Gestisce riconnessione automatica. Notifica la UI dei cambi di stato. | `MqttConnectionManager` |

#### infrastructure/mqtt/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `MqttClientAdapter` | class | Adapter centrale MQTT. Wrappa `MqttClient` di Paho. Gestisce connessione TLS, subscribe/publish, callback. | `MqttConnectionManager`, `MqttTopics`, `MqttPayloadSerializer` |
| `MqttConnectionManager` | class | Gestisce il ciclo di vita della connessione MQTT: connect, disconnect, reconnect con backoff esponenziale. Configura TLS. | — |
| `GameStatePublisher` | class | Pubblica cambi di stato macchina su topic `building/{id}/game/{gameId}/state`. | `MqttClientAdapter`, `GameStatePayload` |
| `SessionPublisher` | class | Pubblica eventi sessione (start/end/pause/resume) sui rispettivi topic. | `MqttClientAdapter`, `SessionStartPayload`, `SessionEndPayload`, `SessionPausePayload` |
| `HeartbeatPublisher` | class | Pubblica heartbeat periodico su topic `building/{id}/game/{gameId}/heartbeat`. | `MqttClientAdapter`, `HeartbeatPayload` |
| `StateSubscriber` | class | Si sottoscrive a `building/{id}/game/+/state` per ricevere aggiornamenti di stato degli altri giochi nell'edificio. Aggiorna la UI. | `MqttClientAdapter`, `GameStatePayload` |

#### infrastructure/ui/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `MainView` | class | View principale JavaFX. Gestisce navigazione tra le view (login → selezione → gioco). Stage root. | `LoginView`, `GameSelectionView`, `GamePlayView` |
| `LoginView` | class | Form login con username/password. Invia credenziali al Local Server via REST (HTTP). | `LoginRequestDto`, `LoginResponseDto` |
| `GameSelectionView` | class | Lista giochi disponibili nell'edificio con stato real-time (aggiornato via MQTT). Pulsante prenota/gioca. | `GameStateDto`, `StateSubscriber` |
| `GamePlayView` | class | View di gioco attiva. Mostra interfaccia specifica per tipo di gioco (punteggio, board, risorse). Pulsanti start/stop/pause/resume. | `GameOrchestrationService`, `ScoreboardComponent`, `TimerComponent` |
| `StatisticsView` | class | Visualizza statistiche locali: partite giocate, win rate, tempo di gioco. | `StatisticsDto` |
| `ScoreboardComponent` | class | Componente JavaFX riusabile. Mostra punteggio per ogni giocatore. Aggiornamento real-time. | — |
| `TimerComponent` | class | Componente JavaFX. Cronometro della sessione di gioco. Start/stop/pause. | — |
| `StatusBarComponent` | class | Componente JavaFX. Mostra stato connessione MQTT, building ID, game ID. | `ConnectionMonitorService` |

#### infrastructure/config/

| Classe | Tipo | Descrizione | Dipendenze |
|---|---|---|---|
| `MqttClientConfig` | class | Carica configurazione MQTT da variabili d'ambiente: `MQTT_BROKER_URL`, `GAME_ID`, `GAME_TYPE`, `BUILDING_ID`. Crea bean di configurazione. | — |

---

## 3. Conteggio Totale delle Classi

| Modulo | Classi | Interfacce | Record | Enum | Totale |
|---|---|---|---|---|---|
| **shared-domain** | 0 | 6 | 13 | 6 | **25** |
| **shared-dto** | 0 | 0 | 14 | 0 | **14** |
| **shared-mqtt** | 2 | 0 | 7 | 0 | **9** |
| **central-system** | 23 | 11 | 0 | 0 | **34** |
| **local-server** | 38 | 17 | 0 | 0 | **55** |
| **game-client-emulator** | 17 | 0 | 0 | 1 | **18** |
| **TOTALE** | **80** | **34** | **34** | **7** | **155** |

