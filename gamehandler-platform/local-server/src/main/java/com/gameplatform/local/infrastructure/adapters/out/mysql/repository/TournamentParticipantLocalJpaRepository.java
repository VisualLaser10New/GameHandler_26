package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentParticipantLocalId;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentParticipantLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Interfaccia Spring Data JPA per l'entità {@link TournamentParticipantLocalJpaEntity}.
 * La chiave primaria composita è {@link TournamentParticipantLocalId}; il
 * metodo {@code save} predefinito esegue un upsert basato sulla chiave
 * composita ({@code tournamentId}, {@code participantId}).
 *
 * @see TournamentParticipantLocalJpaEntity
 * @see TournamentParticipantLocalId
 */
@Repository
public interface TournamentParticipantLocalJpaRepository
        extends JpaRepository<TournamentParticipantLocalJpaEntity, TournamentParticipantLocalId> {

    /**
     * Recupera tutti i partecipanti associati a un determinato torneo.
     *
     * @param tournamentId l'ID del torneo
     * @return una lista di entità {@link TournamentParticipantLocalJpaEntity} per il torneo indicato
     */
    List<TournamentParticipantLocalJpaEntity> findByTournamentId(String tournamentId);

    /**
     * Elimina tutti i partecipanti associati al torneo specificato.
     *
     * @param tournamentId l'ID del torneo di cui rimuovere i partecipanti
     */
    void deleteByTournamentId(String tournamentId);

    /**
     * Elimina un partecipante specifico dal torneo indicato.
     *
     * @param tournamentId l'ID del torneo
     * @param participantId l'ID del partecipante da rimuovere
     */
    void deleteByTournamentIdAndParticipantId(String tournamentId, String participantId);
}
