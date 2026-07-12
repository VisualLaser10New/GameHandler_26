package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentMatchLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentMatchLocalJpaRepository
        extends JpaRepository<TournamentMatchLocalJpaEntity, String> {

    Optional<TournamentMatchLocalJpaEntity> findById(String id);

    List<TournamentMatchLocalJpaEntity> findByTournamentId(String tournamentId);

    @Query("SELECT m FROM TournamentMatchLocalJpaEntity m " +
           "WHERE (m.participantA = :userId OR m.participantB = :userId) " +
           "AND m.status = :status")
    List<TournamentMatchLocalJpaEntity> findByParticipantAndStatus(
            @Param("userId") String userId,
            @Param("status") String status);
}