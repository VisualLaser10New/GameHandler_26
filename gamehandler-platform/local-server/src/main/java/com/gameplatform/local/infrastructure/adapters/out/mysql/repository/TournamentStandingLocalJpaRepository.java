package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentStandingLocalId;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentStandingLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TournamentStandingLocalJpaEntity}.
 * Composite PK is {@link TournamentStandingLocalId}; the default
 * {@code save} is an upsert by the composite PK
 * ({@code tournamentId}, {@code participantId}).
 */
@Repository
public interface TournamentStandingLocalJpaRepository
        extends JpaRepository<TournamentStandingLocalJpaEntity, TournamentStandingLocalId> {

    List<TournamentStandingLocalJpaEntity> findByTournamentId(String tournamentId);

    void deleteByTournamentId(String tournamentId);

    boolean existsByTournamentIdAndParticipantId(String tournamentId, String participantId);
}
