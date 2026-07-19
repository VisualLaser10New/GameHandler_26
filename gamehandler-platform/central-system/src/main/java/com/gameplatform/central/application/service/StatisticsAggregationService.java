package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.ports.in.GetGlobalStatisticsUseCase;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.StatisticsDto;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servizio applicativo lato lettura per le statistiche globali aggregate
 * (FASE 7). Implementa {@link GetGlobalStatisticsUseCase} leggendo le righe
 * {@code aggregated_statistics} dal repository e convertendole in DTO.
 *
 * <p>I parametri di filtro sono opzionali: quando {@code buildingId} o
 * {@code gameType} sono {@code null} la query non applica il relativo
 * vincolo; quando {@code start}/{@code end} sono {@code null} considera
 * l'intero periodo disponibile.</p>
 *
 * @see GetGlobalStatisticsUseCase
 * @see com.gameplatform.central.domain.ports.out.StatisticsRepository
 */
@Service
public class StatisticsAggregationService implements GetGlobalStatisticsUseCase {
    private final StatisticsRepository repository;
    private final ObjectMapper objectMapper;

    public StatisticsAggregationService(StatisticsRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Restituisce le statistiche aggregate filtrate per edificio, tipo di gioco
     * e periodo temporale.
     *
     * @param buildingId l'edificio di riferimento, o {@code null} per tutti
     * @param gameType il tipo di gioco, o {@code null} per tutti i tipi
     * @param start la data di inizio periodo (inclusa), o {@code null}
     * @param end la data di fine periodo (inclusa), o {@code null}
     * @return la lista dei DTO di statistica corrispondenti al filtro; lista
     *         vuota (mai {@code null}) se nessuna riga corrisponde
     */
    @Override
    public List<StatisticsDto> getStatistics(BuildingId buildingId, GameType gameType, LocalDate start, LocalDate end) {
        List<AggregatedStatistics> rawStats = repository.findByPeriod(buildingId, gameType, start, end);

        return rawStats.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Converte una riga {@link AggregatedStatistics} nel relativo DTO,
     * serializzando in JSON il campo {@code data} quando presente.
     *
     * @param stats la statistica aggregata persistita da convertire (non deve
     *        essere {@code null})
     * @return il DTO di statistica con periodi convertiti in istanti UTC
     * @throws RuntimeException se la serializzazione JSON del campo {@code data}
     *         fallisce
     */
    private StatisticsDto toDto(AggregatedStatistics stats) {
        String jsonData = null;
        if (stats.getData() != null && !stats.getData().isEmpty()) {
            try {
                jsonData = objectMapper.writeValueAsString(stats.getData());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize statistics metadata map", e);
            }
        }

        return new StatisticsDto(
                stats.getBuildingId() != null ? stats.getBuildingId().id() : null,
                stats.getGameType() != null ? stats.getGameType().name() : null,
                stats.getPeriodStart() != null ? stats.getPeriodStart().atStartOfDay(ZoneOffset.UTC).toInstant() : null,
                stats.getPeriodEnd() != null ? stats.getPeriodEnd().atTime(java.time.LocalTime.MAX).atZone(ZoneOffset.UTC).toInstant() : null,
                stats.getTotalSessions(),
                stats.getAvgDurationSeconds(),
                stats.getTotalReservations(),
                jsonData,
                stats.getTotalAbortedSessions()
        );
    }
}
