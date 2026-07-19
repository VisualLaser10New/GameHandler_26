package com.gameplatform.local.application.service;

import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Sweep giornaliero che elimina le righe outbox in stato SENT piu' vecchie
 * della finestra di retention configurabile (default 7 giorni). Previene
 * la crescita incontrollata della tabella outbox.
 *
 * @see OutboxEventJpaRepository
 */
@Service
public class OutboxPurgeService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPurgeService.class);

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final Clock clock;
    private final long retentionDays;

    public OutboxPurgeService(
            OutboxEventJpaRepository outboxEventJpaRepository,
            Clock clock,
            @Value("${app.outbox-purge-retention-days:7}") long retentionDays) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    /**
     * Elimina le righe outbox in stato SENT piu' vecchie del periodo di
     * retention configurato.
     */
    @Scheduled(fixedDelayString = "${app.outbox-purge-interval-ms:86400000}")
    @Transactional
    public void purgeOldSentEvents() {
        Instant cutoff = Instant.now(clock).minus(retentionDays, ChronoUnit.DAYS);
        int deleted = outboxEventJpaRepository.deleteSentOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Purged {} SENT outbox events older than {} days (cutoff={})", deleted, retentionDays, cutoff);
        } else {
            log.debug("No SENT outbox events older than {} days to purge.", retentionDays);
        }
    }
}
