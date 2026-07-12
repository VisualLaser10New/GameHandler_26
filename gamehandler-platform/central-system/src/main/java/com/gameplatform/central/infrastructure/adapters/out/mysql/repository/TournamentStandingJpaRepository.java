package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentStandingId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentStandingJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentStandingJpaRepository extends JpaRepository<TournamentStandingJpaEntity, TournamentStandingId> {

    Optional<TournamentStandingJpaEntity> findByTournamentIdAndParticipantId(String tournamentId, String participantId);

    List<TournamentStandingJpaEntity> findByTournamentId(String tournamentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TournamentStandingJpaEntity s WHERE s.tournamentId = :tid")
    List<TournamentStandingJpaEntity> findByTournamentIdForUpdate(@Param("tid") String tournamentId);

    @Modifying
    @Query("delete from TournamentStandingJpaEntity s where s.tournamentId = :tournamentId and s.participantId = :participantId")
    void deleteByTournamentAndParticipantId(@Param("tournamentId") String tournamentId,
                                            @Param("participantId") String participantId);
}