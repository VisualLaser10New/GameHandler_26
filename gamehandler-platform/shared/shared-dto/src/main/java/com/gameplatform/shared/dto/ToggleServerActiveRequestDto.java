package com.gameplatform.shared.dto;

/**
 * Request body for the {@code PATCH /api/admin/servers/{buildingId}/active}
 * PLATFORM_ADMIN endpoint (Feature 3): toggles the {@code is_active} flag of a
 * registered local server projection on the responding Local node.
 *
 * @param active the desired active state ({@code true} to activate, {@code false} to deactivate)
 */
public record ToggleServerActiveRequestDto(
        boolean active
) {
}