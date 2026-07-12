package com.gameplatform.shared.dto;

import java.util.List;

/**
 * DTO returned by the {@code GET /api/auth/me} Local endpoint
 * (PIANO §7.B). Carries the authenticated user's username plus the
 * enriched {@code userId}, {@code roles} (resolved from the locally
 * replicated {@code replicated_users} table) and {@code buildings}
 * (resolved from {@code local_admin_buildings_local} when the user is
 * a {@code LOCAL_ADMIN}; empty for non-admin roles).
 *
 * <p>The short single-arg constructor {@link #UserInfoDto(String)} is
 * retained for backward compatibility with the FASE 2 contract and
 * delegates to the canonical 4-arg constructor with {@code null} userId
 * and empty lists.</p>
 *
 * @param username  the authenticated user's username
 * @param userId    the authenticated user's id (nullable when not yet
 *                  resolved, e.g. a stubbed unit-test response)
 * @param roles     the user roles list (possibly empty, never null)
 * @param buildings the buildings this user is a LOCAL_ADMIN of
 *                  (possibly empty, never null)
 */
public record UserInfoDto(
        String username,
        String userId,
        List<String> roles,
        List<String> buildings
) {
    /** Backward-compatible short constructor. */
    public UserInfoDto(String username) {
        this(username, null, List.of(), List.of());
    }
}
