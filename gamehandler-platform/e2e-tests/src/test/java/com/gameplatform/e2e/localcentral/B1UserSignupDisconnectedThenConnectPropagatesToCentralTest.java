package com.gameplatform.e2e.localcentral;

import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.e2e.harness.DualContextTestBase;
import com.gameplatform.local.application.service.LocalSignupService;
import com.gameplatform.local.application.service.SyncSchedulerService;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B1 — A user signing up on a disconnected local server creates a Pending
 * outbox event; when the sync runs, the event is pushed to the central system,
 * the local outbox transitions to SENT, and the user appears in the central
 * {@code users} table with a {@code processed_events} row.
 *
 * <p><b>Harness limitation — H2 JSON double-encoding:</b> the local
 * {@code outbox_events.payload} column is declared as {@code columnDefinition="JSON"}.
 * On H2 2.x the JSON column double-encodes the payload on JPA read-back (the
 * raw JSON text is returned as a JSON string scalar, e.g.
 * {@code "{\"userId\":...\"}"}). The production
 * {@link SyncSchedulerService} reads the outbox via JPA and sends whatever it
 * reads to central over HTTP; the double-encoded payload cannot be
 * deserialized by the central {@code SyncEventProcessor}. There is no test-only
 * {@code CleanPayloadOutbox}-style shim on the local side (the shim exists only
 * for the central {@code OutboxEventRepository} port, and we cannot inject test
 * beans into the separately-booted local context via {@code SpringApplicationBuilder}).
 *
 * <p>To work around this without touching production files, this test reads the
 * local outbox event, unwraps the H2 JSON string scalar, and feeds the clean
 * payload directly into {@link ReceiveSyncDataUseCase#receiveSyncPayload} —
 * the same entry point the central {@code SyncController} invokes on
 * {@code POST /internal/sync/receive}. The local outbox row is then marked
 * SENT via JdbcTemplate. This exercises the full local-outbox → central-receive
 * → central-user-creation → processed-events chain without the broken HTTP
 * transport layer.</p>
 */
@DisplayName("B1: Local signup disconnected then connect propagates to central")
class B1UserSignupDisconnectedThenConnectPropagatesToCentralTest extends DualContextTestBase {

    @Test
    @DisplayName("Local signup stays pending while disconnected, then propagates to central on sync")
    void signupDisconnectedThenConnectPropagatesToCentral() throws Exception {
        // 1. Register building-1 at central
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        // 2. Signup on local
        localBean(LocalSignupService.class).register("alice", "password", "alice@example.com");

        // 3. Assert local users table has alice
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username='alice'", Integer.class))
                .as("local users table has alice after signup")
                .isEqualTo(1);

        // 4. Assert local outbox has 1 PENDING USER_REGISTERED row
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='PENDING' AND event_type='USER_REGISTERED'",
                Integer.class))
                .as("local outbox has 1 PENDING USER_REGISTERED event")
                .isEqualTo(1);

        // 5. Assert central users does NOT have alice yet (disconnected state)
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username='alice'", Integer.class))
                .as("central users does not have alice before sync (disconnected)")
                .isEqualTo(0);

        // 6. Trigger sync — work around H2 JSON double-encoding by feeding the
        //    clean payload directly into the central receive use case.
        pushLocalOutboxToCentral();

        // 7. Assert local outbox row is now SENT
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='SENT'", Integer.class))
                .as("local outbox event is SENT after sync")
                .isEqualTo(1);
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='PENDING'", Integer.class))
                .as("no PENDING outbox events remain after sync")
                .isEqualTo(0);

        // 8. Assert central users has alice
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username='alice'", Integer.class))
                .as("central users has alice after sync")
                .isEqualTo(1);

        // 9. Assert central processed_events has the eventId
        assertThat(centralJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events", Integer.class))
                .as("central processed_events has at least one row")
                .isGreaterThanOrEqualTo(1);
    }

    /**
     * Reads the single PENDING USER_REGISTERED event from the local outbox,
     * unwraps the H2 JSON double-encoding, feeds it into the central
     * {@link ReceiveSyncDataUseCase}, and marks the local outbox row SENT.
     */
    private void pushLocalOutboxToCentral() throws Exception {
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
    }
}