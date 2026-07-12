package com.gameplatform.shared.dto;

import java.util.List;

/**
 * Response body listing the buildings bound to a LOCAL_ADMIN user.
 *
 * @param userId      the LOCAL_ADMIN user id
 * @param buildingIds the buildings currently bound to the user (empty if none)
 */
public record LocalAdminBuildingsDto(
        String userId,
        List<String> buildingIds
) {
}