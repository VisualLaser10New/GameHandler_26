package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Porta di persistenza per le statistiche aggregate per edificio e tipo di gioco.
 *
 * <p>Fornisce operazioni di salvataggio e consultazione delle statistiche
 * aggregate, incluse varianti con lock pessimistico per garantire la
 * thread-safety negli incrementi concorrenti.</p>
 *
 * @see AggregatedStatistics
 * @see #findByBuildingAndTypeAndPeriodWithLock(BuildingId, GameType, LocalDate)
 */
public interface StatisticsRepository {

    /**
     * Salva o aggiorna le statistiche aggregate fornite.
     *
     * @param stats le statistiche aggregate da persistere; non devono essere {@code null}
     * @return le statistiche salvate, eventualmente arricchite di metadati di persistenza
     * @throws IllegalArgumentException se {@code stats} è {@code null}
     */
    AggregatedStatistics save(AggregatedStatistics stats);

    /**
     * Restituisce le statistiche aggregate per l'edificio, il tipo di gioco e il
     * periodo indicati.
     *
     * @param buildingId   l'identificativo dell'edificio; non deve essere {@code null}
     * @param gameType     il tipo di gioco; non deve essere {@code null}
     * @param periodStart  la data di inizio del periodo; non deve essere {@code null}
     * @return un {@link Optional} contenente le statistiche trovate, o vuoto se assenti
     * @throws IllegalArgumentException se uno dei parametri è {@code null}
     */
    Optional<AggregatedStatistics> findByBuildingAndTypeAndPeriod(BuildingId buildingId, GameType gameType, LocalDate periodStart);

    /**
     * Restituisce le statistiche aggregate per l'edificio, il tipo di gioco e il
     * periodo indicati acquisendo un lock pessimistico in scrittura.
     *
     * <p>Da utilizzare all'interno di una transazione attiva per aggiornamenti
     * concorrenti sicuri.</p>
     *
     * @param buildingId   l'identificativo dell'edificio; non deve essere {@code null}
     * @param gameType     il tipo di gioco; non deve essere {@code null}
     * @param periodStart  la data di inizio del periodo; non deve essere {@code null}
     * @return un {@link Optional} contenente le statistiche trovate e bloccate, o vuoto se assenti
     * @throws IllegalArgumentException se uno dei parametri è {@code null}
     * @throws IllegalStateException    se non è attiva alcuna transazione
     * @see #findByBuildingAndTypeAndPeriod(BuildingId, GameType, LocalDate)
     */
    Optional<AggregatedStatistics> findByBuildingAndTypeAndPeriodWithLock(BuildingId buildingId, GameType gameType, LocalDate periodStart);

    /**
     * Restituisce le statistiche aggregate comprese nell'intervallo di periodi
     * indicato, estremi inclusi, per l'edificio e il tipo di gioco dati.
     *
     * @param buildingId l'identificativo dell'edificio; non deve essere {@code null}
     * @param gameType   il tipo di gioco; non deve essere {@code null}
     * @param start      la data di inizio dell'intervallo; non deve essere {@code null}
     * @param end        la data di fine dell'intervallo; non deve essere {@code null} e non precedente a {@code start}
     * @return la lista delle statistiche nel periodo; mai {@code null}, eventualmente vuota
     * @throws IllegalArgumentException se un parametro è {@code null} o se {@code end} precede {@code start}
     */
    List<AggregatedStatistics> findByPeriod(BuildingId buildingId, GameType gameType, LocalDate start, LocalDate end);
}
