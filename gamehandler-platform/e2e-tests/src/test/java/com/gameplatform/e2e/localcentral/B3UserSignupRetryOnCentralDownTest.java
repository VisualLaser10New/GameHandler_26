package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.e2e.harness.DualContextTestBase;
import com.gameplatform.local.application.service.LocalSignupService;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B3 — A user signed up on the local server is synced to central. Re-sending
 * the same event (same eventId) to the central {@code ReceiveSyncDataUseCase}
 * must not create a duplicate user — the central dedup via
 * {@code processed_events} ensures exactly-once ingestion.
 *
 * <p>Uses the same H2 JSON double-encoding workaround as B1: the clean payload
 * is fed directly into the central receive use case (see B1's javadoc for the
 * rationale).</p>
 */
@DisplayName("B3: User signup retry on central down does not duplicate")
class B3UserSignupRetryOnCentralDownTest extends DualContextTestBase {

    @Test
    @DisplayName("Re-sending the same USER_REGISTERED eventId to central is idempotent")
    void retryOnCentralDownDoesNotDuplicate() throws Exception {
        // 1. Register building-1 at central
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        // 2. Signup on local
        localBean(LocalSignupService.class).register("alice", "password", "alice@example.com");

        // 3. Assert local outbox has 1 PENDING USER_REGISTERED
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='PENDING' AND event_type='USER_REGISTERED'",
                Integer.class))
                .isEqualTo(1);

        // 4. Sync to central (work around H2 JSON double-encoding — see B1)
        String eventId = pushLocalOutboxToCentral();

        // 5. Assert central has alice (count=1) and local outbox is SENT
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username='alice'", Integer.class))
                .as("central has alice after sync")
                .isEqualTo(1);
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='SENT'", Integer.class))
                .as("local outbox is SENT after sync")
                .isEqualTo(1);

        // 6. Re-send the same eventId to central via receiveSyncPayload
        String rawPayload = localJdbcTemplate.queryForObject(
                "SELECT payload FROM outbox_events WHERE id=?", String.class, eventId);
        String payload = rawPayload;
        if (payload != null && payload.startsWith("\"")) {
            payload = objectMapper.readTree(rawPayload).asText();
        }
        OutboxEventDto eventDto = new OutboxEventDto(eventId, "USER_REGISTERED", payload, Instant.now());
        SyncPayloadDto batch = new SyncPayloadDto("building-1", List.of(eventDto));
        centralBean(ReceiveSyncDataUseCase.class).receiveSyncPayload(batch);

        // 7. Assert central users still has exactly 1 alice (no duplicate)
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username='alice'", Integer.class))
                .as("central still has exactly 1 alice after duplicate receive")
                .isEqualTo(1);

        // 8. Assert central processed_events has exactly 1 row for that eventId
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id=?", Integer.class, eventId))
                .as("processed_events has exactly 1 row for the eventId")
                .isEqualTo(1);
    }

    private String pushLocalOutboxToCentral() throws Exception {
        String eventId = localJdbcTemplate.queryForObject(
                "SELECT id FROM outbox_events WHERE event_type='USER_REGISTERED' AND status='PENDING'",
                String.class);
        String rawPayload = localJdbcTemplate.queryForObject(
                "SELECT payload FROM outbox_events WHERE id=?", String.class, eventId);
        String payload = rawPayload;
        if (payload != null && payload.startsWith("\"")) {
            payload = objectMapper.readTree(rawPayload).asText();
        }
        OutboxEventDto eventDto = new OutboxEventDto(eventId, "USER_REGISTERED", payload, Instant.now());
        SyncPayloadDto batch = new SyncPayloadDto("building-1", List.of(eventDto));
        centralBean(ReceiveSyncDataUseCase.class).receiveSyncPayload(batch);
        localJdbcTemplate.update(
                "UPDATE outbox_events SET status='SENT', sent_at=? WHERE id=?",
                Timestamp.from(Instant.now()), eventId);
        return eventId;
    }
}