package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.StatisticsDto;
import java.time.LocalDate;
import java.util.List;

/**
 * Caso d'uso di lettura che aggrega le statistiche globali di gioco a livello
 * di sistema centrale, filtrabili per struttura, tipo di gioco e intervallo temporale.
 */
public interface GetGlobalStatisticsUseCase {

    /**
     * Restituisce le statistiche aggregate in base ai filtri specificati.
     *
     * @param buildingId l'identificativo della struttura di cui calcolare le statistiche; se {@code null} sono incluse tutte le strutture
     * @param gameType il tipo di gioco su cui filtrare le statistiche; se {@code null} sono inclusi tutti i tipi di gioco
     * @param start la data di inizio dell'intervallo temporale (inclusa); se {@code null} non viene applicato un limite inferiore
     * @param end la data di fine dell'intervallo temporale (inclusa); se {@code null} non viene applicato un limite superiore
     * @return la lista di {@link StatisticsDto} contenente le statistiche richieste; la lista è vuota se nessun dato soddisfa i filtri
     */
    List<StatisticsDto> getStatistics(BuildingId buildingId, GameType gameType, LocalDate start, LocalDate end);
}

