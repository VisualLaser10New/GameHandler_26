package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentJpaEntity;
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
 * Repository JPA per l'accesso ai dati dei tornei.
 * <p>
 * Fornisce metodi per interrogare, aggiornare ed eliminare tornei, inclusa
 * la ricerca con blocco pessimistico per garantire l'atomicit&agrave; delle
 * operazioni concorrenti sullo stesso torneo.
 * </p>
 *
 * @see TournamentJpaEntity
 * @see TournamentMatchJpaRepository
 * @see TournamentParticipantJpaRepository
 * @see TournamentStandingJpaRepository
 */
@Repository
public interface TournamentJpaRepository extends JpaRepository<TournamentJpaEntity, String> {

    /**
     * Restituisce il torneo associato all'identificativo specificato, se presente.
     *
     * @param id l'identificativo univoco del torneo (non null)
     * @return un {@code Optional} contenente il torneo se trovato, vuoto altrimenti
     */
    Optional<TournamentJpaEntity> findById(String id);

    /**
     * Restituisce tutti i tornei ordinati per data di creazione decrescente
     * (dal pi&ugrave; recente al pi&ugrave; vecchio).
     *
     * @return una lista di tutti i tornei ordinati per {@code createdAt} decrescente,
     *         vuota se non ci sono tornei
     */
    List<TournamentJpaEntity> findAllByOrderByCreatedAtDesc();

    /**
     * Restituisce tutti i tornei con lo stato specificato.
     *
     * @param status lo stato del torneo da filtrare (non null)
     * @return una lista di tornei con lo stato indicato, vuota se nessun torneo ha quello stato
     */
    List<TournamentJpaEntity> findByStatus(String status);

    /**
     * Verifica se esiste un torneo con l'identificativo specificato.
     *
     * @param id l'identificativo del torneo da verificare (non null)
     * @return {@code true} se il torneo esiste, {@code false} altrimenti
     */
    boolean existsById(String id);

    /**
     * Restituisce il torneo associato all'identificativo specificato, acquisendo
     * un blocco pessimistico di scrittura per prevenire aggiornamenti concorrenti.
     *
     * @param id l'identificativo univoco del torneo (non null)
     * @return un {@code Optional} contenente il torneo con blocco pessimistico se trovato, vuoto altrimenti
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TournamentJpaEntity t WHERE t.id = :id")
    Optional<TournamentJpaEntity> findByIdForUpdate(@Param("id") String id);

    /**
     * Elimina il torneo associato all'identificativo specificato.
     *
     * @param id l'identificativo univoco del torneo da eliminare (non null)
     */
    @Modifying
    @Query("delete from TournamentJpaEntity t where t.id = :id")
    void deleteById(@Param("id") String id);
}