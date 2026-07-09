package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, String> {
    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(String status);
    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
    List<OutboxEventJpaEntity> findByEventTypeAndStatus(String eventType, String status);

    /**
     * Bulk mark-as-sent UPDATE. Sets status=SENT and sentAt=:now for every id
     * currently in PENDING, in a single SQL statement.
     */
    @Modifying
    @Query("UPDATE OutboxEventJpaEntity e " +
           "SET e.status = 'SENT', e.sentAt = :now " +
           "WHERE e.id IN :ids AND e.status = 'PENDING'")
    int markAsSentBatch(@Param("ids") List<String> ids, @Param("now") Instant now);

    /**
     * Bulk increment-retry UPDATE. Bumps retryCount by 1 for each id currently
     * PENDING; a separate statement flips to FAILED those that reached the threshold.
     * Both statements must run in the same transaction (see
     * {@link com.gameplatform.local.infrastructure.adapters.out.mysql.adapter.OutboxEventRepositoryAdapter#incrementRetryBatch}).
     */
    @Modifying
    @Query("UPDATE OutboxEventJpaEntity e " +
           "SET e.retryCount = e.retryCount + 1 " +
           "WHERE e.id IN :ids AND e.status = 'PENDING'")
    int incrementRetryBatch(@Param("ids") List<String> ids);

    @Modifying
    @Query("UPDATE OutboxEventJpaEntity e " +
           "SET e.status = 'FAILED' " +
           "WHERE e.id IN :ids AND e.status = 'PENDING' AND e.retryCount >= :threshold")
    int markAsFailedAboveThreshold(@Param("ids") List<String> ids, @Param("threshold") int threshold);

    /**
     * Bulk delete SENT outbox rows older than the given cutoff. Single SQL statement.
     */
    @Modifying
    @Query("DELETE FROM OutboxEventJpaEntity e WHERE e.status = 'SENT' AND e.sentAt < :cutoff")
    int deleteSentOlderThan(@Param("cutoff") Instant cutoff);
}
