package com.gameplatform.shared.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO (Data Transfer Object) che trasporta l'identificativo del torneo per il quale
 * avviare la pianificazione degli incontri. Viene impiegato nei flussi di schedulazione
 * dei match di un torneo all'interno della piattaforma di gioco.
 *
 * @see com.gameplatform.shared.dto.TournamentDto
 */
public record ScheduleTournamentMatchesDto(
        @NotBlank(message = "tournamentId must not be blank") String tournamentId
) {
}
