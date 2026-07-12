package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentMatchJpaEntity;
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
public interface TournamentMatchJpaRepository extends JpaRepository<TournamentMatchJpaEntity, String> {

    Optional<TournamentMatchJpaEntity> findById(String id);

    List<TournamentMatchJpaEntity> findByTournamentId(String tournamentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM TournamentMatchJpaEntity m WHERE m.id = :id")
    Optional<TournamentMatchJpaEntity> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM TournamentMatchJpaEntity m "
            + "WHERE m.tournamentId = :tid AND m.round = :round AND m.bracketPosition = :pos")
    Optional<TournamentMatchJpaEntity> findByTournamentIdAndRoundAndBracketPositionForUpdate(
            @Param("tid") String tournamentId,
            @Param("round") int round,
            @Param("pos") int bracketPosition);

    @Modifying
    @Query("delete from TournamentMatchJpaEntity m where m.id = :id")
    void deleteById(@Param("id") String id);
}