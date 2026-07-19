package com.gameplatform.shared.dto;

import java.util.List;

/**
 * DTO di risposta che elenca gli edifici associati a un utente con ruolo LOCAL_ADMIN.
 * Rappresenta l'insieme di identificativi di edificio correntemente collegati all'utente indicato.
 *
 * @param userId      identificativo dell'utente LOCAL_ADMIN; non deve essere {@code null}
 * @param buildingIds lista degli identificativi degli edifici associati all'utente;
 *                    non deve essere {@code null}; se l'utente non ha edifici associati la lista è vuota
 *
 * @see com.gameplatform.shared.dto.UserDto
 */
public record LocalAdminBuildingsDto(
        String userId,
        List<String> buildingIds
) {
}