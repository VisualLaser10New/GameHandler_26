package com.gameplatform.local.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.result.GameResult;
import com.gameplatform.shared.domain.result.ChessResult;
import com.gameplatform.shared.domain.result.DartsResult;
import com.gameplatform.shared.domain.result.FoosballResult;
import com.gameplatform.shared.domain.result.MonopolyResult;
import com.gameplatform.shared.domain.result.RiskResult;
import com.gameplatform.shared.domain.result.RouletteResult;
import com.gameplatform.shared.domain.result.SlotResult;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Targeted tests for the polymorphic GameResult deserialization path used by
 * GameSessionController.end().
 *
 * <p>These tests verify that the shared JacksonConfig mix-in for GameResult
 * properly handles all concrete subtypes when they arrive as JSON from the REST API.</p>
 */
class GameResultPolymorphicDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .addMixIn(GameResult.class, com.gameplatform.local.infrastructure.config.JacksonConfig.GameResultMixIn.class)
                .addMixIn(com.gameplatform.shared.domain.result.RouletteResult.class, com.gameplatform.local.infrastructure.config.JacksonConfig.RouletteResultMixIn.class)
                .addMixIn(com.gameplatform.shared.domain.result.SlotResult.class, com.gameplatform.local.infrastructure.config.JacksonConfig.SlotResultMixIn.class);
    }

    @Test
    @DisplayName("ChessResult JSON roundtrips through GameResult reference")
    void chessResultRoundtrip() throws Exception {
        ChessResult original = new ChessResult(
                new UserId("w1"), List.of(new UserId("w1")),
                "checkmate", "fen_string", WinCondition.WIN
        );
        String json = objectMapper.writeValueAsString(original);
        GameResult deserialized = objectMapper.readValue(json, GameResult.class);
        assertThat(deserialized).isInstanceOf(ChessResult.class);
        assertThat(deserialized.getWinnerId()).isEqualTo(new UserId("w1"));
        assertThat(deserialized.getWinCondition()).isEqualTo(WinCondition.WIN);
    }

    @Test
    @DisplayName("DartsResult JSON roundtrips through GameResult reference")
    void dartsResultRoundtrip() throws Exception {
        DartsResult original = new DartsResult(
                new UserId("w1"), List.of(new UserId("w1")),
                Map.of("w1", 501),
                Map.of("w1", 9),
                WinCondition.WIN
        );
        String json = objectMapper.writeValueAsString(original);
        GameResult deserialized = objectMapper.readValue(json, GameResult.class);
        assertThat(deserialized).isInstanceOf(DartsResult.class);
    }

    @Test
    @DisplayName("FoosballResult JSON roundtrips through GameResult reference")
    void foosballResultRoundtrip() throws Exception {
        FoosballResult original = new FoosballResult(
                new UserId("w1"), List.of(new UserId("w1")),
                Map.of("w1", 10, "w2", 5),
                WinCondition.WIN
        );
        String json = objectMapper.writeValueAsString(original);
        GameResult deserialized = objectMapper.readValue(json, GameResult.class);
        assertThat(deserialized).isInstanceOf(FoosballResult.class);
    }

    @Test
    @DisplayName("MonopolyResult JSON roundtrips through GameResult reference")
    void monopolyResultRoundtrip() throws Exception {
        var properties = List.of("Boardwalk", "Park Place");
        MonopolyResult original = new MonopolyResult(
                new UserId("w1"), List.of(new UserId("w1")),
                Map.of("w1", 5000),
                Map.of("w1", properties),
                WinCondition.WIN
        );
        String json = objectMapper.writeValueAsString(original);
        GameResult deserialized = objectMapper.readValue(json, GameResult.class);
        assertThat(deserialized).isInstanceOf(MonopolyResult.class);
    }

    @Test
    @DisplayName("RiskResult JSON roundtrips through GameResult reference")
    void riskResultRoundtrip() throws Exception {
        RiskResult original = new RiskResult(
                new UserId("w1"), List.of(new UserId("w1")),
                Map.of("w1", Map.of("NA", 10, "EU", 5)),
                42,
                WinCondition.WIN
        );
        String json = objectMapper.writeValueAsString(original);
        GameResult deserialized = objectMapper.readValue(json, GameResult.class);
        assertThat(deserialized).isInstanceOf(RiskResult.class);
    }

    @Test
    @DisplayName("RouletteResult JSON with WIN condition produces winnerId from visitorId")
    void rouletteResultWinnerId() throws Exception {
        RouletteResult original = new RouletteResult(
                "visitor-1", 10, 1000, 800,
                List.of("17"), WinCondition.WIN
        );
        String json = objectMapper.writeValueAsString(original);
        GameResult deserialized = objectMapper.readValue(json, GameResult.class);
        assertThat(deserialized).isInstanceOf(RouletteResult.class);
        assertThat(deserialized.getWinnerId()).isEqualTo(new UserId("visitor-1"));
    }

    @Test
    @DisplayName("RouletteResult JSON with TIMEOUT condition produces null winnerId")
    void rouletteResultTimeoutNullWinner() throws Exception {
        RouletteResult original = new RouletteResult(
                "visitor-1", 5, 500, 300,
                List.of(), WinCondition.TIMEOUT
        );
        String json = objectMapper.writeValueAsString(original);
        GameResult deserialized = objectMapper.readValue(json, GameResult.class);
        assertThat(deserialized).isInstanceOf(RouletteResult.class);
        assertThat(deserialized.getWinnerId()).isNull();
        assertThat(deserialized.getWinnerIds()).isEmpty();
    }

    @Test
    @DisplayName("SlotResult JSON with WIN condition produces winnerId from visitorId")
    void slotResultWinnerId() throws Exception {
        SlotResult original = new SlotResult(
                "visitor-1", 100, 200, 150, 500, WinCondition.WIN
        );
        String json = objectMapper.writeValueAsString(original);
        GameResult deserialized = objectMapper.readValue(json, GameResult.class);
        assertThat(deserialized).isInstanceOf(SlotResult.class);
        assertThat(deserialized.getWinnerId()).isEqualTo(new UserId("visitor-1"));
    }

    @Test
    @DisplayName("JsonNode without type field falls back to default Implementation with basic fields")
    void jsonWithoutTypeFieldProducesAnonymousResult() throws Exception {
        // Simulating what happens when ChessResult is serialized WITHOUT type discriminator
        String jsonWithoutType = """
            {
              "winnerId": {"value": "w1"},
              "winnerIds": [{"value": "w1"}],
              "terminationReason": "checkmate",
              "finalFenState": "rnbqkbnr/...",
              "winCondition": "WIN"
            }
            """;

        // With default typing enabled, the existing local-server JacksonConfig would map
        // this to DefaultGameResult if no type field is present.
        GameResult deserialized = objectMapper.readValue(jsonWithoutType, GameResult.class);
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getWinnerId()).isEqualTo(new UserId("w1"));
        assertThat(deserialized.getWinCondition()).isEqualTo(WinCondition.WIN);
    }

    @Test
    @DisplayName("MqttPayloadSerializer includes type discriminator for GameResult subtypes")
    void mqttPayloadSerializerIncludesTypeDiscriminator() throws Exception {
        ChessResult result = new ChessResult(
                new UserId("w1"), List.of(new UserId("w1")),
                "mate", "fen", WinCondition.WIN
        );
        byte[] bytes = com.gameplatform.shared.mqtt.MqttPayloadSerializer.serialize(result);
        String json = new String(bytes);
        assertThat(json).contains("\"type\":\"CHESS\"");
        assertThat(json).contains("\"winnerId\":{\"value\":\"w1\"}");
    }
}
