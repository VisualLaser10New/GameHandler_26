package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentMatchJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Modifying
    @Query("delete from TournamentMatchJpaEntity m where m.id = :id")
    void deleteById(@Param("id") String id);
}