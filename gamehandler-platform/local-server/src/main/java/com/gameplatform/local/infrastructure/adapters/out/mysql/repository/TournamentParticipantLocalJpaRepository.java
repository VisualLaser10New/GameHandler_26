package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentParticipantLocalId;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentParticipantLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TournamentParticipantLocalJpaEntity}.
 * Composite PK is {@link TournamentParticipantLocalId}; the default
 * {@code save} is an upsert by the composite PK
 * ({@code tournamentId}, {@code participantId}).
 */
@Repository
public interface TournamentParticipantLocalJpaRepository
        extends JpaRepository<TournamentParticipantLocalJpaEntity, TournamentParticipantLocalId> {

    List<TournamentParticipantLocalJpaEntity> findByTournamentId(String tournamentId);

    void deleteByTournamentId(String tournamentId);

    void deleteByTournamentIdAndParticipantId(String tournamentId, String participantId);
}
