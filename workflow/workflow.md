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
- [x] `RouletteResult` (record)
  - [x] `RouletteResult(String visitorId, int totalRounds, int totalBetAmount, int totalPayout, List<String> winningNumbers, WinCondition winCondition)`
  - [x] `String visitorId()`
  - [x] `int totalRounds()`
  - [x] `int totalBetAmount()`
  - [x] `int totalPayout()`
  - [x] `List<String> winningNumbers()`
  - [x] `WinCondition winCondition()`

### 1.4 Eventi di Dominio (`events/`)
- [x] `DomainEvent` (interface)
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()`
- [x] `UserRegisteredEvent` (record)
  - [x] `UserRegisteredEvent(String eventId, Instant occurredAt, UserId userId, String username, String hashedPassword, List<String> roles)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()`
- [x] `UserUpdatedEvent` (record)
  - [x] `UserUpdatedEvent(String eventId, Instant occurredAt, UserId userId, String username, String hashedPassword, List<String> roles)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()`
- [x] `ReservationCreatedEvent` (record)
  - [x] `ReservationCreatedEvent(String eventId, Instant occurredAt, ReservationId reservationId, GameId gameId, UserId userId, BuildingId buildingId)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()`
- [x] `ReservationCancelledEvent` (record)
  - [x] `ReservationCancelledEvent(String eventId, Instant occurredAt, ReservationId reservationId)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()`
- [x] `GameSessionCompletedEvent` (record)
  - [x] `GameSessionCompletedEvent(String eventId, Instant occurredAt, GameSessionId sessionId, GameType gameType, String resultJson)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()`
- [x] `GameStateChangedEvent` (record)
  - [x] `GameStateChangedEvent(String eventId, Instant occurredAt, GameId gameId, GameMachineStatus oldStatus, GameMachineStatus newStatus)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()`
- [x] `StatisticsUpdatedEvent` (record)
  - [x] `StatisticsUpdatedEvent(String eventId, Instant occurredAt, BuildingId buildingId)`
  - [x] `String getEventId()`
  - [x] `Instant getOccurredAt()`
  - [x] `String getEventType()`

## 2. shared-dto (`com.gameplatform.shared.dto`)

- [ ] `UserDto` (record)
  - [ ] `String id()`
  - [ ] `String username()`
  - [ ] `String email()`
  - [ ] `List<String> roles()`
  - [ ] `Instant createdAt()`
- [ ] `UserSyncDto` (record)
  - [ ] `String userId()`
  - [ ] `String username()`
  - [ ] `String hashedPassword()`
  - [ ] `List<String> roles()`
- [ ] `LoginRequestDto` (record)
  - [ ] `String username()`
  - [ ] `String password()`
- [ ] `LoginResponseDto` (record)
  - [ ] `String token()`
  - [ ] `String userId()`
  - [ ] `Instant expiresAt()`
- [ ] `ReservationDto` (record)
  - [ ] `String id()`
  - [ ] `String gameId()`
  - [ ] `String userId()`
  - [ ] `ReservationStatus status()`
  - [ ] `Instant startTime()`
  - [ ] `Instant endTime()`
- [ ] `CreateReservationRequestDto` (record)
  - [ ] `String gameId()`
  - [ ] `String userId()`
  - [ ] `Instant startTime()`
  - [ ] `Instant endTime()`
- [ ] `GameStateDto` (record)
  - [ ] `String gameId()`
  - [ ] `GameType gameType()`
  - [ ] `String name()`
  - [ ] `String buildingId()`
  - [ ] `GameMachineStatus status()`
- [ ] `GameSessionDto` (record)
  - [ ] `String id()`
  - [ ] `String gameId()`
  - [ ] `GameType gameType()`
  - [ ] `GameStatus status()`
  - [ ] `Instant startedAt()`
  - [ ] `Instant endedAt()`
  - [ ] `Integer durationSeconds()`
  - [ ] `String winnerId()`
  - [ ] `WinCondition winCondition()`
  - [ ] `String resultData()`
- [ ] `GameSessionResultDto` (record)
  - [ ] `GameSessionDto session()`
  - [ ] `List<String> participants()`
- [ ] `StatisticsDto` (record)
  - [ ] `String buildingId()`
  - [ ] `String gameType()`
  - [ ] `Instant periodStart()`
  - [ ] `Instant periodEnd()`
  - [ ] `Integer totalSessions()`
  - [ ] `Integer avgDuration()`
  - [ ] `Integer totalReservations()`
  - [ ] `String data()`
- [ ] `OutboxEventDto` (record)
  - [ ] `String eventId()`
  - [ ] `String eventType()`
  - [ ] `String payload()`
  - [ ] `Instant createdAt()`
- [ ] `SyncPayloadDto` (record)
  - [ ] `String buildingId()`
  - [ ] `List<OutboxEventDto> events()`
- [ ] `AlertDto` (record)
  - [ ] `String buildingId()`
  - [ ] `String gameId()`
  - [ ] `String alertType()`
  - [ ] `String message()`
  - [ ] `Instant timestamp()`
- [ ] `ErrorResponseDto` (record)
  - [ ] `int status()`
  - [ ] `String error()`
  - [ ] `String message()`
  - [ ] `Instant timestamp()`

## 3. shared-mqtt (`com.gameplatform.shared.mqtt`)

### 3.1 Topic e Configurazione MQTT
- [ ] `MqttTopics` (class)
  - [ ] `static String gameState(String buildingId, String gameId)`
  - [ ] `static String sessionStart(String buildingId, String gameId)`
  - [ ] `static String sessionEnd(String buildingId, String gameId)`
  - [ ] `static String sessionPause(String buildingId, String gameId)`
  - [ ] `static String sessionResume(String buildingId, String gameId)`
  - [ ] `static String heartbeat(String buildingId, String gameId)`
  - [ ] `static String heartbeatAck(String buildingId, String gameId)`
  - [ ] `static String alerts(String buildingId)`
- [ ] `MqttQos` (class)
  - [ ] `static final int STATE = 1`
  - [ ] `static final int SESSION = 1`
  - [ ] `static final int HEARTBEAT = 0`
- [ ] `MqttPayloadSerializer` (class)
  - [ ] `static byte[] serialize(Object obj)`
  - [ ] `static <T> T deserialize(byte[] data, Class<T> clazz)`

### 3.2 Payload MQTT (`payload/`)
- [ ] `GameStatePayload` (record)
  - [ ] `String gameId()`
  - [ ] `GameMachineStatus status()`
  - [ ] `String userId()`
- [ ] `SessionStartPayload` (record)
  - [ ] `String sessionId()`
  - [ ] `GameType gameType()`
  - [ ] `List<String> participants()`
- [ ] `SessionEndPayload` (record)
  - [ ] `String sessionId()`
  - [ ] `String winnerId()`
  - [ ] `WinCondition winCondition()`
  - [ ] `String resultData()`
- [ ] `SessionPausePayload` (record)
  - [ ] `String sessionId()`
  - [ ] `String pausedBy()`
- [ ] `HeartbeatPayload` (record)
  - [ ] `String gameId()`
  - [ ] `Instant timestamp()`
- [ ] `HeartbeatAckPayload` (record)
  - [ ] `String gameId()`
  - [ ] `Instant serverTimestamp()`
- [ ] `AlertPayload` (record)
  - [ ] `String alertType()`
  - [ ] `String gameId()`
  - [ ] `String message()`
  - [ ] `Instant timestamp()`

## 4. central-system (`com.gameplatform.central`)

### 4.1 Modello di Dominio Centrale (`domain/model/`)
- [ ] `User` (class)
  - [ ] `User(UserId id, String username, String passwordHash, String email, List<String> roles, Instant createdAt)`
  - [ ] `void changePassword(String newPasswordHash)`
  - [ ] `void updateRoles(List<String> newRoles)`
- [ ] `AggregatedStatistics` (class)
  - [ ] `AggregatedStatistics(String id, BuildingId buildingId, GameType gameType, LocalDate periodStart, LocalDate periodEnd, int totalSessions, int avgDurationSeconds, int totalReservations, Map<String, Object> data)`
  - [ ] `void mergeWith(AggregatedStatistics other)`
- [ ] `RegisteredLocalServer` (class)
  - [ ] `RegisteredLocalServer(BuildingId buildingId, String baseUrl, Instant lastSeenAt, boolean isActive)`
- [ ] `ProcessedEvent` (class)
  - [ ] `ProcessedEvent(String eventId, Instant processedAt)`
- [ ] `OutboxEvent` (class)
  - [ ] `OutboxEvent(String id, String eventType, String payload, String status, Instant createdAt, Instant sentAt)`

### 4.2 Porte di Ingresso (`domain/ports/in/`)
- [ ] `RegisterUserUseCase` (interface)
  - [ ] `User register(String username, String password, String email)`
- [ ] `UpdateUserUseCase` (interface)
  - [ ] `User updateUser(UserId id, String newPassword, List<String> newRoles)`
- [ ] `AuthenticateUserUseCase` (interface)
  - [ ] `LoginResponseDto authenticate(String username, String password)`
- [ ] `GetGlobalStatisticsUseCase` (interface)
  - [ ] `List<StatisticsDto> getStatistics(BuildingId buildingId, GameType gameType, LocalDate start, LocalDate end)`
- [ ] `ReceiveSyncDataUseCase` (interface)
  - [ ] `void receiveSyncPayload(SyncPayloadDto payload)`
- [ ] `GetAllUsersUseCase` (interface)
  - [ ] `List<UserSyncDto> getAllUsersForSync()`

### 4.3 Porte di Uscita (`domain/ports/out/`)
- [ ] `UserRepository` (interface)
  - [ ] `User save(User user)`
  - [ ] `Optional<User> findById(UserId id)`
  - [ ] `Optional<User> findByUsername(String username)`
  - [ ] `List<User> findAll()`
- [ ] `StatisticsRepository` (interface)
  - [ ] `AggregatedStatistics save(AggregatedStatistics stats)`
  - [ ] `Optional<AggregatedStatistics> findByBuildingAndTypeAndPeriod(BuildingId buildingId, GameType gameType, LocalDate periodStart)`
  - [ ] `List<AggregatedStatistics> findByPeriod(LocalDate start, LocalDate end)`
- [ ] `ProcessedEventRepository` (interface)
  - [ ] `boolean existsByEventId(String eventId)`
  - [ ] `void save(ProcessedEvent event)`
- [ ] `OutboxEventRepository` (interface)
  - [ ] `OutboxEvent save(OutboxEvent event)`
  - [ ] `List<OutboxEvent> findPending()`
  - [ ] `void markAsSent(String id)`
- [ ] `LocalServerRegistryPort` (interface)
  - [ ] `List<RegisteredLocalServer> getActiveLocalServers()`
  - [ ] `void register(RegisteredLocalServer server)`
- [ ] `PushUserToLocalServersPort` (interface)
  - [ ] `void pushUsers(List<UserSyncDto> users, RegisteredLocalServer server)`

### 4.4 Eccezioni di Dominio (`domain/exception/`)
- [ ] `UserAlreadyExistsException` (class)
- [ ] `InvalidCredentialsException` (class)
- [ ] `DuplicateEventException` (class)

### 4.5 Servizi Applicativi (`application/service/`)
- [ ] `UserService` (class)
  - [ ] `User register(String username, String password, String email)`
  - [ ] `User updateUser(UserId id, String newPassword, List<String> newRoles)`
  - [ ] `List<UserSyncDto> getAllUsersForSync()`
- [ ] `AuthService` (class)
  - [ ] `LoginResponseDto authenticate(String username, String password)`
- [ ] `StatisticsAggregationService` (class)
  - [ ] `List<StatisticsDto> getStatistics(BuildingId buildingId, GameType gameType, LocalDate start, LocalDate end)`
- [ ] `SyncReceiverService` (class)
  - [ ] `void receiveSyncPayload(SyncPayloadDto payload)`
- [ ] `UserReplicationSchedulerService` (class)
  - [ ] `void replicateUsers()`

### 4.6 Adattatori REST in Ingresso (`infrastructure/adapters/in/rest/`)
- [ ] `UserController` (class)
  - [ ] `ResponseEntity<UserDto> register(CreateUserRequestDto request)`
- [ ] `AuthController` (class)
  - [ ] `ResponseEntity<LoginResponseDto> login(LoginRequestDto request)`
- [ ] `StatisticsController` (class)
  - [ ] `ResponseEntity<List<StatisticsDto>> getStats(String buildingId, String gameType, String start, String end)`
- [ ] `SyncController` (class)
  - [ ] `ResponseEntity<Void> receiveSync(SyncPayloadDto payload, String apiKey)`

### 4.7 Adattatori di Persistenza MySQL (`infrastructure/adapters/out/mysql/`)
- [ ] `UserJpaEntity` (class)
- [ ] `AggregatedStatisticsJpaEntity` (class)
- [ ] `ProcessedEventJpaEntity` (class)
- [ ] `OutboxEventJpaEntity` (class)
- [ ] `RegisteredLocalServerJpaEntity` (class)

- [ ] `UserJpaRepository` (interface)
  - [ ] `Optional<UserJpaEntity> findByUsername(String username)`
- [ ] `StatisticsJpaRepository` (interface)
  - [ ] `Optional<AggregatedStatisticsJpaEntity> findByBuildingIdAndGameTypeAndPeriodStart(String buildingId, String gameType, LocalDate periodStart)`
- [ ] `ProcessedEventJpaRepository` (interface)
- [ ] `OutboxEventJpaRepository` (interface)
  - [ ] `List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(String status)`
- [ ] `LocalServerJpaRepository` (interface)
  - [ ] `List<RegisteredLocalServerJpaEntity> findByIsActiveTrue()`

- [ ] `UserRepositoryAdapter` (class)
  - [ ] `User save(User user)`
  - [ ] `Optional<User> findById(UserId id)`
  - [ ] `Optional<User> findByUsername(String username)`
  - [ ] `List<User> findAll()`
- [ ] `StatisticsRepositoryAdapter` (class)
  - [ ] `AggregatedStatistics save(AggregatedStatistics stats)`
  - [ ] `Optional<AggregatedStatistics> findByBuildingAndTypeAndPeriod(BuildingId buildingId, GameType gameType, LocalDate periodStart)`
  - [ ] `List<AggregatedStatistics> findByPeriod(LocalDate start, LocalDate end)`
- [ ] `ProcessedEventRepositoryAdapter` (class)
  - [ ] `boolean existsByEventId(String eventId)`
  - [ ] `void save(ProcessedEvent event)`
- [ ] `OutboxEventRepositoryAdapter` (class)
  - [ ] `OutboxEvent save(OutboxEvent event)`
  - [ ] `List<OutboxEvent> findPending()`
  - [ ] `void markAsSent(String id)`
- [ ] `LocalServerRegistryAdapter` (class)
  - [ ] `List<RegisteredLocalServer> getActiveLocalServers()`
  - [ ] `void register(RegisteredLocalServer server)`

- [ ] `UserMapper` (class)
  - [ ] `User toDomain(UserJpaEntity entity)`
  - [ ] `UserJpaEntity toEntity(User domain)`
- [ ] `StatisticsMapper` (class)
  - [ ] `AggregatedStatistics toDomain(AggregatedStatisticsJpaEntity entity)`
  - [ ] `AggregatedStatisticsJpaEntity toEntity(AggregatedStatistics domain)`
- [ ] `OutboxEventMapper` (class)
  - [ ] `OutboxEvent toDomain(OutboxEventJpaEntity entity)`
  - [ ] `OutboxEventJpaEntity toEntity(OutboxEvent domain)`

### 4.8 Adattatori REST in Uscita (`infrastructure/adapters/out/rest/`)
- [ ] `LocalServerRestAdapter` (class)
  - [ ] `void pushUsers(List<UserSyncDto> users, RegisteredLocalServer server)`

### 4.9 Configurazione e Sicurezza (`infrastructure/config/` e `security/`)
- [ ] `CentralSystemApplication` (class)
  - [ ] `static void main(String[] args)`
- [ ] `SecurityConfig` (class)
  - [ ] `SecurityFilterChain filterChain(HttpSecurity http)`
- [ ] `JwtConfig` (class)
  - [ ] `JwtTokenProvider jwtTokenProvider()`
- [ ] `SchedulerConfig` (class)
- [ ] `CorsConfig` (class)
  - [ ] `CorsFilter corsFilter()`
- [ ] `JwtTokenProvider` (class)
  - [ ] `String generateToken(User user)`
  - [ ] `Claims getClaims(String token)`
- [ ] `JwtAuthenticationFilter` (class)
  - [ ] `void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)`
- [ ] `InternalApiKeyFilter` (class)
  - [ ] `void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)`
- [ ] `PasswordEncoderConfig` (class)
  - [ ] `PasswordEncoder passwordEncoder()`

## 5. local-server (`com.gameplatform.local`)

### 5.1 Modello di Dominio Locale (`domain/model/`)
- [ ] `Reservation` (class)
  - [ ] `Reservation(ReservationId id, GameId gameId, UserId userId, ReservationStatus status, Instant startTime, Instant endTime, Instant createdAt)`
  - [ ] `boolean canBeCancelled(Clock clock)`
  - [ ] `void confirm()`
  - [ ] `void cancel()`
  - [ ] `void expire()`
- [ ] `Game` (class)
  - [ ] `Game(GameId id, GameType gameType, String name, BuildingId buildingId, GameMachineStatus status)`
  - [ ] `void reserve()`
  - [ ] `void startUse()`
  - [ ] `void release()`
  - [ ] `void setMaintenance()`
- [ ] `User` (class)
  - [ ] `User(UserId userId, String username, String passwordHash, List<String> roles, Instant syncedAt)`
- [ ] `GameSession` (class)
  - [ ] `GameSession(GameSessionId id, GameId gameId, GameType gameType, BuildingId buildingId, GameStatus status, Instant startedAt, Instant endedAt, Integer durationSeconds, UserId winnerId, WinCondition winCondition, GameResult result)`
  - [ ] `void complete(GameResult result)`
  - [ ] `void abort(StopReason reason)`
  - [ ] `void pause()`
  - [ ] `void resume()`
  - [ ] `void calculateDuration()`
- [ ] `OutboxEvent` (class)
  - [ ] `OutboxEvent(String id, String eventType, String payload, String status, Instant createdAt, Instant sentAt, int retryCount)`
  - [ ] `void markAsSent()`
  - [ ] `void incrementRetry()`
  - [ ] `boolean hasFailed()`
- [ ] `LocalStatistics` (class)
  - [ ] `LocalStatistics(GameType gameType, int totalSessions, double avgDuration, int totalReservations, Map<String, Double> winRateByUser)`
  - [ ] `void recalculate(List<GameSession> sessions)`

### 5.2 Porte di Ingresso Locali (`domain/ports/in/`)
- [ ] `CreateReservationUseCase` (interface)
  - [ ] `Reservation create(GameId gameId, UserId userId, Instant start, Instant end)`
- [ ] `CancelReservationUseCase` (interface)
  - [ ] `void cancel(ReservationId reservationId)`
- [ ] `GetReservationsUseCase` (interface)
  - [ ] `List<Reservation> getByUser(UserId userId)`
  - [ ] `List<Reservation> getByGame(GameId gameId)`
- [ ] `UpdateGameStateUseCase` (interface)
  - [ ] `void updateState(GameId gameId, GameMachineStatus newStatus)`
- [ ] `GetAvailableGamesUseCase` (interface)
  - [ ] `List<Game> getAvailable()`
  - [ ] `List<Game> getAll()`
- [ ] `StartGameSessionUseCase` (interface)
  - [ ] `GameSession start(GameId gameId, GameType gameType, List<UserId> participants, ReservationId reservationId)`
- [ ] `EndGameSessionUseCase` (interface)
  - [ ] `void end(GameSessionId sessionId, GameResult result)`
- [ ] `PauseGameSessionUseCase` (interface)
  - [ ] `void pause(GameSessionId sessionId)`
- [ ] `ResumeGameSessionUseCase` (interface)
  - [ ] `void resume(GameSessionId sessionId)`
- [ ] `GetStatisticsUseCase` (interface)
  - [ ] `LocalStatistics getStatistics(GameType gameType)`
  - [ ] `List<GameSession> getActiveSessions()`
- [ ] `AuthenticateLocalUserUseCase` (interface)
  - [ ] `LoginResponseDto authenticate(String username, String password)`
- [ ] `SyncUsersUseCase` (interface)
  - [ ] `void syncUsers(List<UserSyncDto> users)`

### 5.3 Porte di Uscita Locali (`domain/ports/out/`)
- [ ] `ReservationRepository` (interface)
  - [ ] `Reservation save(Reservation reservation)`
  - [ ] `Optional<Reservation> findById(ReservationId id)`
  - [ ] `List<Reservation> findByUserId(UserId userId)`
  - [ ] `List<Reservation> findByGameId(GameId gameId)`
  - [ ] `List<Reservation> findByStatus(ReservationStatus status)`
  - [ ] `List<Reservation> findExpired(Instant now)`
- [ ] `GameRepository` (interface)
  - [ ] `Game save(Game game)`
  - [ ] `Optional<Game> findById(GameId id)`
  - [ ] `List<Game> findByBuildingId(BuildingId buildingId)`
  - [ ] `List<Game> findByStatus(GameMachineStatus status)`
  - [ ] `List<Game> findAll()`
- [ ] `UserRepository` (interface)
  - [ ] `User save(User user)`
  - [ ] `Optional<User> findByUsername(String username)`
  - [ ] `void saveAll(List<User> users)`
- [ ] `GameSessionRepository` (interface)
  - [ ] `GameSession save(GameSession session)`
  - [ ] `Optional<GameSession> findById(GameSessionId id)`
  - [ ] `List<GameSession> findByBuildingId(BuildingId buildingId)`
  - [ ] `List<GameSession> findByGameType(GameType gameType)`
  - [ ] `List<GameSession> findByStatus(GameStatus status)`
  - [ ] `List<GameSession> findPendingSync()`
  - [ ] `Optional<GameSession> findActiveByGameId(GameId gameId)`
- [ ] `OutboxEventRepository` (interface)
  - [ ] `OutboxEvent save(OutboxEvent event)`
  - [ ] `List<OutboxEvent> findPending()`
  - [ ] `void markAsSent(String id)`
  - [ ] `void incrementRetry(String id)`
- [ ] `SyncCentralSystemPort` (interface)
  - [ ] `boolean isReachable()`
  - [ ] `boolean sendSyncPayload(SyncPayloadDto payload)`
- [ ] `PublishGameStatePort` (interface)
  - [ ] `void publishState(GameId gameId, GameMachineStatus status)`
  - [ ] `void publishSessionEvent(String topic, Object payload)`
- [ ] `PublishAlertPort` (interface)
  - [ ] `void publishAlert(AlertPayload payload)`

### 5.4 Eccezioni Locali (`domain/exception/`)
- [ ] `GameNotAvailableException` (class)
- [ ] `ReservationNotFoundException` (class)
- [ ] `ReservationExpiredException` (class)
- [ ] `UserNotFoundException` (class)
- [ ] `SessionAlreadyActiveException` (class)
- [ ] `InvalidGameStateTransitionException` (class)

### 5.5 Servizi Applicativi Locali (`application/service/`)
- [ ] `ReservationService` (class)
  - [ ] `Reservation create(GameId gameId, UserId userId, Instant start, Instant end)`
  - [ ] `void cancel(ReservationId reservationId)`
  - [ ] `List<Reservation> getByUser(UserId userId)`
  - [ ] `List<Reservation> getByGame(GameId gameId)`
- [ ] `ReservationExpirationService` (class)
  - [ ] `void expireReservations()`
- [ ] `GameStateService` (class)
  - [ ] `void updateState(GameId gameId, GameMachineStatus newStatus)`
  - [ ] `List<Game> getAvailable()`
  - [ ] `List<Game> getAll()`
- [ ] `GameSessionService` (class)
  - [ ] `GameSession start(GameId gameId, GameType gameType, List<UserId> participants, ReservationId reservationId)`
  - [ ] `void end(GameSessionId sessionId, GameResult result)`
  - [ ] `void pause(GameSessionId sessionId)`
  - [ ] `void resume(GameSessionId sessionId)`
- [ ] `SessionRecoveryService` (class)
  - [ ] `void start()`
  - [ ] `void stop()`
- [ ] `StatisticsService` (class)
  - [ ] `LocalStatistics getStatistics(GameType gameType)`
  - [ ] `List<GameSession> getActiveSessions()`
- [ ] `LocalAuthService` (class)
  - [ ] `LoginResponseDto authenticate(String username, String password)`
- [ ] `UserSyncService` (class)
  - [ ] `void syncUsers(List<UserSyncDto> users)`
- [ ] `SyncSchedulerService` (class)
  - [ ] `void syncWithCentral()`
- [ ] `HealthCheckService` (class)
  - [ ] `void performHealthCheck()`

### 5.6 Adattatori REST in Ingresso Locali (`infrastructure/adapters/in/rest/`)
- [ ] `ReservationController` (class)
  - [ ] `ResponseEntity<ReservationDto> create(CreateReservationRequestDto req)`
  - [ ] `ResponseEntity<Void> cancel(String id)`
  - [ ] `ResponseEntity<List<ReservationDto>> getByUser(String userId)`
- [ ] `GameController` (class)
  - [ ] `ResponseEntity<List<GameStateDto>> getGames()`
  - [ ] `ResponseEntity<List<GameStateDto>> getAvailableGames()`
- [ ] `GameSessionController` (class)
  - [ ] `ResponseEntity<GameSessionDto> start(CreateSessionRequestDto req)`
  - [ ] `ResponseEntity<Void> end(String id, GameResult result)`
  - [ ] `ResponseEntity<Void> pause(String id)`
  - [ ] `ResponseEntity<Void> resume(String id)`
- [ ] `StatisticsController` (class)
  - [ ] `ResponseEntity<LocalStatistics> getStats(String gameType)`
  - [ ] `ResponseEntity<List<GameSessionDto>> getActiveSessions()`
- [ ] `AuthController` (class)
  - [ ] `ResponseEntity<LoginResponseDto> login(LoginRequestDto req)`
- [ ] `InternalSyncController` (class)
  - [ ] `ResponseEntity<Void> syncUsers(List<UserSyncDto> users, String apiKey)`

### 5.7 Adattatori MQTT in Ingresso (`infrastructure/adapters/in/mqtt/`)
- [ ] `GameStateListener` (class)
  - [ ] `void handleStateMessage(String topic, byte[] payload)`
- [ ] `GameSessionListener` (class)
  - [ ] `void handleSessionMessage(String topic, byte[] payload)`
- [ ] `HeartbeatListener` (class)
  - [ ] `void handleHeartbeat(String topic, byte[] payload)`

### 5.8 Adattatori MySQL Locali (`infrastructure/adapters/out/mysql/`)
- [ ] `ReservationJpaEntity` (class)
- [ ] `GameJpaEntity` (class)
- [ ] `UserJpaEntity` (class)
- [ ] `GameSessionJpaEntity` (class)
- [ ] `SessionParticipantJpaEntity` (class)
- [ ] `OutboxEventJpaEntity` (class)

- [ ] `ReservationJpaRepository` (interface)
  - [ ] `List<ReservationJpaEntity> findByUserId(String userId)`
  - [ ] `List<ReservationJpaEntity> findByGameId(String gameId)`
- [ ] `GameJpaRepository` (interface)
  - [ ] `List<GameJpaEntity> findByBuildingId(String buildingId)`
  - [ ] `List<GameJpaEntity> findByStatus(String status)`
- [ ] `UserJpaRepository` (interface)
  - [ ] `Optional<UserJpaEntity> findByUsername(String username)`
- [ ] `GameSessionJpaRepository` (interface)
  - [ ] `List<GameSessionJpaEntity> findByBuildingId(String buildingId)`
  - [ ] `List<GameSessionJpaEntity> findByStatus(String status)`
- [ ] `OutboxEventJpaRepository` (interface)
  - [ ] `List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(String status)`

- [ ] `ReservationRepositoryAdapter` (class)
  - [ ] `Reservation save(Reservation reservation)`
  - [ ] `Optional<Reservation> findById(ReservationId id)`
  - [ ] `List<Reservation> findByUserId(UserId userId)`
- [ ] `GameRepositoryAdapter` (class)
  - [ ] `Game save(Game game)`
  - [ ] `Optional<Game> findById(GameId id)`
  - [ ] `List<Game> findAll()`
- [ ] `UserRepositoryAdapter` (class)
  - [ ] `User save(User user)`
  - [ ] `Optional<User> findByUsername(String username)`
  - [ ] `void saveAll(List<User> users)`
- [ ] `GameSessionRepositoryAdapter` (class)
  - [ ] `GameSession save(GameSession session)`
  - [ ] `Optional<GameSession> findById(GameSessionId id)`
  - [ ] `List<GameSession> findPendingSync()`
- [ ] `OutboxEventRepositoryAdapter` (class)
  - [ ] `OutboxEvent save(OutboxEvent event)`
  - [ ] `List<OutboxEvent> findPending()`

- [ ] `ReservationMapper` (class)
  - [ ] `Reservation toDomain(ReservationJpaEntity entity)`
  - [ ] `ReservationJpaEntity toEntity(Reservation domain)`
- [ ] `GameMapper` (class)
  - [ ] `Game toDomain(GameJpaEntity entity)`
  - [ ] `GameJpaEntity toEntity(Game domain)`
- [ ] `UserMapper` (class)
  - [ ] `User toDomain(UserJpaEntity entity)`
  - [ ] `UserJpaEntity toEntity(User domain)`
- [ ] `GameSessionMapper` (class)
  - [ ] `GameSession toDomain(GameSessionJpaEntity entity)`
  - [ ] `GameSessionJpaEntity toEntity(GameSession domain)`
- [ ] `OutboxEventMapper` (class)
  - [ ] `OutboxEvent toDomain(OutboxEventJpaEntity entity)`
  - [ ] `OutboxEventJpaEntity toEntity(OutboxEvent domain)`

### 5.9 REST e MQTT in Uscita (`infrastructure/adapters/out/rest/` e `mqtt/`)
- [ ] `CentralSystemRestAdapter` (class)
  - [ ] `boolean isReachable()`
  - [ ] `boolean sendSyncPayload(SyncPayloadDto payload)`
- [ ] `MqttPublisherAdapter` (class)
  - [ ] `void publishState(GameId gameId, GameMachineStatus status)`
  - [ ] `void publishAlert(AlertPayload payload)`

### 5.10 Configurazione e Sicurezza Locali (`infrastructure/config/` e `security/`)
- [ ] `LocalServerApplication` (class)
  - [ ] `static void main(String[] args)`
- [ ] `MqttConfig` (class)
  - [ ] `IMqttClient mqttClient()`
- [ ] `SecurityConfig` (class)
  - [ ] `SecurityFilterChain filterChain(HttpSecurity http)`
- [ ] `TlsConfig` (class)
  - [ ] `SSLContext sslContext()`
- [ ] `SchedulerConfig` (class)
  - [ ] `Clock clock()`
- [ ] `JwtTokenProvider` (class)
  - [ ] `String generateToken(User user)`
- [ ] `JwtTokenValidator` (class)
  - [ ] `Claims validateToken(String token)`
- [ ] `JwtAuthenticationFilter` (class)
  - [ ] `void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)`
- [ ] `InternalApiKeyFilter` (class)
  - [ ] `void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)`

## 6. game-client-emulator (`com.gameplatform.client`)

### 6.1 Implementazioni dei Giochi ed Emulatori (`domain/games/`)
- [ ] `FoosballGame` (class)
  - [ ] `void start(List<UserId> participants)`
  - [ ] `void stop(StopReason reason)`
  - [ ] `void recordScore(UserId player, int delta)`
- [ ] `ChessGame` (class)
  - [ ] `void start(List<UserId> participants)`
  - [ ] `void stop(StopReason reason)`
  - [ ] `void endTurn()`
  - [ ] `String serializeBoardState()`
- [ ] `DartsGame` (class)
  - [ ] `void start(List<UserId> participants)`
  - [ ] `void stop(StopReason reason)`
  - [ ] `void recordScore(UserId player, int delta)`
  - [ ] `void endTurn()`
- [ ] `MonopolyGame` (class)
  - [ ] `void start(List<UserId> participants)`
  - [ ] `void stop(StopReason reason)`
  - [ ] `void updateResource(UserId player, String key, int val)`
  - [ ] `void endTurn()`
- [ ] `RiskGame` (class)
  - [ ] `void start(List<UserId> participants)`
  - [ ] `void stop(StopReason reason)`
  - [ ] `void updateResource(UserId player, String key, int val)`
  - [ ] `String serializeBoardState()`
- [ ] `SlotMachineGame` (class)
  - [ ] `void start(List<UserId> participants)`
  - [ ] `void stop(StopReason reason)`
  - [ ] `void recordScore(UserId player, int delta)`
  - [ ] `void spin()`
- [ ] `RouletteGame` (class)
  - [ ] `void start(List<UserId> participants)`
  - [ ] `void stop(StopReason reason)`
  - [ ] `void endTurn()`
  - [ ] `void placeBet(UserId player, String num, int amount)`
- [ ] `GameFactory` (class)
  - [ ] `static GameLifecycle createGame(GameType type, GameSessionId sessionId)`
- [ ] `ClientState` (enum)
  - [ ] `DISCONNECTED`
  - [ ] `CONNECTED`
  - [ ] `LOGGED_IN`
  - [ ] `IN_GAME`
  - [ ] `PAUSED`

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
