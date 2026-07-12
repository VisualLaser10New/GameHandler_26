package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

public record UserSyncDto(
    String userId,
    String username,
    String email,
    String hashedPassword,
    List<String> roles,
    Instant occurredAt,
    String originatingRequestId
) {
    public UserSyncDto(String userId, String username, String hashedPassword, List<String> roles) {
        this(userId, username, null, hashedPassword, roles, null, null);
    }

    public UserSyncDto(String userId, String username, String email, String hashedPassword,
                       List<String> roles, Instant occurredAt) {
        this(userId, username, email, hashedPassword, roles, occurredAt, null);
    }
}