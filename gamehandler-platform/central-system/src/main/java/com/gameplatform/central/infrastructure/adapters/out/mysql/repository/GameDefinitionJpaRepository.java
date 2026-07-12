package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.GameDefinitionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameDefinitionJpaRepository extends JpaRepository<GameDefinitionJpaEntity, String> {

    Optional<GameDefinitionJpaEntity> findByGameType(String gameType);

    List<GameDefinitionJpaEntity> findAllByOrderByGameTypeAsc();

    boolean existsByGameType(String gameType);

    @Modifying
    @Query("delete from GameDefinitionJpaEntity d where d.gameType = :gameType")
    void deleteByGameType(@Param("gameType") String gameType);
}
