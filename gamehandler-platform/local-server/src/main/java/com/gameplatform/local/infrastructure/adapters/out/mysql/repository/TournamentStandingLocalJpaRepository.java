package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentStandingLocalId;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentStandingLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Interfaccia Spring Data JPA per l'entità {@link TournamentStandingLocalJpaEntity}.
 * La chiave primaria composita è {@link TournamentStandingLocalId}; il
 * metodo {@code save} predefinito esegue un upsert basato sulla chiave
 * composita ({@code tournamentId}, {@code participantId}).
 *
 * @see TournamentStandingLocalJpaEntity
 * @see TournamentStandingLocalId
 */
@Repository
public interface TournamentStandingLocalJpaRepository
        extends JpaRepository<TournamentStandingLocalJpaEntity, TournamentStandingLocalId> {

    /**
     * Recupera tutte le classifiche per un determinato torneo.
     *
     * @param tournamentId l'ID del torneo
     * @return una lista di entità {@link TournamentStandingLocalJpaEntity} per il torneo indicato
     */
    List<TournamentStandingLocalJpaEntity> findByTournamentId(String tournamentId);

    /**
     * Elimina tutte le classifiche associate al torneo specificato.
     *
     * @param tournamentId l'ID del torneo di cui rimuovere le classifiche
     */
    void deleteByTournamentId(String tournamentId);

    /**
     * Verifica se esiste una classifica per un determinato partecipante
     * all'interno del torneo specificato.
     *
     * @param tournamentId l'ID del torneo
     * @param participantId l'ID del partecipante
     * @return {@code true} se la classifica esiste, {@code false} altrimenti
     */
    boolean existsByTournamentIdAndParticipantId(String tournamentId, String participantId);
}
