package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.ports.in.GetGlobalStatisticsUseCase;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.StatisticsDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST adapter that exposes global aggregated statistics.
 *
 * <p>An invalid {@code gameType} parameter value is now caught here and re-thrown
 * as {@link IllegalArgumentException} with a descriptive message. The
 * {@link GlobalExceptionHandler} maps this to HTTP 400 Bad Request.</p>
 */
@RestController
@RequestMapping("/api/statistics")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class StatisticsController {

    private final GetGlobalStatisticsUseCase getGlobalStatisticsUseCase;

    /**
     * Costruisce il controller iniettando il caso d'uso per le statistiche globali.
     *
     * @param getGlobalStatisticsUseCase caso d'uso per il calcolo delle statistiche aggregate, non {@code null}
     */
    public StatisticsController(GetGlobalStatisticsUseCase getGlobalStatisticsUseCase) {
        this.getGlobalStatisticsUseCase = getGlobalStatisticsUseCase;
    }

    /**
     * Restituisce le statistiche globali aggregate, filtrabili per edificio, tipo di gioco e periodo.
     *
     * <p>L'accesso richiede il ruolo {@code PLATFORM_ADMIN}. I parametri di filtro sono tutti
     * opzionali: se un filtro non è fornito, le statistiche non vengono limitate su quel dominio.</p>
     *
     * @param buildingId identificativo opzionale dell'edificio per cui filtrare; se {@code null} o vuoto non filtra
     * @param gameType   tipo di gioco opzionale per filtrare; se {@code null} o vuoto non filtra
     * @param start      data di inizio opzionale del periodo (estremo incluso); se {@code null} non filtra per inizio
     * @param end        data di fine opzionale del periodo (estremo incluso); se {@code null} non filtra per fine
     * @return {@link ResponseEntity} con stato {@code 200 OK} e la lista di {@link StatisticsDto};
     *         la lista è vuota se nessun dato soddisfa i filtri
     * @throws IllegalArgumentException se {@code gameType} non corrisponde a un valore valido (mappato a {@code 400})
     * @see GlobalExceptionHandler
     */
    @GetMapping
    public ResponseEntity<List<StatisticsDto>> getStatistics(
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) String gameType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        BuildingId parsedBuildingId = (buildingId != null && !buildingId.isBlank())
                ? new BuildingId(buildingId)
                : null;

        GameType parsedGameType = null;
        if (gameType != null && !gameType.isBlank()) {
            try {
                parsedGameType = GameType.valueOf(gameType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown game type: '" + gameType + "'. Valid values are: "
                                + java.util.Arrays.toString(GameType.values()));
            }
        }

        List<StatisticsDto> statistics = getGlobalStatisticsUseCase.getStatistics(
                parsedBuildingId,
                parsedGameType,
                start,
                end
        );

        return ResponseEntity.ok(statistics);
    }
}
