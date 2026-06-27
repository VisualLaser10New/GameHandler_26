package com.gameplatform.local.infrastructure.adapters.out.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.PublishAlertPort;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.result.ChessResult;
import com.gameplatform.shared.domain.result.FoosballResult;
import com.gameplatform.shared.mqtt.MqttPayloadSerializer;
import com.gameplatform.shared.mqtt.MqttQos;
import com.gameplatform.shared.mqtt.MqttTopics;
import com.gameplatform.shared.mqtt.payload.*;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Targeted compatibility tests for the MQTT adapter layer in local-server.
 *
 * <p>These tests verify that the shared-mqtt module (point 3) topic-patterns,
 * QoS constants, and payload records are correctly used by the MQTT outbound
 * adapter, and specifically probe hidden edge cases like the SessionResumePayload
 * bypass and the GameResult-in-resultData serialization.</p>
 */
@ExtendWith(MockitoExtension.class)
class MqttPublisherAdapterCompatibilityTest {

    @Mock
    private IMqttClient mqttClient;

    private MqttPublisherAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.addMixIn(com.gameplatform.shared.domain.result.GameResult.class,
                com.gameplatform.local.infrastructure.config.JacksonConfig.GameResultMixIn.class);
        objectMapper.addMixIn(com.gameplatform.shared.domain.result.RouletteResult.class,
                com.gameplatform.local.infrastructure.config.JacksonConfig.RouletteResultMixIn.class);
        objectMapper.addMixIn(com.gameplatform.shared.domain.result.SlotResult.class,
                com.gameplatform.local.infrastructure.config.JacksonConfig.SlotResultMixIn.class);
        adapter = new MqttPublisherAdapter(mqttClient, objectMapper, "bld-1");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Topic-Pattern Consistency (shared-mqtt vs adapter)
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Topic-pattern consistency between shared-mqtt and adapter")
    class TopicPatternCompatibility {

        @Test
        @DisplayName("publishState builds topic matching MqttTopics.gameState()")
        void publishStateUsesCorrectTopic() throws Exception {
            GameId gameId = new GameId("game-42");
            doNothing().when(mqttClient).publish(anyString(), any(MqttMessage.class));

            adapter.publishState(gameId, GameMachineStatus.IN_USE);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(mqttClient).publish(topicCaptor.capture(), any(MqttMessage.class));
            String usedTopic = topicCaptor.getValue();
            assertThat(usedTopic).isEqualTo("building/bld-1/game/game-42/state");
        }

        @Test
        @DisplayName("publishState sets QoS=STATE and retained=true per shared-mqtt MqttQos")
        void publishStateQosAndRetained() throws Exception {
            GameId gameId = new GameId("game-1");
            doNothing().when(mqttClient).publish(anyString(), any(MqttMessage.class));

            adapter.publishState(gameId, GameMachineStatus.AVAILABLE);

            ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
            verify(mqttClient).publish(anyString(), msgCaptor.capture());
            assertThat(msgCaptor.getValue().getQos()).isEqualTo(MqttQos.STATE);
            assertThat(msgCaptor.getValue().isRetained()).isTrue();
        }

        @Test
        @DisplayName("publishSessionEvent for session/start calls MqttTopics.sessionStart")
        void publishSessionStartTopic() throws Exception {
            GameSession session = buildSession("session-1", "game-1", GameType.CHESS);
            doNothing().when(mqttClient).publish(anyString(), any(MqttMessage.class));

            adapter.publishSessionEvent(
                    MqttTopics.sessionStart("bld-1", "game-1"), session
            );

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(mqttClient).publish(topicCaptor.capture(), any(MqttMessage.class));
            assertThat(topicCaptor.getValue()).isEqualTo("building/bld-1/game/game-1/session/start");
        }

        @Test
        @DisplayName("publishSessionEvent for session/end calls MqttTopics.sessionEnd")
        void publishSessionEndTopic() throws Exception {
            GameSession session = buildSession("session-1", "game-1", GameType.CHESS);
            doNothing().when(mqttClient).publish(anyString(), any(MqttMessage.class));

            adapter.publishSessionEvent(
                    MqttTopics.sessionEnd("bld-1", "game-1"), session
            );

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(mqttClient).publish(topicCaptor.capture(), any(MqttMessage.class));
            assertThat(topicCaptor.getValue()).isEqualTo("building/bld-1/game/game-1/session/end");
        }

        @Test
        @DisplayName("publishSessionEvent for session/pause calls MqttTopics.sessionPause")
        void publishSessionPauseTopic() throws Exception {
            GameSession session = buildSession("session-1", "game-1", GameType.CHESS);
            doNothing().when(mqttClient).publish(anyString(), any(MqttMessage.class));

            adapter.publishSessionEvent(
                    MqttTopics.sessionPause("bld-1", "game-1"), session
            );

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(mqttClient).publish(topicCaptor.capture(), any(MqttMessage.class));
            assertThat(topicCaptor.getValue()).isEqualTo("building/bld-1/game/game-1/session/pause");
        }

        @Test
        @DisplayName("publishSessionEvent for session/resume topic builds a SessionResumePayload payload")
        void publishSessionResumeUsesSessionResumePayload() throws Exception {
            GameSession session = buildSession("session-1", "game-1", GameType.CHESS);
            doNothing().when(mqttClient).publish(anyString(), any(MqttMessage.class));

            adapter.publishSessionEvent(
                    MqttTopics.sessionResume("bld-1", "game-1"), session
            );

            ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
            verify(mqttClient).publish(anyString(), msgCaptor.capture());
            byte[] payloadBytes = msgCaptor.getValue().getPayload();

            SessionResumePayload deserialized = MqttPayloadSerializer.deserialize(payloadBytes, SessionResumePayload.class);
            assertThat(deserialized.sessionId()).isEqualTo("session-1");
        }

        @Test
        @DisplayName("publishAlert uses MqttTopics.alerts with buildingId String")
        void publishAlertTopic() throws Exception {
            AlertPayload alert = new AlertPayload(
                    "UNREACHABLE", "game-1", "Client unreachable", Instant.now()
            );
            doNothing().when(mqttClient).publish(anyString(), any(MqttMessage.class));

            adapter.publishAlert(alert);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(mqttClient).publish(topicCaptor.capture(), any(MqttMessage.class));
            assertThat(topicCaptor.getValue()).isEqualTo("building/bld-1/alerts");
        }

        @Test
        @DisplayName("publishSessionEvent QoS=SESSION and not retained, matching MqttQos")
        void publishSessionEventQos() throws Exception {
            GameSession session = buildSession("s1", "g1", GameType.CHESS);
            doNothing().when(mqttClient).publish(anyString(), any(MqttMessage.class));

            adapter.publishSessionEvent(MqttTopics.sessionStart("bld-1", "g1"), session);

            ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
            verify(mqttClient).publish(anyString(), msgCaptor.capture());
            assertThat(msgCaptor.getValue().getQos()).isEqualTo(MqttQos.SESSION);
            assertThat(msgCaptor.getValue().isRetained()).isFalse();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. Payload Serialization/Deserialization Compatibility
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Payload serialization compatibility via shared MqttPayloadSerializer")
    class PayloadSerializationCompatibility {

        @Test
        @DisplayName("SessionStartPayload roundtrip preserves participants order")
        void sessionStartPayloadRoundtrip() throws Exception {
            SessionStartPayload original = new SessionStartPayload(
                    "session-1", GameType.FOOSBALL, List.of("u1", "u2", "u3")
            );
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            SessionStartPayload deserialized = MqttPayloadSerializer.deserialize(bytes, SessionStartPayload.class);
            assertThat(deserialized.sessionId()).isEqualTo("session-1");
            assertThat(deserialized.gameType()).isEqualTo(GameType.FOOSBALL);
            assertThat(deserialized.participants()).containsExactly("u1", "u2", "u3");
        }

        @Test
        @DisplayName("SessionEndPayload roundtrip preserves all fields")
        void sessionEndPayloadRoundtrip() throws Exception {
            String resultJson = objectMapper.writeValueAsString(
                    new FoosballResult(
                            new UserId("winner"), List.of(new UserId("winner")),
                            Map.of("winner", 10), WinCondition.WIN
                    )
            );
            SessionEndPayload original = new SessionEndPayload(
                    "session-1", "winner", WinCondition.WIN, resultJson
            );
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            SessionEndPayload deserialized = MqttPayloadSerializer.deserialize(bytes, SessionEndPayload.class);
            assertThat(deserialized.sessionId()).isEqualTo("session-1");
            assertThat(deserialized.winnerId()).isEqualTo("winner");
            assertThat(deserialized.winCondition()).isEqualTo(WinCondition.WIN);
            assertThat(deserialized.resultData()).contains("FOOSBALL");
        }

        @Test
        @DisplayName("SessionPausePayload with pausedBy=null roundtrips correctly")
        void sessionPausePayloadNullPausedBy() throws Exception {
            SessionPausePayload original = new SessionPausePayload("session-1", null);
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            SessionPausePayload deserialized = MqttPayloadSerializer.deserialize(bytes, SessionPausePayload.class);
            assertThat(deserialized.sessionId()).isEqualTo("session-1");
            assertThat(deserialized.pausedBy()).isNull();
        }

        @Test
        @DisplayName("GameStatePayload roundtrip preserves userId in payload")
        void gameStatePayloadRoundtrip() throws Exception {
            GameStatePayload original = new GameStatePayload(
                    "game-1", GameMachineStatus.RESERVED, "user-1"
            );
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            GameStatePayload deserialized = MqttPayloadSerializer.deserialize(bytes, GameStatePayload.class);
            assertThat(deserialized.gameId()).isEqualTo("game-1");
            assertThat(deserialized.status()).isEqualTo(GameMachineStatus.RESERVED);
            assertThat(deserialized.userId()).isEqualTo("user-1");
        }

        @Test
        @DisplayName("HeartbeatAckPayload with null timestamp roundtrips")
        void heartbeatAckPayloadWithNullTimestamp() throws Exception {
            HeartbeatAckPayload original = new HeartbeatAckPayload("game-1", null);
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            HeartbeatAckPayload deserialized = MqttPayloadSerializer.deserialize(bytes, HeartbeatAckPayload.class);
            assertThat(deserialized.gameId()).isEqualTo("game-1");
            assertThat(deserialized.serverTimestamp()).isNull();
        }

        @Test
        @DisplayName("AlertPayload roundtrip preserves alertType, gameId, message, timestamp")
        void alertPayloadRoundtrip() throws Exception {
            Instant ts = Instant.parse("2026-06-27T12:00:00Z");
            AlertPayload original = new AlertPayload(
                    "CRITICAL", "game-9", "Hardware failure", ts
            );
            byte[] bytes = MqttPayloadSerializer.serialize(original);
            AlertPayload deserialized = MqttPayloadSerializer.deserialize(bytes, AlertPayload.class);
            assertThat(deserialized.alertType()).isEqualTo("CRITICAL");
            assertThat(deserialized.gameId()).isEqualTo("game-9");
            assertThat(deserialized.message()).isEqualTo("Hardware failure");
            assertThat(deserialized.timestamp()).isEqualTo(ts);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Hidden Edge Cases
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Hidden edge cases in MQTT publishing")
    class HiddenMqttEdgeCases {

        @Test
        @DisplayName("publishState uses GameId.id() correctly for topic (not the record itself)")
        void publishStateTopicUsesIdStringNotRecord() throws Exception {
            GameId gameId = new GameId("g-123");
            doNothing().when(mqttClient).publish(anyString(), any(MqttMessage.class));
            adapter.publishState(gameId, GameMachineStatus.MAINTENANCE);

            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(mqttClient).publish(topicCaptor.capture(), any(MqttMessage.class));
            assertThat(topicCaptor.getValue()).contains("g-123");
            assertThat(topicCaptor.getValue()).doesNotContain("com.gameplatform");
        }

        @Test
        @DisplayName("MqttPayloadSerializer using its own ObjectMapper includes GameResultMixIn type discriminator")
        void mqttPayloadSerializerIncludesGameResultMixIn() throws Exception {
            ChessResult result = new ChessResult(
                    new UserId("w"), List.of(new UserId("w")),
                    "mate", "fen", WinCondition.WIN
            );
            byte[] bytes = MqttPayloadSerializer.serialize(result);
            String json = new String(bytes);

            // The MqttPayloadSerializer's static ObjectMapper has GameResultMixIn,
            // so type discriminator should be present
            assertThat(json).contains("\"type\":\"CHESS\"");
            assertThat(json).contains("\"winnerId\":{\"value\":\"w\"}");
        }

        @Test
        @DisplayName("publishState sets Qos=1 when using MqttQos.STATE constant")
        void publishStateQosIsOne() throws Exception {
            doNothing().when(mqttClient).publish(anyString(), any(MqttMessage.class));
            adapter.publishState(new GameId("g1"), GameMachineStatus.AVAILABLE);

            ArgumentCaptor<MqttMessage> captor = ArgumentCaptor.forClass(MqttMessage.class);
            verify(mqttClient).publish(anyString(), captor.capture());
            assertThat(captor.getValue().getQos()).isEqualTo(1);
        }

        @Test
        @DisplayName("publishAlert uses Qos=1 by hardcoded value in adapter")
        void publishAlertQosIsOne() throws Exception {
            AlertPayload alert = new AlertPayload("INFO", "g1", "msg", Instant.now());
            doNothing().when(mqttClient).publish(anyString(), any(MqttMessage.class));

            adapter.publishAlert(alert);

            ArgumentCaptor<MqttMessage> captor = ArgumentCaptor.forClass(MqttMessage.class);
            verify(mqttClient).publish(anyString(), captor.capture());
            assertThat(captor.getValue().getQos()).isEqualTo(1);
        }
    }

    private GameSession buildSession(String sessionId, String gameId, GameType gameType) {
        return new GameSession(
                new GameSessionId(sessionId), new GameId(gameId), gameType,
                new BuildingId("bld-1"), GameStatus.IN_PROGRESS,
                Instant.now(), null, null, null, null, null,
                List.of(new UserId("u1"))
        );
    }
}
