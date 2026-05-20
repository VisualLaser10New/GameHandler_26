package main.java.com.gameplatform.shared.dto;

import java.time.Instant;

public record LoginResponseDto(
    String token,
    String userId,
    Instant expiresAt
) {}
