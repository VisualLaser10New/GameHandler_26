package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamMemberId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamMemberJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentTeamMemberJpaRepository extends JpaRepository<TournamentTeamMemberJpaEntity, TournamentTeamMemberId> {

    List<TournamentTeamMemberJpaEntity> findByTeamId(String teamId);

    @Modifying
    @Query("delete from TournamentTeamMemberJpaEntity m where m.teamId = :teamId")
    void deleteByTeamId(@Param("teamId") String teamId);

    @Query("select m.teamId from TournamentTeamMemberJpaEntity m where m.userId = :userId")
    List<String> findTeamIdsByUserId(@Param("userId") String userId);
}