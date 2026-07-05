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
 * Daily sweep that purges outbox rows already SENT older than the configured retention
 * window (default 7 days). Prevents unbounded growth of the outbox table.
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
