package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerStatisticsId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerStatisticsJpaEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA per l'accesso ai dati delle statistiche dei giocatori.
 * <p>
 * Fornisce metodi per interrogare le statistiche individuali per utente e tipo
 * di gioco. Il metodo {@link #findByUserIdAndGameTypeForUpdate} acquisisce un
 * blocco pessimistico di scrittura per serializzare proiezioni concorrenti
 * sullo stesso giocatore e tipo di gioco, garantendo l'atomicit&agrave;
 * dell'incremento di partite giocate e vinte.
 * </p>
 *
 * @see PlayerStatisticsJpaEntity
 * @see PlayerStatisticsId
 * @see StatisticsJpaRepository
 */
@Repository
public interface PlayerStatisticsJpaRepository extends JpaRepository<PlayerStatisticsJpaEntity, PlayerStatisticsId> {

    /**
     * Restituisce tutte le statistiche associate all'identificativo utente specificato.
     *
     * @param userId l'identificativo dell'utente (non null)
     * @return una lista di statistiche del giocatore, vuota se l'utente non ha statistiche registrate
     */
    List<PlayerStatisticsJpaEntity> findByUserId(String userId);

    /**
     * Restituisce le statistiche dell'utente per il tipo di gioco specificato, se presenti.
     *
     * @param userId   l'identificativo dell'utente (non null)
     * @param gameType il tipo di gioco (non null)
     * @return un {@code Optional} contenente le statistiche se trovate, vuoto altrimenti
     */
    Optional<PlayerStatisticsJpaEntity> findByUserIdAndGameType(String userId, String gameType);

    /**
     * Restituisce le statistiche dell'utente per il tipo di gioco specificato, acquisendo
     * un blocco pessimistico di scrittura per prevenire aggiornamenti concorrenti.
     *
     * @param userId   l'identificativo dell'utente (non null)
     * @param gameType il tipo di gioco (non null)
     * @return un {@code Optional} contenente le statistiche con blocco pessimistico se trovate, vuoto altrimenti
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PlayerStatisticsJpaEntity s WHERE s.userId = :userId AND s.gameType = :gameType")
    Optional<PlayerStatisticsJpaEntity> findByUserIdAndGameTypeForUpdate(
            @Param("userId") String userId,
            @Param("gameType") String gameType);
}