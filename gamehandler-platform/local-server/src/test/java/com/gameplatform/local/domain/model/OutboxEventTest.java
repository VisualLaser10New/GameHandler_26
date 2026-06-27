package com.gameplatform.local.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    private static final Instant CREATED = Instant.parse("2026-06-25T10:00:00Z");

    @Nested
    class Construction {

        @Test
        void shouldCreateOutboxEventSuccessfully() {
            OutboxEvent event = new OutboxEvent("evt-1", "USER_REGISTERED",
                    "{\"username\":\"john\"}", "PENDING", CREATED, null, 0);

            assertThat(event.getId()).isEqualTo("evt-1");
            assertThat(event.getEventType()).isEqualTo("USER_REGISTERED");
            assertThat(event.getPayload()).isEqualTo("{\"username\":\"john\"}");
            assertThat(event.getStatus()).isEqualTo("PENDING");
            assertThat(event.getCreatedAt()).isEqualTo(CREATED);
            assertThat(event.getSentAt()).isNull();
            assertThat(event.getRetryCount()).isZero();
        }

        @Test
        void shouldAcceptBlankPayload() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "", "PENDING", CREATED, null, 0);
            assertThat(event.getPayload()).isEmpty();
        }

        @Test
        void shouldAcceptNullSentAt() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "PENDING", CREATED, null, 0);
            assertThat(event.getSentAt()).isNull();
        }

        @Test
        void shouldAcceptNegativeRetryCount() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "PENDING", CREATED, null, -5);
            assertThat(event.getRetryCount()).isEqualTo(-5);
        }

        @Test
        void shouldAcceptLargeRetryCount() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "PENDING", CREATED, null,
                    Integer.MAX_VALUE);
            assertThat(event.getRetryCount()).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        void shouldRejectInvalidIdEventTypeStatusAndCreatedAt() {
            assertThatThrownBy(() -> new OutboxEvent(null, "T", "{}", "PENDING", CREATED, null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new OutboxEvent("  ", "T", "{}", "PENDING", CREATED, null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new OutboxEvent("id", null, "{}", "PENDING", CREATED, null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new OutboxEvent("id", "  ", "{}", "PENDING", CREATED, null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new OutboxEvent("id", "T", null, "PENDING", CREATED, null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new OutboxEvent("id", "T", "{}", null, CREATED, null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new OutboxEvent("id", "T", "{}", "  ", CREATED, null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new OutboxEvent("id", "T", "{}", "PENDING", null, null, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldAcceptAnyNonBlankStatusString() {
            OutboxEvent sent = new OutboxEvent("id", "T", "{}", "SENT", CREATED, null, 0);
            assertThat(sent.getStatus()).isEqualTo("SENT");
            OutboxEvent failed = new OutboxEvent("id", "T", "{}", "FAILED", CREATED, null, 10);
            assertThat(failed.getStatus()).isEqualTo("FAILED");
        }
    }

    @Nested
    class MarkAsSent {

        @Test
        void shouldMarkAsSentWithProvidedInstant() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "PENDING", CREATED, null, 0);
            Instant sent = CREATED.plusSeconds(10);

            event.markAsSent(sent);

            assertThat(event.getStatus()).isEqualTo("SENT");
            assertThat(event.getSentAt()).isEqualTo(sent);
        }

        @Test
        void shouldMarkAsSentUsingNowWhenNoInstantProvided() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "PENDING", CREATED, null, 0);
            Instant before = Instant.now();

            event.markAsSent();

            Instant after = Instant.now();
            assertThat(event.getStatus()).isEqualTo("SENT");
            assertThat(event.getSentAt()).isBetween(before, after);
        }

        @Test
        void markAsSentIsPermissiveAndCanOverrideFailedStatus() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "FAILED", CREATED, null, 10);
            event.markAsSent(CREATED.plusSeconds(1));
            assertThat(event.getStatus()).isEqualTo("SENT");
        }

        @Test
        void markAsSentCanOverrideAnAlreadySentEvent() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "PENDING", CREATED, null, 0);
            Instant first = CREATED.plusSeconds(1);
            Instant second = CREATED.plusSeconds(2);
            event.markAsSent(first);
            event.markAsSent(second);
            assertThat(event.getSentAt()).isEqualTo(second);
            assertThat(event.getStatus()).isEqualTo("SENT");
        }

        @Test
        void markAsSentAfterIncrementRetryBeyondThresholdRescuesTheEvent() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "PENDING", CREATED, null, 0);
            for (int i = 0; i < 10; i++) {
                event.incrementRetry();
            }
            assertThat(event.hasFailed()).isTrue();
            event.markAsSent(CREATED.plusSeconds(1));
            assertThat(event.hasFailed()).isFalse();
            assertThat(event.getStatus()).isEqualTo("SENT");
        }

        @Test
        void markAsSentDoesNotResetRetryCount() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "PENDING", CREATED, null, 0);
            event.incrementRetry();
            event.markAsSent(CREATED.plusSeconds(1));
            assertThat(event.getRetryCount()).isEqualTo(1);
        }
    }

    @Nested
    class RetryAndFailure {

        @Test
        void shouldIncrementRetryAndMarkFailedExactlyAtTenthRetry() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "PENDING", CREATED, null, 0);

            for (int i = 1; i <= 9; i++) {
                event.incrementRetry();
                assertThat(event.getRetryCount()).isEqualTo(i);
                assertThat(event.hasFailed()).isFalse();
            }

            event.incrementRetry();
            assertThat(event.getRetryCount()).isEqualTo(10);
            assertThat(event.hasFailed()).isTrue();
            assertThat(event.getStatus()).isEqualTo("FAILED");
        }

        @Test
        void shouldKeepFailedAndKeepIncreasingRetryCountAfterThreshold() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "PENDING", CREATED, null, 0);

            for (int i = 0; i < 12; i++) {
                event.incrementRetry();
            }

            assertThat(event.getRetryCount()).isEqualTo(12);
            assertThat(event.hasFailed()).isTrue();
            assertThat(event.getStatus()).isEqualTo("FAILED");
        }

        @Test
        void shouldFailImmediatelyWhenStartingAtRetryCountNine() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "PENDING", CREATED, null, 9);
            event.incrementRetry();
            assertThat(event.getRetryCount()).isEqualTo(10);
            assertThat(event.hasFailed()).isTrue();
        }

        @Test
        void shouldFailImmediatelyWhenStartingAtRetryCountAboveThreshold() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "PENDING", CREATED, null, 12);
            assertThat(event.hasFailed()).isFalse();
            event.incrementRetry();
            assertThat(event.hasFailed()).isTrue();
            assertThat(event.getRetryCount()).isEqualTo(13);
        }

        @Test
        void hasFailedIsCaseInsensitive() {
            OutboxEvent event = new OutboxEvent("evt-1", "T", "{}", "failed", CREATED, null, 0);
            assertThat(event.hasFailed()).isTrue();
        }

        @Test
        void hasFailedReturnsFalseForNonFailedStatuses() {
            assertThat(new OutboxEvent("e", "T", "{}", "PENDING", CREATED, null, 0)
                    .hasFailed()).isFalse();
            assertThat(new OutboxEvent("e", "T", "{}", "SENT", CREATED, null, 0)
                    .hasFailed()).isFalse();
            assertThat(new OutboxEvent("e", "T", "{}", "pending", CREATED, null, 0)
                    .hasFailed()).isFalse();
        }

        @Test
        void incrementRetryOnSentEventFlipsStatusToFailed() {
            OutboxEvent event = new OutboxEvent("e", "T", "{}", "PENDING", CREATED, null, 0);
            event.markAsSent(CREATED.plusSeconds(1));
            assertThat(event.hasFailed()).isFalse();
            for (int i = 0; i < 10; i++) {
                event.incrementRetry();
            }
            assertThat(event.hasFailed()).isTrue();
            assertThat(event.getStatus()).isEqualTo("FAILED");
        }

        @Test
        void incrementRetryFromNegativeCountEventuallyReachesFailureThreshold() {
            OutboxEvent event = new OutboxEvent("e", "T", "{}", "PENDING", CREATED, null, -10);
            for (int i = 0; i < 20; i++) {
                event.incrementRetry();
            }
            assertThat(event.getRetryCount()).isEqualTo(10);
            assertThat(event.hasFailed()).isTrue();
        }

        @Test
        void incrementRetryOverflowWrapsAroundIntegerMaxValue() {
            OutboxEvent event = new OutboxEvent("e", "T", "{}", "PENDING", CREATED, null,
                    Integer.MAX_VALUE);
            event.incrementRetry();
            assertThat(event.getRetryCount()).isEqualTo(Integer.MIN_VALUE);
            assertThat(event.hasFailed()).isFalse();
        }
    }

    @Nested
    class Equality {

        @Test
        void eventsDoNotOverrideEqualsSoIdentityEqualityHolds() {
            OutboxEvent a = new OutboxEvent("e", "T", "{}", "PENDING", CREATED, null, 0);
            OutboxEvent b = new OutboxEvent("e", "T", "{}", "PENDING", CREATED, null, 0);
            assertThat(a).isNotSameAs(b);
            assertThat(a.equals(b)).isFalse();
            assertThat(a.equals(a)).isTrue();
            assertThat(a.equals(null)).isFalse();
        }
    }
}
