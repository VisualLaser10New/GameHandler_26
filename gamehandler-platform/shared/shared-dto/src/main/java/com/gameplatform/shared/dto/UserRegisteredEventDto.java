package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

public record UserRegisteredEventDto(
    String userId,
    String username,
    String email,
    String hashedPassword,
    List<String> roles,
    Instant createdAt
) {}
