package com.gameplatform.central.integration;

import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FASE 5 — scoped-down message contract tests for M1..M5 (local → central
 * outbox events). For each event type the test feeds a {@link SyncPayloadDto}
 * into the central {@link ReceiveSyncDataUseCase} (the same entry point the
 * {@code SyncController} calls when a local server POSTs /internal/sync/receive),
 * then asserts that:
 * <ol>
 *   <li>the event id is recorded in {@code processed_events} (idempotency
 *       audit trail — proves the central successfully consumed the wire
 *       payload and marked it deduped); and</li>
 *   <li>the side-effect table for the event type was populated
 *       ({@code users} for M1, {@code aggregated_statistics} for M2–M5).</li>
 * </ol>
 *
 * <p>This deliberately does NOT exercise the HTTP wire (WireMock) for these
 * inbound events: the {@code SyncController} HTTP layer is already covered by
 * {@code SyncControllerTest}. The wire-level format tests for M6/M7 are also
 * already covered by {@code LocalServerRestAdapterTest} (central-out) and
 * {@code RegisterLocalServerAdapterTest} (local-out). The remaining gap, the
 * full dual-context {@code EndToEndSimulationIT}, is left as future work.</p>
 *
 * <p>GameType enum literals used in payloads ({@code CHESS, DARTS, MONOPOLY})
 * are drawn from the production {@code com.gameplatform.shared.domain.model.GameType}
 * enum — the literals {@code BOWLING/BOWLLING} mentioned in the original plan
 * are NOT valid enum values and were replaced with {@code MONOPOLY}.</p>
 */
@DisplayName("Message Contract — M1..M5 (local → central events)")
class MessageContractIT extends ContractTestBase {

    @Autowired
    ReceiveSyncDataUseCase receiveSyncDataUseCase;

    static Stream<Arguments> events() {
        Instant occurredAt = Instant.parse("2026-07-05T12:00:00Z");
        return Stream.of(
            // M1: USER_REGISTERED — central inserts into users table.
            Arguments.of("M1 USER_REGISTERED",
                "USER_REGISTERED",
                "{\"userId\":\"u-1\",\"username\":\"alice\",\"email\":\"a@example.com\","
                    + "\"hashedPassword\":\"$2a$10$abc\",\"roles\":[\"USER\"],"
                    + "\"createdAt\":\"2026-07-05T10:00:00Z\"}",
                "users"),
            // M4: GAME_SESSION_COMPLETED — central increments total_sessions.
            Arguments.of("M4 GAME_SESSION_COMPLETED",
                "GAME_SESSION_COMPLETED",
                "{\"eventId\":\"e-2\",\"occurredAt\":\"" + occurredAt + "\","
                    + "\"sessionId\":\"s-1\",\"gameType\":\"CHESS\","
                    + "\"durationSeconds\":120,\"status\":\"COMPLETED\",\"resultJson\":null}",
                "aggregated_statistics"),
            // M5: GAME_SESSION_ABORTED — central increments total_aborted_sessions only.
            Arguments.of("M5 GAME_SESSION_ABORTED",
                "GAME_SESSION_ABORTED",
                "{\"eventId\":\"e-3\",\"occurredAt\":\"" + occurredAt + "\","
                    + "\"sessionId\":\"s-2\",\"gameType\":\"DARTS\","
                    + "\"durationSeconds\":0,\"status\":\"ABORTED\",\"stopReason\":\"TIMEOUT\"}",
                "aggregated_statistics"),
            // M2: RESERVATION_CREATED — central increments total_reservations.
            Arguments.of("M2 RESERVATION_CREATED",
                "RESERVATION_CREATED",
                "{\"eventId\":\"e-4\",\"occurredAt\":\"" + occurredAt + "\","
                    + "\"reservationId\":\"r-1\",\"gameId\":\"g-1\",\"userId\":\"u-1\","
                    + "\"buildingId\":\"building-test\",\"gameType\":\"MONOPOLY\"}",
                "aggregated_statistics"),
            // M3: RESERVATION_CANCELLED — central decrements total_reservations.
            Arguments.of("M3 RESERVATION_CANCELLED",
                "RESERVATION_CANCELLED",
                "{\"eventId\":\"e-5\",\"occurredAt\":\"" + occurredAt + "\","
                    + "\"reservationId\":\"r-1\",\"gameId\":\"g-1\",\"userId\":\"u-1\","
                    + "\"buildingId\":\"building-test\",\"gameType\":\"MONOPOLY\"}",
                "aggregated_statistics")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    void centralProcessesEventAndMarksProcessed(String name, String eventType, String payload, String affectedTable) {
        String eventId = UUID.randomUUID().toString();
        OutboxEventDto event = new OutboxEventDto(eventId, eventType, payload, Instant.now());
        SyncPayloadDto batch = new SyncPayloadDto("building-test", List.of(event));

        receiveSyncDataUseCase.receiveSyncPayload(batch);

        // Assertion 1: succeeded event was recorded in processed_events (idempotency audit).
        Integer processedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM processed_events WHERE event_id = ?", Integer.class, eventId);
        assertThat(processedCount).isEqualTo(1);

        // Assertion 2: a row was inserted into the affected side-effect table.
        if ("users".equals(affectedTable)) {
            // M1: central registered the synced user.
            Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = 'alice'", Integer.class);
            assertThat(userCount).isEqualTo(1);
        } else {
            // M2/M3/M4/M5: aggregated_statistics row was created for this building.
            Integer statsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aggregated_statistics WHERE building_id = 'building-test'",
                Integer.class);
            assertThat(statsCount).isGreaterThan(0);

            // M5-specific invariant: abortedSessions incremented, totalSessions stays 0.
            if ("M5 GAME_SESSION_ABORTED".equals(name)) {
                Integer aborted = jdbcTemplate.queryForObject(
                    "SELECT total_aborted_sessions FROM aggregated_statistics "
                        + "WHERE building_id = 'building-test' AND game_type = 'DARTS'",
                    Integer.class);
                assertThat(aborted).isGreaterThan(0);
                Integer totalSessions = jdbcTemplate.queryForObject(
                    "SELECT total_sessions FROM aggregated_statistics "
                        + "WHERE building_id = 'building-test' AND game_type = 'DARTS'",
                    Integer.class);
                assertThat(totalSessions).isEqualTo(0);
            }
            // M4-specific invariant: totalSessions incremented for the COMPLETED session.
            if ("M4 GAME_SESSION_COMPLETED".equals(name)) {
                Integer totalSessions = jdbcTemplate.queryForObject(
                    "SELECT total_sessions FROM aggregated_statistics "
                        + "WHERE building_id = 'building-test' AND game_type = 'CHESS'",
                    Integer.class);
                assertThat(totalSessions).isEqualTo(1);
            }
        }
    }
}
