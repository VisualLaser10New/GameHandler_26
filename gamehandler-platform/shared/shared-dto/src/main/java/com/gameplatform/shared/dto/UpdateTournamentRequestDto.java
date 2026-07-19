package com.gameplatform.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * DTO di richiesta per l'operazione {@code PUT /api/tournaments/{id}}
 * (caso d'uso §7.A.1).
 *
 * <p>Trasporta i tre campi mutabili di un torneo in stato {@code DRAFT}:
 * il {@code name}, lo {@code startsAt} e la lista {@code buildingIds}.
 * I messaggi di validazione rispecchiano quelli definiti in
 * {@link CreateTournamentRequestDto}.</p>
 *
 * <p>I componenti del record rappresentano i seguenti dati:
 * <ul>
 *     <li>{@code name} - il nome del torneo, non vuoto;</li>
 *     <li>{@code startsAt} - l'istante di avvio del torneo, non nullo;</li>
 *     <li>{@code buildingIds} - gli identificativi degli edifici coinvolti,
 *     non nulli e di cardinalità minima pari a 2.</li>
 * </ul>
 * </p>
 *
 * @param name        il nome del torneo, non deve essere vuoto.
 * @param startsAt    l'istante di avvio pianificato del torneo, non deve essere nullo.
 * @param buildingIds la lista degli identificativi degli edifici partecipanti,
 *                    non nulla e contenente almeno 2 elementi.
 *
 * @see CreateTournamentRequestDto
 */
public record UpdateTournamentRequestDto(
        @NotBlank(message = "name must not be blank") String name,
        @NotNull(message = "startsAt must not be null") Instant startsAt,
        @NotNull(message = "buildingIds must not be null")
        @Size(min = 2, message = "buildingIds must contain at least 2 buildings") List<String> buildingIds
) {
}