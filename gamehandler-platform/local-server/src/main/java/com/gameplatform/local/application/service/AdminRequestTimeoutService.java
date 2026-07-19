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
 * Servizio schedulato che porta in timeout le richieste admin in stato
 * PENDING quando l'evento di ritorno dal Central non arriva entro la
 * finestra configurata (default 30 minuti). Le richieste vengono
 * transizionate a FAILED con motivo TIMEOUT, chiudendo il ciclo di vita
 * lato Local per le richieste che altrimenti rimarrebbero PENDING
 * indefinitamente.
 *
 * <p>Il polling avviene ogni {@code admin.request.timeout-check-ms}
 * (default 60 s); la soglia now() meno {@code admin.request.timeout-ms}
 * viene passata a {@link AdminRequestRepository#findPendingOlderThan} e
 * ogni riga ancora PENDING viene transizionata a FAILED via
 * {@link AdminRequestRepository#markFailed} con condizione
 * {@code WHERE status = 'PENDING'} (idempotente in caso di sovrapposizione).</p>
 *
 * @see AdminRequestRepository
 * @see AdminRequestLocal
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

    /**
     * Esegue lo sweep delle richieste admin PENDING piu' vecchie della soglia
     * di timeout configurata. Ogni riga ancora in stato PENDING viene
     * transizionata a FAILED con motivo TIMEOUT. L'operazione e' idempotente
     * grazie alla clausola condizionale {@code WHERE status = 'PENDING'}.
     */
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