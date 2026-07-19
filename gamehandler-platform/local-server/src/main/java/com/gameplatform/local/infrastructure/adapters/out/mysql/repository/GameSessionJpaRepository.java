package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameSessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Interfaccia Spring Data JPA per l'entità {@link GameSessionJpaEntity}.
 * Gestisce le sessioni di gioco, consentendo la ricerca per edificio,
 * stato, tipo di gioco e partecipanti. Utilizzata dal servizio di
 * statistica per il calcolo on-demand locale.
 *
 * @see GameSessionJpaEntity
 */
@Repository
public interface GameSessionJpaRepository extends JpaRepository<GameSessionJpaEntity, String> {
    /**
     * Recupera tutte le sessioni di gioco per l'edificio specificato.
     *
     * @param buildingId l'identificativo dell'edificio
     * @return una lista di entità {@link GameSessionJpaEntity} per l'edificio indicato
     */
    List<GameSessionJpaEntity> findByBuildingId(String buildingId);

    /**
     * Recupera tutte le sessioni di gioco con lo stato specificato.
     *
     * @param status lo stato della sessione (es. "ACTIVE", "COMPLETED", "CANCELLED")
     * @return una lista di entità {@link GameSessionJpaEntity} con lo stato indicato
     */
    List<GameSessionJpaEntity> findByStatus(String status);

    /**
     * Recupera tutte le sessioni di gioco per il tipo di gioco specificato.
     *
     * @param gameType il tipo di gioco (es. "football", "basketball")
     * @return una lista di entità {@link GameSessionJpaEntity} per il tipo indicato
     */
    List<GameSessionJpaEntity> findByGameType(String gameType);

    /**
     * Recupera la prima sessione di gioco associata a una specifica macchina
     * e con stato compreso tra quelli indicati.
     *
     * @param gameId   l'identificativo della macchina da gioco
     * @param statuses la collezione di stati validi per la ricerca
     * @return un {@link Optional} contenente la prima sessione trovata, oppure vuoto se non presente
     */
    Optional<GameSessionJpaEntity> findFirstByGameIdAndStatusIn(String gameId, Collection<String> statuses);

    /**
     * Recupera tutte le sessioni di gioco il cui stato è compreso nella
     * collezione specificata.
     *
     * @param statuses la collezione di stati da includere nella ricerca
     * @return una lista di entità {@link GameSessionJpaEntity} con stati corrispondenti
     */
    List<GameSessionJpaEntity> findByStatusIn(Collection<String> statuses);

    /**
     * Recupera tutte le sessioni di gioco distinte a cui ha partecipato
     * l'utente specificato, attraverso la tabella di join
     * {@code session_participants}. Utilizzato da
     * {@code StatisticsService.getPlayerStatistics} per il calcolo
     * on-demand locale delle statistiche del giocatore.
     *
     * @param userId l'ID dell'utente partecipante
     * @return una lista di entità {@link GameSessionJpaEntity} distinte per l'utente indicato
     */
    @Query("SELECT DISTINCT s FROM GameSessionJpaEntity s JOIN s.participants p WHERE p.userId = :userId")
    List<GameSessionJpaEntity> findByParticipantUserId(@Param("userId") String userId);
}
