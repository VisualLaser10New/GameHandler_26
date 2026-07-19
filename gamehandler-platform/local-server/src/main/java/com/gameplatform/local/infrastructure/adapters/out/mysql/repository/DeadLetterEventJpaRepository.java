package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.DeadLetterEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Interfaccia Spring Data JPA per l'entità {@link DeadLetterEventJpaEntity}.
 * Fornisce le operazioni CRUD standard per gli eventi finiti nella coda
 * dei messaggi non recapitabili (dead-letter), utilizzata per il tracciamento
 * e la diagnostica degli eventi che hanno superato il numero massimo di tentativi.
 *
 * @see DeadLetterEventJpaEntity
 */
@Repository
public interface DeadLetterEventJpaRepository extends JpaRepository<DeadLetterEventJpaEntity, String> {
}
