package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.e2e.harness.DualContextTestBase;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B7 — Receiving the same {@code GAME_SESSION_COMPLETED} event (same eventId)
 * twice through the central {@code ReceiveSyncDataUseCase} must not double-count
 * the statistics. The central {@code processed_events} table provides the
 * exactly-once guard: the second receive is skipped.
 */
@DisplayName("B7: Central idempotency — same eventId re-receive does not duplicate stats")
class B7CentralIdempotencySameEventIdReReceiveTest extends DualContextTestBase {

    @Test
    @DisplayName("Receiving the same GAME_SESSION_COMPLETED twice keeps total_sessions=1")
    void centralIdempotencySameEventIdReReceive() {
        // 1. Register building-1 at central
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        // 2. Construct and send a GAME_SESSION_COMPLETED event for building-1/CHESS
        String eventId = UUID.randomUUID().toString();
        String occurredAt = "2026-07-05T12:00:00Z";
        String payload = "{\"eventId\":\"" + eventId + "\","
                + "\"occurredAt\":\"" + occurredAt + "\","
                + "\"sessionId\":\"sess-chess\","
                + "\"gameType\":\"CHESS\","
                + "\"durationSeconds\":120,"
                + "\"status\":\"COMPLETED\","
                + "\"resultJson\":null}";
        OutboxEventDto event = new OutboxEventDto(eventId, "GAME_SESSION_COMPLETED", payload, Instant.parse(occurredAt));
        SyncPayloadDto batch = new SyncPayloadDto("building-1", List.of(event));
        centralBean(ReceiveSyncDataUseCase.class).receiveSyncPayload(batch);

        // 3. Assert central aggregated_statistics has total_sessions=1 for building-1/CHESS
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT total_sessions FROM aggregated_statistics WHERE building_id='building-1' AND game_type='CHESS'",
                Integer.class))
                .as("total_sessions=1 after first receive")
                .isEqualTo(1);

        // 4. Re-send the SAME batch (same eventId)
        centralBean(ReceiveSyncDataUseCase.class).receiveSyncPayload(batch);

        // 5. Assert central aggregated_statistics still has total_sessions=1 (no duplicate)
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT total_sessions FROM aggregated_statistics WHERE building_id='building-1' AND game_type='CHESS'",
                Integer.class))
                .as("total_sessions still 1 after duplicate receive (idempotent)")
                .isEqualTo(1);

        // 6. Assert central processed_events has exactly 1 row for eventId
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id=?", Integer.class, eventId))
                .as("processed_events has exactly 1 row for the eventId")
                .isEqualTo(1);
    }
}