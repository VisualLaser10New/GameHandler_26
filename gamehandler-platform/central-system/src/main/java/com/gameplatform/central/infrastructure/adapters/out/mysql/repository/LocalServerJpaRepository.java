package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocalServerJpaRepository extends JpaRepository<RegisteredLocalServerJpaEntity, String> {
    List<RegisteredLocalServerJpaEntity> findByIsActiveTrue();

    /**
     * M12 — returns ALL registered servers, newest {@code lastSeenAt} first.
     * Used by the admin {@code /internal/servers} health endpoint.
     */
    List<RegisteredLocalServerJpaEntity> findAllByOrderByLastSeenAtDesc();

    /**
     * M13 — atomically flips {@code is_active} to {@code false} for the given
     * building. The entity field is named {@code isActive} (field access), so
     * the JPQL attribute path is {@code s.isActive}.
     *
     * @param buildingId the building id whose server must be deactivated
     * @return the number of rows updated (0 if the building was not registered
     *         or was already inactive)
     */
    @Modifying
    @Query("update RegisteredLocalServerJpaEntity s set s.isActive = false where s.buildingId = :buildingId")
    int deactivateByBuildingId(@Param("buildingId") String buildingId);
}
