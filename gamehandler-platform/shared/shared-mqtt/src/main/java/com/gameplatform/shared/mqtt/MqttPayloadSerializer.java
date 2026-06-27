package com.gameplatform.shared.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class MqttPayloadSerializer {

    @com.fasterxml.jackson.annotation.JsonTypeInfo(
        use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME,
        include = com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    @com.fasterxml.jackson.annotation.JsonSubTypes({
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.ChessResult.class, name = "CHESS"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.DartsResult.class, name = "DARTS"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.FoosballResult.class, name = "FOOSBALL"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.MonopolyResult.class, name = "MONOPOLY"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.RiskResult.class, name = "RISK"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.RouletteResult.class, name = "ROULETTE"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.SlotResult.class, name = "SLOT")
    })
    private interface GameResultMixIn {}

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .addMixIn(com.gameplatform.shared.domain.result.GameResult.class, GameResultMixIn.class)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
