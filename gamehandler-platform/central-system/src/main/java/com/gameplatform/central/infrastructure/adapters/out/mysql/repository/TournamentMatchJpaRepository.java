package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentMatchJpaEntity;
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
 * Repository JPA per l'accesso ai dati degli incontri dei tornei.
 * <p>
 * Fornisce metodi per interrogare ed eliminare incontri, inclusa la ricerca
 * con blocco pessimistico per garantire aggiornamenti atomici durante
 * l'elaborazione dei risultati e la gestione del tabellone.
 * </p>
 *
 * @see TournamentMatchJpaEntity
 * @see TournamentJpaRepository
 */
@Repository
public interface TournamentMatchJpaRepository extends JpaRepository<TournamentMatchJpaEntity, String> {

    /**
     * Restituisce l'incontro associato all'identificativo specificato, se presente.
     *
     * @param id l'identificativo univoco dell'incontro (non null)
     * @return un {@code Optional} contenente l'incontro se trovato, vuoto altrimenti
     */
    Optional<TournamentMatchJpaEntity> findById(String id);

    /**
     * Restituisce tutti gli incontri appartenenti al torneo specificato.
     *
     * @param tournamentId l'identificativo del torneo (non null)
     * @return una lista di incontri del torneo, vuota se il torneo non ha incontri o non esiste
     */
    List<TournamentMatchJpaEntity> findByTournamentId(String tournamentId);

    /**
     * Restituisce l'incontro associato all'identificativo specificato, acquisendo
     * un blocco pessimistico di scrittura per prevenire aggiornamenti concorrenti.
     *
     * @param id l'identificativo univoco dell'incontro (non null)
     * @return un {@code Optional} contenente l'incontro con blocco pessimistico se trovato, vuoto altrimenti
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM TournamentMatchJpaEntity m WHERE m.id = :id")
    Optional<TournamentMatchJpaEntity> findByIdForUpdate(@Param("id") String id);

    /**
     * Restituisce l'incontro del torneo specificato in base al round e alla
     * posizione nel tabellone, acquisendo un blocco pessimistico di scrittura
     * per prevenire aggiornamenti concorrenti.
     *
     * @param tournamentId   l'identificativo del torneo (non null)
     * @param round          il numero del round (non negativo)
     * @param bracketPosition la posizione nel tabellone (non negativo)
     * @return un {@code Optional} contenente l'incontro con blocco pessimistico se trovato, vuoto altrimenti
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM TournamentMatchJpaEntity m "
            + "WHERE m.tournamentId = :tid AND m.round = :round AND m.bracketPosition = :pos")
    Optional<TournamentMatchJpaEntity> findByTournamentIdAndRoundAndBracketPositionForUpdate(
            @Param("tid") String tournamentId,
            @Param("round") int round,
            @Param("pos") int bracketPosition);

    /**
     * Elimina l'incontro associato all'identificativo specificato.
     *
     * @param id l'identificativo univoco dell'incontro da eliminare (non null)
     */
    @Modifying
    @Query("delete from TournamentMatchJpaEntity m where m.id = :id")
    void deleteById(@Param("id") String id);
}