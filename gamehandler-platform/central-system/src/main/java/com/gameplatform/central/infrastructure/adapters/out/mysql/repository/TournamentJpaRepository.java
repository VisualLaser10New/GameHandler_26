package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentJpaEntity;
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
public interface TournamentJpaRepository extends JpaRepository<TournamentJpaEntity, String> {

    Optional<TournamentJpaEntity> findById(String id);

    List<TournamentJpaEntity> findAllByOrderByCreatedAtDesc();

    List<TournamentJpaEntity> findByStatus(String status);

    boolean existsById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TournamentJpaEntity t WHERE t.id = :id")
    Optional<TournamentJpaEntity> findByIdForUpdate(@Param("id") String id);

    @Modifying
    @Query("delete from TournamentJpaEntity t where t.id = :id")
    void deleteById(@Param("id") String id);
}