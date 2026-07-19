package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentStandingId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentStandingJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA per l'accesso ai dati delle classifiche dei tornei.
 * <p>
 * Fornisce metodi per interrogare e modificare le posizioni in classifica dei
 * partecipanti a un torneo, inclusa la ricerca con blocco pessimistico per
 * garantire aggiornamenti atomici durante l'elaborazione dei risultati.
 * </p>
 *
 * @see TournamentStandingJpaEntity
 * @see TournamentStandingId
 * @see TournamentJpaRepository
 */
@Repository
public interface TournamentStandingJpaRepository extends JpaRepository<TournamentStandingJpaEntity, TournamentStandingId> {

    /**
     * Restituisce la posizione in classifica del partecipante specificato all'interno del torneo indicato, se presente.
     *
     * @param tournamentId  l'identificativo del torneo (non null)
     * @param participantId l'identificativo del partecipante (non null)
     * @return un {@code Optional} contenente la posizione in classifica se trovata, vuoto altrimenti
     */
    Optional<TournamentStandingJpaEntity> findByTournamentIdAndParticipantId(String tournamentId, String participantId);

    /**
     * Restituisce tutte le posizioni in classifica per il torneo specificato.
     *
     * @param tournamentId l'identificativo del torneo (non null)
     * @return una lista di posizioni in classifica ordinate secondo l'ordinamento predefinito dell'entit&agrave;,
     *         vuota se il torneo non ha partecipanti o non esiste
     */
    List<TournamentStandingJpaEntity> findByTournamentId(String tournamentId);

    /**
     * Restituisce tutte le posizioni in classifica per il torneo specificato, acquisendo
     * un blocco pessimistico di scrittura per prevenire aggiornamenti concorrenti.
     *
     * @param tournamentId l'identificativo del torneo (non null)
     * @return una lista di posizioni in classifica con blocco pessimistico attivo,
     *         vuota se il torneo non ha partecipanti
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM TournamentStandingJpaEntity s WHERE s.tournamentId = :tid")
    List<TournamentStandingJpaEntity> findByTournamentIdForUpdate(@Param("tid") String tournamentId);

    /**
     * Elimina la posizione in classifica del partecipante specificato all'interno del torneo indicato.
     *
     * @param tournamentId  l'identificativo del torneo (non null)
     * @param participantId l'identificativo del partecipante da rimuovere dalla classifica (non null)
     */
    @Modifying
    @Query("delete from TournamentStandingJpaEntity s where s.tournamentId = :tournamentId and s.participantId = :participantId")
    void deleteByTournamentAndParticipantId(@Param("tournamentId") String tournamentId,
                                            @Param("participantId") String participantId);
}