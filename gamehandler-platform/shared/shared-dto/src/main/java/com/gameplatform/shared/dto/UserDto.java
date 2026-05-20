package com.gameplatform.shared.dto;

import java.time.Instant;
import java.util.List;

public record UserDto(
    String id,
    String username,
    String email,
    List<String> roles,
    Instant createdAt
) {}
