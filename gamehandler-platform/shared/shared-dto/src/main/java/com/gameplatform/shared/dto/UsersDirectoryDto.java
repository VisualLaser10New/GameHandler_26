package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

/**
 * Read-model projection of a user for the Local
 * {@code GET /api/admin/users} PLATFORM_ADMIN directory endpoint
 * (PIANO §7.B). Sourced from the {@code replicated_users} replica;
 * the {@code hashedPassword} is intentionally NOT exposed.
 *
 * @param userId     the user id
 * @param username   the username
 * @param email      the user email (nullable)
 * @param roles      the user roles list
 * @param updatedAt  the last mutation instant
 */
public record UsersDirectoryDto(
        String userId,
        String username,
        String email,
        List<String> roles,
        Instant updatedAt
) {
}
