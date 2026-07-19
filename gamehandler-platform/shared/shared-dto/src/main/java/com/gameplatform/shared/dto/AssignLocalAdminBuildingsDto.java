package com.gameplatform.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * DTO di richiesta per assegnare o revocare edifici a un utente di tipo LOCAL_ADMIN.
 *
 * <p>Viene utilizzato dagli endpoint del Central System {@code POST /api/admin/local/buildings}
 * e {@code DELETE /api/admin/local/buildings}. {@code userId} è l'identificativo canonico
 * (UUID) dell'amministratore, mentre {@code buildingIds} rappresenta l'insieme completo
 * degli edifici da assegnare (in caso di POST) o da revocare (in caso di DELETE).</p>
 *
 * @param userId      identificativo (UUID) dell'utente LOCAL_ADMIN; non deve essere {@code null} né vuoto
 * @param buildingIds elenco degli identificativi degli edifici da associare o dissociare; non deve essere
 *                    {@code null} né vuoto
 *
 * @see com.gameplatform.shared.dto.LocalAdminBuildingDto
 */
public record AssignLocalAdminBuildingsDto(
        @NotBlank String userId,
        @NotEmpty List<String> buildingIds
) {
}