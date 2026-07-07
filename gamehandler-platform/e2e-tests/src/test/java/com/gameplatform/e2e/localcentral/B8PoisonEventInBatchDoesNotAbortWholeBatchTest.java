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
 * B8 — A batch of 3 sync events containing a poison event (malformed JSON
 * payload) must not abort the whole batch. The two valid events are processed
 * and their statistics are updated; the poison event is marked processed
 * (poison isolation) without aborting the loop.
 */
@DisplayName("B8: Poison event in batch does not abort whole batch")
class B8PoisonEventInBatchDoesNotAbortWholeBatchTest extends DualContextTestBase {

    @Test
    @DisplayName("3-event batch with 1 poison: 2 valid events processed, poison marked processed, batch not aborted")
    void poisonEventInBatchDoesNotAbortWholeBatch() {
        // 1. Register building-1 at central
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        // 2. Construct a batch of 3 events
        String occurredAt = "2026-07-05T12:00:00Z";

        // Event 1: valid GAME_SESSION_COMPLETED (CHESS)
        String e1 = UUID.randomUUID().toString();
        OutboxEventDto event1 = new OutboxEventDto(e1, "GAME_SESSION_COMPLETED",
                "{\"eventId\":\"" + e1 + "\",\"occurredAt\":\"" + occurredAt + "\","
                        + "\"sessionId\":\"s-chess\",\"gameType\":\"CHESS\",\"durationSeconds\":60,"
                        + "\"status\":\"COMPLETED\",\"resultJson\":null}",
                Instant.parse(occurredAt));

        // Event 2: valid GAME_SESSION_COMPLETED (DARTS)
        String e2 = UUID.randomUUID().toString();
        OutboxEventDto event2 = new OutboxEventDto(e2, "GAME_SESSION_COMPLETED",
                "{\"eventId\":\"" + e2 + "\",\"occurredAt\":\"" + occurredAt + "\","
                        + "\"sessionId\":\"s-darts\",\"gameType\":\"DARTS\",\"durationSeconds\":30,"
                        + "\"status\":\"COMPLETED\",\"resultJson\":null}",
                Instant.parse(occurredAt));

        // Event 3: poison — invalid JSON payload
        String e3 = UUID.randomUUID().toString();
        OutboxEventDto event3 = new OutboxEventDto(e3, "GAME_SESSION_COMPLETED",
                "not-a-valid-json", Instant.parse(occurredAt));

        SyncPayloadDto batch = new SyncPayloadDto("building-1", List.of(event1, event2, event3));

        // 3. Call receiveSyncPayload
        centralBean(ReceiveSyncDataUseCase.class).receiveSyncPayload(batch);

        // 4. Assert central aggregated_statistics has total_sessions=1 for building-1/CHESS
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT total_sessions FROM aggregated_statistics WHERE building_id='building-1' AND game_type='CHESS'",
                Integer.class))
                .as("CHESS total_sessions=1 (valid event processed)")
                .isEqualTo(1);

        // Assert central aggregated_statistics has total_sessions=1 for building-1/DARTS
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT total_sessions FROM aggregated_statistics WHERE building_id='building-1' AND game_type='DARTS'",
                Integer.class))
                .as("DARTS total_sessions=1 (valid event processed)")
                .isEqualTo(1);

        // 5. Assert central processed_events has all 3 eventIds (poison was marked processed)
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id=?", Integer.class, e1))
                .as("processed_events has e1")
                .isEqualTo(1);
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id=?", Integer.class, e2))
                .as("processed_events has e2")
                .isEqualTo(1);
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id=?", Integer.class, e3))
                .as("processed_events has e3 (poison marked processed, batch not aborted)")
                .isEqualTo(1);
    }
}