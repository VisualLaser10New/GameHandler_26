package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameDefinitionLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameDefinitionLocalJpaRepository extends JpaRepository<GameDefinitionLocalJpaEntity, String> {

    Optional<GameDefinitionLocalJpaEntity> findByGameType(String gameType);

    List<GameDefinitionLocalJpaEntity> findAllByOrderByGameTypeAsc();

    boolean existsByGameType(String gameType);

    @Modifying
    @Query("delete from GameDefinitionLocalJpaEntity d where d.gameType = :gameType")
    void deleteByGameType(@Param("gameType") String gameType);
}