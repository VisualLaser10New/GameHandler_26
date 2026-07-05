package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.DeadLetterEvent;
import com.gameplatform.local.domain.ports.out.DeadLetterRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Sweeps local {@code outbox_events} rows that reached the FAILED retry threshold,
 * promotes a copy into the {@code outbox_dead_letter} table (DLQ) with the reason
 * {@link #REASON_RETRY_THRESHOLD_EXCEEDED}, and deletes the original outbox row.
 *
 * <p>This caps the unbounded growth of {@code outbox_events} PENDING -> FAILED rows
 * and keeps failed events available for offline inspection. The sweep is
 * transactional, so promotion + deletion are atomic w.r.t. concurrent
 * {@code incrementRetry} paths (which only ever flip PENDING -> FAILED).</p>
 *
 * <p>The running DLQ size is logged on every tick so operators can monitor backlog
 * without a separate {@code DlqMonitorService}.</p>
 */
@Service
public class OutboxDlqPromotionService {

    private static final Logger log = LoggerFactory.getLogger(OutboxDlqPromotionService.class);

    public static final String REASON_RETRY_THRESHOLD_EXCEEDED = "RETRY_THRESHOLD_EXCEEDED";
    private static final String FAILED_STATUS = "FAILED";

    private final OutboxEventJpaRepository outboxJpaRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final Clock clock;

    public OutboxDlqPromotionService(OutboxEventJpaRepository outboxJpaRepository,
                                      DeadLetterRepository deadLetterRepository,
                                      Clock clock) {
        this.outboxJpaRepository = outboxJpaRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.clock = clock;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${app.dlq-promotion-interval-ms:600000}")
    public void promoteFailedToDlq() {
        List<OutboxEventJpaEntity> failedEvents = outboxJpaRepository.findByStatusOrderByCreatedAtAsc(FAILED_STATUS);

        if (failedEvents.isEmpty()) {
            log.info("Promoted 0 failed outbox events to DLQ (current DLQ size: {})", deadLetterRepository.count());
            return;
        }

        Instant promotedAt = Instant.now(clock);
        for (OutboxEventJpaEntity entity : failedEvents) {
            DeadLetterEvent dlq = new DeadLetterEvent(
                entity.getId(),
                entity.getId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getStatus(),
                entity.getRetryCount(),
                REASON_RETRY_THRESHOLD_EXCEEDED,
                promotedAt
            );
            deadLetterRepository.save(dlq);
            outboxJpaRepository.delete(entity);
        }

        log.info("Promoted {} failed outbox events to DLQ (current DLQ size: {})",
                failedEvents.size(), deadLetterRepository.count());
    }
}
