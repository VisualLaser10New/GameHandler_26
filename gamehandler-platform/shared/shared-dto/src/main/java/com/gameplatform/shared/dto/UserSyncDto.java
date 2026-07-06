package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

public record UserSyncDto(
    String userId,
    String username,
    String email,
    String hashedPassword,
    List<String> roles,
    Instant occurredAt
) {
    public UserSyncDto(String userId, String username, String hashedPassword, List<String> roles) {
        this(userId, username, null, hashedPassword, roles, null);
    }
}