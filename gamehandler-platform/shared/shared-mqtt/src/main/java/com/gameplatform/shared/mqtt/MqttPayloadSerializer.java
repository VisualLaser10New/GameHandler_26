package com.gameplatform.shared.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;

public final class MqttPayloadSerializer {

   @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.ChessResult.class, name = "CHESS"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.DartsResult.class, name = "DARTS"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.FoosballResult.class, name = "FOOSBALL"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.MonopolyResult.class, name = "MONOPOLY"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.RiskResult.class, name = "RISK"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.RouletteResult.class, name = "ROULETTE"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.SlotResult.class, name = "SLOT"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.TeamResult.class, name = "TEAM")
    })
    private interface GameResultMixIn {}

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .addMixIn(com.gameplatform.shared.domain.result.GameResult.class, GameResultMixIn.class)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private MqttPayloadSerializer() {}

    public static byte[] serialize(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object to JSON payload", e);
        }
    }

    public static <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            return objectMapper.readValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON payload to " + clazz.getSimpleName(), e);
        }
    }
}
