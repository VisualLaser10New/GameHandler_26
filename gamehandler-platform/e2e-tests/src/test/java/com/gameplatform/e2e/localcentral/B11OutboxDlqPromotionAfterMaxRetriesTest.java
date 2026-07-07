package com.gameplatform.e2e.localcentral;

import com.gameplatform.e2e.harness.DualContextTestBase;
import com.gameplatform.local.application.service.LocalSignupService;
import com.gameplatform.local.application.service.OutboxDlqPromotionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B11 — A FAILED outbox event with retry_count at the threshold is promoted to
 * the dead-letter queue by {@code OutboxDlqPromotionService.promoteFailedToDlq()}.
 * The event is removed from {@code outbox_events} and appears in
 * {@code outbox_dead_letter}.
 */
@DisplayName("B11: Outbox DLQ promotion after max retries")
class B11OutboxDlqPromotionAfterMaxRetriesTest extends DualContextTestBase {

    @Test
    @DisplayName("FAILED outbox event with retry_count=10 is promoted to DLQ and removed from outbox")
    void outboxDlqPromotionAfterMaxRetries() {
        // 1. Register building-1 at central
        registerBuildingAtCentral("building-1", "http://localhost:" + localPort);

        // 2. Signup on local: alice → PENDING outbox event
        localBean(LocalSignupService.class).register("alice", "password", "alice@example.com");
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='PENDING'", Integer.class))
                .as("local outbox has 1 PENDING event from signup")
                .isEqualTo(1);

        // 3. Directly insert a FAILED outbox event with retry_count=10
        String failedEventId = UUID.randomUUID().toString();
        localJdbcTemplate.update(
                "INSERT INTO outbox_events (id, event_type, payload, status, created_at, sent_at, retry_count) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                failedEventId,
                "USER_REGISTERED",
                "{\"userId\":\"dlq-test\",\"username\":\"dlq\",\"email\":\"dlq@x.com\"}",
                "FAILED",
                Timestamp.from(Instant.now()),
                null,
                10);

        // 4. Call promoteFailedToDlq() — moves FAILED events to DLQ
        localBean(OutboxDlqPromotionService.class).promoteFailedToDlq();

        // 5. Assert local outbox_dead_letter has 1 row
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_dead_letter", Integer.class))
                .as("outbox_dead_letter has 1 row after promotion")
                .isEqualTo(1);

        // 6. Assert local outbox_events does NOT have the FAILED event anymore
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE id=?", Integer.class, failedEventId))
                .as("outbox_events no longer has the FAILED event")
                .isEqualTo(0);

        // The PENDING alice event should still be there (only FAILED events are promoted)
        assertThat(localJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE status='PENDING'", Integer.class))
                .as("PENDING alice event is still in outbox (not promoted)")
                .isEqualTo(1);
    }
}