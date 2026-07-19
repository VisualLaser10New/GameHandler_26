package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Repository JPA per l'accesso ai dati degli eventi della coda di outbox.
 * <p>
 * Fornisce metodi per interrogare gli eventi in base al loro stato e tipo,
 * supportando sia la pubblicazione ordinata verso i server locali sia il
 * recupero degli eventi in sospeso per la replica verso server appena registrati.
 * Include una query nativa per il conteggio degli eventi di replica utente
 * ancora pendenti per un dato server.
 * </p>
 *
 * @see OutboxEventJpaEntity
 * @see ReplicationProgressJpaRepository
 */
@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, String> {

    /**
     * Restituisce tutti gli eventi con lo stato specificato, ordinati per data di creazione crescente.
     *
     * @param status lo stato degli eventi da filtrare (non null)
     * @return una lista di eventi con lo stato indicato, ordinati dal pi&ugrave; vecchio al pi&ugrave; recente,
     *         vuota se non ci sono eventi con quello stato
     */
    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * Restituisce gli eventi con lo stato specificato, ordinati per data di creazione crescente,
     * applicando la paginazione fornita.
     *
     * @param status   lo stato degli eventi da filtrare (non null)
     * @param pageable oggetto di paginazione che specifica offset e limite (non null)
     * @return una pagina di eventi con lo stato indicato, ordinati dal pi&ugrave; vecchio al pi&ugrave; recente,
     *         vuota se non ci sono eventi corrispondenti
     */
    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    /**
     * Restituisce gli eventi con lo stato specificato e il cui tipo &egrave; contenuto
     * nella collezione indicata, ordinati per data di creazione crescente.
     *
     * @param status     lo stato degli eventi da filtrare (non null)
     * @param eventTypes la collezione dei tipi di evento da includere (non null, pu&ograve; essere vuota)
     * @return una lista di eventi corrispondenti, ordinati dal pi&ugrave; vecchio al pi&ugrave; recente,
     *         vuota se non ci sono eventi corrispondenti
     */
    List<OutboxEventJpaEntity> findByStatusAndEventTypeInOrderByCreatedAtAsc(String status, Collection<String> eventTypes);

    /**
     * Restituisce gli eventi il cui stato &egrave; contenuto nella collezione specificata
     * e il cui tipo &egrave; contenuto nella collezione indicata, ordinati per data di creazione crescente.
     * Utilizzato per il recupero degli eventi di replica utente verso server appena registrati.
     *
     * @param statuses   la collezione degli stati degli eventi da includere (non null, pu&ograve; essere vuota)
     * @param eventTypes la collezione dei tipi di evento da includere (non null, pu&ograve; essere vuota)
     * @return una lista di eventi corrispondenti, ordinati dal pi&ugrave; vecchio al pi&ugrave; recente,
     *         vuota se non ci sono eventi corrispondenti
     */
    List<OutboxEventJpaEntity> findByStatusInAndEventTypeInOrderByCreatedAtAsc(
            Collection<String> statuses, Collection<String> eventTypes);

    /**
     * Conta gli eventi di replica utente ({@code USER_REGISTERED}, {@code USER_UPDATED})
     * con stato diverso da {@code SENT} e per i quali non esiste ancora una riga di
     * avanzamento replicazione per il server specificato.
     *
     * @param serverId l'identificativo dell'edificio del server locale (non null)
     * @return il numero di eventi di replica utente ancora pendenti per il server (zero o positivo)
     */
    @Query("select count(e) from OutboxEventJpaEntity e " +
            "where e.eventType in ('USER_REGISTERED','USER_UPDATED') " +
            "and e.status <> 'SENT' " +
            "and not exists (select rp from ReplicationProgressJpaEntity rp " +
            "where rp.eventId = e.id and rp.serverId = :serverId)")
    long countPendingReplicationForServer(@Param("serverId") String serverId);
}
