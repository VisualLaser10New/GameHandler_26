package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentSummaryLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Interfaccia Spring Data JPA per l'entità {@link TournamentSummaryLocalJpaEntity}.
 * Il metodo {@code save} predefinito esegue un upsert per chiave primaria
 * {@code tournamentId}. Costituisce il mirror locale dei dati di riepilogo
 * del torneo, in modo analogo a {@code TournamentMatchLocalJpaRepository}
 * e {@code GameDefinitionLocalJpaRepository}.
 *
 * @see TournamentSummaryLocalJpaEntity
 * @see TournamentMatchLocalJpaRepository
 * @see GameDefinitionLocalJpaRepository
 */
@Repository
public interface TournamentSummaryLocalJpaRepository
        extends JpaRepository<TournamentSummaryLocalJpaEntity, String> {
}
