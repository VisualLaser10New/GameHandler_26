package com.gameplatform.shared.dto;

/**
 * DTO returned by the {@code GET /api/auth/me} endpoint.
 * Contains the username of the currently authenticated user.
 *
 * @param username the authenticated user's username
 */
public record UserInfoDto(String username) {}
