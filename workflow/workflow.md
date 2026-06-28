# Workflow di Implementazione Piattaforma Giochi

> [!NOTE]
> Questo documento definisce il workflow sequenziale per l'implementazione del sistema.
> Ciascun modulo rappresenta un package minimale del codice, con una corrispondenza 1:1 tra classi e sottopunti.

## 1. shared-domain (`com.gameplatform.shared.domain`)

### 1.1 Modello di Dominio (`model/`)
- [x] `UserId` (record)
  - [x] `UserId(String value)`
  - [x] `String value()`
- [x] `GameId` (record)
  - [x] `GameId(String value)`
  - [x] `String value()`
- [x] `BuildingId` (record)
  - [x] `BuildingId(String value)`
  - [x] `String value()`
- [x] `GameSessionId` (record)
  - [x] `GameSessionId(String value)`
  - [x] `String value()`
- [x] `ReservationId` (record)
  - [x] `ReservationId(String value)`
  - [x] `String value()`
- [x] `GameType` (enum)
  - [x] `CHESS`
  - [x] `FOOSBALL`
  - [x] `DARTS`
  - [x] `MONOPOLY`
  - [x] `RISK`
  - [x] `SLOT_MACHINE`
  - [x] `ROULETTE`
- [x] `GameStatus` (enum)
  - [x] `WAITING`
  - [x] `IN_PROGRESS`
  - [x] `PAUSED`
  - [x] `COMPLETED`
  - [x] `ABORTED`
- [x] `GameMachineStatus` (enum)
  - [x] `AVAILABLE`
  - [x] `RESERVED`
  - [x] `IN_USE`
  - [x] `MAINTENANCE`
- [x] `ReservationStatus` (enum)
  - [x] `PENDING`
  - [x] `CONFIRMED`
  - [x] `CANCELLED`
  - [x] `EXPIRED`
- [x] `WinCondition` (enum)
  - [x] `WIN`
  - [x] `DRAW`
  - [x] `ABANDONED`
  - [x] `TIMEOUT`
- [x] `StopReason` (enum)
  - [x] `COMPLETED`
  - [x] `ABORTED`
  - [x] `TIMEOUT`

### 1.2 Interfacce di Gioco (`game/`)
- [x] `GameLifecycle` (interface)
  - [x] `void start(List<UserId> participants)`
  - [x] `void stop(StopReason reason)`
  - [x] `void pause()`
  - [x] `void resume()`
  - [x] `GameStatus getStatus()`
  - [x] `GameType getGameType()`
  - [x] `GameSessionId getSessionId()`
- [x] `TurnBasedGame` (interface)
  - [x] `UserId getCurrentPlayer()`
  - [x] `void endTurn()`
  - [x] `int getTurnNumber()`
- [x] `ScoredGame` (interface)
  - [x] `Map<UserId, Integer> getCurrentScores()`
  - [x] `void recordScore(UserId player, int delta)`
- [x] `ResourceBasedGame` (interface)
  - [x] `Map<UserId, Map<String, Integer>> getResources()`
  - [x] `void updateResource(UserId player, String resourceKey, int newValue)`
- [x] `BoardGame` (interface)
  - [x] `String serializeBoardState()`
  - [x] `void restoreBoardState(String serializedState)`

### 1.3 Risultati dei Giochi (`result/`)
- [x] `GameResult` (interface)
  - [x] `UserId getWinnerId()`
  - [x] `List<UserId> getWinnerIds()`
  - [x] `WinCondition getWinCondition()`
- [x] `FoosballResult` (record)
  - [x] `FoosballResult(UserId winnerId, List<UserId> winnerIds, Map<String, Integer> finalScores, WinCondition winCondition)`
  - [x] `UserId winnerId()`
  - [x] `List<UserId> winnerIds()`
  - [x] `Map<String, Integer> finalScores()`
  - [x] `WinCondition winCondition()`
- [x] `ChessResult` (record)
  - [x] `ChessResult(UserId winnerId, List<UserId> winnerIds, String terminationReason, String finalFenState, WinCondition winCondition)`
  - [x] `UserId winnerId()`
  - [x] `List<UserId> winnerIds()`
  - [x] `String terminationReason()`
  - [x] `String finalFenState()`
  - [x] `WinCondition winCondition()`
- [x] `DartsResult` (record)
  - [x] `DartsResult(UserId winnerId, List<UserId> winnerIds, Map<String, Integer> finalScores, Map<String, Integer> dartsThrown, WinCondition winCondition)`
  - [x] `UserId winnerId()`
  - [x] `List<UserId> winnerIds()`
  - [x] `Map<String, Integer> finalScores()`
  - [x] `Map<String, Integer> dartsThrown()`
  - [x] `WinCondition winCondition()`
- [x] `MonopolyResult` (record)
  - [x] `MonopolyResult(UserId winnerId, List<UserId> winnerIds, Map<String, Integer> finalMoney, Map<String, List<String>> ownedProperties, WinCondition winCondition)`
  - [x] `UserId winnerId()`
  - [x] `List<UserId> winnerIds()`
  - [x] `Map<String, Integer> finalMoney()`
  - [x] `Map<String, List<String>> ownedProperties()`
  - [x] `WinCondition winCondition()`
- [x] `RiskResult` (record)
  - [x] `RiskResult(UserId winnerId, List<UserId> winnerIds, Map<String, Map<String, Integer>> territoriesAtEnd, int totalRounds, WinCondition winCondition)`
  - [x] `UserId winnerId()`
  - [x] `List<UserId> winnerIds()`
  - [x] `Map<String, Map<String, Integer>> territoriesAtEnd()`
  - [x] `int totalRounds()`
  - [x] `WinCondition winCondition()`
- [x] `SlotResult` (record)
  - [x] `SlotResult(String visitorId, int totalSpins, int creditsIn, int creditsOut, int biggestWin, WinCondition winCondition)`
  - [x] `String visitorId()`
  - [x] `int totalSpins()`
  - [x] `int creditsIn()`
  - [x] `int creditsOut()`
  - [x] `int biggestWin()`
  - [x] `WinCondition winCondition()`
  - [x] `@Override UserId getWinnerId()` (restituisce `new UserId(visitorId)` se `winCondition == WinCondition.WIN`, altrimenti `null`)
  - [x] `@Override List<UserId> getWinnerIds()` (restituisce `List.of(getWinnerId())` se non nullo, altrimenti lista vuota)
- [x] `RouletteResult` (record)
  - [x] `RouletteResult(String visitorId, int totalRounds, int totalBetAmount, int totalPayout, List<String> winningNumbers, WinCondition winCondition)`
  - [x] `String visitorId()`
  - [x] `int totalRounds()`
  - [x] `int totalBetAmount()`
  - [x] `int totalPayout()`
  - [x] `List<String> winningNumbers()`
  - [x] `WinCondition winCondition()`
  - [x] `@Override UserId getWinnerId()` (restituisce `new UserId(visitorId)` se `winCondition == WinCondition.WIN`, altrimenti `null`)
  - [x] `@Override List<UserId> getWinnerIds()` (restituisce `List.of(getWinnerId())` se non nullo, altrimenti lista vuota)

### 1.4 Eventi di Dominio (`events/`)
- [x] `DomainEvent` (interface)
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()` (restituisce il tipo di evento come stringa statica)
- [x] `UserRegisteredEvent` (record)
  - [x] `UserRegisteredEvent(String eventId, Instant occurredAt, UserId userId, String username, String hashedPassword, List<String> roles)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()` (restituisce sempre "USER_REGISTERED")
- [x] `UserUpdatedEvent` (record)
  - [x] `UserUpdatedEvent(String eventId, Instant occurredAt, UserId userId, String username, String hashedPassword, List<String> roles)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()` (restituisce sempre "USER_UPDATED")
- [x] `ReservationCreatedEvent` (record)
  - [x] `ReservationCreatedEvent(String eventId, Instant occurredAt, ReservationId reservationId, GameId gameId, UserId userId, BuildingId buildingId)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()` (restituisce sempre "RESERVATION_CREATED")
- [x] `ReservationCancelledEvent` (record)
  - [x] `ReservationCancelledEvent(String eventId, Instant occurredAt, ReservationId reservationId)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()` (restituisce sempre "RESERVATION_CANCELLED")
- [x] `GameSessionCompletedEvent` (record)
  - [x] `GameSessionCompletedEvent(String eventId, Instant occurredAt, GameSessionId sessionId, GameType gameType, String resultJson)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()` (restituisce sempre "GAME_SESSION_COMPLETED")
- [x] `GameStateChangedEvent` (record)
  - [x] `GameStateChangedEvent(String eventId, Instant occurredAt, GameId gameId, GameMachineStatus oldStatus, GameMachineStatus newStatus)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()` (restituisce sempre "GAME_STATE_CHANGED")
- [x] `StatisticsUpdatedEvent` (record)
  - [x] `StatisticsUpdatedEvent(String eventId, Instant occurredAt, BuildingId buildingId)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()` (restituisce sempre "STATISTICS_UPDATED")

## 2. shared-dto (`com.gameplatform.shared.dto`)

`UserDto` (record)
  - [x] `String id()`
  - [x] `String username()`
  - [x] `String email()`
  - [x] `List<String> roles()`
  - [x] `Instant createdAt()`
`UserSyncDto` (record)
  - [x] `String userId()`
  - [x] `String username()`
  - [x] `String hashedPassword()`
  - [x] `List<String> roles()`
`LoginRequestDto` (record)
  - [x] `String username()`
  - [x] `String password()`
`LoginResponseDto` (record)
  - [x] `String token()`
  - [x] `String userId()`
  - [x] `Instant expiresAt()`
`ReservationDto` (record)
  - [x] `String id()`
  - [x] `String gameId()`
  - [x] `String userId()`
  - [x] `ReservationStatus status()`
  - [x] `Instant startTime()`
  - [x] `Instant endTime()`
`CreateReservationRequestDto` (record)
  - [x] `String gameId()`
  - [x] `String userId()`
  - [x] `Instant startTime()`
  - [x] `Instant endTime()`
`GameStateDto` (record)
  - [x] `String gameId()`
  - [x] `GameType gameType()`
  - [x] `String name()`
  - [x] `String buildingId()`
  - [x] `GameMachineStatus status()`
`GameSessionDto` (record)
  - [x] `String id()`
  - [x] `String gameId()`
  - [x] `GameType gameType()`
  - [x] `GameStatus status()`
  - [x] `Instant startedAt()`
  - [x] `Instant endedAt()`
  - [x] `Integer durationSeconds()`
  - [x] `String winnerId()`
  - [x] `WinCondition winCondition()`
  - [x] `String resultData()`
`GameSessionResultDto` (record)
  - [x] `GameSessionDto session()`
  - [x] `List<String> participants()`
`StatisticsDto` (record)
  - [x] `String buildingId()`
  - [x] `String gameType()`
  - [x] `Instant periodStart()`
  - [x] `Instant periodEnd()`
  - [x] `Integer totalSessions()`
  - [x] `Integer avgDuration()`
  - [x] `Integer totalReservations()`
  - [x] `String data()`
`OutboxEventDto` (record)
  - [x] `String eventId()`
  - [x] `String eventType()`
  - [x] `String payload()`
  - [x] `Instant createdAt()`
`SyncPayloadDto` (record)
  - [x] `String buildingId()`
  - [x] `List<OutboxEventDto> events()`
`AlertDto` (record)
  - [x] `String buildingId()`
  - [x] `String gameId()`
  - [x] `String alertType()`
  - [x] `String message()`
  - [x] `Instant timestamp()`
`ErrorResponseDto` (record)
  - [x] `int status()`
  - [x] `String error()`
  - [x] `String message()`
  - [x] `Instant timestamp()`

## 3. shared-mqtt (`com.gameplatform.shared.mqtt`)

### 3.1 Topic e Configurazione MQTT
- [x] `MqttTopics` (class)
  - [x] `static String gameState(String buildingId, String gameId)`
  - [x] `static String sessionStart(String buildingId, String gameId)`
  - [x] `static String sessionEnd(String buildingId, String gameId)`
  - [x] `static String sessionPause(String buildingId, String gameId)`
  - [x] `static String sessionResume(String buildingId, String gameId)`
  - [x] `static String heartbeat(String buildingId, String gameId)`
  - [x] `static String heartbeatAck(String buildingId, String gameId)`
  - [x] `static String alerts(String buildingId)`
- [x] `MqttQos` (class)
  - [x] `static final int STATE = 1`
  - [x] `static final int SESSION = 1`
  - [x] `static final int HEARTBEAT = 0`
- [x] `MqttPayloadSerializer` (class)
  - [x] `static byte[] serialize(Object obj)`
  - [x] `static <T> T deserialize(byte[] data, Class<T> clazz)`

### 3.2 Payload MQTT (`payload/`)
- [x] `GameStatePayload` (record)
  - [x] `String gameId()`
  - [x] `GameMachineStatus status()`
  - [x] `String userId()`
- [x] `SessionStartPayload` (record)
  - [x] `String sessionId()`
  - [x] `GameType gameType()`
  - [x] `List<String> participants()`
- [x] `SessionEndPayload` (record)
  - [x] `String sessionId()`
  - [x] `String winnerId()`
  - [x] `WinCondition winCondition()`
  - [x] `String resultData()`
- [x] `SessionPausePayload` (record)
  - [x] `String sessionId()`
  - [x] `String pausedBy()`
- [x] `HeartbeatPayload` (record)
  - [x] `String gameId()`
  - [x] `Instant timestamp()`
- [x] `HeartbeatAckPayload` (record)
  - [x] `String gameId()`
  - [x] `Instant serverTimestamp()`
- [x] `AlertPayload` (record)
  - [x] `String alertType()`
  - [x] `String gameId()`
  - [x] `String message()`
  - [x] `Instant timestamp()`

## 4. central-system (`com.gameplatform.central`)

### 4.1 Modello di Dominio Centrale (`domain/model/`)
- [x] `User` (class)
  - [x] `User(UserId id, String username, String passwordHash, String email, List<String> roles, Instant createdAt)`
  - [x] `void changePassword(String newPasswordHash)`
  - [x] `void updateRoles(List<String> newRoles)`
- [x] `AggregatedStatistics` (class)
  - [x] `AggregatedStatistics(String id, BuildingId buildingId, GameType gameType, LocalDate periodStart, LocalDate periodEnd, int totalSessions, int avgDurationSeconds, int totalReservations, Map<String, Object> data)`
  - [x] `void mergeWith(AggregatedStatistics other)`
- [x] `RegisteredLocalServer` (class)
  - [x] `RegisteredLocalServer(BuildingId buildingId, String baseUrl, Instant lastSeenAt, boolean isActive)`
- [x] `ProcessedEvent` (class)
  - [x] `ProcessedEvent(String eventId, Instant processedAt)`
- [x] `OutboxEvent` (class)
  - [x] `OutboxEvent(String id, String eventType, String payload, String status, Instant createdAt, Instant sentAt)`

### 4.2 Porte di Ingresso (`domain/ports/in/`)
- [x] `RegisterUserUseCase` (interface)
  - [x] `User register(String username, String password, String email)`
- [x] `UpdateUserUseCase` (interface)
  - [x] `User updateUser(UserId id, String newPassword, List<String> newRoles)`
- [x] `AuthenticateUserUseCase` (interface)
  - [x] `LoginResponseDto authenticate(String username, String password)`
- [x] `GetGlobalStatisticsUseCase` (interface)
  - [x] `List<StatisticsDto> getStatistics(BuildingId buildingId, GameType gameType, LocalDate start, LocalDate end)`
- [x] `ReceiveSyncDataUseCase` (interface)
  - [x] `void receiveSyncPayload(SyncPayloadDto payload)`
- [x] `GetAllUsersUseCase` (interface)
  - [x] `List<UserSyncDto> getAllUsersForSync()`

### 4.3 Porte di Uscita (`domain/ports/out/`)
- [x] `UserRepository` (interface)
  - [x] `User save(User user)`
  - [x] `Optional<User> findById(UserId id)`
  - [x] `Optional<User> findByUsername(String username)`
  - [x] `Optional<User> findByEmail(String email)`
  - [x] `List<User> findAll()`
- [x] `StatisticsRepository` (interface)
  - [x] `AggregatedStatistics save(AggregatedStatistics stats)`
  - [x] `Optional<AggregatedStatistics> findByBuildingAndTypeAndPeriod(BuildingId buildingId, GameType gameType, LocalDate periodStart)`
  - [x] `Optional<AggregatedStatistics> findByBuildingAndTypeAndPeriodWithLock(BuildingId buildingId, GameType gameType, LocalDate periodStart)`
  - [x] `List<AggregatedStatistics> findByPeriod(BuildingId buildingId, GameType gameType, LocalDate start, LocalDate end)`
- [x] `ProcessedEventRepository` (interface)
  - [x] `boolean existsByEventId(String eventId)`
  - [x] `void save(ProcessedEvent event)`
- [x] `OutboxEventRepository` (interface)
  - [x] `OutboxEvent save(OutboxEvent event)`
  - [x] `List<OutboxEvent> findPending()`
  - [x] `List<OutboxEvent> findPendingLimit(int limit)`
  - [x] `void markAsSent(String id)`
- [x] `LocalServerRegistryPort` (interface)
  - [x] `List<RegisteredLocalServer> getActiveLocalServers()`
  - [x] `void register(RegisteredLocalServer server)`
  - [x] `void updateLastSeenAt(BuildingId buildingId, Instant lastSeenAt)`
- [x] `PushUserToLocalServersPort` (interface)
  - [x] `void pushUsers(List<UserSyncDto> users, RegisteredLocalServer server)`

### 4.4 Eccezioni di Dominio (`domain/exception/`)
- [x] `UserAlreadyExistsException` (class)
- [x] `InvalidCredentialsException` (class)
- [x] `DuplicateEventException` (class)

### 4.5 Servizi Applicativi (`application/service/`)
- [x] `UserService` (class)
  - [x] `User register(String username, String password, String email)`
  - [x] `User updateUser(UserId id, String newPassword, List<String> newRoles)`
  - [x] `List<UserSyncDto> getAllUsersForSync()`
- [x] `AuthService` (class)
  - [x] `LoginResponseDto authenticate(String username, String password)`
- [x] `StatisticsAggregationService` (class)
  - [x] `List<StatisticsDto> getStatistics(BuildingId buildingId, GameType gameType, LocalDate start, LocalDate end)`
- [x] `SyncReceiverService` (class)
  - [x] `void receiveSyncPayload(SyncPayloadDto payload)`
- [x] `UserReplicationSchedulerService` (class)
  - [x] `void replicateUsers()`

### 4.6 Adattatori REST in Ingresso (`infrastructure/adapters/in/rest/`)
- [x] `UserController` (class)
  - [x] `ResponseEntity<UserDto> register(CreateUserRequestDto request)`
- [x] `AuthController` (class)
  - [x] `ResponseEntity<LoginResponseDto> login(LoginRequestDto request)`
- [x] `StatisticsController` (class)
  - [x] `ResponseEntity<List<StatisticsDto>> getStats(String buildingId, String gameType, String start, String end)`
- [x] `SyncController` (class)
  - [x] `ResponseEntity<Void> receiveSync(SyncPayloadDto payload, String apiKey)`

### 4.7 Adattatori di Persistenza MySQL (`infrastructure/adapters/out/mysql/`)
- [x] `UserJpaEntity` (class)
- [x] `AggregatedStatisticsJpaEntity` (class)
- [x] `ProcessedEventJpaEntity` (class)
- [x] `OutboxEventJpaEntity` (class)
- [x] `RegisteredLocalServerJpaEntity` (class)

- [x] `UserJpaRepository` (interface)
  - [x] `Optional<UserJpaEntity> findByUsername(String username)`
- [x] `StatisticsJpaRepository` (interface)
  - [x] `Optional<AggregatedStatisticsJpaEntity> findByBuildingIdAndGameTypeAndPeriodStart(String buildingId, String gameType, LocalDate periodStart)`
- [x] `ProcessedEventJpaRepository` (interface)
- [x] `OutboxEventJpaRepository` (interface)
  - [x] `List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(String status)`
- [x] `LocalServerJpaRepository` (interface)
  - [x] `List<RegisteredLocalServerJpaEntity> findByIsActiveTrue()`

- [x] `UserRepositoryAdapter` (class)
  - [x] `User save(User user)`
  - [x] `Optional<User> findById(UserId id)`
  - [x] `Optional<User> findByUsername(String username)`
  - [x] `List<User> findAll()`
- [x] `StatisticsRepositoryAdapter` (class)
  - [x] `AggregatedStatistics save(AggregatedStatistics stats)`
  - [x] `Optional<AggregatedStatistics> findByBuildingAndTypeAndPeriod(BuildingId buildingId, GameType gameType, LocalDate periodStart)`
  - [x] `List<AggregatedStatistics> findByPeriod(LocalDate start, LocalDate end)`
- [x] `ProcessedEventRepositoryAdapter` (class)
  - [x] `boolean existsByEventId(String eventId)`
  - [x] `void save(ProcessedEvent event)`
- [x] `OutboxEventRepositoryAdapter` (class)
  - [x] `OutboxEvent save(OutboxEvent event)`
  - [x] `List<OutboxEvent> findPending()`
  - [x] `void markAsSent(String id)`
- [x] `LocalServerRegistryAdapter` (class)
  - [x] `List<RegisteredLocalServer> getActiveLocalServers()`
  - [x] `void register(RegisteredLocalServer server)`

- [x] `UserMapper` (class)
  - [x] `User toDomain(UserJpaEntity entity)`
  - [x] `UserJpaEntity toEntity(User domain)`
- [x] `StatisticsMapper` (class)
  - [x] `AggregatedStatistics toDomain(AggregatedStatisticsJpaEntity entity)`
  - [x] `AggregatedStatisticsJpaEntity toEntity(AggregatedStatistics domain)`
- [x] `OutboxEventMapper` (class)
  - [x] `OutboxEvent toDomain(OutboxEventJpaEntity entity)`
  - [x] `OutboxEventJpaEntity toEntity(OutboxEvent domain)`

### 4.8 Adattatori REST in Uscita (`infrastructure/adapters/out/rest/`)
- [x] `LocalServerRestAdapter` (class)
  - [x] `void pushUsers(List<UserSyncDto> users, RegisteredLocalServer server)`

### 4.9 Configurazione e Sicurezza (`infrastructure/config/` e `security/`)
- [x] `CentralSystemApplication` (class)
  - [x] `static void main(String[] args)`
- [x] `SecurityConfig` (class)
  - [x] `SecurityFilterChain filterChain(HttpSecurity http)`
- [x] `JwtConfig` (class)
  - [x] `JwtTokenProvider jwtTokenProvider()`
- [x] `SchedulerConfig` (class)
- [x] `CorsConfig` (class)
  - [x] `CorsFilter corsFilter()`
- [x] `JwtTokenProvider` (class)
  - [x] `String generateToken(User user)`
  - [x] `Claims getClaims(String token)`
- [x] `JwtAuthenticationFilter` (class)
  - [x] `void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)`
- [x] `InternalApiKeyFilter` (class)
  - [x] `void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)`
- [x] `PasswordEncoderConfig` (class)
  - [x] `PasswordEncoder passwordEncoder()`

## 5. local-server (`com.gameplatform.local`)

### 5.1 Modello di Dominio Locale (`domain/model/`)
- [x] `Reservation` (class)
  - [x] `Reservation(ReservationId id, GameId gameId, UserId userId, ReservationStatus status, Instant startTime, Instant endTime, Instant createdAt)`
  - [x] `boolean canBeCancelled(Clock clock)`
  - [x] `void confirm()`
  - [x] `void cancel()`
  - [x] `void expire()`
- [x] `Game` (class)
  - [x] `Game(GameId id, GameType gameType, String name, BuildingId buildingId, GameMachineStatus status)`
  - [x] `void reserve()`
  - [x] `void startUse()`
  - [x] `void release()`
  - [x] `void setMaintenance()`
- [x] `User` (class)
  - [x] `User(UserId userId, String username, String passwordHash, List<String> roles, Instant syncedAt)`
- [x] `GameSession` (class)
  - [x] `GameSession(GameSessionId id, GameId gameId, GameType gameType, BuildingId buildingId, GameStatus status, Instant startedAt, Instant endedAt, Integer durationSeconds, UserId winnerId, WinCondition winCondition, GameResult result)`
  - [x] `void complete(GameResult result)`
  - [x] `void abort(StopReason reason)`
  - [x] `void pause()`
  - [x] `void resume()`
  - [x] `void calculateDuration()`
- [x] `OutboxEvent` (class)
  - [x] `OutboxEvent(String id, String eventType, String payload, String status, Instant createdAt, Instant sentAt, int retryCount)`
  - [x] `void markAsSent()`
  - [x] `void incrementRetry()`
  - [x] `boolean hasFailed()`
- [x] `LocalStatistics` (class)
  - [x] `LocalStatistics(GameType gameType, int totalSessions, double avgDuration, int totalReservations, Map<String, Double> winRateByUser)`
  - [x] `void recalculate(List<GameSession> sessions)`

### 5.2 Porte di Ingresso Locali (`domain/ports/in/`)
- [x] `CreateReservationUseCase` (interface)
  - [x] `Reservation create(GameId gameId, UserId userId, Instant start, Instant end)`
- [x] `CancelReservationUseCase` (interface)
  - [x] `void cancel(ReservationId reservationId)`
- [x] `GetReservationsUseCase` (interface)
  - [x] `List<Reservation> getByUser(UserId userId)`
  - [x] `List<Reservation> getByGame(GameId gameId)`
- [x] `UpdateGameStateUseCase` (interface)
  - [x] `void updateState(GameId gameId, GameMachineStatus newStatus)`
- [x] `GetAvailableGamesUseCase` (interface)
  - [x] `List<Game> getAvailable()`
  - [x] `List<Game> getAll()`
- [x] `StartGameSessionUseCase` (interface)
  - [x] `GameSession start(GameId gameId, GameType gameType, List<UserId> participants, ReservationId reservationId)`
- [x] `EndGameSessionUseCase` (interface)
  - [x] `void end(GameSessionId sessionId, GameResult result)`
- [x] `PauseGameSessionUseCase` (interface)
  - [x] `void pause(GameSessionId sessionId)`
- [x] `ResumeGameSessionUseCase` (interface)
  - [x] `void resume(GameSessionId sessionId)`
- [x] `GetStatisticsUseCase` (interface)
  - [x] `LocalStatistics getStatistics(GameType gameType)`
  - [x] `List<GameSession> getActiveSessions()`
- [x] `AuthenticateLocalUserUseCase` (interface)
  - [x] `LoginResponseDto authenticate(String username, String password)`
- [x] `SyncUsersUseCase` (interface)
  - [x] `void syncUsers(List<UserSyncDto> users)`

### 5.3 Porte di Uscita Locali (`domain/ports/out/`)
- [x] `ReservationRepository` (interface)
  - [x] `Reservation save(Reservation reservation)`
  - [x] `Optional<Reservation> findById(ReservationId id)`
  - [x] `List<Reservation> findByUserId(UserId userId)`
  - [x] `List<Reservation> findByGameId(GameId gameId)`
  - [x] `List<Reservation> findByStatus(ReservationStatus status)`
  - [x] `List<Reservation> findExpired(Instant now)`
- [x] `GameRepository` (interface)
  - [x] `Game save(Game game)`
  - [x] `Optional<Game> findById(GameId id)`
  - [x] `List<Game> findByBuildingId(BuildingId buildingId)`
  - [x] `List<Game> findByStatus(GameMachineStatus status)`
  - [x] `List<Game> findAll()`
- [x] `UserRepository` (interface)
  - [x] `User save(User user)`
  - [x] `Optional<User> findByUsername(String username)`
  - [x] `void saveAll(List<User> users)`
- [x] `GameSessionRepository` (interface)
  - [x] `GameSession save(GameSession session)`
  - [x] `Optional<GameSession> findById(GameSessionId id)`
  - [x] `List<GameSession> findByBuildingId(BuildingId buildingId)`
  - [x] `List<GameSession> findByGameType(GameType gameType)`
  - [x] `List<GameSession> findByStatus(GameStatus status)`
  - [x] `List<GameSession> findPendingSync()`
  - [x] `Optional<GameSession> findActiveByGameId(GameId gameId)`
- [x] `OutboxEventRepository` (interface)
  - [x] `OutboxEvent save(OutboxEvent event)`
  - [x] `List<OutboxEvent> findPending()`
  - [x] `void markAsSent(String id)`
  - [x] `void incrementRetry(String id)`
- [x] `SyncCentralSystemPort` (interface)
  - [x] `boolean isReachable()`
  - [x] `boolean sendSyncPayload(SyncPayloadDto payload)`
- [x] `PublishGameStatePort` (interface)
  - [x] `void publishState(GameId gameId, GameMachineStatus status)`
  - [x] `void publishSessionEvent(String topic, Object payload)`
- [x] `PublishAlertPort` (interface)
  - [x] `void publishAlert(AlertPayload payload)`

### 5.4 Eccezioni Locali (`domain/exception/`)
- [x] `GameNotAvailableException` (class)
- [x] `ReservationNotFoundException` (class)
- [x] `ReservationExpiredException` (class)
- [x] `UserNotFoundException` (class)
- [x] `SessionAlreadyActiveException` (class)
- [x] `InvalidGameStateTransitionException` (class)

### 5.5 Servizi Applicativi Locali (`application/service/`)
- [x] `ReservationService` (class)
  - [x] `Reservation create(GameId gameId, UserId userId, Instant start, Instant end)`
  - [x] `void cancel(ReservationId reservationId)`
  - [x] `List<Reservation> getByUser(UserId userId)`
  - [x] `List<Reservation> getByGame(GameId gameId)`
- [x] `ReservationExpirationService` (class)
  - [x] `void expireReservations()`
- [x] `GameStateService` (class)
  - [x] `void updateState(GameId gameId, GameMachineStatus newStatus)`
  - [x] `List<Game> getAvailable()`
  - [x] `List<Game> getAll()`
- [x] `GameSessionService` (class)
  - [x] `GameSession start(GameId gameId, GameType gameType, List<UserId> participants, ReservationId reservationId)`
  - [x] `void end(GameSessionId sessionId, GameResult result)`
  - [x] `void pause(GameSessionId sessionId)`
  - [x] `void resume(GameSessionId sessionId)`
- [x] `SessionRecoveryService` (class)
  - [x] `void start()`
  - [x] `void stop()`
- [x] `StatisticsService` (class)
  - [x] `LocalStatistics getStatistics(GameType gameType)`
  - [x] `List<GameSession> getActiveSessions()`
- [x] `LocalAuthService` (class)
  - [x] `LoginResponseDto authenticate(String username, String password)`
- [x] `UserSyncService` (class)
  - [x] `void syncUsers(List<UserSyncDto> users)`
- [x] `SyncSchedulerService` (class)
  - [x] `void syncWithCentral()`
- [x] `HealthCheckService` (class)
  - [x] `void performHealthCheck()`

### 5.6 Adattatori REST in Ingresso Locali (`infrastructure/adapters/in/rest/`)
- [x] `ReservationController` (class)
  - [x] `ResponseEntity<ReservationDto> create(CreateReservationRequestDto req)`
  - [x] `ResponseEntity<Void> cancel(String id)`
  - [x] `ResponseEntity<List<ReservationDto>> getByUser(String userId)`
- [x] `GameController` (class)
  - [x] `ResponseEntity<List<GameStateDto>> getGames()`
  - [x] `ResponseEntity<List<GameStateDto>> getAvailableGames()`
- [x] `GameSessionController` (class)
  - [x] `ResponseEntity<GameSessionDto> start(CreateSessionRequestDto req)`
  - [x] `ResponseEntity<Void> end(String id, GameResult result)`
  - [x] `ResponseEntity<Void> pause(String id)`
  - [x] `ResponseEntity<Void> resume(String id)`
- [x] `StatisticsController` (class)
  - [x] `ResponseEntity<LocalStatistics> getStats(String gameType)`
  - [x] `ResponseEntity<List<GameSessionDto>> getActiveSessions()`
- [x] `AuthController` (class)
  - [x] `ResponseEntity<LoginResponseDto> login(LoginRequestDto req)`
- [x] `InternalSyncController` (class)
  - [x] `ResponseEntity<Void> syncUsers(List<UserSyncDto> users, String apiKey)`

### 5.7 Adattatori MQTT in Ingresso (`infrastructure/adapters/in/mqtt/`)
- [x] `GameStateListener` (class)
  - [x] `void handleStateMessage(String topic, byte[] payload)`
- [x] `GameSessionListener` (class)
  - [x] `void handleSessionMessage(String topic, byte[] payload)`
- [x] `HeartbeatListener` (class)
  - [x] `void handleHeartbeat(String topic, byte[] payload)`

### 5.8 Adattatori MySQL Locali (`infrastructure/adapters/out/mysql/`)
- [x] `ReservationJpaEntity` (class)
- [x] `GameJpaEntity` (class)
- [x] `UserJpaEntity` (class)
- [x] `GameSessionJpaEntity` (class)
- [x] `SessionParticipantJpaEntity` (class)
- [x] `OutboxEventJpaEntity` (class)

- [x] `ReservationJpaRepository` (interface)
  - [x] `List<ReservationJpaEntity> findByUserId(String userId)`
  - [x] `List<ReservationJpaEntity> findByGameId(String gameId)`
- [x] `GameJpaRepository` (interface)
  - [x] `List<GameJpaEntity> findByBuildingId(String buildingId)`
  - [x] `List<GameJpaEntity> findByStatus(String status)`
- [x] `UserJpaRepository` (interface)
  - [x] `Optional<UserJpaEntity> findByUsername(String username)`
- [x] `GameSessionJpaRepository` (interface)
  - [x] `List<GameSessionJpaEntity> findByBuildingId(String buildingId)`
  - [x] `List<GameSessionJpaEntity> findByStatus(String status)`
- [x] `OutboxEventJpaRepository` (interface)
  - [x] `List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(String status)`

- [x] `ReservationRepositoryAdapter` (class)
  - [x] `Reservation save(Reservation reservation)`
  - [x] `Optional<Reservation> findById(ReservationId id)`
  - [x] `List<Reservation> findByUserId(UserId userId)`
- [x] `GameRepositoryAdapter` (class)
  - [x] `Game save(Game game)`
  - [x] `Optional<Game> findById(GameId id)`
  - [x] `List<Game> findAll()`
- [x] `UserRepositoryAdapter` (class)
  - [x] `User save(User user)`
  - [x] `Optional<User> findByUsername(String username)`
  - [x] `void saveAll(List<User> users)`
- [x] `GameSessionRepositoryAdapter` (class)
  - [x] `GameSession save(GameSession session)`
  - [x] `Optional<GameSession> findById(GameSessionId id)`
  - [x] `List<GameSession> findPendingSync()`
- [x] `OutboxEventRepositoryAdapter` (class)
  - [x] `OutboxEvent save(OutboxEvent event)`
  - [x] `List<OutboxEvent> findPending()`

- [x] `ReservationMapper` (class)
  - [x] `Reservation toDomain(ReservationJpaEntity entity)`
  - [x] `ReservationJpaEntity toEntity(Reservation domain)`
- [x] `GameMapper` (class)
  - [x] `Game toDomain(GameJpaEntity entity)`
  - [x] `GameJpaEntity toEntity(Game domain)`
- [x] `UserMapper` (class)
  - [x] `User toDomain(UserJpaEntity entity)`
  - [x] `UserJpaEntity toEntity(User domain)`
- [x] `GameSessionMapper` (class)
  - [x] `GameSession toDomain(GameSessionJpaEntity entity)`
  - [x] `GameSessionJpaEntity toEntity(GameSession domain)`
- [x] `OutboxEventMapper` (class)
  - [x] `OutboxEvent toDomain(OutboxEventJpaEntity entity)`
  - [x] `OutboxEventJpaEntity toEntity(OutboxEvent domain)`

### 5.9 REST e MQTT in Uscita (`infrastructure/adapters/out/rest/` e `mqtt/`)
- [x] `CentralSystemRestAdapter` (class)
  - [x] `boolean isReachable()`
  - [x] `boolean sendSyncPayload(SyncPayloadDto payload)`
- [x] `MqttPublisherAdapter` (class)
  - [x] `void publishState(GameId gameId, GameMachineStatus status)`
  - [x] `void publishAlert(AlertPayload payload)`

### 5.10 Configurazione e Sicurezza Locali (`infrastructure/config/` e `security/`)
- [x] `LocalServerApplication` (class)
  - [x] `static void main(String[] args)`
- [x] `MqttConfig` (class)
  - [x] `IMqttClient mqttClient()`
- [x] `SecurityConfig` (class)
  - [x] `SecurityFilterChain filterChain(HttpSecurity http)`
- [x] `TlsConfig` (class)
  - [x] `SSLContext sslContext()`
- [x] `SchedulerConfig` (class)
  - [x] `Clock clock()`
- [x] `JwtTokenProvider` (class)
  - [x] `String generateToken(User user)`
- [x] `JwtTokenValidator` (class)
  - [x] `Claims validateToken(String token)`
- [x] `JwtAuthenticationFilter` (class)
  - [x] `void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)`
- [x] `InternalApiKeyFilter` (class)
  - [x] `void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)`

### 5.11 Verifica utilizzo Eccezioni
- [x] Verificare che le eccezioni create nel punto 5.4 siano state usate ogni qualvolta possibile dal codice scritto nei punti 5.x

## 6. game-client-emulator (`com.gameplatform.client`)

### 6.1 Implementazioni dei Giochi ed Emulatori (`domain/games/`)
- [X] `FoosballGame` (class)
  - [X] `void start(List<UserId> participants)`
  - [X] `void stop(StopReason reason)`
  - [X] `void recordScore(UserId player, int delta)`
- [X] `ChessGame` (class)
  - [X] `void start(List<UserId> participants)`
  - [X] `void stop(StopReason reason)`
  - [X] `void endTurn()`
  - [X] `String serializeBoardState()`
- [X] `DartsGame` (class)
  - [X] `void start(List<UserId> participants)`
  - [X] `void stop(StopReason reason)`
  - [X] `void recordScore(UserId player, int delta)`
  - [X] `void endTurn()`
- [X] `MonopolyGame` (class)
  - [X] `void start(List<UserId> participants)`
  - [X] `void stop(StopReason reason)`
  - [X] `void updateResource(UserId player, String key, int val)`
  - [X] `void endTurn()`
- [X] `RiskGame` (class)
  - [X] `void start(List<UserId> participants)`
  - [X] `void stop(StopReason reason)`
  - [X] `void updateResource(UserId player, String key, int val)`
  - [X] `String serializeBoardState()`
- [X] `SlotMachineGame` (class)
  - [X] `void start(List<UserId> participants)`
  - [X] `void stop(StopReason reason)`
  - [X] `void recordScore(UserId player, int delta)`
  - [ ] `void spin()`
- [X] `RouletteGame` (class)
  - [X] `void start(List<UserId> participants)`
  - [X] `void stop(StopReason reason)`
  - [X] `void endTurn()`
  - [X] `void placeBet(UserId player, String num, int amount)`
- [ ] `GameFactory` (class)
  - [ ] `static GameLifecycle createGame(GameType type, GameSessionId sessionId)`
- [X] `ClientState` (enum)
  - [X] `DISCONNECTED`
  - [X] `CONNECTED`
  - [X] `LOGGED_IN`
  - [X] `IN_GAME`
  - [X] `PAUSED`

### 6.2 Servizi Applicativi Client (`application/service/`)
- [ ] `GameOrchestrationService` (class)
  - [ ] `void startGame(GameType type, List<String> participants)`
  - [ ] `void stopGame(StopReason reason)`
  - [ ] `void pauseGame()`
  - [ ] `void resumeGame()`
- [ ] `HeartbeatService` (class)
  - [ ] `void startHeartbeat()`
  - [ ] `void stopHeartbeat()`
  - [ ] `void handleHeartbeatAck()`
- [ ] `ConnectionMonitorService` (class)
  - [ ] `void checkConnection()`

### 6.3 Adattatori MQTT Client (`infrastructure/mqtt/`)
- [ ] `MqttClientAdapter` (class)
  - [ ] `void connect()`
  - [ ] `void disconnect()`
  - [ ] `void publish(String topic, byte[] payload)`
- [ ] `MqttConnectionManager` (class)
  - [ ] `void manageConnection()`
- [ ] `GameStatePublisher` (class)
  - [ ] `void publishState(GameMachineStatus status)`
- [ ] `SessionPublisher` (class)
  - [ ] `void publishStart(SessionStartPayload p)`
  - [ ] `void publishEnd(SessionEndPayload p)`
- [ ] `HeartbeatPublisher` (class)
  - [ ] `void publishHeartbeat(HeartbeatPayload p)`
- [ ] `StateSubscriber` (class)
  - [ ] `void subscribeToStates()`

### 6.4 Interfaccia Grafica JavaFX (`infrastructure/ui/` e `components/`)
- [ ] `MainView` (class)
  - [ ] `void start(Stage stage)`
  - [ ] `void navigateTo(String viewName)`
- [ ] `LoginView` (class)
  - [ ] `Parent getView()`
  - [ ] `void performLogin()`
- [ ] `GameSelectionView` (class)
  - [ ] `Parent getView()`
  - [ ] `void refreshGames()`
- [ ] `GamePlayView` (class)
  - [ ] `Parent getView()`
  - [ ] `void updateGameDisplay()`
- [ ] `StatisticsView` (class)
  - [ ] `Parent getView()`
  - [ ] `void showStats()`
- [ ] `ScoreboardComponent` (class)
  - [ ] `void updateScores(Map<String, Integer> scores)`
- [ ] `TimerComponent` (class)
  - [ ] `void startTimer()`
  - [ ] `void stopTimer()`
- [ ] `StatusBarComponent` (class)
  - [ ] `void updateStatus(String statusText)`

### 6.5 Configurazione Client (`infrastructure/config/`)
- [ ] `GameClientApplication` (class)
  - [ ] `static void main(String[] args)`
- [ ] `MqttClientConfig` (class)
  - [ ] `MqttConnectOptions getMqttOptions()`

## 7 Configurazioni
- [ ] Creare il certificato per `JwtTokenProvider`
