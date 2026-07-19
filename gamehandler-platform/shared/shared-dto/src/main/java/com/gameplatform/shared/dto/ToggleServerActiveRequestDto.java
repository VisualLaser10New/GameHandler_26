package com.gameplatform.shared.dto;

/**
 * DTO di richiesta per l'endpoint {@code PATCH /api/admin/servers/{buildingId}/active}
 * riservato al ruolo {@code PLATFORM_ADMIN} (Feature 3). Rappresenta il payload
 * necessario per invertire o impostare il valore del flag {@code is_active} di una
 * proiezione di server locale registrata sul nodo Local che elabora la richiesta.
 *
 * @param active lo stato di attivazione desiderato: {@code true} per attivare il server,
 *               {@code false} per disattivarlo
 *
 * @see com.gameplatform.shared.dto
 */
public record ToggleServerActiveRequestDto(
        boolean active
) {
}