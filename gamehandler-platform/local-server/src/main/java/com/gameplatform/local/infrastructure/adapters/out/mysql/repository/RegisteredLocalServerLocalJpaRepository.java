package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link RegisteredLocalServerLocalJpaEntity}.
 * PK is {@code buildingId}; the default {@code save} is an upsert by PK.
 */
@Repository
public interface RegisteredLocalServerLocalJpaRepository
        extends JpaRepository<RegisteredLocalServerLocalJpaEntity, String> {
}
