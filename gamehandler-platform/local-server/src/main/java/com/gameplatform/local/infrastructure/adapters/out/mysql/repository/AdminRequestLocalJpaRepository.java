package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.AdminRequestLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link AdminRequestLocalJpaEntity}. The
 * lifecycle state transitions ({@code markCompleted}, {@code markFailed})
 * are conditional {@code WHERE status = 'PENDING'} bulk updates —
 * idempotent on re-delivery of the same return-event (a second call
 * against an already-{@code COMPLETED} row is a no-op).
 */
@Repository
public interface AdminRequestLocalJpaRepository extends JpaRepository<AdminRequestLocalJpaEntity, String> {

    List<AdminRequestLocalJpaEntity> findByActingUserId(String actingUserId);

    List<AdminRequestLocalJpaEntity> findByActingUserIdAndStatus(String actingUserId, String status);

    List<AdminRequestLocalJpaEntity> findByStatusAndCreatedAtBefore(String status, Instant threshold);

    @Modifying
    @Query("UPDATE AdminRequestLocalJpaEntity a " +
           "SET a.status = 'COMPLETED', a.resultData = :resultData, a.completedAt = :now " +
           "WHERE a.requestId = :requestId AND a.status = 'PENDING'")
    int markCompleted(@Param("requestId") String requestId,
                     @Param("resultData") String resultData,
                     @Param("now") Instant now);

    @Modifying
    @Query("UPDATE AdminRequestLocalJpaEntity a " +
           "SET a.status = 'FAILED', a.resultData = :reason, a.completedAt = :now " +
           "WHERE a.requestId = :requestId AND a.status = 'PENDING'")
    int markFailed(@Param("requestId") String requestId,
                   @Param("reason") String reason,
                   @Param("now") Instant now);
}
