package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.PlayerStatistics;
import com.gameplatform.central.domain.ports.out.PlayerStatisticsRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerStatisticsJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.PlayerStatisticsMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.PlayerStatisticsJpaRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link PlayerStatisticsRepository} port.
 *
 * <p>Reads ({@link #findByUserId}, {@link #findByUserIdAndGameType}) are plain
 * delegations to the JPA repository. {@link #increment} is the FASE 3 critical
 * section (PIANO &sect;2.4 / protocol &sect;2.C thread-safety mandate); it is
 * <strong>not</strong> annotated {@code @Transactional} and is always invoked
 * inside the caller's transaction (the {@code SyncEventProcessor#processOne}
 * {@code REQUIRES_NEW} transaction). It:</p>
 * <ol>
 *   <li>acquires a {@code PESSIMISTIC_WRITE} lock on the (userId, gameType)
 *       row via {@link PlayerStatisticsJpaRepository#findByUserIdAndGameTypeForUpdate};
 *       the locked row is a managed entity returned by the same transactional
 *       {@link EntityManager};</li>
 *   <li>if present, mutates the managed counters in place and flushes
 *       (atomic update under the row lock);</li>
 *   <li>if absent, inserts a fresh row using {@link EntityManager#persist} +
 *       {@link EntityManager#flush} directly &mdash; <em>not</em> the Spring
 *       Data repo proxy &mdash; so a first-row PK race is observed as a raw
 *       {@code PersistenceException} <em>before</em> it can cross a
 *       transactional proxy boundary and mark the caller's transaction
 *       rollback-only. On such a constraint-violation race the persistence
 *       context is cleared and the row is re-acquired under the lock and
 *       merged, all within the same transaction; genuine flush errors are
 *       rethrown so the event is handled by poison-isolation.</li>
 * </ol>
 *
 * <p>This deliberately differs from the {@code aggregated_statistics}
 * first-bucket race strategy, which uses a fresh {@code REQUIRES_NEW} retry
 * transaction (because that flow persists via the Spring Data repo proxy whose
 * proxy marks the outer transaction rollback-only on the caught duplicate).
 * Persisting via the EntityManager here keeps the retry in the same
 * transaction while guaranteeing no rollback-only side effect.</p>
 */
@Component
public class PlayerStatisticsRepositoryAdapter implements PlayerStatisticsRepository {

    private static final Logger log = LoggerFactory.getLogger(PlayerStatisticsRepositoryAdapter.class);

    private final PlayerStatisticsJpaRepository jpaRepository;
    private final PlayerStatisticsMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public PlayerStatisticsRepositoryAdapter(PlayerStatisticsJpaRepository jpaRepository, PlayerStatisticsMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<PlayerStatistics> findByUserId(UserId userId) {
        if (userId == null) {
            return List.of();
        }
        return jpaRepository.findByUserId(userId.value()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<PlayerStatistics> findByUserIdAndGameType(UserId userId, GameType gameType) {
        if (userId == null || gameType == null) {
            return Optional.empty();
        }
        return jpaRepository.findByUserIdAndGameType(userId.value(), gameType.name())
                .map(mapper::toDomain);
    }

    @Override
    public void increment(UserId userId, GameType gameType, boolean won, Instant endedAt) {
        if (userId == null || gameType == null || endedAt == null) {
            throw new IllegalArgumentException("userId, gameType and endedAt are required for increment");
        }
        String uid = userId.value();
        String gt = gameType.name();

        Optional<PlayerStatisticsJpaEntity> existingOpt =
                jpaRepository.findByUserIdAndGameTypeForUpdate(uid, gt);
        if (existingOpt.isPresent()) {
            mergeInPlace(existingOpt.get(), won, endedAt);
            entityManager.flush();
            return;
        }

        // First-row insert path.
        PlayerStatisticsJpaEntity fresh = new PlayerStatisticsJpaEntity(uid, gt, 1, won ? 1 : 0, endedAt);
        try {
            entityManager.persist(fresh);
            entityManager.flush();
        } catch (jakarta.persistence.PersistenceException dup) {
            if (!isDataIntegrityCause(dup)) {
                throw dup; // genuine flush error → let poison-isolation handle it
            }
            log.info("First-bucket race on player_statistics insert [{}|{}], merging under lock", uid, gt);
            entityManager.clear();
            Optional<PlayerStatisticsJpaEntity> existingAfterRace =
                    jpaRepository.findByUserIdAndGameTypeForUpdate(uid, gt);
            if (existingAfterRace.isPresent()) {
                mergeInPlace(existingAfterRace.get(), won, endedAt);
                entityManager.flush();
            } else {
                // Row vanished between the race and the locked re-read — treat as a genuine failure.
                throw dup;
            }
        }
    }

    private void mergeInPlace(PlayerStatisticsJpaEntity existing, boolean won, Instant endedAt) {
        existing.setMatchesPlayed(existing.getMatchesPlayed() + 1);
        existing.setMatchesWon(existing.getMatchesWon() + (won ? 1 : 0));
        Instant current = existing.getLastPlayedAt();
        existing.setLastPlayedAt((current == null || endedAt.isAfter(current)) ? endedAt : current);
    }

    private static boolean isDataIntegrityCause(Throwable t) {
        Throwable c = t;
        while (c != null) {
            if (c instanceof java.sql.SQLIntegrityConstraintViolationException) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }
}