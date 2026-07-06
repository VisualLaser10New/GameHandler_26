package com.gameplatform.shared.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R4: verifies the new 6-field {@link UserSyncDto} record round-trips through JSON and that a
 * legacy 4-field payload (no {@code email}/{@code occurredAt}) deserialises with nulls instead
 * of failing — keeping the wire contract backward compatible with older central-system nodes.
 */
class UserSyncDtoSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void sixFieldRecordRoundTripsThroughJson() throws Exception {
        UserSyncDto original = new UserSyncDto(
                "user-1", "alice", "alice@example.com", "hashed-pw",
                List.of("USER", "PLAYER"), Instant.parse("2026-01-01T00:00:00Z"));

        String json = mapper.writeValueAsString(original);
        UserSyncDto roundTripped = mapper.readValue(json, UserSyncDto.class);

        assertThat(roundTripped).isEqualTo(original);
        assertThat(roundTripped.email()).isEqualTo("alice@example.com");
        assertThat(roundTripped.occurredAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(roundTripped.roles()).containsExactly("USER", "PLAYER");
    }

    @Test
    void legacyFourFieldJsonDeserialisesWithNullEmailAndNullOccurredAt() throws Exception {
        String legacyJson = "{\"userId\":\"user-1\",\"username\":\"alice\",\"hashedPassword\":\"hashed-pw\",\"roles\":[\"USER\"]}";

        UserSyncDto deserialised = mapper.readValue(legacyJson, UserSyncDto.class);

        assertThat(deserialised.userId()).isEqualTo("user-1");
        assertThat(deserialised.username()).isEqualTo("alice");
        assertThat(deserialised.hashedPassword()).isEqualTo("hashed-pw");
        assertThat(deserialised.roles()).containsExactly("USER");
        assertThat(deserialised.email())
                .as("legacy payloads without email must deserialise to null, not fail")
                .isNull();
        assertThat(deserialised.occurredAt())
                .as("legacy payloads without occurredAt must deserialise to null, not fail")
                .isNull();
    }

    @Test
    void backwardCompatFourArgConstructorMatchesNullEmailAndNullOccurredAt() {
        UserSyncDto legacy = new UserSyncDto("user-1", "alice", "hashed-pw", List.of("USER"));

        assertThat(legacy.email()).isNull();
        assertThat(legacy.occurredAt()).isNull();
        assertThat(legacy.roles()).containsExactly("USER");
        assertThat(legacy.userId()).isEqualTo("user-1");
        assertThat(legacy.username()).isEqualTo("alice");
        assertThat(legacy.hashedPassword()).isEqualTo("hashed-pw");
    }
}