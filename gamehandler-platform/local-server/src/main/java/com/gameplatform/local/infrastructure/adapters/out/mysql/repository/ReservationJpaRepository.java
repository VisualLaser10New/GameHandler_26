package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.ReservationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, String> {
    List<ReservationJpaEntity> findByUserId(String userId);
    List<ReservationJpaEntity> findByGameId(String gameId);
    List<ReservationJpaEntity> findByStatus(String status);
    List<ReservationJpaEntity> findByStatusAndEndTimeBefore(String status, Instant endTime);
    List<ReservationJpaEntity> findByStatusInAndEndTimeBefore(Collection<String> statuses, Instant endTime);
}
