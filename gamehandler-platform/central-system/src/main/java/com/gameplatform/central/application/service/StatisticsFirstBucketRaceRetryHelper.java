package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * S3 / C-R1 fix for BUG A — first-bucket {@code aggregated_statistics} insert race.
 *
 * <p>The retry runs in a FRESH {@code REQUIRES_NEW} transaction (TX2) so it is not
 * affected by the outer tx (TX1) which is poisoned (rollback-only / polluted
 * persistence context) after the loser's {@code saveAndFlush} DIVE. TX2 starts with
 * a clean persistence context — the failed {@code newStats} entity is NOT present,
 * so the retry's {@code findByBuildingAndTypeAndPeriodWithLock} does NOT trigger a
 * re-INSERT auto-flush.</p>
 *
 * <p>TX2 commits BOTH the merged stats AND the {@code processed_events} record in
 * one fresh tx, so the event is marked processed even though TX1 will roll back.</p>
 */
@Service
public class StatisticsFirstBucketRaceRetryHelper {

    private static final Logger log = LoggerFactory.getLogger(StatisticsFirstBucketRaceRetryHelper.class);

    private final StatisticsRepository statisticsRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final Clock clock;

    public StatisticsFirstBucketRaceRetryHelper(StatisticsRepository statisticsRepository,
                                                ProcessedEventRepository processedEventRepository,
                                                Clock clock) {
        this.statisticsRepository = statisticsRepository;
        this.processedEventRepository = processedEventRepository;
        this.clock = clock;
    }

    /**
     * Esegue il merge del delta statistico e marca l'evento come processato in
     * una transazione {@code REQUIRES_NEW} separata (TX2).
     *
     * <p>Viene invocato quando la transazione originale (TX1) è stata
     * "avvelenata" da una violazione di chiave univoca sul primo bucket. TX2
     * parte con un persistence context pulito e committa sia le statistiche
     * unite sia il record {@code processed_events}, così che l'evento risulti
     * processato anche se TX1 andrà in rollback.</p>
     *
     * @param buildingId l'edificio di riferimento (non deve essere {@code null})
     * @param gameType il tipo di gioco (non deve essere {@code null})
     * @param period il periodo (giorno) di riferimento (non deve essere {@code null})
     * @param delta il delta statistico da unire alla riga esistente
     * @param eventId l'identificativo dell'evento da marcare come processato
     * @throws IllegalStateException se la riga del primo bucket è scomparsa tra
     *         la violazione e il retry
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryMergeAndMarkProcessed(BuildingId buildingId, GameType gameType,
                                           LocalDate period, AggregatedStatistics delta,
                                           String eventId) {
        AggregatedStatistics winner = statisticsRepository
                .findByBuildingAndTypeAndPeriodWithLock(buildingId, gameType, period)
                .orElseThrow(() -> new IllegalStateException(
                        "First-bucket race: winner vanished after UK violation for ["
                                + buildingId + "|" + gameType + "|" + period + "]"));
        winner.mergeWith(delta);
        statisticsRepository.save(winner);
        try {
            processedEventRepository.save(new ProcessedEvent(eventId, Instant.now(clock)));
        } catch (DataIntegrityViolationException already) {
            log.debug("processed_events already present for [{}] during race retry", eventId);
        }
    }
}
