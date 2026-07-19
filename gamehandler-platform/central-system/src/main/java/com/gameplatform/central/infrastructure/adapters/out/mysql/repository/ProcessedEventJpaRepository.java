package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.ProcessedEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository JPA per l'accesso ai dati degli eventi gi&agrave; processati.
 * <p>
 * Utilizzato per garantire l'idempotenza nella gestione degli eventi: un evento
 * viene censito come processato prima della sua elaborazione effettiva, in modo
 * da evitare duplicazioni in caso di consegne multiple.
 * </p>
 *
 * @see ProcessedEventJpaEntity
 */
@Repository
public interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, String> {
}
