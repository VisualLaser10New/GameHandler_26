package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalAdminBuildingId;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalAdminBuildingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocalAdminBuildingJpaRepository extends JpaRepository<LocalAdminBuildingJpaEntity, LocalAdminBuildingId> {

    List<LocalAdminBuildingJpaEntity> findByUserId(String userId);

    boolean existsByUserIdAndBuildingId(String userId, String buildingId);

    @Modifying
    @Query("delete from LocalAdminBuildingJpaEntity b where b.userId = :userId and b.buildingId = :buildingId")
    void deleteByUserIdAndBuildingId(@Param("userId") String userId, @Param("buildingId") String buildingId);
}