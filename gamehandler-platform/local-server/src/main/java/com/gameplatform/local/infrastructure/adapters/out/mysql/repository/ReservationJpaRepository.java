package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.ReservationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Interfaccia Spring Data JPA per l'entità {@link ReservationJpaEntity}.
 * Gestisce le prenotazioni delle macchine da gioco, consentendo la ricerca
 * per utente, macchina, stato e scadenza temporale, nonché il conteggio
 * delle prenotazioni per gruppo di macchine.
 *
 * @see ReservationJpaEntity
 */
@Repository
public interface ReservationJpaRepository extends JpaRepository<ReservationJpaEntity, String> {
    /**
     * Recupera tutte le prenotazioni effettuate dall'utente specificato.
     *
     * @param userId l'ID dell'utente che ha effettuato la prenotazione
     * @return una lista di entità {@link ReservationJpaEntity} per l'utente indicato
     */
    List<ReservationJpaEntity> findByUserId(String userId);

    /**
     * Recupera tutte le prenotazioni per la macchina da gioco specificata.
     *
     * @param gameId l'ID della macchina da gioco prenotata
     * @return una lista di entità {@link ReservationJpaEntity} per la macchina indicata
     */
    List<ReservationJpaEntity> findByGameId(String gameId);

    /**
     * Recupera tutte le prenotazioni con lo stato specificato.
     *
     * @param status lo stato della prenotazione (es. "ACTIVE", "COMPLETED", "CANCELLED")
     * @return una lista di entità {@link ReservationJpaEntity} con lo stato indicato
     */
    List<ReservationJpaEntity> findByStatus(String status);

    /**
     * Recupera tutte le prenotazioni con un determinato stato e data di
     * fine antecedente alla soglia specificata.
     *
     * @param status  lo stato della prenotazione
     * @param endTime l'istante di soglia per la data di fine
     * @return una lista di entità {@link ReservationJpaEntity} scadute per stato
     */
    List<ReservationJpaEntity> findByStatusAndEndTimeBefore(String status, Instant endTime);

    /**
     * Recupera tutte le prenotazioni con stato compreso nella collezione
     * specificata e data di fine antecedente alla soglia indicata.
     *
     * @param statuses la collezione di stati validi
     * @param endTime  l'istante di soglia per la data di fine
     * @return una lista di entità {@link ReservationJpaEntity} scadute per stati multipli
     */
    List<ReservationJpaEntity> findByStatusInAndEndTimeBefore(Collection<String> statuses, Instant endTime);

    /**
     * Conta il numero di prenotazioni associate a un insieme di macchine
     * da gioco.
     *
     * @param gameIds la collezione di ID delle macchine da gioco
     * @return il numero di prenotazioni per le macchine indicate
     */
    int countByGameIdIn(Collection<String> gameIds);
}
