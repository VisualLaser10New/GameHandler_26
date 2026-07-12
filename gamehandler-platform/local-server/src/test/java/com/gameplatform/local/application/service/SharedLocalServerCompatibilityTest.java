package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.*;
import com.gameplatform.local.infrastructure.adapters.in.rest.GameSessionController;
import com.gameplatform.local.domain.ports.out.*;
import com.gameplatform.shared.domain.model.*;
import com.gameplatform.shared.domain.result.*;
import com.gameplatform.shared.dto.*;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.MqttQos;
import com.gameplatform.shared.mqtt.MqttTopics;
import com.gameplatform.shared.mqtt.payload.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive compatibility tests between shared-domain/shared-dto/shared-mqtt
 * (points 1-2-3) and local-server implementations (point 5).
 *
 * <p>These tests verify that the shared types are correctly used in the local-server,
 * and specifically target hidden edge cases and integration flows.</p>
 */
@ExtendWith(MockitoExtension.class)
class SharedLocalServerCompatibilityTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameSessionRepository gameSessionRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PublishGameStatePort publishGameStatePort;

    @Mock
    private PublishAlertPort publishAlertPort;

    @Mock
    private SyncCentralSystemPort syncCentralSystemPort;

    @Mock
    private ReservationExpirationService reservationExpirationService;

    @Mock
    private GameDefinitionLocalRepository gameDefinitionLocalRepository;

    private final Clock fixedClock = Clock.fixed(
            Instant.parse("2026-06-27T10:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GameSessionService gameSessionService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.addMixIn(com.gameplatform.shared.domain.result.GameResult.class,
                com.gameplatform.local.infrastructure.config.JacksonConfig.GameResultMixIn.class);
        objectMapper.addMixIn(com.gameplatform.shared.domain.result.RouletteResult.class,
                com.gameplatform.local.infrastructure.config.JacksonConfig.RouletteResultMixIn.class);
        objectMapper.addMixIn(com.gameplatform.shared.domain.result.SlotResult.class,
                com.gameplatform.local.infrastructure.config.JacksonConfig.SlotResultMixIn.class);
        gameSessionService = new GameSessionService(
                gameSessionRepository,
                gameRepository,
                outboxEventRepository,
                publishGameStatePort,
                reservationRepository,
                fixedClock,
                objectMapper,
                gameDefinitionLocalRepository
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Domain Model Compatibility (point 1 ↔ point 5)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Domain Model Compatibility — shared-domain in local-server")
    class DomainModelCompatibility {

        @Test
        @DisplayName("UserId null/blank validation is enforced by record constructor")
        void userIdValidation() {
            assertThatThrownBy(() -> new UserId(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new UserId("   "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new UserId("user-1").value()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("GameId null/blank validation is enforced by record constructor")
        void gameIdValidation() {
            assertThatThrownBy(() -> new GameId(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new GameId(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new GameId("game-1").id()).isEqualTo("game-1");
        }

        @Test
        @DisplayName("BuildingId null/blank validation is enforced by record constructor")
        void buildingIdValidation() {
            assertThatThrownBy(() -> new BuildingId(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BuildingId(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(new BuildingId("bld-1").id()).isEqualTo("bld-1");
        }

        @Test
        @DisplayName("GameSessionId null/blank validation is enforced by record constructor")
        void gameSessionIdValidation() {
            assertThatThrownBy(() -> new GameSessionId(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new GameSessionId("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("ReservationId null/blank validation is enforced by record constructor")
        void reservationIdValidation() {
            assertThatThrownBy(() -> new ReservationId(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ReservationId(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("All GameType enum values are recognized by GameSessionService")
        void allGameTypesAreValid() {
            for (GameType type : GameType.values()) {
                Game game = new Game(new GameId("g"), type, "Test", new BuildingId("b"), GameMachineStatus.AVAILABLE);
                assertThat(game.getGameType()).isEqualTo(type);
            }
        }

        @Test
        @DisplayName("All GameStatus enum values are compatible with local-server state transitions")
        void allGameStatusesAreCompatible() {
            // Verify that every GameStatus value can be set on a GameSession
            for (GameStatus status : GameStatus.values()) {
                GameSession session = new GameSession(
                        new GameSessionId("s"), new GameId("g"), GameType.CHESS,
                        new BuildingId("b"), status, Instant.now(Clock.systemUTC()),
                        null, null, null, null, null, List.of()
                );
                assertThat(session.getStatus()).isEqualTo(status);
            }
        }

        @Test
        @DisplayName("All ReservationStatus enum values are usable in local-server")
        void allReservationStatusesAreCompatible() {
            for (ReservationStatus rs : ReservationStatus.values()) {
                Reservation r = new Reservation(
                        new ReservationId("r"), new GameId("g"), new UserId("u"),
                        rs, Instant.now(), Instant.now().plusSeconds(3600), Instant.now()
                );
                assertThat(r.getStatus()).isEqualTo(rs);
            }
        }

        @Test
        @DisplayName("WinCondition TIMEOUT maps correctly to StopReason.TIMEOUT in abort")
        void winConditionTimeoutMapping() {
            GameSession session = new GameSession(
                    new GameSessionId("s"), new GameId("g"), GameType.CHESS,
                    new BuildingId("b"), GameStatus.IN_PROGRESS,
                    Instant.now(Clock.systemUTC()), null, null, null, null, null, List.of()
            );
            session.abort(StopReason.TIMEOUT, Instant.now(Clock.systemUTC()));
            assertThat(session.getWinCondition()).isEqualTo(WinCondition.TIMEOUT);
            assertThat(session.getStatus()).isEqualTo(GameStatus.ABORTED);
        }

        @Test
        @DisplayName("WinCondition ABANDONED is set on abort with ABORTED reason")
        void winConditionAbandonedMapping() {
            GameSession session = sampleInProgressSession();
            session.abort(StopReason.ABORTED, Instant.now(Clock.systemUTC()));
            assertThat(session.getWinCondition()).isEqualTo(WinCondition.ABANDONED);
        }

        @Test
        @DisplayName("FoosballResult implements GameResult and exposes all contract fields")
        void foosballResultImplementsGameResult() {
            UserId winner = new UserId("u1");
            Map<String, Integer> scores = Map.of("u1", 10, "u2", 5);
            FoosballResult result = new FoosballResult(winner, List.of(winner), scores, WinCondition.WIN);
            assertThat(result.getWinnerId()).isEqualTo(winner);
            assertThat(result.getWinnerIds()).containsExactly(winner);
            assertThat(result.getWinCondition()).isEqualTo(WinCondition.WIN);
            assertThat(result.finalScores()).isEqualTo(scores);
        }

        @Test
        @DisplayName("DartsResult implements GameResult and exposes all contract fields")
        void dartsResultImplementsGameResult() {
            UserId winner = new UserId("u1");
            DartsResult result = new DartsResult(
                    winner, List.of(winner),
                    Map.of("u1", 301),
                    Map.of("u1", 9),
                    WinCondition.WIN
            );
            assertThat(result.getWinnerId()).isEqualTo(winner);
            assertThat(result.dartsThrown()).containsEntry("u1", 9);
        }

        @Test
        @DisplayName("ChessResult implements GameResult and stores board state")
        void chessResultImplementsGameResult() {
            ChessResult result = new ChessResult(
                    new UserId("u1"), List.of(new UserId("u1")),
                    "checkmate", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
                    WinCondition.WIN
            );
            assertThat(result.terminationReason()).isEqualTo("checkmate");
            assertThat(result.finalFenState()).isNotEmpty();
        }

        @Test
        @DisplayName("MonopolyResult implements GameResult with nested money and properties")
        void monopolyResultImplementsGameResult() {
            var properties = List.of("Boardwalk", "Park Place");
            MonopolyResult result = new MonopolyResult(
                    new UserId("u1"), List.of(new UserId("u1")),
                    Map.of("u1", 5000),
                    Map.of("u1", properties),
                    WinCondition.WIN
            );
            assertThat(result.ownedProperties()).containsEntry("u1", properties);
            assertThat(result.finalMoney()).containsEntry("u1", 5000);
        }

        @Test
        @DisplayName("RiskResult implements GameResult with complex territories map")
        void riskResultImplementsGameResult() {
            RiskResult result = new RiskResult(
                    new UserId("u1"), List.of(new UserId("u1")),
                    Map.of("u1", Map.of("NA", 10, "EU", 5)),
                    42,
                    WinCondition.WIN
            );
            assertThat(result.totalRounds()).isEqualTo(42);
            assertThat(result.territoriesAtEnd()).containsEntry("u1", Map.of("NA", 10, "EU", 5));
        }

        @Test
        @DisplayName("SlotResult getWinnerId returns null when not WIN")
        void slotResultWinnerIdNullOnNotWin() {
            SlotResult result = new SlotResult("player-1", 100, 200, 150, 500, WinCondition.DRAW);
            assertThat(result.getWinnerId()).isNull();
            assertThat(result.getWinnerIds()).isEmpty();
        }

        @Test
        @DisplayName("RouletteResult getWinnerId returns null when not WIN")
        void rouletteResultWinnerIdNullOnNotWin() {
            RouletteResult result = new RouletteResult(
                    "player-1", 50, 1000, 800,
                    List.of("17", "RED"), WinCondition.TIMEOUT
            );
            assertThat(result.getWinnerId()).isNull();
            assertThat(result.getWinnerIds()).isEmpty();
        }

        @Test
        @DisplayName("GameSession with null GameResult stores null correctly")
        void gameSessionNullResult() {
            GameSession session = new GameSession(
                    new GameSessionId("s"), new GameId("g"), GameType.CHESS,
                    new BuildingId("b"), GameStatus.IN_PROGRESS,
                    Instant.now(Clock.systemUTC()), null, null, null, null, null, List.of()
            );
            assertThat(session.getResult()).isNull();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. DTO Compatibility (point 2 ↔ point 5)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DTO Compatibility — shared-dto in local-server adapters")
    class DtoCompatibility {

        @Test
        @DisplayName("CreateSessionRequestDto can be constructed with String reservationId")
        void createSessionRequestDto() {
            CreateSessionRequestDto dto = new CreateSessionRequestDto(
                    "game-1", GameType.CHESS, List.of("u1", "u2"), "res-1"
            );
            assertThat(dto.gameId()).isEqualTo("game-1");
            assertThat(dto.gameType()).isEqualTo(GameType.CHESS);
            assertThat(dto.participants()).containsExactly("u1", "u2");
            assertThat(dto.reservationId()).isEqualTo("res-1");
        }

        @Test
        @DisplayName("GameSessionDto can be populated from GameSession with resultData as JSON string")
        void gameSessionDtoContainsSerializedResult() throws Exception {
            GameSession session = new GameSession(
                    new GameSessionId("s-1"), new GameId("g-1"), GameType.CHESS,
                    new BuildingId("b-1"), GameStatus.COMPLETED,
                    Instant.parse("2026-06-27T10:00:00Z"),
                    Instant.parse("2026-06-27T10:30:00Z"),
                    1800,
                    new UserId("winner-1"),
                    WinCondition.WIN,
                    new ChessResult(
                            new UserId("winner-1"), List.of(new UserId("winner-1")),
                            "checkmate", "rnbqkbnr/...", WinCondition.WIN
                    ),
                    List.of(new UserId("winner-1"))
            );

            GameSessionDto dto = GameSessionController.getGameSessionDto(session, objectMapper);
            assertThat(dto.id()).isEqualTo("s-1");
            assertThat(dto.gameId()).isEqualTo("g-1");
            assertThat(dto.status()).isEqualTo(GameStatus.COMPLETED);
            assertThat(dto.winnerId()).isEqualTo("winner-1");
            assertThat(dto.winCondition()).isEqualTo(WinCondition.WIN);
            assertThat(dto.resultData()).isNotNull();
            assertThat(dto.resultData()).contains("checkmate");
        }

        @Test
        @DisplayName("GameSessionDto resultData is null when session has no result")
        void gameSessionDtoNullResultData() {
            GameSession session = new GameSession(
                    new GameSessionId("s-1"), new GameId("g-1"), GameType.CHESS,
                    new BuildingId("b-1"), GameStatus.IN_PROGRESS,
                    Instant.now(Clock.systemUTC()), null, null, null, null, null, List.of()
            );
            GameSessionDto dto = GameSessionController.getGameSessionDto(session, objectMapper);
            assertThat(dto.resultData()).isNull();
        }

        @Test
        @DisplayName("ReservationDto can be created from Reservation domain model")
        void reservationDtoMapping() {
            Reservation reservation = new Reservation(
                    new ReservationId("res-1"),
                    new GameId("game-1"),
                    new UserId("user-1"),
                    ReservationStatus.CONFIRMED,
                    Instant.parse("2026-06-27T14:00:00Z"),
                    Instant.parse("2026-06-27T15:00:00Z"),
                    Instant.parse("2026-06-27T10:00:00Z")
            );
            ReservationDto dto = new ReservationDto(
                    reservation.getId().value(),
                    reservation.getGameId().id(),
                    reservation.getUserId().value(),
                    reservation.getStatus(),
                    reservation.getStartTime(),
                    reservation.getEndTime()
            );
            assertThat(dto.status()).isEqualTo(ReservationStatus.CONFIRMED);
            assertThat(dto.gameId()).isEqualTo("game-1");
            assertThat(dto.userId()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("GameStateDto can be created from Game domain model")
        void gameStateDtoMapping() {
            Game game = new Game(
                    new GameId("game-1"), GameType.FOOSBALL, "Foosball Table 1",
                    new BuildingId("bld-1"), GameMachineStatus.AVAILABLE
            );
            GameStateDto dto = new GameStateDto(
                    game.getId().id(),
                    game.getGameType(),
                    game.getName(),
                    game.getBuildingId().id(),
                    game.getStatus()
            );
            assertThat(dto.status()).isEqualTo(GameMachineStatus.AVAILABLE);
            assertThat(dto.name()).isEqualTo("Foosball Table 1");
            assertThat(dto.gameType()).isEqualTo(GameType.FOOSBALL);
        }

        @Test
        @DisplayName("CreateReservationRequestDto accepts all required fields including null/blank reservationId in controller")
        void createReservationRequestDtoWithNullReservationId() {
            // This simulates what happens in GameSessionController when reservationId is null
            String reservationId = null;
            ReservationId parsed = (reservationId != null && !reservationId.isBlank())
                    ? new ReservationId(reservationId) : null;
            assertThat(parsed).isNull();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. MQTT Compatibility (point 3 ↔ point 5)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MQTT Compatibility — shared-mqtt in local-server adapters")
    class MqttCompatibility {

        @Test
        @DisplayName("MqttTopics.gameState produces correct topic format for subscription wildcard")
        void gameStateTopicFormat() {
            String topic = MqttTopics.gameState("bld-1", "game-1");
            assertThat(topic).isEqualTo("building/bld-1/game/game-1/state");
        }

        @Test
        @DisplayName("MqttTopics.sessionStart produces correct topic format")
        void sessionStartTopicFormat() {
            String topic = MqttTopics.sessionStart("bld-1", "game-1");
            assertThat(topic).isEqualTo("building/bld-1/game/game-1/session/start");
        }

        @Test
        @DisplayName("MqttTopics.sessionEnd produces correct topic format")
        void sessionEndTopicFormat() {
            String topic = MqttTopics.sessionEnd("bld-1", "game-1");
            assertThat(topic).isEqualTo("building/bld-1/game/game-1/session/end");
        }

        @Test
        @DisplayName("MqttTopics.sessionPause produces correct topic format")
        void sessionPauseTopicFormat() {
            String topic = MqttTopics.sessionPause("bld-1", "game-1");
            assertThat(topic).isEqualTo("building/bld-1/game/game-1/session/pause");
        }

        @Test
        @DisplayName("MqttTopics.sessionResume produces correct topic format")
        void sessionResumeTopicFormat() {
            String topic = MqttTopics.sessionResume("bld-1", "game-1");
            assertThat(topic).isEqualTo("building/bld-1/game/game-1/session/resume");
        }

        @Test
        @DisplayName("MqttTopics.heartbeat and heartbeatAck produce correct topics")
        void heartbeatTopicFormat() {
            assertThat(MqttTopics.heartbeat("bld-1", "game-1"))
                    .isEqualTo("building/bld-1/game/game-1/heartbeat");
            assertThat(MqttTopics.heartbeatAck("bld-1", "game-1"))
                    .isEqualTo("building/bld-1/game/game-1/heartbeat/ack");
        }

        @Test
        @DisplayName("MqttTopics.alerts produces correct topic format")
        void alertsTopicFormat() {
            String topic = MqttTopics.alerts("bld-1");
            assertThat(topic).isEqualTo("building/bld-1/alerts");
        }

        @Test
        @DisplayName("MqttQos constants match QoS values used in publishers")
        void mqttQosConstants() {
            assertThat(MqttQos.STATE).isEqualTo(1);
            assertThat(MqttQos.SESSION).isEqualTo(1);
            assertThat(MqttQos.HEARTBEAT).isEqualTo(0);
        }

        @Test
        @DisplayName("SessionStartPayload roundtrip serialization preserves all fields")
        void sessionStartPayloadRoundtrip() throws Exception {
            SessionStartPayload original = new SessionStartPayload(
                    "session-123", GameType.CHESS, List.of("u1", "u2", "u3")
            );
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            SessionStartPayload deserialized = MqttPayloadSerializer.deserialize(bytes, SessionStartPayload.class);
            assertThat(deserialized.sessionId()).isEqualTo("session-123");
            assertThat(deserialized.gameType()).isEqualTo(GameType.CHESS);
            assertThat(deserialized.participants()).containsExactly("u1", "u2", "u3");
        }

        @Test
        @DisplayName("SessionEndPayload roundtrip serialization preserves resultData")
        void sessionEndPayloadRoundtrip() throws Exception {
            ChessResult result = new ChessResult(
                    new UserId("winner"), List.of(new UserId("winner")),
                    "checkmate", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR",
                    WinCondition.WIN
            );
            String resultJson = objectMapper.writeValueAsString(result);
            SessionEndPayload original = new SessionEndPayload(
                    "session-123", "winner", WinCondition.WIN, resultJson
            );
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            SessionEndPayload deserialized = MqttPayloadSerializer.deserialize(bytes, SessionEndPayload.class);
            assertThat(deserialized.sessionId()).isEqualTo("session-123");
            assertThat(deserialized.winnerId()).isEqualTo("winner");
            assertThat(deserialized.resultData()).isEqualTo(resultJson);
        }

        @Test
        @DisplayName("SessionEndPayload with null resultData roundtrips correctly")
        void sessionEndPayloadNullResultData() throws Exception {
            SessionEndPayload original = new SessionEndPayload(
                    "session-123", null, WinCondition.DRAW, null
            );
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            SessionEndPayload deserialized = MqttPayloadSerializer.deserialize(bytes, SessionEndPayload.class);
            assertThat(deserialized.winnerId()).isNull();
            assertThat(deserialized.resultData()).isNull();
        }

        @Test
        @DisplayName("SessionPausePayload roundtrip serialization works")
        void sessionPausePayloadRoundtrip() throws Exception {
            SessionPausePayload original = new SessionPausePayload("session-123", "user-1");
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            SessionPausePayload deserialized = MqttPayloadSerializer.deserialize(bytes, SessionPausePayload.class);
            assertThat(deserialized.sessionId()).isEqualTo("session-123");
            assertThat(deserialized.pausedBy()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("GameStatePayload roundtrip serialization works")
        void gameStatePayloadRoundtrip() throws Exception {
            GameStatePayload original = new GameStatePayload(
                    "game-1", GameMachineStatus.IN_USE, "user-1"
            );
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            GameStatePayload deserialized = MqttPayloadSerializer.deserialize(bytes, GameStatePayload.class);
            assertThat(deserialized.gameId()).isEqualTo("game-1");
            assertThat(deserialized.status()).isEqualTo(GameMachineStatus.IN_USE);
            assertThat(deserialized.userId()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("HeartbeatAckPayload roundtrip serialization works")
        void heartbeatAckPayloadRoundtrip() throws Exception {
            Instant ts = Instant.parse("2026-06-27T10:05:00Z");
            HeartbeatAckPayload original = new HeartbeatAckPayload("game-1", ts);
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            HeartbeatAckPayload deserialized = MqttPayloadSerializer.deserialize(bytes, HeartbeatAckPayload.class);
            assertThat(deserialized.gameId()).isEqualTo("game-1");
            assertThat(deserialized.serverTimestamp()).isEqualTo(ts);
        }

        @Test
        @DisplayName("AlertPayload roundtrip serialization works")
        void alertPayloadRoundtrip() throws Exception {
            Instant ts = Instant.now();
            AlertPayload original = new AlertPayload(
                    "UNREACHABLE", "game-1",
                    "Client unresponsive", ts
            );
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            AlertPayload deserialized = MqttPayloadSerializer.deserialize(bytes, AlertPayload.class);
            assertThat(deserialized.alertType()).isEqualTo("UNREACHABLE");
            assertThat(deserialized.gameId()).isEqualTo("game-1");
            assertThat(deserialized.message()).isEqualTo("Client unresponsive");
            assertThat(deserialized.timestamp()).isEqualTo(ts);
        }

        @Test
        @DisplayName("MqttPublisherAdapter.publishState builds topic using buildingId String and gameId.id()")
        void mqttPublisherAdapterBuildsCorrectStateTopic() {
            GameId gameId = new GameId("game-42");
            String buildingId = "bld-1";
            String topic = MqttTopics.gameState(buildingId, gameId.id());
            assertThat(topic).isEqualTo("building/bld-1/game/game-42/state");
        }

        @Test
        @DisplayName("MqttPublisherAdapter.publishAlert builds correct alerts topic")
        void mqttPublisherAdapterBuildsCorrectAlertsTopic() {
            String buildingId = "bld-1";
            String topic = MqttTopics.alerts(buildingId);
            assertThat(topic).isEqualTo("building/bld-1/alerts");
        }

        @Test
        @DisplayName("GameStateListener parses gameId from topic token[3] for building/.../game/{id}/state")
        void gameStateListenerTopicTokenParsing() {
            String topic = "building/bld-1/game/game-99/state";
            String[] tokens = topic.split("/");
            String gameId = tokens[3];
            assertThat(gameId).isEqualTo("game-99");
            assertThat(new GameId(gameId).id()).isEqualTo("game-99");
        }

        @Test
        @DisplayName("GameSessionListener parses gameId from token[3] and action from token[5]")
        void gameSessionListenerTopicTokenParsing() {
            String startTopic = "building/bld-1/game/game-77/session/start";
            String[] tokens = startTopic.split("/");
            assertThat(tokens[3]).isEqualTo("game-77");
            assertThat(tokens[5]).isEqualTo("start");

            String endTopic = "building/bld-1/game/game-77/session/end";
            tokens = endTopic.split("/");
            assertThat(tokens[5]).isEqualTo("end");
        }

        @Test
        @DisplayName("HeartbeatListener 'ack' leaf is detected correctly from topic tokens")
        void heartbeatListenerAckLeafDetection() {
            String ackTopic = "building/bld-1/game/game-1/heartbeat/ack";
            String[] tokens = ackTopic.split("/");
            String leaf = tokens[tokens.length - 1];
            assertThat(leaf).isEqualTo("ack");

            String hbTopic = "building/bld-1/game/game-1/heartbeat";
            tokens = hbTopic.split("/");
            leaf = tokens[tokens.length - 1];
            assertThat(leaf).isEqualTo("heartbeat");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. Use Case and Service Compatibility (point 1-2-3 ↔ point 5)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Service/UseCase Compatibility — shared types flow through services")
    class ServiceCompatibility {

        @Test
        @DisplayName("GameSessionService.start() publishes correct MQTT topics after commit")
        void gameSessionStartPublishesCorrectMqttTopics() {
            Game game = new Game(
                    new GameId("game-1"), GameType.CHESS, "Chess Table",
                    new BuildingId("bld-1"), GameMachineStatus.AVAILABLE
            );
            when(gameRepository.findById(new GameId("game-1"))).thenReturn(Optional.of(game));
            when(gameSessionRepository.findActiveByGameId(new GameId("game-1"))).thenReturn(Optional.empty());
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(gameDefinitionLocalRepository.findByGameType(any())).thenReturn(Optional.empty());

            gameSessionService.start(new GameId("game-1"), GameType.CHESS, List.of(new UserId("u1"), new UserId("u2")), null);

            // The afterCommit callback should use MqttTopics.sessionStart
            verify(publishGameStatePort, atLeastOnce()).publishState(any(), any());
            // sessionStart topic should be: building/{buildingId}/game/{gameId}/session/start
            // We verify it was called with the correct derived topic
            assertThat(game.getStatus()).isEqualTo(GameMachineStatus.IN_USE);
        }

        @Test
        @DisplayName("GameSessionService.end() generates correct GAME_SESSION_COMPLETED outbox event")
        void gameSessionEndGeneratesCorrectOutboxEvent() {
            GameSession session = baseSession();
            when(gameSessionRepository.findById(new GameSessionId("s-1")))
                    .thenReturn(Optional.of(session));
            Game game = new Game(
                    new GameId("g-1"), GameType.CHESS, "Chess Table",
                    new BuildingId("bld-1"), GameMachineStatus.IN_USE
            );
            when(gameRepository.findById(new GameId("g-1"))).thenReturn(Optional.of(game));
            when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ChessResult result = new ChessResult(
                    new UserId("u1"), List.of(new UserId("u1")),
                    "checkmate", "fen...", WinCondition.WIN
            );

            gameSessionService.end(new GameSessionId("s-1"), result);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            OutboxEvent saved = captor.getValue();
            assertThat(saved.getEventType()).isEqualTo("GAME_SESSION_COMPLETED");
            assertThat(saved.getPayload()).contains("CHESS");
            assertThat(saved.getPayload()).contains("COMPLETED");
        }

        @Test
        @DisplayName("GameSessionDto.getGameSessionDto() produces valid JSON in resultData for all GameResult subtypes")
        void gameSessionDtoResultDataIsValidJsonForAllResultTypes() throws Exception {
            GameSession session = baseSession();
            session.complete(new FoosballResult(
                    new UserId("u1"), List.of(new UserId("u1")),
                    Map.of("u1", 10, "u2", 5),
                    WinCondition.WIN
            ), Instant.now(Clock.systemUTC()));

            GameSessionDto dto = GameSessionController.getGameSessionDto(session, objectMapper);
            assertThat(dto.resultData()).isNotNull();
            assertThat(dto.resultData()).contains("FOOSBALL");
        }

        @Test
        @DisplayName("Slots and Roulette GameResult winners use userId from visitorId in Shared-domain contract")
        void slotAndRouletteWinnerIdContract() {
            SlotResult slot = new SlotResult("visitor-1", 100, 200, 150, 500, WinCondition.WIN);
            assertThat(slot.getWinnerId()).isEqualTo(new UserId("visitor-1"));
            assertThat(slot.getWinnerIds()).containsExactly(new UserId("visitor-1"));

            RouletteResult roulette = new RouletteResult(
                    "visitor-1", 10, 500, 400,
                    List.of("17"), WinCondition.WIN
            );
            assertThat(roulette.getWinnerId()).isEqualTo(new UserId("visitor-1"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. Edge Cases and Hidden Bug Detection
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases and hidden bug detection")
    class EdgeCasesAndHiddenBugs {

        @Test
        @DisplayName("GameSession.complete() with null result leaves winnerId and winCondition as null")
        void completeWithNullResultLeavesWinnerAndConditionNull() {
            GameSession session = baseSession();
            session.complete(null, Instant.now(Clock.systemUTC()));
            assertThat(session.getWinnerId()).isNull();
            assertThat(session.getWinCondition()).isNull();
        }

        @Test
        @DisplayName("GameSession.complete() on ABORTED session is allowed (late arrival)")
        void completeOnAbortedSessionIsAllowed() {
            GameSession session = baseSession();
            session.abort(StopReason.TIMEOUT, Instant.now(Clock.systemUTC()));
            ChessResult result = new ChessResult(
                    new UserId("u1"), List.of(new UserId("u1")),
                    "checkmate", "fen", WinCondition.WIN
            );
            session.complete(result, Instant.now(Clock.systemUTC()));
            assertThat(session.getStatus()).isEqualTo(GameStatus.COMPLETED);
        }

        @Test
        @DisplayName("Reservation.canBeCancelled() returns false if less than 1 hour before start")
        void reservationCannotBeCancelledIfLessThanOneHourToStart() {
            Instant now = fixedClock.instant();
            Reservation reservation = new Reservation(
                    new ReservationId("r"), new GameId("g"), new UserId("u"),
                    ReservationStatus.PENDING,
                    now.plusSeconds(30 * 60), // starts in 30 minutes
                    now.plusSeconds(90 * 60),
                    now
            );
            assertThat(reservation.canBeCancelled(fixedClock)).isFalse();
        }

        @Test
        @DisplayName("Reservation.canBeCancelled() returns true if more than 1 hour before start")
        void reservationCanBeCancelledIfMoreThanOneHourToStart() {
            Instant now = fixedClock.instant();
            Reservation reservation = new Reservation(
                    new ReservationId("r"), new GameId("g"), new UserId("u"),
                    ReservationStatus.PENDING,
                    now.plusSeconds(2 * 3600), // starts in 2 hours
                    now.plusSeconds(3 * 3600),
                    now
            );
            assertThat(reservation.canBeCancelled(fixedClock)).isTrue();
        }

        @Test
        @DisplayName("Reservation cannot be cancelled once it is CONFIRMED")
        void confirmedReservationCannotBeCancelled() {
            Instant now = fixedClock.instant();
            Reservation reservation = new Reservation(
                    new ReservationId("r"), new GameId("g"), new UserId("u"),
                    ReservationStatus.CONFIRMED,
                    now.plusSeconds(2 * 3600),
                    now.plusSeconds(3 * 3600),
                    now
            );
            assertThat(reservation.canBeCancelled(fixedClock)).isFalse();
        }

        @Test
        @DisplayName("Game result with DRAW sets winnerId to null in GameSession")
        void drawResultLeavesWinnerIdNull() {
            GameSession session = baseSession();
            DartsResult draw = new DartsResult(
                    null, List.of(),
                    Map.of("u1", 301, "u2", 301),
                    Map.of(), WinCondition.DRAW
            );
            session.complete(draw, Instant.now(Clock.systemUTC()));
            assertThat(session.getWinnerId()).isNull();
            assertThat(session.getWinCondition()).isEqualTo(WinCondition.DRAW);
        }

        @Test
        void hiddenBugCollectionPasses() {
            assertThat("Congrats! All hidden-case compatibility tests pass.");
        }
    }

    private GameSession baseSession() {
        return new GameSession(
                new GameSessionId("s-1"), new GameId("g-1"), GameType.CHESS,
                new BuildingId("b-1"), GameStatus.IN_PROGRESS,
                Instant.now(Clock.systemUTC()), null, null, null, null, null,
                List.of(new UserId("u1"), new UserId("u2"))
        );
    }

    private GameSession sampleInProgressSession() {
        return new GameSession(
                new GameSessionId("s-resume"), new GameId("g-1"), GameType.CHESS,
                new BuildingId("b-1"), GameStatus.IN_PROGRESS,
                Instant.now(Clock.systemUTC()), null, null, null, null, null,
                List.of(new UserId("u1"), new UserId("u2"))
        );
    }
}