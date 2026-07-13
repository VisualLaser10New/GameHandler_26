package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TeamMemberLocalId;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TeamMemberLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TeamMemberLocalJpaEntity}
 * (BUG-TEAM-3). Composite PK is {@link TeamMemberLocalId}; the default
 * {@code save} is an upsert by the composite PK
 * ({@code tournamentId}, {@code teamId}, {@code userId}). The sync service
 * uses {@link #deleteByTournamentId} for the full-snapshot replace
 * (delete+insert idempotency by {@code tournamentId}).
 */
@Repository
public interface TeamMemberLocalJpaRepository
        extends JpaRepository<TeamMemberLocalJpaEntity, TeamMemberLocalId> {

    List<TeamMemberLocalJpaEntity> findByTournamentId(String tournamentId);

    void deleteByTournamentId(String tournamentId);
}