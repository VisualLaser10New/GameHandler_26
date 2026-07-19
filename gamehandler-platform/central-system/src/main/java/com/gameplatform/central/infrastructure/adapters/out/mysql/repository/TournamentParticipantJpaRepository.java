package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentParticipantId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentParticipantJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA per l'accesso ai dati dei partecipanti ai tornei.
 * <p>
 * Fornisce metodi per interrogare, contare, verificare l'esistenza ed
 * eliminare partecipanti a un torneo.
 * </p>
 *
 * @see TournamentParticipantJpaEntity
 * @see TournamentParticipantId
 * @see TournamentJpaRepository
 */
@Repository
public interface TournamentParticipantJpaRepository extends JpaRepository<TournamentParticipantJpaEntity, TournamentParticipantId> {

    /**
     * Restituisce il partecipante specificato all'interno del torneo indicato, se presente.
     *
     * @param tournamentId  l'identificativo del torneo (non null)
     * @param participantId l'identificativo del partecipante (non null)
     * @return un {@code Optional} contenente il partecipante se trovato, vuoto altrimenti
     */
    Optional<TournamentParticipantJpaEntity> findByTournamentIdAndParticipantId(String tournamentId, String participantId);

    /**
     * Restituisce tutti i partecipanti al torneo specificato.
     *
     * @param tournamentId l'identificativo del torneo (non null)
     * @return una lista di partecipanti al torneo, vuota se il torneo non ha partecipanti o non esiste
     */
    List<TournamentParticipantJpaEntity> findByTournamentId(String tournamentId);

    /**
     * Conta il numero di partecipanti iscritti al torneo specificato.
     *
     * @param tournamentId l'identificativo del torneo (non null)
     * @return il numero di partecipanti al torneo (zero o positivo)
     */
    long countByTournamentId(String tournamentId);

    /**
     * Verifica se un partecipante specificato &egrave; iscritto al torneo indicato.
     *
     * @param tournamentId  l'identificativo del torneo (non null)
     * @param participantId l'identificativo del partecipante (non null)
     * @return {@code true} se il partecipante &egrave; iscritto al torneo, {@code false} altrimenti
     */
    boolean existsByTournamentIdAndParticipantId(String tournamentId, String participantId);

    /**
     * Rimuove il partecipante specificato dal torneo indicato.
     *
     * @param tournamentId  l'identificativo del torneo (non null)
     * @param participantId l'identificativo del partecipante da rimuovere (non null)
     */
    @Modifying
    @Query("delete from TournamentParticipantJpaEntity p where p.tournamentId = :tournamentId and p.participantId = :participantId")
    void deleteByTournamentIdAndParticipantId(@Param("tournamentId") String tournamentId,
                                              @Param("participantId") String participantId);
}