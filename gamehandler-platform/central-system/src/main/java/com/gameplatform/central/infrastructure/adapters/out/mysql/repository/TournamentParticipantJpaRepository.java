package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentParticipantId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentParticipantJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentParticipantJpaRepository extends JpaRepository<TournamentParticipantJpaEntity, TournamentParticipantId> {

    Optional<TournamentParticipantJpaEntity> findByTournamentIdAndParticipantId(String tournamentId, String participantId);

    List<TournamentParticipantJpaEntity> findByTournamentId(String tournamentId);

    long countByTournamentId(String tournamentId);

    boolean existsByTournamentIdAndParticipantId(String tournamentId, String participantId);

    @Modifying
    @Query("delete from TournamentParticipantJpaEntity p where p.tournamentId = :tournamentId and p.participantId = :participantId")
    void deleteByTournamentIdAndParticipantId(@Param("tournamentId") String tournamentId,
                                              @Param("participantId") String participantId);
}