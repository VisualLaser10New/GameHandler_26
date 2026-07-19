package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.RegisteredLocalServerLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Interfaccia Spring Data JPA per l'entità {@link RegisteredLocalServerLocalJpaEntity}.
 * La chiave primaria è {@code buildingId}; il metodo {@code save} predefinito
 * esegue un upsert per chiave primaria.
 *
 * @see RegisteredLocalServerLocalJpaEntity
 */
@Repository
public interface RegisteredLocalServerLocalJpaRepository
        extends JpaRepository<RegisteredLocalServerLocalJpaEntity, String> {
}
