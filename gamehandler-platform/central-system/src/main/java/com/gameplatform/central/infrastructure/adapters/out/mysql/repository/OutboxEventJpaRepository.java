package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, String> {
    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(String status);
    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    List<OutboxEventJpaEntity> findByStatusAndEventTypeInOrderByCreatedAtAsc(String status, Collection<String> eventTypes);

    /**
     * Returns events whose status is in {@code statuses} and whose type is in
     * {@code eventTypes}, oldest first. Used by the late-registration catch-up
     * (R1) to replay both SENT and PENDING user-replication events to a newly
     * registered server.
     */
    List<OutboxEventJpaEntity> findByStatusInAndEventTypeInOrderByCreatedAtAsc(
            Collection<String> statuses, Collection<String> eventTypes);

    /**
     * M12 — counts user-replication events (USER_REGISTERED / USER_UPDATED)
     * whose status is NOT {@code SENT} and for which NO
     * {@code replication_progress} row has been recorded for the given server.
     * This is the per-server pending-replication backlog surfaced by the
     * {@code /internal/servers} admin endpoint.
     *
     * <p>Implemented as a single JPQL query with a correlated {@code NOT EXISTS}
     * subquery against {@code ReplicationProgressJpaEntity}. H2 (MODE=MySQL)
     * supports correlated subqueries, so the same query runs in tests and in
     * production against MySQL.</p>
     *
     * @param serverId the building id of the local server
     * @return non-negative count of pending user-replication events
     */
    @Query("select count(e) from OutboxEventJpaEntity e " +
            "where e.eventType in ('USER_REGISTERED','USER_UPDATED') " +
            "and e.status <> 'SENT' " +
            "and not exists (select rp from ReplicationProgressJpaEntity rp " +
            "where rp.eventId = e.id and rp.serverId = :serverId)")
    long countPendingReplicationForServer(@Param("serverId") String serverId);
}
