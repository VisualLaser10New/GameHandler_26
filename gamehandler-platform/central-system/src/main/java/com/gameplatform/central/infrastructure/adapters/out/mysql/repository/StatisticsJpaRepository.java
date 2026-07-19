package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.AggregatedStatisticsJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository JPA per l'accesso ai dati delle statistiche aggregate.
 * <p>
 * Fornisce metodi per interrogare le statistiche aggregate per edificio, tipo
 * di gioco e periodo, inclusa una query con blocco pessimistico per
 * aggiornamenti atomici. Supporta la ricerca per intervallo di date e
 * filtri opzionali su edificio e tipo di gioco.
 * </p>
 *
 * @see AggregatedStatisticsJpaEntity
 * @see PlayerStatisticsJpaRepository
 */
@Repository
public interface StatisticsJpaRepository extends JpaRepository<AggregatedStatisticsJpaEntity, String> {

    /**
     * Restituisce le statistiche aggregate per l'edificio, il tipo di gioco e
     * la data di inizio periodo specificati, se presenti.
     *
     * @param buildingId  l'identificativo dell'edificio (non null)
     * @param gameType    il tipo di gioco (non null)
     * @param periodStart la data di inizio del periodo (non null)
     * @return un {@code Optional} contenente le statistiche aggregate se trovate, vuoto altrimenti
     */
    Optional<AggregatedStatisticsJpaEntity> findByBuildingIdAndGameTypeAndPeriodStart(String buildingId, String gameType, LocalDate periodStart);

    /**
     * Restituisce le statistiche aggregate per l'edificio, il tipo di gioco e
     * la data di inizio periodo specificati, acquisendo un blocco pessimistico
     * di scrittura per prevenire aggiornamenti concorrenti.
     *
     * @param buildingId  l'identificativo dell'edificio (non null)
     * @param gameType    il tipo di gioco (non null)
     * @param periodStart la data di inizio del periodo (non null)
     * @return un {@code Optional} contenente le statistiche aggregate con blocco pessimistico se trovate, vuoto altrimenti
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM AggregatedStatisticsJpaEntity s WHERE s.buildingId = :buildingId AND s.gameType = :gameType AND s.periodStart = :periodStart")
    Optional<AggregatedStatisticsJpaEntity> findByBuildingIdAndGameTypeAndPeriodStartWithLock(
            @Param("buildingId") String buildingId,
            @Param("gameType") String gameType,
            @Param("periodStart") LocalDate periodStart);

    /**
     * Restituisce tutte le statistiche aggregate il cui periodo ricade
     * nell'intervallo di date specificato.
     *
     * @param start la data di inizio intervallo (inclusiva, non null)
     * @param end   la data di fine intervallo (inclusiva, non null)
     * @return una lista di statistiche aggregate nel periodo specificato,
     *         vuota se nessuna statistica corrisponde
     */
    List<AggregatedStatisticsJpaEntity> findByPeriodStartGreaterThanEqualAndPeriodEndLessThanEqual(LocalDate start, LocalDate end);

    /**
     * Restituisce le statistiche aggregate in base ai criteri di filtro opzionali specificati.
     * I parametri con valore {@code null} vengono ignorati nel filtro.
     *
     * @param buildingId l'identificativo dell'edificio da filtrare, o {@code null} per non filtrare per edificio
     * @param gameType   il tipo di gioco da filtrare, o {@code null} per non filtrare per tipo
     * @param start      la data di inizio intervallo, o {@code null} per non filtrare per data minima
     * @param end        la data di fine intervallo, o {@code null} per non filtrare per data massima
     * @return una lista di statistiche aggregate corrispondenti ai criteri,
     *         vuota se nessuna statistica corrisponde
     */
    @Query("SELECT s FROM AggregatedStatisticsJpaEntity s " +
           "WHERE (:buildingId IS NULL OR s.buildingId = :buildingId) " +
           "AND (:gameType IS NULL OR s.gameType = :gameType) " +
           "AND (:start IS NULL OR s.periodStart >= :start) " +
           "AND (:end IS NULL OR s.periodEnd <= :end)")
    List<AggregatedStatisticsJpaEntity> findByCriteria(
            @Param("buildingId") String buildingId,
            @Param("gameType") String gameType,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
