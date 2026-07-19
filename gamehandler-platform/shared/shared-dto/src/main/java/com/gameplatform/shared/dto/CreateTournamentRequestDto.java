package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * DTO di richiesta utilizzato per creare un nuovo torneo sulla piattaforma.
 * Trasporta i dati essenziali forniti dal client, quali denominazione, tipologia di gioco,
 * modalità a squadre, dimensione della squadra, data di avvio e gli identificativi
 * degli edifici che ospitano il torneo.
 *
 * @see GameType
 */
public record CreateTournamentRequestDto(
        @NotBlank(message = "name must not be blank") String name,
        @NotNull(message = "gameType must not be null") GameType gameType,
        boolean teamBased,
        @Min(value = 1, message = "teamSize must be at least 1") int teamSize,
        @NotNull(message = "startsAt must not be null") Instant startsAt,
        @NotNull(message = "buildingIds must not be null")
        @Size(min = 2, message = "buildingIds must contain at least 2 buildings") List<String> buildingIds
) {
}
