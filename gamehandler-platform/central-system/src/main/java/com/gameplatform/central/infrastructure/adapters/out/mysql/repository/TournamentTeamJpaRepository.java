package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentTeamJpaRepository extends JpaRepository<TournamentTeamJpaEntity, String> {

    Optional<TournamentTeamJpaEntity> findById(String id);

    List<TournamentTeamJpaEntity> findByTournamentId(String tournamentId);

    Optional<TournamentTeamJpaEntity> findByTournamentIdAndName(String tournamentId, String name);

    boolean existsByTournamentIdAndName(String tournamentId, String name);

    @Modifying
    @Query("delete from TournamentTeamJpaEntity t where t.id = :id")
    void deleteById(@Param("id") String id);
}