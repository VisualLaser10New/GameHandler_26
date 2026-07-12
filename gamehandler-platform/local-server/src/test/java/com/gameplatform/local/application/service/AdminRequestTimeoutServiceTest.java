package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.AdminRequestLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Pure-Mockito unit tests for {@link AdminRequestTimeoutService}
 * (PIANO §7.B): transitions stale PENDING admin-request rows to FAILED
 * with {@code result_data = \{"reason":"TIMEOUT"}} when their
 * {@code createdAt} is older than the configured timeout threshold.
 */
@ExtendWith(MockitoExtension.class)
class AdminRequestTimeoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

    @Mock AdminRequestRepository adminRequestRepository;

    private final Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
    private final long timeoutMs = 30L * 60 * 1000; // 30 minutes
    private AdminRequestTimeoutService service;

    @BeforeEach
    void setUp() {
        service = new AdminRequestTimeoutService(adminRequestRepository, clock, timeoutMs);
    }

    private AdminRequestLocal pendingRequest(String requestId, Instant createdAt) {
        return new AdminRequestLocal(requestId, "TOURNAMENT_CREATE_REQUESTED", "u-1",
                "PLATFORM_ADMIN", "building-1", "{}", "PENDING", null, createdAt, null, requestId);
    }

    @Test
    void timeoutPendingRequests_marksStaleRowFailed() {
        AdminRequestLocal stale = pendingRequest("req-1", Instant.parse("2026-07-12T11:00:00Z"));
        when(adminRequestRepository.findPendingOlderThan(any()))
                .thenReturn(List.of(stale));
        when(adminRequestRepository.markFailed(eq("req-1"), anyString(), any())).thenReturn(1);

        service.timeoutPendingRequests();

        verify(adminRequestRepository).markFailed(eq("req-1"),
                eq(AdminRequestTimeoutService.TIMEOUT_REASON), eq(NOW));
    }

    @Test
    void timeoutPendingRequests_isNoOpWhenNoStaleRows() {
        when(adminRequestRepository.findPendingOlderThan(any())).thenReturn(List.of());

        service.timeoutPendingRequests();

        verify(adminRequestRepository, never()).markFailed(anyString(), anyString(), any());
    }

    @Test
    void timeoutPendingRequests_isNoOpWhenNullList() {
        when(adminRequestRepository.findPendingOlderThan(any())).thenReturn(null);

        service.timeoutPendingRequests();

        verify(adminRequestRepository, never()).markFailed(anyString(), anyString(), any());
    }

    @Test
    void timeoutPendingRequests_isIdempotentWhenRowAlreadyResolved() {
        AdminRequestLocal stale = pendingRequest("req-1", Instant.parse("2026-07-12T11:00:00Z"));
        when(adminRequestRepository.findPendingOlderThan(any())).thenReturn(List.of(stale));
        when(adminRequestRepository.markFailed(anyString(), anyString(), any())).thenReturn(0);

        // Should NOT throw — the service just logs at DEBUG.
        assertDoesNotThrow(() -> service.timeoutPendingRequests());

        verify(adminRequestRepository).markFailed(eq("req-1"), eq(AdminRequestTimeoutService.TIMEOUT_REASON), eq(NOW));
    }

    @Test
    void timeoutPendingRequests_marksMultipleStaleRows() {
        AdminRequestLocal s1 = pendingRequest("req-1", Instant.parse("2026-07-12T10:00:00Z"));
        AdminRequestLocal s2 = pendingRequest("req-2", Instant.parse("2026-07-12T11:00:00Z"));
        when(adminRequestRepository.findPendingOlderThan(any())).thenReturn(List.of(s1, s2));

        service.timeoutPendingRequests();

        verify(adminRequestRepository).markFailed(eq("req-1"), eq(AdminRequestTimeoutService.TIMEOUT_REASON), eq(NOW));
        verify(adminRequestRepository).markFailed(eq("req-2"), eq(AdminRequestTimeoutService.TIMEOUT_REASON), eq(NOW));
    }

    @Test
    void timeoutPendingRequests_thresholdIsNowMinusTimeout() {
        when(adminRequestRepository.findPendingOlderThan(any())).thenReturn(List.of());

        service.timeoutPendingRequests();

        // The threshold passed to findPendingOlderThan is exactly now minus timeoutMs.
        Instant expectedThreshold = NOW.minus(timeoutMs, java.time.temporal.ChronoUnit.MILLIS);
        verify(adminRequestRepository).findPendingOlderThan(argThat(t -> t.equals(expectedThreshold)));
    }
}
