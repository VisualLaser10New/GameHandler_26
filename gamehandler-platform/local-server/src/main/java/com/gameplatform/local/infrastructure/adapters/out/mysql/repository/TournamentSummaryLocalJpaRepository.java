package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentSummaryLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TournamentSummaryLocalJpaEntity}. The
 * default {@code save} is an upsert by PK {@code tournament_id} — mirror of
 * {@code TournamentMatchLocalJpaRepository} and
 * {@code GameDefinitionLocalJpaRepository}.
 */
@Repository
public interface TournamentSummaryLocalJpaRepository
        extends JpaRepository<TournamentSummaryLocalJpaEntity, String> {
}
