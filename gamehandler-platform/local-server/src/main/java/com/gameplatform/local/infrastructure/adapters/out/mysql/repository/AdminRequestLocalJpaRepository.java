package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.AdminRequestLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Interfaccia Spring Data JPA per l'entità {@link AdminRequestLocalJpaEntity}.
 * Le transizioni di stato del ciclo di vita ({@code markCompleted},
 * {@code markFailed}) sono aggiornamenti bulk condizionali con clausola
 * {@code WHERE status = 'PENDING'}, garantendo idempotenza in caso di
 * riconsegna dello stesso evento di ritorno (una seconda chiamata su una
 * riga già {@code COMPLETED} è un no-op).
 *
 * @see AdminRequestLocalJpaEntity
 */
@Repository
public interface AdminRequestLocalJpaRepository extends JpaRepository<AdminRequestLocalJpaEntity, String> {

    /**
     * Recupera tutte le richieste amministrative associate a un determinato
     * ID utente agente.
     *
     * @param actingUserId l'ID dell'utente agente che ha eseguito la richiesta
     * @return una lista di entità {@link AdminRequestLocalJpaEntity} per l'utente specificato
     */
    List<AdminRequestLocalJpaEntity> findByActingUserId(String actingUserId);

    /**
     * Recupera tutte le richieste amministrative per un dato ID utente agente
     * e stato.
     *
     * @param actingUserId l'ID dell'utente agente
     * @param status       lo stato della richiesta (es. PENDING, COMPLETED, FAILED)
     * @return una lista di entità {@link AdminRequestLocalJpaEntity} filtrate per utente e stato
     */
    List<AdminRequestLocalJpaEntity> findByActingUserIdAndStatus(String actingUserId, String status);

    /**
     * Recupera tutte le richieste amministrative con un determinato stato e
     * con data di creazione antecedente alla soglia specificata.
     *
     * @param status    lo stato delle richieste da cercare
     * @param threshold l'istante di soglia temporale (createdAt antecedente)
     * @return una lista di entità {@link AdminRequestLocalJpaEntity} scadute per stato
     */
    List<AdminRequestLocalJpaEntity> findByStatusAndCreatedAtBefore(String status, Instant threshold);

    /**
     * Aggiorna lo stato di una richiesta amministrativa a {@code COMPLETED},
     * impostando il risultato e la data di completamento, solo se la richiesta
     * si trova attualmente nello stato {@code PENDING}.
     *
     * @param requestId  l'identificativo univoco della richiesta
     * @param resultData i dati di risultato della richiesta completata
     * @param now        l'istante di completamento
     * @return il numero di righe aggiornate (0 se la richiesta non era in PENDING)
     */
    @Modifying
    @Query("UPDATE AdminRequestLocalJpaEntity a " +
           "SET a.status = 'COMPLETED', a.resultData = :resultData, a.completedAt = :now " +
           "WHERE a.requestId = :requestId AND a.status = 'PENDING'")
    int markCompleted(@Param("requestId") String requestId,
                     @Param("resultData") String resultData,
                     @Param("now") Instant now);

    /**
     * Aggiorna lo stato di una richiesta amministrativa a {@code FAILED},
     * impostando il motivo del fallimento e la data di completamento, solo
     * se la richiesta si trova attualmente nello stato {@code PENDING}.
     *
     * @param requestId l'identificativo univoco della richiesta
     * @param reason    il motivo del fallimento
     * @param now       l'istante di completamento
     * @return il numero di righe aggiornate (0 se la richiesta non era in PENDING)
     */
    @Modifying
    @Query("UPDATE AdminRequestLocalJpaEntity a " +
           "SET a.status = 'FAILED', a.resultData = :reason, a.completedAt = :now " +
           "WHERE a.requestId = :requestId AND a.status = 'PENDING'")
    int markFailed(@Param("requestId") String requestId,
                   @Param("reason") String reason,
                   @Param("now") Instant now);
}
