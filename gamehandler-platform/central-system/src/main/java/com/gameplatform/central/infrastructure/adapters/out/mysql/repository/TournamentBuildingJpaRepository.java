package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentBuildingId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentBuildingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentBuildingJpaRepository extends JpaRepository<TournamentBuildingJpaEntity, TournamentBuildingId> {

    List<TournamentBuildingJpaEntity> findByTournamentId(String tournamentId);

    boolean existsByTournamentIdAndBuildingId(String tournamentId, String buildingId);

    @Modifying
    @Query("delete from TournamentBuildingJpaEntity b where b.tournamentId = :tournamentId")
    void deleteByTournamentId(@Param("tournamentId") String tournamentId);
}