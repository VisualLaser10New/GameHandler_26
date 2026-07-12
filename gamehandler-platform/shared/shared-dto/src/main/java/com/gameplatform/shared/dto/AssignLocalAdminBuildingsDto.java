package com.gameplatform.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body for assigning (or revoking) buildings to a LOCAL_ADMIN user.
 *
 * <p>Used by the Central System {@code POST /api/admin/local/buildings} and
 * {@code DELETE /api/admin/local/buildings} endpoints. {@code userId} is the
 * canonical user id (UUID) of the administrator; {@code buildingIds} is the full
 * set of buildings to assign (for POST) or revoke (for DELETE).</p>
 *
 * @param userId      the LOCAL_ADMIN user id (UUID)
 * @param buildingIds the building ids to bind/unbind
 */
public record AssignLocalAdminBuildingsDto(
        @NotBlank String userId,
        @NotEmpty List<String> buildingIds
) {
}