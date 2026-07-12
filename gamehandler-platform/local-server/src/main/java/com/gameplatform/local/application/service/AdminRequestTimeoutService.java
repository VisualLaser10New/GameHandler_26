package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.AdminRequestLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled service (PIANO §7.B) that times out stale PENDING
 * admin-requests. The Central return-event observed by the
 * {@code *SyncService}s closes the lifecycle of an admin-request via
 * {@link AdminRequestRepository#markCompleted}; when no return-event
 * arrives within {@code admin.request.timeout-ms} (default 30 min), the
 * request is transitioned to {@code FAILED} with
 * {@code result_data = \{"reason":"TIMEOUT"}} here — closing the loop on
 * the Local side (poison rejection Central: the Central could not
 * process the {@code *_REQUESTED} event, so the request would otherwise
 * stay PENDING forever).
 *
 * <p>The {@link Scheduled} annotation polls every
 * {@code admin.request.timeout-check-ms} (default 60 s); the deadline
 * now() minus {@code admin.request.timeout-ms} is passed to
 * {@link AdminRequestRepository#findPendingOlderThan} and each surviving
 * row is transitioned to {@code FAILED} via
 * {@link AdminRequestRepository#markFailed} (conditional
 * {@code WHERE status = 'PENDING'} — idempotent on overlap).</p>
 */
@Service
public class AdminRequestTimeoutService {

    private static final Logger log = LoggerFactory.getLogger(AdminRequestTimeoutService.class);

    static final String TIMEOUT_REASON = "{\"reason\":\"TIMEOUT\"}";

    private final AdminRequestRepository adminRequestRepository;
    private final Clock clock;
    private final long timeoutMs;

    public AdminRequestTimeoutService(AdminRequestRepository adminRequestRepository,
                                       Clock clock,
                                       @Value("${admin.request.timeout-ms:1800000}") long timeoutMs) {
        this.adminRequestRepository = adminRequestRepository;
        this.clock = clock;
        this.timeoutMs = timeoutMs;
    }

    @Scheduled(fixedDelayString = "${admin.request.timeout-check-ms:60000}")
    @Transactional
    public void timeoutPendingRequests() {
        Instant now = Instant.now(clock);
        Instant threshold = now.minus(timeoutMs, ChronoUnit.MILLIS);
        List<AdminRequestLocal> stale = adminRequestRepository.findPendingOlderThan(threshold);
        if (stale == null || stale.isEmpty()) {
            return;
        }
        log.warn("Timing out {} stale admin-request row(s) (threshold={})",
                stale.size(), threshold);
        for (AdminRequestLocal request : stale) {
            int mutated = adminRequestRepository.markFailed(request.getRequestId(), TIMEOUT_REASON, now);
            if (mutated > 0) {
                log.warn("Admin request {} timed out (PENDING since {}, eventType={})",
                        request.getRequestId(), request.getCreatedAt(), request.getEventType());
            } else if (log.isDebugEnabled()) {
                // Row was already COMPLETED/FAILED by another path between findPendingOlderThan and markFailed
                log.debug("Admin request {} already resolved before timeout fired — markFailed returned 0",
                        request.getRequestId());
            }
        }
    }
}