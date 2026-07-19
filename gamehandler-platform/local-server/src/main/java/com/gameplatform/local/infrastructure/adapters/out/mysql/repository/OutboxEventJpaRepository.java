package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Interfaccia Spring Data JPA per l'entità {@link OutboxEventJpaEntity}.
 * Gestisce il pattern outbox per gli eventi di dominio, fornendo operazioni
 * di ricerca, aggiornamento bulk dello stato, gestione dei tentativi e
 * pulizia degli eventi già inviati. Le operazioni di modifica bulk sono
 * condizionali sullo stato {@code PENDING} per garantire idempotenza.
 *
 * @see OutboxEventJpaEntity
 */
@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, String> {
    /**
     * Recupera tutti gli eventi outbox con lo stato specificato, ordinati
     * per data di creazione ascendente.
     *
     * @param status lo stato degli eventi da cercare (es. PENDING, SENT, FAILED)
     * @return una lista di entità {@link OutboxEventJpaEntity} ordinate per createdAt
     */
    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * Recupera tutti gli eventi outbox con lo stato specificato, ordinati
     * per data di creazione ascendente, con supporto alla paginazione.
     *
     * @param status   lo stato degli eventi da cercare
     * @param pageable le informazioni di paginazione e ordinamento
     * @return una lista paginata di entità {@link OutboxEventJpaEntity}
     */
    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    /**
     * Recupera tutti gli eventi outbox per tipo di evento e stato specificati.
     *
     * @param eventType il tipo di evento
     * @param status    lo stato dell'evento
     * @return una lista di entità {@link OutboxEventJpaEntity} per tipo e stato
     */
    List<OutboxEventJpaEntity> findByEventTypeAndStatus(String eventType, String status);

    /**
     * Aggiornamento bulk dello stato a {@code SENT} per tutti gli eventi
     * outbox con ID specificati e stato corrente {@code PENDING}. Imposta
     * anche il timestamp di invio. Operazione eseguita in una singola
     * istruzione SQL.
     *
     * @param ids la lista degli ID degli eventi da marcare come inviati
     * @param now l'istante di invio
     * @return il numero di righe aggiornate
     */
    @Modifying
    @Query("UPDATE OutboxEventJpaEntity e " +
           "SET e.status = 'SENT', e.sentAt = :now " +
           "WHERE e.id IN :ids AND e.status = 'PENDING'")
    int markAsSentBatch(@Param("ids") List<String> ids, @Param("now") Instant now);

    /**
     * Incremento bulk del contatore di tentativi per tutti gli eventi outbox
     * con ID specificati e stato corrente {@code PENDING}. Un'istruzione
     * separata porta a {@code FAILED} quelli che hanno raggiunto la soglia.
     * Entrambe le istruzioni devono essere eseguite nella stessa transazione.
     *
     * @param ids la lista degli ID degli eventi da incrementare
     * @return il numero di righe aggiornate
     * @see com.gameplatform.local.infrastructure.adapters.out.mysql.adapter.OutboxEventRepositoryAdapter#incrementRetryBatch
     */
    @Modifying
    @Query("UPDATE OutboxEventJpaEntity e " +
           "SET e.retryCount = e.retryCount + 1 " +
           "WHERE e.id IN :ids AND e.status = 'PENDING'")
    int incrementRetryBatch(@Param("ids") List<String> ids);

    /**
     * Aggiornamento bulk dello stato a {@code FAILED} per tutti gli eventi
     * outbox con ID specificati, stato {@code PENDING} e contatore tentativi
     * uguale o superiore alla soglia indicata.
     *
     * @param ids       la lista degli ID degli eventi da marcare come falliti
     * @param threshold il numero massimo di tentativi consentiti
     * @return il numero di righe aggiornate
     */
    @Modifying
    @Query("UPDATE OutboxEventJpaEntity e " +
           "SET e.status = 'FAILED' " +
           "WHERE e.id IN :ids AND e.status = 'PENDING' AND e.retryCount >= :threshold")
    int markAsFailedAboveThreshold(@Param("ids") List<String> ids, @Param("threshold") int threshold);

    /**
     * Eliminazione bulk delle righe outbox con stato {@code SENT} e data
     * di invio antecedente alla soglia specificata. Operazione eseguita in
     * una singola istruzione SQL per la pulizia periodica degli eventi
     * processati.
     *
     * @param cutoff l'istante di taglio temporale; le righe con sentAt
     *               antecedente vengono eliminate
     * @return il numero di righe eliminate
     */
    @Modifying
    @Query("DELETE FROM OutboxEventJpaEntity e WHERE e.status = 'SENT' AND e.sentAt < :cutoff")
    int deleteSentOlderThan(@Param("cutoff") Instant cutoff);
}
