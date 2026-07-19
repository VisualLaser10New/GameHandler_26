package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.DeadLetterEvent;
import com.gameplatform.local.domain.model.OutboxEventStatus;
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
 * Servizio schedulato che promuove le righe {@code outbox_events} in stato
 * FAILED nella tabella {@code outbox_dead_letter} (DLQ) con motivo
 * {@link #REASON_RETRY_THRESHOLD_EXCEEDED}, eliminando la riga originale.
 * Impedisce la crescita incontrollata delle righe FAILED e mantiene gli
 * eventi falliti disponibili per ispezione offline. La dimensione corrente
 * del DLQ viene loggata ad ogni tick.
 *
 * @see DeadLetterRepository
 * @see OutboxEventJpaRepository
 */
@Service
public class OutboxDlqPromotionService {

    private static final Logger log = LoggerFactory.getLogger(OutboxDlqPromotionService.class);

    public static final String REASON_RETRY_THRESHOLD_EXCEEDED = "RETRY_THRESHOLD_EXCEEDED";
    private static final String FAILED_STATUS = OutboxEventStatus.FAILED.name();

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

    /**
     * Promuove tutti gli eventi outbox in stato FAILED nella tabella
     * DLQ, eliminando le righe originali. L'operazione e' transazionale
     * e atomica rispetto a percorsi concorrenti di incrementRetry.
     */
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
