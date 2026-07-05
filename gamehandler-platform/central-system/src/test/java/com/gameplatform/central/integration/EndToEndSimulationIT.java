package com.gameplatform.central.integration;

import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * FASE 5 — End-to-end simulation (scoped-down).
 *
 * <p>Plan reference: lines 189-198 of {@code risoluzione_comunicazioni_local_central.md}.
 * The original plan asks for a dual-context @SpringBootTest (central+local) with
 * Moquette embedded. This implementation is a single-context test (central only)
 * that simulates the local-server side via direct use-case invocation (the same
 * entry point {@code SyncController} calls on POST /internal/sync/receive) and
 * stubs the {@code LocalServerRegistryPort} (inherited from {@link ContractTestBase})
 * so no real outbound REST traffic occurs. It walks through the 8 steps from the
 * plan:</p>
 *
 * <ol>
 *   <li>Central context is up (H2 + WireMock stub available on demand).</li>
 *   <li>Local auto-registers (M7) — skipped here, already covered by
 *       {@code RegisterLocalServerAdapterTest}; the registry mock returns empty.</li>
 *   <li>Local emits GAME_SESSION_COMPLETED (M4) → assert totalSessions=1.</li>
 *   <li>Local emits GAME_SESSION_ABORTED (M5) → assert abortedSessions=1,
 *       totalSessions still 1 (aborted does NOT increment totalSessions).</li>
 *   <li>Local emits USER_REGISTERED (M1) → assert user row in users table.</li>
 *   <li>Central replicates user to local (M6) — simplified: actual REST push is
 *       covered by {@code LocalServerRestAdapterRetryTest}; here we only verify
 *       the central-side registration succeeded without exception.</li>
 *   <li>Idempotency: re-send the SAME completedEventId → central dedup →
 *       counts unchanged (exactly-once inbound).</li>
 *   <li>Poison: malformed JSON payload → batch does not abort, poison event is
 *       marked processed (poison isolation via REQUIRES_NEW + markProcessed).</li>
 * </ol>
 *
 * <p>Simplifications vs. the plan's dual-context E2E:</p>
 * <ul>
 *   <li>Step 2 (M7 auto-registration) is skipped — the local-server Spring context
 *       is not booted; the registry is mocked. Already covered by adapter tests.</li>
 *   <li>Step 6 (M6 replication push) is not asserted on the wire — the registry
 *       returns an empty active-server list so the replication scheduler is a
 *       no-op. Wire-level push is covered by {@code LocalServerRestAdapterRetryTest}.</li>
 *   <li>WireMock is started by the base class but not actively stubbed for this
 *       test (no outbound HTTP is expected given the empty registry).</li>
 * </ul>
 */
@DisplayName("End-to-end simulation (FASE 5)")
class EndToEndSimulationIT extends ContractTestBase {

    @Autowired
    ReceiveSyncDataUseCase receiveSyncDataUseCase;

    @Test
    @DisplayName("Steps 1-8: bootstrap → session-completed → session-aborted → user-registered → idempotency → poison")
    void fullFlowSimulated() {
        // Setup: localServerRegistryPort (inherited @MockBean from ContractTestBase)
        // returns empty → no real local server, replication scheduler is a no-op.
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of());

        // Step 1: central context is up (assumed by @SpringBootTest via ContractTestBase)

        // Step 2: M7 auto-registration — simplified (skipped); see class javadoc.

        // Step 3: M4 — local emits GAME_SESSION_COMPLETED
        String completedEventId = UUID.randomUUID().toString();
        sendEvent(completedEventId, "GAME_SESSION_COMPLETED",
            "{\"eventId\":\"" + completedEventId + "\",\"occurredAt\":\"2026-07-05T12:00:00Z\","
                + "\"sessionId\":\"s-1\",\"gameType\":\"CHESS\",\"durationSeconds\":120,"
                + "\"status\":\"COMPLETED\",\"resultJson\":null}");

        Integer totalSessions = jdbcTemplate.queryForObject(
            "SELECT total_sessions FROM aggregated_statistics WHERE building_id='building-test' AND game_type='CHESS'",
            Integer.class);
        assertThat(totalSessions).isEqualTo(1);

        // Step 4: M5 — local emits GAME_SESSION_ABORTED (same building/game/period)
        String abortedEventId = UUID.randomUUID().toString();
        sendEvent(abortedEventId, "GAME_SESSION_ABORTED",
            "{\"eventId\":\"" + abortedEventId + "\",\"occurredAt\":\"2026-07-05T12:00:00Z\","
                + "\"sessionId\":\"s-2\",\"gameType\":\"CHESS\",\"durationSeconds\":0,"
                + "\"status\":\"ABORTED\",\"stopReason\":\"TIMEOUT\"}");

        Integer aborted = jdbcTemplate.queryForObject(
            "SELECT total_aborted_sessions FROM aggregated_statistics WHERE building_id='building-test' AND game_type='CHESS'",
            Integer.class);
        assertThat(aborted).isEqualTo(1);
        Integer totalSessionsAfterAbort = jdbcTemplate.queryForObject(
            "SELECT total_sessions FROM aggregated_statistics WHERE building_id='building-test' AND game_type='CHESS'",
            Integer.class);
        assertThat(totalSessionsAfterAbort).isEqualTo(1); // still 1, not 2 — aborted does not increment totalSessions

        // Step 5: M1 — local emits USER_REGISTERED
        String userEventId = UUID.randomUUID().toString();
        sendEvent(userEventId, "USER_REGISTERED",
            "{\"userId\":\"u-1\",\"username\":\"alice\",\"email\":\"a@example.com\","
                + "\"hashedPassword\":\"$2a$10$abc\",\"roles\":[\"USER\"],"
                + "\"createdAt\":\"2026-07-05T10:00:00Z\"}");

        Integer userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE username='alice'", Integer.class);
        assertThat(userCount).isEqualTo(1);

        // Step 6: M6 — central replicates user to local (UserReplicationSchedulerService).
        // Simplified: the registry returns an empty active-server list, so the
        // replication scheduler has nothing to push. Actual REST push is covered by
        // LocalServerRestAdapterRetryTest. Just verify no exception occurred above.

        // Step 7: idempotency — re-send the SAME completedEventId
        sendEvent(completedEventId, "GAME_SESSION_COMPLETED",
            "{\"eventId\":\"" + completedEventId + "\",\"occurredAt\":\"2026-07-05T12:00:00Z\","
                + "\"sessionId\":\"s-1\",\"gameType\":\"CHESS\",\"durationSeconds\":120,"
                + "\"status\":\"COMPLETED\",\"resultJson\":null}");

        Integer totalSessionsAfterDup = jdbcTemplate.queryForObject(
            "SELECT total_sessions FROM aggregated_statistics WHERE building_id='building-test' AND game_type='CHESS'",
            Integer.class);
        assertThat(totalSessionsAfterDup).isEqualTo(1); // still 1 — exactly-once

        // Step 8: poison — malformed event (invalid JSON)
        String poisonEventId = UUID.randomUUID().toString();
        sendEvent(poisonEventId, "GAME_SESSION_COMPLETED", "not-a-valid-json-payload");

        // Assert: the poison event was marked processed (no batch abort — poison isolation)
        Integer poisonProcessed = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM processed_events WHERE event_id=?", Integer.class, poisonEventId);
        assertThat(poisonProcessed).isEqualTo(1);
        // And the previous stats are unchanged
        Integer totalSessionsAfterPoison = jdbcTemplate.queryForObject(
            "SELECT total_sessions FROM aggregated_statistics WHERE building_id='building-test' AND game_type='CHESS'",
            Integer.class);
        assertThat(totalSessionsAfterPoison).isEqualTo(1);
    }

    private void sendEvent(String eventId, String eventType, String payload) {
        OutboxEventDto event = new OutboxEventDto(eventId, eventType, payload, Instant.now());
        SyncPayloadDto batch = new SyncPayloadDto("building-test", List.of(event));
        receiveSyncDataUseCase.receiveSyncPayload(batch);
    }
}
