package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerMatchFactId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerMatchFactJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link PlayerMatchFactJpaEntity}. The
 * composite primary key ({@code session_id}, {@code user_id}) is surfaced as
 * {@link PlayerMatchFactId} for {@link JpaRepository} operations. The
 * existence probe is used by the adapter to make the player-match-fact insert
 * idempotent without relying on a caught constraint violation.
 */
@Repository
public interface PlayerMatchFactJpaRepository extends JpaRepository<PlayerMatchFactJpaEntity, PlayerMatchFactId> {

    boolean existsBySessionIdAndUserId(String sessionId, String userId);
}